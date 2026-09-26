-- Sign-in through external identity providers (issue #1).
--
-- One row per account and provider: the account is known to the provider
-- under `subject` (OIDC "sub", GitHub's numeric user id), and that is what
-- the sign-in looks up by -- never the address. Addresses change, and some
-- providers let people change theirs to one they do not own; the subject a
-- provider hands out stays with the person.
--
-- registration_id is the key of the client registration in the application's
-- configuration (spring.security.oauth2.client.registration.<id>), not a
-- provider name: an application may register the same provider twice.
--
-- Unique per (registration_id, subject), so that one identity at a provider
-- never leads into two accounts; and unique per (user_id, registration_id),
-- so that an account has at most one identity per provider and unlinking can
-- name it by the provider alone.
--
-- email is what the provider reported at the last sign-in, for display only.
--
-- The account owns its identities: ON DELETE CASCADE, like its passkeys.
--
-- external_sign_in on auth_user says whether the account has at least one of
-- them. Kept by the code next to the rows, so that asking whether an account
-- is claimed -- which the mapping of every account does -- never has to load
-- them.
--
-- INCREMENT BY 20 has to match the allocationSize of the @SequenceGenerator.
CREATE SEQUENCE auth_external_identity_seq START WITH 1 INCREMENT BY 20;

CREATE TABLE auth_external_identity
(
    id              BIGINT       NOT NULL PRIMARY KEY,
    version         BIGINT       NOT NULL DEFAULT 0,
    user_id         BIGINT       NOT NULL REFERENCES auth_user (id) ON DELETE CASCADE,
    registration_id VARCHAR(64)  NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    email           VARCHAR(320),
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_used_at    TIMESTAMP(6) WITH TIME ZONE
);

CREATE UNIQUE INDEX ux_auth_external_identity_subject ON auth_external_identity (registration_id, subject);
CREATE UNIQUE INDEX ux_auth_external_identity_user ON auth_external_identity (user_id, registration_id);

ALTER TABLE auth_user
    ADD COLUMN external_sign_in BOOLEAN NOT NULL DEFAULT FALSE;
