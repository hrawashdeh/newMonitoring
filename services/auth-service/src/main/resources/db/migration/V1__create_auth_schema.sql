-- =====================================================
-- Auth Service Schema Migration V1
-- =====================================================
-- Creates authentication schema with roles, users, and login tracking
-- Database: alerts_db
-- Created: 2026-01-01
-- =====================================================

-- =====================================================
-- SCHEMA
-- =====================================================

CREATE SCHEMA IF NOT EXISTS auth;

COMMENT ON SCHEMA auth IS 'Authentication and authorization schema';

-- =====================================================
-- AUTH SCHEMA TABLES
-- =====================================================

-- Roles table
CREATE TABLE IF NOT EXISTS auth.roles (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_role_name_format CHECK (role_name ~ '^ROLE_[A-Z_]+$')
);

CREATE INDEX idx_roles_role_name ON auth.roles(role_name);

COMMENT ON TABLE auth.roles IS 'System roles for RBAC';
COMMENT ON COLUMN auth.roles.role_name IS 'Role name must start with ROLE_ prefix (e.g., ROLE_ADMIN)';

-- Users table
CREATE TABLE IF NOT EXISTS auth.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    full_name VARCHAR(255),
    enabled BOOLEAN DEFAULT true NOT NULL,
    account_non_expired BOOLEAN DEFAULT true NOT NULL,
    account_non_locked BOOLEAN DEFAULT true NOT NULL,
    credentials_non_expired BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50)
);

CREATE INDEX idx_users_username ON auth.users(username);
CREATE INDEX idx_users_email ON auth.users(email);
CREATE INDEX idx_users_enabled ON auth.users(enabled);

COMMENT ON TABLE auth.users IS 'System users with authentication credentials';
COMMENT ON COLUMN auth.users.password IS 'BCrypt hashed password ($2a$ prefix)';

-- User_roles join table
CREATE TABLE IF NOT EXISTS auth.user_roles (
    user_id BIGINT NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES auth.roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON auth.user_roles(user_id);
CREATE INDEX idx_user_roles_role ON auth.user_roles(role_id);

COMMENT ON TABLE auth.user_roles IS 'Many-to-many relationship between users and roles';

-- Login_attempts table
CREATE TABLE IF NOT EXISTS auth.login_attempts (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    ip_address VARCHAR(50),
    success BOOLEAN NOT NULL,
    reason VARCHAR(255),
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_login_attempts_username ON auth.login_attempts(username, attempted_at DESC);
CREATE INDEX idx_login_attempts_ip ON auth.login_attempts(ip_address);
CREATE INDEX idx_login_attempts_attempted ON auth.login_attempts(attempted_at DESC);

COMMENT ON TABLE auth.login_attempts IS 'Audit log of all login attempts for security monitoring';

-- =====================================================
-- COMPLETION
-- =====================================================

DO $$
BEGIN
    RAISE NOTICE 'Auth schema initialization complete - V1';
    RAISE NOTICE 'Schema created: auth';
    RAISE NOTICE 'Tables created: roles, users, user_roles, login_attempts';
END $$;
