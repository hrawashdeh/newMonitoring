-- =====================================================
-- Migration: V3 - Add Configuration Management System
-- Description: Database-driven configuration with plan switching support
-- Created: 2025-11-17
-- Issue: #11 - Dynamic Configuration Management System
-- =====================================================

-- Configuration Plans Table
-- Stores different configuration plans (e.g., "normal", "high-load", "maintenance")
-- Each parent (e.g., "scheduler", "loader", "api") can have multiple plans
CREATE TABLE loader.config_plan (
    id BIGSERIAL PRIMARY KEY,
    parent VARCHAR(64) NOT NULL,           -- Config parent group (e.g., "scheduler", "loader", "api")
    plan_name VARCHAR(64) NOT NULL,        -- Plan name (e.g., "normal", "high-load", "maintenance")
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(128),

    CONSTRAINT uq_config_plan_parent_name UNIQUE (parent, plan_name)
);

-- Configuration Values Table
-- Stores key-value pairs for each plan
CREATE TABLE loader.config_value (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    config_key VARCHAR(128) NOT NULL,      -- e.g., "scheduler.polling-interval-seconds"
    config_value TEXT NOT NULL,            -- String value, parsed by application based on data_type
    data_type VARCHAR(32) NOT NULL,        -- INTEGER, BOOLEAN, STRING, DOUBLE, LONG
    description TEXT,

    CONSTRAINT fk_config_value_plan FOREIGN KEY (plan_id)
        REFERENCES loader.config_plan(id) ON DELETE CASCADE,
    CONSTRAINT uq_config_value_plan_key UNIQUE (plan_id, config_key)
);

-- Indexes
-- Ensure only one active plan per parent
CREATE UNIQUE INDEX idx_config_plan_active ON loader.config_plan(parent) WHERE is_active = true;

-- Index for fast plan lookups
CREATE INDEX idx_config_plan_parent ON loader.config_plan(parent);

-- Index for fast config value lookups
CREATE INDEX idx_config_value_plan ON loader.config_value(plan_id);
CREATE INDEX idx_config_value_key ON loader.config_value(config_key);

-- Comments for documentation
COMMENT ON TABLE loader.config_plan IS 'Configuration plans - allows switching between different config sets without code deployment';
COMMENT ON TABLE loader.config_value IS 'Configuration values for each plan - key-value pairs';
COMMENT ON COLUMN loader.config_plan.parent IS 'Config parent group (e.g., scheduler, loader, api)';
COMMENT ON COLUMN loader.config_plan.plan_name IS 'Plan name (e.g., normal, high-load, maintenance)';
COMMENT ON COLUMN loader.config_plan.is_active IS 'Only one plan per parent can be active';
COMMENT ON COLUMN loader.config_value.data_type IS 'Data type for parsing: INTEGER, BOOLEAN, STRING, DOUBLE, LONG';
COMMENT ON COLUMN loader.config_value.config_key IS 'Hierarchical key using dot notation (e.g., scheduler.polling-interval-seconds)';

-- Seed data for scheduler configuration plans
INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES
    ('scheduler', 'normal', true, 'Normal operations - standard polling and thread pool settings', NOW(), NOW()),
    ('scheduler', 'high-load', false, 'High load mode - reduced polling frequency, increased thread pool', NOW(), NOW()),
    ('scheduler', 'maintenance', false, 'Maintenance mode - minimal polling, minimal threads', NOW(), NOW());

-- Seed data for normal operations plan
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '1',
    'INTEGER',
    'How often (in seconds) the scheduler checks for loaders ready to execute'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '10',
    'INTEGER',
    'Core thread pool size for executing loaders'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '50',
    'INTEGER',
    'Maximum thread pool size for executing loaders'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'normal';

-- Seed data for high-load plan
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '5',
    'INTEGER',
    'Reduced polling frequency to decrease system load'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '20',
    'INTEGER',
    'Increased core thread pool size to handle backlog'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '100',
    'INTEGER',
    'Increased maximum thread pool size for burst capacity'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'high-load';

-- Seed data for maintenance plan
INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'polling-interval-seconds',
    '60',
    'INTEGER',
    'Minimal polling during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-core-size',
    '2',
    'INTEGER',
    'Minimal thread pool during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'thread-pool-max-size',
    '5',
    'INTEGER',
    'Minimal maximum threads during maintenance'
FROM loader.config_plan WHERE parent = 'scheduler' AND plan_name = 'maintenance';

-- Seed data for logging configuration
INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES ('logging', 'default', true, 'Default logging configuration', NOW(), NOW());

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'rotation.max-file-size',
    '100MB',
    'STRING',
    'Maximum log file size before rotation'
FROM loader.config_plan WHERE parent = 'logging' AND plan_name = 'default';

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'rotation.max-days',
    '30',
    'INTEGER',
    'Keep log files for N days'
FROM loader.config_plan WHERE parent = 'logging' AND plan_name = 'default';

-- Seed data for loader configuration
INSERT INTO loader.config_plan (parent, plan_name, is_active, description, created_at, updated_at)
VALUES ('loader', 'default', true, 'Default loader configuration', NOW(), NOW());

INSERT INTO loader.config_value (plan_id, config_key, config_value, data_type, description)
SELECT
    id,
    'max-zero-record-runs',
    '10',
    'INTEGER',
    'Alert threshold for consecutive executions with 0 records (possible downtime)'
FROM loader.config_plan WHERE parent = 'loader' AND plan_name = 'default';

-- Verification queries (commented out - for manual verification)
-- SELECT * FROM loader.config_plan;
-- SELECT * FROM loader.config_value;
-- SELECT cp.parent, cp.plan_name, cp.is_active, cv.config_key, cv.config_value
-- FROM loader.config_plan cp
-- JOIN loader.config_value cv ON cp.id = cv.plan_id
-- ORDER BY cp.parent, cp.plan_name, cv.config_key;
