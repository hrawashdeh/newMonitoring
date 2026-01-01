// src/main/java/com/tiqmo/monitoring/loader/probe/DbPermissionInspector.java
package com.tiqmo.monitoring.loader.probe;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DbPermissionInspector {

  private enum DbType { POSTGRES, MYSQL, UNKNOWN }

  public PermissionReport inspect(String sourceCode, HikariDataSource ds) {
    var b = PermissionReport.builder(sourceCode);

    try (Connection c = ds.getConnection()) {
      DbType type = detect(c);

      switch (type) {
        case POSTGRES -> checkPostgres(b, c);
        case MYSQL -> checkMySql(b, c);
        default -> b.addViolation("Unknown DB type — cannot verify privileges");
      }
    } catch (Exception e) {
      b.addViolation("Failed to inspect privileges: " + rootMsg(e));
      log.warn("Privilege inspection failed for {}: {}", sourceCode, e.toString());
    }
    return b.build();
  }

  private DbType detect(Connection c) {
    try {
      String prod = c.getMetaData().getDatabaseProductName();
      if (prod == null) return DbType.UNKNOWN;
      String p = prod.toLowerCase(Locale.ROOT);
      if (p.contains("postgres")) return DbType.POSTGRES;
      if (p.contains("mysql") || p.contains("mariadb")) return DbType.MYSQL;
      return DbType.UNKNOWN;
    } catch (SQLException e) {
      return DbType.UNKNOWN;
    }
  }

  // --------- PostgreSQL ----------
  // Policy: allow only SELECT on tables/views. Disallow INSERT/UPDATE/DELETE/TRUNCATE and CREATE on schemas.
  private void checkPostgres(PermissionReport.Builder b, Connection c) throws SQLException {
    // 1) Any table-level privileges other than SELECT?
    // include role memberships for current_user
    String roleSql = """
      with my_roles as (
        select r.oid, r.rolname
        from pg_roles r
        where pg_has_role(current_user, r.oid, 'member')
        union all
        select NULL::oid, current_user
      )
      select distinct tp.privilege_type, tp.table_schema, tp.table_name
      from information_schema.table_privileges tp
      join my_roles mr on tp.grantee = mr.rolname
      where tp.privilege_type not in ('SELECT')
      order by tp.privilege_type
      """;
    try (PreparedStatement ps = c.prepareStatement(roleSql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        String priv = rs.getString(1);
        String schema = rs.getString(2);
        String table = rs.getString(3);
        b.addViolation("PG table privilege: " + priv + " on " + schema + "." + table);
      }
    }

    // 2) Any CREATE on non-system schemas?
    String schemaSql = """
      select n.nspname,
             has_schema_privilege(current_user, n.nspname, 'CREATE') as can_create
      from pg_namespace n
      where n.nspname not in ('pg_catalog','information_schema')
      """;
    try (PreparedStatement ps = c.prepareStatement(schemaSql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        String schema = rs.getString("nspname");
        boolean canCreate = rs.getBoolean("can_create");
        if (canCreate) {
          b.addViolation("PG schema privilege: CREATE on schema " + schema);
        }
      }
    }

    // 3) Owned objects (ownership implies full control)
    String ownedSql = """
      select 'table' as kind, schemaname, tablename
      from pg_tables where tableowner = current_user
      union all
      select 'view'  as kind, schemaname, viewname
      from pg_views where viewowner = current_user
      """;
    try (PreparedStatement ps = c.prepareStatement(ownedSql);
         ResultSet rs = ps.executeQuery()) {
      if (rs.next()) {
        b.addViolation("PG ownership detected: user owns at least one table/view (implies write/DDL).");
      }
    }
  }

  private static boolean isTrue(Boolean b) { return b != null && b; }

  private static boolean containsAny(String haystack, List<String> words, Set<String> whitelist) {
    for (String w : words) {
      if (whitelist.contains(w)) continue;
      if (haystack.contains(w)) return true;
    }
    return false;
  }

  private static Boolean readVarAsBoolean(Connection c, String sql) {
    try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
      if (rs.next()) {
        Object v = rs.getObject(1);
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = v.toString().trim();
        // MySQL returns "ON"/"OFF" or 1/0 depending on context
        if ("ON".equalsIgnoreCase(s) || "1".equals(s)) return true;
        if ("OFF".equalsIgnoreCase(s) || "0".equals(s)) return false;
        return null;
      }
    } catch (SQLException ignore) {
      // variable may not exist (e.g., MariaDB without super_read_only), caller will try fallback
    }
    return null;
  }

  private static Boolean readVarAsBoolean_ShowVariables(Connection c, String name) {
    String sql = "SHOW VARIABLES LIKE '" + name.replace("'", "''") + "'";
    try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
      if (rs.next()) {
        String val = rs.getString(2);
        if (val == null) return null;
        val = val.trim();
        if ("ON".equalsIgnoreCase(val) || "1".equals(val) || "TRUE".equalsIgnoreCase(val)) return true;
        if ("OFF".equalsIgnoreCase(val) || "0".equals(val) || "FALSE".equalsIgnoreCase(val)) return false;
      }
    } catch (SQLException ignore) {
      // ignore; not all servers expose variables the same way
    }
    return null;
  }

  private void checkMySql(PermissionReport.Builder b, Connection c) throws SQLException {
    // --- 0) Collect GRANTS up front (used in both branches)
    List<String> grants = new ArrayList<>();
    try (Statement st = c.createStatement();
         ResultSet rs = st.executeQuery("SHOW GRANTS")) {
      while (rs.next()) {
        grants.add(rs.getString(1));
      }
    } catch (SQLException ex) {
      b.addViolation("SHOW GRANTS failed: " + ex.getMessage());
    }

    // --- 1) Check instance/session read-only flags
    Boolean globalRO  = readVarAsBoolean(c, "SELECT @@GLOBAL.read_only");
    Boolean superRO   = readVarAsBoolean(c, "SELECT @@GLOBAL.super_read_only");  // MySQL; may be NULL on MariaDB
    Boolean sessionRO = readVarAsBoolean(c, "SELECT @@SESSION.read_only");

    // If variables aren’t available (older MariaDB or restricted), try SHOW VARIABLES fallback
    if (globalRO == null)  globalRO  = readVarAsBoolean_ShowVariables(c, "read_only");
    if (superRO == null)   superRO   = readVarAsBoolean_ShowVariables(c, "super_read_only");
    if (sessionRO == null) sessionRO = readVarAsBoolean_ShowVariables(c, "read_only"); // session usually mirrors read_only

    // Determine if user can bypass read_only (SUPER usually bypasses)
    boolean hasSuper = grants.stream()
            .map(s -> s.toUpperCase(Locale.ROOT))
            .anyMatch(s -> s.contains(" SUPER")); // e.g. "GRANT SUPER ON *.* TO ..."

    // In MySQL, if read_only (or super_read_only) is ON and user doesn't have SUPER, treat as read-only and return early
    boolean instanceReadOnly = (isTrue(globalRO) || isTrue(sessionRO) || isTrue(superRO));
    if (instanceReadOnly && !hasSuper) {
      // Mno violation added, exit inspection
      return;
    }

    // --- 2) Instance is writable (or user can bypass read-only) => fall back to GRANTS analysis
    if (grants.isEmpty()) {
      b.addViolation("No grants returned by SHOW GRANTS (cannot verify).");
      return;
    }

    // Disallowed verbs for a read-only user
    var disallowed = List.of(
            "INSERT","UPDATE","DELETE","REPLACE","ALTER","CREATE","DROP","TRUNCATE",
            "INDEX","TRIGGER","EVENT","EXECUTE","REFERENCES","GRANT OPTION","FILE",
            "SUPER","CREATE VIEW","CREATE ROUTINE","ALTER ROUTINE"
    );

    Pattern allPrivs = Pattern.compile("\\bALL PRIVILEGES\\b|\\bALL\\b", Pattern.CASE_INSENSITIVE);

    for (String g : grants) {
      String up = g.toUpperCase(Locale.ROOT);

      // ALL/ALL PRIVILEGES is always a violation for RO users
      if (allPrivs.matcher(up).find()) {
        b.addViolation("MySQL grant contains ALL PRIVILEGES: " + g);
        continue;
      }

      // Global scope (*.*): only SELECT (with optional SHOW VIEW) or pure USAGE should appear
      if (up.contains("*.*")) {
        // USAGE is a connection-only privilege - safe to allow on global scope
        boolean isPureUsage =
                up.contains("GRANT USAGE") &&
                up.contains("ON *.* TO") &&
                (!containsAny(up, disallowed, Set.of("USAGE")));

        // SELECT with optional SHOW VIEW (existing logic)
        boolean onlySelectAndShowView =
                up.contains("SELECT") &&
                        up.replace("SELECT", "").contains("ON *.* TO") &&        // crude guard to avoid false positives
                        (!up.contains("GRANT OPTION")) &&
                        (!containsAny(up, disallowed, Set.of("SELECT","SHOW VIEW")));

        if (!isPureUsage && !onlySelectAndShowView) {
          b.addViolation("MySQL global grant not RO-safe: " + g);
          continue;
        }
      }

      // Any explicitly disallowed privilege is a violation
      for (String bad : disallowed) {
        if (up.contains(bad)) {
          b.addViolation("MySQL disallowed privilege (" + bad + "): " + g);
        }
      }
      // Acceptable: SELECT (and SHOW VIEW) on specific objects; everything else flagged above.
    }
  }


  private static String rootMsg(Throwable t) {
    Throwable x = t;
    while (x.getCause() != null) x = x.getCause();
    return x.getMessage();
  }
}
