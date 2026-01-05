-- =====================================================================
-- V19: API Configuration Schema
-- =====================================================================
-- Purpose: Store API endpoint configuration for management UI.
-- Runtime data is in Redis, this is for persistence and admin.
--
-- Author: Hassan Rawashdeh
-- Date: 2026-01-05
-- =====================================================================

-- Create config schema
CREATE SCHEMA IF NOT EXISTS config;

-- =====================================================================
-- Table: config.api_endpoints
-- Persists discovered API endpoints for management
-- =====================================================================
CREATE TABLE IF NOT EXISTS config.api_endpoints (
    id SERIAL PRIMARY KEY,

    -- Logical key (e.g., "ldr.loaders.list")
    endpoint_key VARCHAR(100) NOT NULL UNIQUE,

    -- Full path (e.g., "/api/v1/ldr/ldr/loaders")
    path VARCHAR(255) NOT NULL,

    -- HTTP method
    http_method VARCHAR(10) NOT NULL,

    -- Service ID (e.g., "ldr", "auth")
    service_id VARCHAR(50) NOT NULL,

    -- Controller class name
    controller_class VARCHAR(100),

    -- Method name
    method_name VARCHAR(100),

    -- Description
    description TEXT,

    -- Is endpoint enabled? (overrides code default)
    enabled BOOLEAN DEFAULT TRUE,

    -- Tags (JSON array)
    tags JSONB DEFAULT '[]',

    -- Status: ACTIVE, DISABLED, DEPRECATED, REMOVED
    status VARCHAR(20) DEFAULT 'ACTIVE',

    -- Last time this endpoint was seen during registration
    last_seen_at TIMESTAMP,

    -- Service instance that last registered this
    last_registered_by VARCHAR(100),

    -- Audit fields
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- =====================================================================
-- Table: config.api_endpoint_history
-- Tracks changes to endpoint configuration
-- =====================================================================
CREATE TABLE IF NOT EXISTS config.api_endpoint_history (
    id SERIAL PRIMARY KEY,

    endpoint_key VARCHAR(100) NOT NULL,

    -- Type: DISCOVERED, ENABLED, DISABLED, PATH_CHANGED, REMOVED
    change_type VARCHAR(30) NOT NULL,

    -- Old and new values
    old_value TEXT,
    new_value TEXT,

    -- Who made the change
    changed_by VARCHAR(100) NOT NULL,

    -- When
    changed_at TIMESTAMP DEFAULT NOW(),

    -- Notes
    notes TEXT
);

-- =====================================================================
-- Indexes
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_api_endpoints_service ON config.api_endpoints(service_id);
CREATE INDEX IF NOT EXISTS idx_api_endpoints_status ON config.api_endpoints(status);
CREATE INDEX IF NOT EXISTS idx_api_endpoints_enabled ON config.api_endpoints(enabled) WHERE enabled = TRUE;
CREATE INDEX IF NOT EXISTS idx_api_endpoint_history_key ON config.api_endpoint_history(endpoint_key);
CREATE INDEX IF NOT EXISTS idx_api_endpoint_history_date ON config.api_endpoint_history(changed_at);

-- =====================================================================
-- View: Active endpoints
-- =====================================================================
CREATE OR REPLACE VIEW config.v_active_endpoints AS
SELECT
    endpoint_key,
    path,
    http_method,
    service_id,
    description,
    tags,
    last_seen_at
FROM config.api_endpoints
WHERE enabled = TRUE AND status = 'ACTIVE';

-- =====================================================================
-- Function: Update timestamp on modification
-- =====================================================================
CREATE OR REPLACE FUNCTION config.update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for api_endpoints
DROP TRIGGER IF EXISTS trg_api_endpoints_timestamp ON config.api_endpoints;
CREATE TRIGGER trg_api_endpoints_timestamp
    BEFORE UPDATE ON config.api_endpoints
    FOR EACH ROW
    EXECUTE FUNCTION config.update_timestamp();

-- =====================================================================
-- Success
-- =====================================================================
DO $$
BEGIN
    RAISE NOTICE 'V19: API Configuration schema created successfully';
END $$;
