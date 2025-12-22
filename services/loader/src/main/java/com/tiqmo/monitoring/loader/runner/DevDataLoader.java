package com.tiqmo.monitoring.loader.runner;

import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.loader.domain.loader.entity.SegmentDictionary;
import com.tiqmo.monitoring.loader.domain.loader.entity.SourceDatabase;
import com.tiqmo.monitoring.loader.domain.loader.repo.LoaderRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.SegmentDictionaryRepository;
import com.tiqmo.monitoring.loader.domain.loader.repo.SourceDatabaseRepository;
import com.tiqmo.monitoring.loader.infra.db.SourceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Seeds development data using JPA repositories.
 *
 * <p><b>Why use this instead of data-dev.sql?</b>
 * <ul>
 *   <li>SQL INSERT statements bypass JPA AttributeConverter</li>
 *   <li>Encrypted fields (passwords, SQL queries) would be stored as plaintext</li>
 *   <li>This component uses repositories, so {@code @Convert} automatically encrypts data</li>
 * </ul>
 *
 * <p><b>Execution:</b> Runs once after ApplicationReadyEvent in dev profile only.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
// @Component // Disabled: etl_initializer now manages all data loading via YAML files
@Profile("dev")
@RequiredArgsConstructor
public class DevDataLoader {

  private final SourceDatabaseRepository sourceDatabaseRepository;
  private final LoaderRepository loaderRepository;
  private final SegmentDictionaryRepository segmentDictionaryRepository;
  private final SourceRegistry sourceRegistry;

