-- ============================================================================
-- DEV SEED DATA - ENCRYPTION NOTICE
-- ============================================================================
-- SQL INSERT statements bypass JPA, so @Convert annotation doesn't encrypt data.
-- This would store passwords and SQL queries as PLAINTEXT in the database!
--
-- SOLUTION: Data is now loaded via DevDataLoader.java component which:
--   1. Uses JPA repositories (automatic encryption via AttributeConverter)
--   2. Runs after ApplicationReadyEvent in dev profile
--   3. Ensures all sensitive fields are encrypted (passwords, SQL queries)
--
-- See: com.tiqmo.monitoring.loader.runner.DevDataLoader
-- ============================================================================

-- COMMENTED OUT: Now loaded via DevDataLoader with automatic encryption
-- INSERT INTO loader.loader (loader_code, loader_sql, loader_interval, interval_type, max_loaders) VALUES
--   ('ALERTS01', 'SELECT NOW() AS ts', 60, 1, 2),
--   ('ALERTS02', 'SELECT COUNT(*) FROM information_schema.tables', 120, 2, 1)
-- ON CONFLICT (loader_code) DO NOTHING;

-- COMMENTED OUT: Now loaded via DevDataLoader with automatic encryption
-- INSERT INTO loader.source_databases (db_code, ip, port, db_type, db_name, user_name, pass_word)
-- VALUES ('WALLET', '10.0.15.79', 3306, 'MYSQL', 'k8s_wallyt_cms_db', 'h_rawashdeh', 'A9#dF4z!Qw8*Lm');

-- COMMENTED OUT: Now loaded via DevDataLoader
-- INSERT INTO loader.segments_dictionary(segment_number, loader, segment_description)
-- VALUES (1, 'ALERTS01', 'code 1');