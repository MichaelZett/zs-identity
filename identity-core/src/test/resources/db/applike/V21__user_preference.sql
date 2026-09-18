-- An application migration that leans on the building block's table: only
-- possible because the identity schema is migrated before the application's.
CREATE TABLE user_preference
(
    user_id            BIGINT PRIMARY KEY REFERENCES identity.auth_user (id) ON DELETE CASCADE,
    last_tournament_id BIGINT
);
