-- Baseline for a fresh installation: the state V1_1 to V1_6 arrive at, in one
-- script. Flyway runs it only where our history is empty; an existing
-- installation never sees it, and the V scripts stay in the artefact for the
-- ones that are still on their way up. IdentityMigrationsIT checks that this
-- script and the V chain produce the same schema -- change neither without
-- the other.
--
-- From here on: never change an applied script, only add new versions.
--
-- Every INCREMENT BY 20 has to match the allocationSize of the corresponding
-- @SequenceGenerator. If it differs, Hibernate hands out ids that exist.

CREATE SEQUENCE auth_user_seq START WITH 1 INCREMENT BY 20;
CREATE SEQUENCE auth_role_seq START WITH 1 INCREMENT BY 20;
CREATE SEQUENCE auth_token_seq START WITH 1 INCREMENT BY 20;
CREATE SEQUENCE auth_user_role_seq START WITH 1 INCREMENT BY 20;
CREATE SEQUENCE auth_passkey_seq START WITH 1 INCREMENT BY 20;

-- email and password_hash may be empty: an application can create managed
-- accounts for people who never register themselves, and they can neither
-- be reached nor sign in. Deliberately no CHECK "both or neither": claiming
-- an account passes through "address set, password not yet"; the building
-- block's code keeps the invariant.
--
-- display_name is the one required name. first_name and last_name are
-- optional because applications with freely chosen names
-- (zs.identity.name-mode = DISPLAY_NAME) keep no real names.
--
-- locale is NULL for "no choice of its own"; zs.identity.locale applies then.
-- 35 characters hold a BCP 47 tag as Locale#toLanguageTag writes it.
--
-- passkey_user_handle is the opaque id WebAuthn knows the account by. Random
-- rather than the address, because the authenticator stores it and the
-- specification forbids personal data in it; NULL until the first passkey.
CREATE TABLE auth_user
(
    id                   BIGINT       NOT NULL PRIMARY KEY,
    version              BIGINT       NOT NULL DEFAULT 0,
    email                VARCHAR(320),
    password_hash        VARCHAR(128),
    first_name           VARCHAR(128),
    last_name            VARCHAR(128),
    enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_login_at        TIMESTAMP(6) WITH TIME ZONE,
    display_name         VARCHAR(260) NOT NULL,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    locale               VARCHAR(35),
    passkey_user_handle  VARCHAR(128)
);

-- Addresses are stored in lower case, so an index on the column keeps them
-- unique. Accounts without an address do not collide: PostgreSQL treats NULL
-- values as distinct from each other.
CREATE UNIQUE INDEX ux_auth_user_email ON auth_user (email);
CREATE UNIQUE INDEX ux_auth_user_passkey_user_handle ON auth_user (passkey_user_handle);

CREATE TABLE auth_role
(
    id               BIGINT       NOT NULL PRIMARY KEY,
    version          BIGINT       NOT NULL DEFAULT 0,
    code             VARCHAR(64)  NOT NULL,
    display_name_key VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX ux_auth_role_code ON auth_role (code);

CREATE TABLE auth_role_authority
(
    role_id   BIGINT       NOT NULL REFERENCES auth_role (id) ON DELETE CASCADE,
    authority VARCHAR(128) NOT NULL,
    PRIMARY KEY (role_id, authority)
);

-- A role assignment with its scope: "admin OF club 17" rather than "admin".
-- Empty scope columns mean a global role. NOT NULL DEFAULT '' rather than
-- NULL on purpose: PostgreSQL treats NULLs in a unique index as distinct, and
-- the same global role could then be assigned any number of times. The
-- assignment has a key of its own (the entity RoleAssignment); the index
-- below carries the uniqueness.
CREATE TABLE auth_user_role
(
    user_id    BIGINT      NOT NULL REFERENCES auth_user (id) ON DELETE CASCADE,
    role_id    BIGINT      NOT NULL REFERENCES auth_role (id) ON DELETE CASCADE,
    scope_type VARCHAR(32) NOT NULL DEFAULT '',
    scope_id   VARCHAR(64) NOT NULL DEFAULT '',
    id         BIGINT      NOT NULL PRIMARY KEY,
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_auth_user_role ON auth_user_role (user_id, role_id, scope_type, scope_id);
-- "Which scopes does this person have" goes through the user, "who belongs to
-- club 17" through the scope.
CREATE INDEX ix_auth_user_role_scope ON auth_user_role (scope_type, scope_id);

CREATE TABLE auth_token
(
    id         BIGINT      NOT NULL PRIMARY KEY,
    version    BIGINT      NOT NULL DEFAULT 0,
    user_id    BIGINT      NOT NULL REFERENCES auth_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    type       VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP(6) WITH TIME ZONE
);

-- Redeeming looks the token up by its hash; unique, because a hash may never
-- be handed out twice.
CREATE UNIQUE INDEX ux_auth_token_hash ON auth_token (token_hash);
CREATE INDEX ix_auth_token_user_type ON auth_token (user_id, type);

-- Passkeys (WebAuthn credentials), one row per registered authenticator. The
-- account owns them (ON DELETE CASCADE). The credential id is unique across
-- all accounts: when signing in, the authenticator names its credential and
-- nothing else. Binary parts are stored as base64url text, the shape they
-- travel through JSON in; the attestation has no length limit because packed
-- attestations run to a few kilobytes.
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
