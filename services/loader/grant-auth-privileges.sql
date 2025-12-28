-- ============================================
-- Grant Privileges on auth Schema
-- ============================================
-- Run this as postgres admin user first!
-- Then reconnect as alerts_user
-- ============================================

-- Connect as admin: psql -h localhost -p 30432 -U postgres -d alerts_db

-- Grant schema usage to alerts_user
GRANT USAGE ON SCHEMA auth TO alerts_user;

-- Grant all privileges on all tables in auth schema
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA auth TO alerts_user;

-- Grant privileges on sequences (for SERIAL/BIGSERIAL columns)
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA auth TO alerts_user;

-- Grant privileges on functions
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA auth TO alerts_user;

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA auth
    GRANT ALL PRIVILEGES ON TABLES TO alerts_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA auth
    GRANT ALL PRIVILEGES ON SEQUENCES TO alerts_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA auth
    GRANT EXECUTE ON FUNCTIONS TO alerts_user;

-- Verify privileges
\dn+ auth
\dp auth.*