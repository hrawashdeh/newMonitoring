-- V6: Create Message Dictionary Schema for Error Messages and Notifications
-- Author: Hassan Rawashdeh
-- Date: 2025-12-24
-- Note: Message data is loaded via ETL Initializer from messages-data-v1.yaml

-- Create message_dictionary table in general schema
CREATE TABLE IF NOT EXISTS general.message_dictionary (
    id BIGSERIAL PRIMARY KEY,
    message_code VARCHAR(100) UNIQUE NOT NULL,
    message_category VARCHAR(50) NOT NULL,  -- ERROR, WARNING, INFO, SUCCESS
    message_en TEXT NOT NULL,                -- English message
    message_ar TEXT,                         -- Arabic message (optional)
    description VARCHAR(500),                 -- Description for developers
    created_at TIMESTAMP DEFAULT NOW(),
    created_by VARCHAR(100) DEFAULT 'system',
    updated_at TIMESTAMP,
    updated_by VARCHAR(100)
);

-- Create indexes on message_code for fast lookups
CREATE INDEX IF NOT EXISTS idx_message_code ON general.message_dictionary(message_code);
CREATE INDEX IF NOT EXISTS idx_message_category ON general.message_dictionary(message_category);

-- Grant select permissions to application user
GRANT SELECT ON general.message_dictionary TO alerts_user;

-- Add comments to table and columns
COMMENT ON TABLE general.message_dictionary IS 'Centralized message dictionary for error messages, notifications, and user-facing text in multiple languages';
COMMENT ON COLUMN general.message_dictionary.message_code IS 'Unique identifier for the message (e.g., AUTH_LOGIN_FAILED)';
COMMENT ON COLUMN general.message_dictionary.message_category IS 'Category: ERROR, WARNING, INFO, SUCCESS';
COMMENT ON COLUMN general.message_dictionary.message_en IS 'English version of the message';
COMMENT ON COLUMN general.message_dictionary.message_ar IS 'Arabic version of the message';
COMMENT ON COLUMN general.message_dictionary.description IS 'Description of when/where this message is used';
