-- Passkeys (WebAuthn credentials), one row per registered authenticator.
--
-- The account owns its passkeys: ON DELETE CASCADE, so deleting an account
-- takes them along without the code having to remember. The credential id is
-- unique across ALL accounts -- when signing in, the authenticator names its
-- credential and nothing else; the account is only known once the signature
-- has been checked.
--
-- Binary parts (credential id, public key in COSE encoding, attestation) are
-- stored as base64url text: that is the shape they travel through JSON in,
-- and an entity with arrays would need a defensive copy on every access.
-- The attestation object has no length limit because it comes from the
-- authenticator and packed attestations run to a few kilobytes.
--
-- passkey_user_handle on auth_user is the opaque id WebAuthn knows the
-- account by (the "user handle"). Random rather than the email address,
-- because the authenticator stores it and shows it to nobody -- and the
-- specification forbids personal data in it. Assigned the first time a
-- passkey is registered; NULL until then.
--
-- INCREMENT BY 20 has to match the allocationSize of the @SequenceGenerator.
CREATE SEQUENCE auth_passkey_seq START WITH 1 INCREMENT BY 20;

ALTER TABLE auth_user
    ADD COLUMN passkey_user_handle VARCHAR(128);

CREATE UNIQUE INDEX ux_auth_user_passkey_user_handle ON auth_user (passkey_user_handle);

CREATE TABLE auth_passkey
(
    id                           BIGINT        NOT NULL PRIMARY KEY,
    version                      BIGINT        NOT NULL DEFAULT 0,
    user_id                      BIGINT        NOT NULL REFERENCES auth_user (id) ON DELETE CASCADE,
    credential_id                VARCHAR(1024) NOT NULL,
    credential_type              VARCHAR(32)   NOT NULL,
    public_key_cose              TEXT          NOT NULL,
    signature_count              BIGINT        NOT NULL,
    uv_initialized               BOOLEAN       NOT NULL DEFAULT FALSE,
    backup_eligible              BOOLEAN       NOT NULL DEFAULT FALSE,
    backup_state                 BOOLEAN       NOT NULL DEFAULT FALSE,
    transports                   VARCHAR(255),
    attestation_object           TEXT,
    attestation_client_data_json TEXT,
    label                        VARCHAR(128)  NOT NULL,
    created_at                   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_used_at                 TIMESTAMP(6) WITH TIME ZONE
);

CREATE UNIQUE INDEX ux_auth_passkey_credential_id ON auth_passkey (credential_id);
CREATE INDEX ix_auth_passkey_user ON auth_passkey (user_id);
