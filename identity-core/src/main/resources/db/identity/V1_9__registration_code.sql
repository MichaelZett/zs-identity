-- Self-registration that has not been completed yet (1.5.0).
--
-- registration_pending marks an account that came through the registration
-- form and has not become usable yet. It is cleared the moment the account
-- does, and AccountRegistered is published then -- exactly once.
--
-- registration_code is the invitation code the account registered with, kept
-- only until then. The building block does not interpret it; the application
-- receives it with the event. 100 characters is the limit the service checks.
--
-- Accounts registered before this migration count as completed: their
-- confirmation, should it still come, publishes nothing.
ALTER TABLE auth_user
    ADD COLUMN registration_pending BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN registration_code    VARCHAR(100);
