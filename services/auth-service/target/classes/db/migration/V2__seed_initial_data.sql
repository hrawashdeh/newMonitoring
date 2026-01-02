-- =====================================================
-- Auth Service Seed Data Migration V2
-- =====================================================
-- Seeds initial roles and admin user
-- Database: alerts_db
-- Created: 2026-01-01
-- =====================================================

-- =====================================================
-- SEED ROLES
-- =====================================================

-- Insert default roles
INSERT INTO auth.roles (role_name, description) VALUES
    ('ROLE_ADMIN', 'Administrator with full access to all features'),
    ('ROLE_VIEWER', 'Read-only access to view data and dashboards'),
    ('ROLE_EDITOR', 'Can create and edit loaders, but cannot manage users')
ON CONFLICT (role_name) DO NOTHING;

-- =====================================================
-- SEED ADMIN USER
-- =====================================================

-- Insert admin user
-- Username: admin
-- Password: password (BCrypt hash: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG)
INSERT INTO auth.users (username, password, email, full_name, enabled, created_by) VALUES
    ('admin', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'admin@tiqmo.sa', 'System Administrator', true, 'system')
ON CONFLICT (username) DO NOTHING;

-- Link admin user to ROLE_ADMIN
INSERT INTO auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM auth.users u, auth.roles r
WHERE u.username = 'admin' AND r.role_name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- =====================================================
-- VERIFICATION
-- =====================================================

-- Verify seed data was inserted
DO $$
DECLARE
    role_count INTEGER;
    user_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO role_count FROM auth.roles;
    SELECT COUNT(*) INTO user_count FROM auth.users;

    RAISE NOTICE 'Seed data complete - V2';
    RAISE NOTICE 'Roles inserted: %', role_count;
    RAISE NOTICE 'Users inserted: %', user_count;
    RAISE NOTICE 'Default credentials: username=admin, password=password';
END $$;
