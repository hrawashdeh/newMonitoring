-- =====================================================================
-- V21: Add missing columns to menu_role_permissions
-- =====================================================================
-- Fix: V20 created menu_role_permissions without granted_by and granted_at
-- columns which are expected by the MenuRolePermission entity.
--
-- Author: Claude Code
-- Date: 2026-01-06
-- =====================================================================

-- Add missing columns to menu_role_permissions
ALTER TABLE config.menu_role_permissions
ADD COLUMN IF NOT EXISTS granted_by VARCHAR(100);

ALTER TABLE config.menu_role_permissions
ADD COLUMN IF NOT EXISTS granted_at TIMESTAMP DEFAULT NOW();

-- Update existing records to have a default granted_at timestamp
UPDATE config.menu_role_permissions
SET granted_at = NOW()
WHERE granted_at IS NULL;

-- =====================================================================
-- Success
-- =====================================================================
DO $$
BEGIN
    RAISE NOTICE 'V21: Added granted_by and granted_at columns to menu_role_permissions';
END $$;