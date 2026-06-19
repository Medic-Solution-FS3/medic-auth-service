-- WARNING: Development seed data only.
-- Remove this script or move it to classpath:db/migration/local before deploying to production.
-- Seed doctor user (password: password123)
-- BCrypt hash generated with strength 10
INSERT INTO users (email, password_hash, full_name, phone, role_id, active, email_verified, created_at, updated_at)
SELECT
    'tomateartzzxd@gmail.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Dr. Amaro Montero',
    '+56912345678',
    r.id,
    true,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM roles r WHERE r.name = 'MEDICO';
