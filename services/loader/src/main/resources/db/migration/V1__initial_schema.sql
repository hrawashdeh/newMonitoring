-- ============================================================================
-- V1: Initial Schema - Baseline before loader scheduling implementation
-- ============================================================================
-- Author: Hassan Rawashdeh
-- Date: 2025-10-27
-- Description: Creates the initial loader and signals schemas with basic tables
-- ============================================================================

-- Create schemas
CREATE SCHEMA IF NOT EXISTS loader;
CREATE SCHEMA IF NOT EXISTS signals;

-- ============================================================================
-- LOADER SCHEMA TABLES
-- ============================================================================

-- Source databases configuration
CREATE TABLE loader.source_databases (
    id SERIAL PRIMARY KEY,
    db_code VARCHAR(64) NOT NULL UNIQUE,
    ip VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    db_type VARCHAR(20) NOT NULL,
    db_name VARCHAR(255) NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    pass_word VARCHAR(512),  -- Encrypted (AES-256-GCM)

    CONSTRAINT chk_db_type CHECK (db_type IN ('MYSQL', 'POSTGRESQL'))
);

CREATE INDEX idx_source_databases_code ON loader.source_databases(db_code);
CREATE INDEX idx_source_databases_type ON loader.source_databases(db_type);

-- ETL Loader definitions (old structure)
CREATE TABLE loader.loader (
    id SERIAL PRIMARY KEY,
    loader_code VARCHAR(64) NOT NULL UNIQUE,
    loader_sql TEXT NOT NULL,              -- Encrypted (AES-256-GCM)
    loader_interval INTEGER,               -- Deprecated: Use min/max intervals
    interval_type INTEGER,                 -- Deprecated: Use min/max intervals
    max_loaders INTEGER                    -- Deprecated: Use max_parallel_executions
);

CREATE INDEX idx_loader_code ON loader.loader(loader_code);

-- Segments dictionary
CREATE TABLE loader.segments_dictionary (
    id SERIAL PRIMARY KEY,
    segment_number INTEGER NOT NULL,
    loader VARCHAR(64) NOT NULL,
    segment_description VARCHAR(255),

    CONSTRAINT uq_segment_loader UNIQUE (segment_number, loader)
);

CREATE INDEX idx_segments_dictionary_loader ON loader.segments_dictionary(loader);

-- ============================================================================
-- SIGNALS SCHEMA TABLES
-- ============================================================================

-- Signals history (execution metrics)
CREATE TABLE signals.signals_history (
    id SERIAL PRIMARY KEY,
    loader_code VARCHAR(64) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    value NUMERIC,
    record_count INTEGER,
    min_value NUMERIC,
    max_value NUMERIC,
    avg_value NUMERIC,
    segment1 VARCHAR(255),
    segment2 VARCHAR(255),
    segment3 VARCHAR(255),
    segment4 VARCHAR(255),
    segment5 VARCHAR(255),
    segment6 VARCHAR(255),
    segment7 VARCHAR(255),
    segment8 VARCHAR(255),
    segment9 VARCHAR(255),
    segment10 VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_signals_history_loader ON signals.signals_history(loader_code);
CREATE INDEX idx_signals_history_timestamp ON signals.signals_history(timestamp);
CREATE INDEX idx_signals_history_loader_timestamp ON signals.signals_history(loader_code, timestamp);

-- Segment combination view
CREATE TABLE signals.segment_combination (
    id SERIAL PRIMARY KEY,
    loader_code VARCHAR(64) NOT NULL,
    segment_number INTEGER NOT NULL,
    segment_value VARCHAR(255),

    CONSTRAINT uq_segment_combination UNIQUE (loader_code, segment_number, segment_value)
);

CREATE INDEX idx_segment_combination_loader ON signals.segment_combination(loader_code);
