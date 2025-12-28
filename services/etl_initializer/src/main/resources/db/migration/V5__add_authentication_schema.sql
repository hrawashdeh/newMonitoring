-- ============================================
-- Authentication Schema DDL
-- ============================================
-- This schema defines the database structure for user authentication
-- and authorization in the ETL Monitoring System.
--
-- Design Notes:
-- - BCrypt password encoding (60 characters minimum)
-- - Multiple roles per user supported via user_roles junction table
-- - Account status tracking (enabled, locked, expired)
-- - Audit timestamps for security tracking
-- - Case-insensitive username uniqueness
-- ============================================

-- Create auth schema
CREATE SCHEMA IF NOT EXISTS auth;

-- Set search path for subsequent operations
SET search_path TO auth, public;

-- ============================================
-- Users Table
-- ============================================
-- Stores user account information and credentials
CREATE TABLE auth.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL, -- BCrypt encoded password ($2a$10$...)
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
    CONSTRAINT chk_password_length CHECK (char_length(password) >= 60) -- BCrypt minimum
);

-- Index for case-insensitive username lookup
CREATE INDEX idx_users_username_lower ON auth.users (LOWER(username));

-- Index for email lookup
CREATE INDEX idx_users_email ON auth.users (email) WHERE email IS NOT NULL;

-- Index for enabled users
CREATE INDEX idx_users_enabled ON auth.users (enabled) WHERE enabled = TRUE;

COMMENT ON TABLE auth.users IS 'User accounts for authentication and authorization';
COMMENT ON COLUMN auth.users.username IS 'Unique username (case-insensitive)';
COMMENT ON COLUMN auth.users.password IS 'BCrypt encoded password hash';
COMMENT ON COLUMN auth.users.enabled IS 'Account activation status';
COMMENT ON COLUMN auth.users.account_non_expired IS 'Account expiration status';
COMMENT ON COLUMN auth.users.account_non_locked IS 'Account lock status (security)';
COMMENT ON COLUMN auth.users.credentials_non_expired IS 'Password expiration status';

-- ============================================
-- Roles Table
-- ============================================
-- Defines available system roles
CREATE TABLE auth.roles (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_roles_name UNIQUE (role_name),
    CONSTRAINT chk_role_name_format CHECK (role_name ~ '^ROLE_[A-Z_]+$')
);

COMMENT ON TABLE auth.roles IS 'System roles for authorization';
COMMENT ON COLUMN auth.roles.role_name IS 'Role name (must start with ROLE_ prefix)';

-- ============================================
-- User Roles Junction Table
-- ============================================
-- Many-to-many relationship between users and roles
CREATE TABLE auth.user_roles (
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

-- Index for role-based queries
CREATE INDEX idx_user_roles_role ON auth.user_roles (role_id);

COMMENT ON TABLE auth.user_roles IS 'User-to-role assignments (many-to-many)';

-- ============================================
-- Login Attempts Table (Security Auditing)
-- ============================================
-- Tracks login attempts for security monitoring and brute-force protection
CREATE TABLE auth.login_attempts (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45), -- IPv6 support (max 45 chars)
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(255),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for recent login attempts by username
CREATE INDEX idx_login_attempts_username_time
    ON auth.login_attempts (username, attempted_at DESC);

-- Index for failed login attempts (security monitoring)
CREATE INDEX idx_login_attempts_failed
    ON auth.login_attempts (success, attempted_at DESC)
    WHERE success = FALSE;

COMMENT ON TABLE auth.login_attempts IS 'Login attempt history for security auditing';

-- ============================================
-- Refresh Tokens Table (Optional - for JWT refresh)
-- ============================================
-- Stores refresh tokens for long-lived sessions
CREATE TABLE auth.refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL, -- SHA-256 hash of refresh token
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

-- Index for token lookup
CREATE INDEX idx_refresh_tokens_user ON auth.refresh_tokens (user_id);

-- Index for active tokens
CREATE INDEX idx_refresh_tokens_active
    ON auth.refresh_tokens (expires_at, revoked)
    WHERE revoked = FALSE;

COMMENT ON TABLE auth.refresh_tokens IS 'Refresh tokens for JWT token renewal';

-- ============================================
-- Updated At Trigger Function
-- ============================================
-- Automatically updates the updated_at timestamp
CREATE OR REPLACE FUNCTION auth.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to users table
CREATE TRIGGER tr_users_updated_at
    BEFORE UPDATE ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION auth.update_updated_at_column();

-- ============================================
-- Initial Data (System Roles)
-- ============================================
-- Insert standard system roles
INSERT INTO auth.roles (role_name, description) VALUES
    ('ROLE_ADMIN', 'Full system access - CRUD operations, admin endpoints'),
    ('ROLE_OPERATOR', 'Read access + operational endpoints (pause/resume, reload)'),
    ('ROLE_VIEWER', 'Read-only access to data endpoints')
ON CONFLICT (role_name) DO NOTHING;

-- ============================================
-- Views
-- ============================================
-- User details view with roles
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
    ARRAY_AGG(r.role_name ORDER BY r.role_name) AS roles,
    COUNT(r.id) AS role_count
FROM auth.users u
LEFT JOIN auth.user_roles ur ON u.id = ur.user_id
LEFT JOIN auth.roles r ON ur.role_id = r.id
GROUP BY u.id, u.username, u.email, u.full_name, u.enabled,
         u.account_non_expired, u.account_non_locked,
         u.credentials_non_expired, u.created_at, u.updated_at, u.last_login_at;

COMMENT ON VIEW auth.vw_user_details IS 'User details with aggregated roles';

-- ============================================
-- Grants (adjust as needed for your deployment)
-- ============================================
-- Grant usage on schema
-- GRANT USAGE ON SCHEMA auth TO loader_app;

-- Grant table permissions
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA auth TO loader_app;

-- Grant sequence permissions
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA auth TO loader_app;

-- ============================================
-- Clean-up Functions (Maintenance)
-- ============================================
-- Function to clean old login attempts (keep last 90 days)
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

COMMENT ON FUNCTION auth.cleanup_old_login_attempts()
    IS 'Deletes login attempts older than 90 days';

-- Function to revoke expired refresh tokens
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

COMMENT ON FUNCTION auth.revoke_expired_refresh_tokens()
    IS 'Revokes all expired refresh tokens';