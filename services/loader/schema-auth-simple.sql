-- ============================================
-- Authentication Schema DDL (Simplified)
-- ============================================
-- Execute in IntelliJ/DBeaver without connection issues
-- All table names fully qualified (auth.table_name)
-- ============================================

-- Create auth schema
CREATE SCHEMA IF NOT EXISTS auth;

-- ============================================
-- Users Table
-- ============================================
CREATE TABLE IF NOT EXISTS auth.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    full_name VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_username_length CHECK (char_length(username) >= 3),
    CONSTRAINT chk_password_length CHECK (char_length(password) >= 60)
);

CREATE INDEX IF NOT EXISTS idx_users_username_lower ON auth.users (LOWER(username));
CREATE INDEX IF NOT EXISTS idx_users_email ON auth.users (email) WHERE email IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_enabled ON auth.users (enabled) WHERE enabled = TRUE;

-- ============================================
-- Roles Table
-- ============================================
CREATE TABLE IF NOT EXISTS auth.roles (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_roles_name UNIQUE (role_name),
    CONSTRAINT chk_role_name_format CHECK (role_name ~ '^ROLE_[A-Z_]+$')
);

-- ============================================
-- User Roles Junction Table
-- ============================================
CREATE TABLE IF NOT EXISTS auth.user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(50),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES auth.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id)
        REFERENCES auth.roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_roles_role ON auth.user_roles (role_id);

-- ============================================
-- Login Attempts Table
-- ============================================
CREATE TABLE IF NOT EXISTS auth.login_attempts (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_username_time ON auth.login_attempts (username, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_attempts_failed ON auth.login_attempts (success, attempted_at DESC) WHERE success = FALSE;

-- ============================================
-- Refresh Tokens Table
-- ============================================
CREATE TABLE IF NOT EXISTS auth.refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(45),
    user_agent TEXT,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON auth.refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_active ON auth.refresh_tokens (expires_at, revoked) WHERE revoked = FALSE;

-- ============================================
-- Trigger for updated_at
-- ============================================
CREATE OR REPLACE FUNCTION auth.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_users_updated_at ON auth.users;
CREATE TRIGGER tr_users_updated_at
    BEFORE UPDATE ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION auth.update_updated_at_column();

-- ============================================
-- Initial Roles Data
-- ============================================
INSERT INTO auth.roles (role_name, description) VALUES
    ('ROLE_ADMIN', 'Full system access - CRUD operations, admin endpoints'),
    ('ROLE_OPERATOR', 'Read access + operational endpoints (pause/resume, reload)'),
    ('ROLE_VIEWER', 'Read-only access to data endpoints')
ON CONFLICT (role_name) DO NOTHING;

-- ============================================
-- User Details View
-- ============================================
CREATE OR REPLACE VIEW auth.vw_user_details AS
SELECT
    u.id,
    u.username,
    u.email,
    u.full_name,
    u.enabled,
    u.account_non_expired,
    u.account_non_locked,
    u.credentials_non_expired,
    u.created_at,
    u.updated_at,
    u.last_login_at,
    ARRAY_AGG(r.role_name ORDER BY r.role_name) FILTER (WHERE r.role_name IS NOT NULL) AS roles,
    COUNT(r.id) AS role_count
FROM auth.users u
LEFT JOIN auth.user_roles ur ON u.id = ur.user_id
LEFT JOIN auth.roles r ON ur.role_id = r.id
GROUP BY u.id, u.username, u.email, u.full_name, u.enabled,
         u.account_non_expired, u.account_non_locked,
         u.credentials_non_expired, u.created_at, u.updated_at, u.last_login_at;

-- ============================================
-- Maintenance Functions
-- ============================================
CREATE OR REPLACE FUNCTION auth.cleanup_old_login_attempts()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM auth.login_attempts
    WHERE attempted_at < CURRENT_TIMESTAMP - INTERVAL '90 days';
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION auth.revoke_expired_refresh_tokens()
RETURNS INTEGER AS $$
DECLARE
    updated_count INTEGER;
BEGIN
    UPDATE auth.refresh_tokens
    SET revoked = TRUE,
        revoked_at = CURRENT_TIMESTAMP
    WHERE expires_at < CURRENT_TIMESTAMP
      AND revoked = FALSE;
    GET DIAGNOSTICS updated_count = ROW_COUNT;
    RETURN updated_count;
END;
$$ LANGUAGE plpgsql;