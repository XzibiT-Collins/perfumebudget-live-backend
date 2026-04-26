ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_roles_check;

ALTER TABLE users
    ADD CONSTRAINT users_roles_check
        CHECK (roles IN ('ADMIN', 'CUSTOMER', 'FRONT_DESK'));