  /**
   * Loads development seed data with automatic encryption.
   * Runs after application is fully started.
   */
  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void loadDevData() {
    log.info("Loading development seed data with automatic encryption...");

    // Clear existing data (dev profile uses create-drop, but just in case)
    loaderRepository.deleteAll();
    sourceDatabaseRepository.deleteAll();
    segmentDictionaryRepository.deleteAll();

    // ========== SOURCE DATABASES ==========

    // COMMENTED OUT: Production WALLET database (uncomment when ready for production testing)
    // SourceDatabase walletDb = SourceDatabase.builder()
    //   .dbCode("WALLET")
    //   .ip("10.103.1.248")
    //   .port(3306)
    //   .dbType(SourceDatabase.DbType.MYSQL)
    //   .dbName("k8s_wallyt_cms_db")
    //   .userName("h_rawashdeh")
    //   .passWord("A9#dF4z!Qw8*Lm")  // ← Auto-encrypted before save
    //   .build();

    // MySQL test database for mock data (deployed in K8s)
    SourceDatabase testMysqlDb = SourceDatabase.builder()
      .dbCode("TEST_MYSQL")
      .ip("mysql.loader-infra.svc.cluster.local")  // K8s service DNS
      .port(3306)
      .dbType(SourceDatabase.DbType.MYSQL)
      .dbName("testdata")
      .userName("testuser")
      .passWord("testuser_password_dev")  // ← Auto-encrypted before save
      .build();

    // sourceDatabaseRepository.save(walletDb);  // COMMENTED OUT
    sourceDatabaseRepository.save(testMysqlDb);
    log.info("Created {} source database with encrypted password (TEST_MYSQL)", 1);

    // ========== LOADERS (TEST DATA GENERATORS) ==========

    // Scheduling field initialization:
    // - lastLoadTimestamp: Set to 1 hour ago so loaders can start immediately
    // - loadStatus: Defaults to IDLE (via @Builder.Default in Loader entity)
    // - failedSince: Null (not failed)

    // COMMENTED OUT: Production WALLET_TRANS loader (uncomment when ready for production testing)
    // Loader walletTransLoader = Loader.builder()
    //   .loaderCode("WALLET_TRANS")
    //   .sourceDatabase(walletDb)
    //   .loaderSql("""
    //     SELECT
    //       DATE_FORMAT(FROM_UNIXTIME(CREATE_TIME/1000), '%Y-%m-%d %H:%i') AS load_time_stamp,
    //       SCENE_TYPE       AS segment_1,
    //       ORDER_STATUS     AS segment_2,
    //       PURCHASE_STATUS  AS segment_3,
    //       NULL AS segment_4,
    //       NULL AS segment_5,
    //       NULL AS segment_6,
    //       NULL AS segment_7,
    //       NULL AS segment_8,
    //       NULL AS segment_9,
    //       NULL AS segment_10,
    //       COUNT(*) AS rec_count,
    //       SUM(amount) AS sum_val,
    //       AVG(amount) AS avg_val,
    //       MAX(amount) AS max_val,
    //       MIN(amount) AS min_val
    //     FROM k8s_wallyt_tms_db.tms_order
    //     WHERE CREATE_TIME >= UNIX_TIMESTAMP(CONVERT_TZ(STR_TO_DATE(:fromTime, '%Y-%m-%d %H:%i'), '+03:00', '+00:00')) * 1000
    //       AND CREATE_TIME <  UNIX_TIMESTAMP(CONVERT_TZ(STR_TO_DATE(:toTime,   '%Y-%m-%d %H:%i'), '+03:00', '+00:00')) * 1000
    //     GROUP BY
    //       load_time_stamp, segment_1, segment_2, segment_3,
    //       segment_4, segment_5, segment_6, segment_7, segment_8, segment_9, segment_10
    //     ORDER BY load_time_stamp
    //     """)
    //   .minIntervalSeconds(10)
    //   .maxIntervalSeconds(60)
    //   .maxQueryPeriodSeconds(432000)  // 5 days
    //   .maxParallelExecutions(5)
    //   .enabled(true)
    //   .build();

    // Loader 1: Sales Data (from data-generator service)
    Loader salesDataLoader = Loader.builder()
      .loaderCode("SALES_DATA")
      .sourceDatabase(testMysqlDb)
      .loaderSql("""
        SELECT
          UNIX_TIMESTAMP(DATE_FORMAT(timestamp, '%Y-%m-%d %H:%i:00')) AS load_time_stamp,
          product AS segment_1,
          NULL AS segment_2,
          NULL AS segment_3,
          NULL AS segment_4,
          NULL AS segment_5,
          NULL AS segment_6,
          NULL AS segment_7,
          NULL AS segment_8,
          NULL AS segment_9,
          NULL AS segment_10,
          COUNT(*) AS rec_count,
          SUM(amount) AS sum_val,
          AVG(amount) AS avg_val,
          MAX(amount) AS max_val,
          MIN(amount) AS min_val
        FROM sales_data
        WHERE timestamp >= STR_TO_DATE(':fromTime', '%Y-%m-%d %H:%i')
          AND timestamp <  STR_TO_DATE(':toTime',   '%Y-%m-%d %H:%i')
        GROUP BY load_time_stamp, segment_1, segment_2, segment_3,
          segment_4, segment_5, segment_6, segment_7, segment_8, segment_9, segment_10
        ORDER BY load_time_stamp
        """)
      .minIntervalSeconds(60)
      .maxIntervalSeconds(120)
      .maxQueryPeriodSeconds(86400)  // 1 day
      .maxParallelExecutions(2)
      .enabled(true)
      .lastLoadTimestamp(Instant.now().minusSeconds(3600))  // 1 hour ago
      .build();

    // Loader 2: User Activity (from data-generator service)
    Loader userActivityLoader = Loader.builder()
      .loaderCode("USER_ACTIVITY")
      .sourceDatabase(testMysqlDb)
      .loaderSql("""
        SELECT
          UNIX_TIMESTAMP(DATE_FORMAT(timestamp, '%Y-%m-%d %H:%i:00')) AS load_time_stamp,
          action AS segment_1,
          CASE
            WHEN user_id BETWEEN 1 AND 3333 THEN 'Tier-1'
            WHEN user_id BETWEEN 3334 AND 6666 THEN 'Tier-2'
            ELSE 'Tier-3'
          END AS segment_2,
          NULL AS segment_3,
          NULL AS segment_4,
          NULL AS segment_5,
          NULL AS segment_6,
          NULL AS segment_7,
          NULL AS segment_8,
          NULL AS segment_9,
          NULL AS segment_10,
          COUNT(*) AS rec_count,
          SUM(session_duration) AS sum_val,
          AVG(session_duration) AS avg_val,
          MAX(session_duration) AS max_val,
          MIN(session_duration) AS min_val
        FROM user_activity
        WHERE timestamp >= STR_TO_DATE(':fromTime', '%Y-%m-%d %H:%i')
          AND timestamp <  STR_TO_DATE(':toTime',   '%Y-%m-%d %H:%i')
        GROUP BY load_time_stamp, segment_1, segment_2, segment_3,
          segment_4, segment_5, segment_6, segment_7, segment_8, segment_9, segment_10
        ORDER BY load_time_stamp
        """)
      .minIntervalSeconds(60)
      .maxIntervalSeconds(120)
      .maxQueryPeriodSeconds(86400)  // 1 day
      .maxParallelExecutions(2)
      .enabled(true)
      .lastLoadTimestamp(Instant.now().minusSeconds(3600))  // 1 hour ago
      .build();

    // Loader 3: Sensor Readings (from data-generator service)
    Loader sensorReadingsLoader = Loader.builder()
      .loaderCode("SENSOR_READINGS")
      .sourceDatabase(testMysqlDb)
      .loaderSql("""
        SELECT
          UNIX_TIMESTAMP(DATE_FORMAT(timestamp, '%Y-%m-%d %H:%i:00')) AS load_time_stamp,
          location AS segment_1,
          CASE
            WHEN temperature < 0 THEN 'Freezing'
            WHEN temperature < 10 THEN 'Cold'
            WHEN temperature < 25 THEN 'Moderate'
            ELSE 'Hot'
          END AS segment_2,
          NULL AS segment_3,
          NULL AS segment_4,
          NULL AS segment_5,
          NULL AS segment_6,
          NULL AS segment_7,
          NULL AS segment_8,
          NULL AS segment_9,
          NULL AS segment_10,
          COUNT(*) AS rec_count,
          SUM(temperature) AS sum_val,
          AVG(temperature) AS avg_val,
          MAX(temperature) AS max_val,
          MIN(temperature) AS min_val
        FROM sensor_readings
        WHERE timestamp >= STR_TO_DATE(':fromTime', '%Y-%m-%d %H:%i')
          AND timestamp <  STR_TO_DATE(':toTime',   '%Y-%m-%d %H:%i')
        GROUP BY load_time_stamp, segment_1, segment_2, segment_3,
          segment_4, segment_5, segment_6, segment_7, segment_8, segment_9, segment_10
        ORDER BY load_time_stamp
        """)
      .minIntervalSeconds(60)
      .maxIntervalSeconds(120)
      .maxQueryPeriodSeconds(86400)  // 1 day
      .maxParallelExecutions(2)
      .enabled(true)
      .lastLoadTimestamp(Instant.now().minusSeconds(3600))  // 1 hour ago
      .build();

    // loaderRepository.save(walletTransLoader);  // COMMENTED OUT
    loaderRepository.save(salesDataLoader);
    loaderRepository.save(userActivityLoader);
    loaderRepository.save(sensorReadingsLoader);
    log.info("Created {} loaders with encrypted SQL queries (TEST_MYSQL)", 3);

    // ========== SEGMENT DICTIONARY ==========

    // COMMENTED OUT: Production WALLET_TRANS segments (uncomment when ready for production testing)
    // SegmentDictionary seg1 = SegmentDictionary.builder()
    //   .segmentNumber(1).loader("WALLET_TRANS").segmentDescription("Transaction Loader").build();
    // segmentDictionaryRepository.save(seg1);

    // SALES_DATA segments
    SegmentDictionary salesSeg1 = SegmentDictionary.builder()
      .segmentNumber(1).loader("SALES_DATA").segmentDescription("Product Type").build();

    // USER_ACTIVITY segments
    SegmentDictionary userSeg1 = SegmentDictionary.builder()
      .segmentNumber(1).loader("USER_ACTIVITY").segmentDescription("Action Type").build();
    SegmentDictionary userSeg2 = SegmentDictionary.builder()
      .segmentNumber(2).loader("USER_ACTIVITY").segmentDescription("User Tier (1-3333=Tier-1, 3334-6666=Tier-2, 6667-10000=Tier-3)").build();

    // SENSOR_READINGS segments
    SegmentDictionary sensorSeg1 = SegmentDictionary.builder()
      .segmentNumber(1).loader("SENSOR_READINGS").segmentDescription("Sensor Location").build();
    SegmentDictionary sensorSeg2 = SegmentDictionary.builder()
      .segmentNumber(2).loader("SENSOR_READINGS").segmentDescription("Temperature Category (Freezing/Cold/Moderate/Hot)").build();

    segmentDictionaryRepository.save(salesSeg1);
    segmentDictionaryRepository.save(userSeg1);
    segmentDictionaryRepository.save(userSeg2);
    segmentDictionaryRepository.save(sensorSeg1);
    segmentDictionaryRepository.save(sensorSeg2);
    log.info("Created {} segment dictionary entries for TEST_MYSQL loaders", 5);

    log.info("✓ Development seed data loaded successfully");
    log.info("  - 1 source database (TEST_MYSQL) with encrypted password");
    log.info("  - 3 loaders (SALES_DATA, USER_ACTIVITY, SENSOR_READINGS) with encrypted SQL queries");
    log.info("  - 5 segment dictionary entries");

    // Reload SourceRegistry to initialize connection pools for newly created databases
    // DISABLED: Causes crash if external MySQL is not available
    // Use etl_initializer to populate real source databases instead
    log.info("⚠️ SourceRegistry reload skipped in dev mode (use etl_initializer to add real sources)");
    // sourceRegistry.loadAll();
    // log.info("✅ SourceRegistry reloaded with TEST_MYSQL connection pool");
  }
}
