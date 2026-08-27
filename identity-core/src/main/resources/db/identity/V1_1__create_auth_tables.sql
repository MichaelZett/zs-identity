-- Tabellen des Identity-Bausteins. Präfix auth_, damit sie sich in jeder
-- einbindenden Anwendung eindeutig von deren Fachtabellen unterscheiden.
--
-- Diese Datei beschreibt den **Endzustand**: Solange es außer der lokalen
-- Entwicklungsdatenbank keine Installation gibt, wird nicht fortgeschrieben,
-- sondern zusammengefasst (User-Entscheidung 2026-08-13). Ab der ersten
-- betriebenen Datenbank gilt wieder die Regel „nie eine angewendete Migration
-- ändern" — dann kommen neue Versionen dazu.
--
-- Der Ablageort ist bewusst db/identity und NICHT db/migration/identity: Flyway
-- durchsucht Ablageorte rekursiv, und der Vorgabe-Ablageort einer Anwendung ist
-- classpath:db/migration. Läge dieser Ordner darunter, fände Flyway dieselbe
-- Datei zweimal — einmal über den Vorgabe-Ablageort, einmal über den vom
-- Baustein beigesteuerten — und bräche mit „Found more than one migration with
-- version 1.1" ab. IdentityFlywayAutoConfiguration hängt classpath:db/identity
-- selbst an die Ablageorte an.
--
-- Versionsraum V1_x ist für diesen Baustein reserviert; Anwendungen belegen
-- V2_x. Beide teilen sich eine Flyway-History-Tabelle, kollidieren aber nicht.
--
-- INCREMENT BY 20 muss zur allocationSize der @SequenceGenerator in den
-- Entities passen. Weicht es ab, vergibt Hibernate Kennungen, die es schon gibt.

CREATE SEQUENCE auth_user_seq START WITH 1 INCREMENT BY 20;
CREATE SEQUENCE auth_role_seq START WITH 1 INCREMENT BY 20;
CREATE SEQUENCE auth_token_seq START WITH 1 INCREMENT BY 20;

-- email und password_hash sind nullbar: Verwaltete Konten legt eine Anwendung
-- (z. B. eine Gruppenleitung) an, ohne dass sich die Person selbst
-- registriert; sie haben weder Adresse noch Passwort und können sich deshalb
-- nicht anmelden. Bewusst ohne CHECK-Constraint „beide gesetzt oder beide
-- leer": Der spätere Beanspruchen-Fluss (E-Mail nachtragen, dann Passwort
-- setzen) hat den Zwischenzustand „E-Mail gesetzt, Passwort noch nicht" — die
-- Invariante sichert der Code im Baustein.
CREATE TABLE auth_user
(
    id             BIGINT       NOT NULL PRIMARY KEY,
    version        BIGINT       NOT NULL DEFAULT 0,
    email          VARCHAR(320),
    password_hash  VARCHAR(128),
    first_name     VARCHAR(128) NOT NULL,
    last_name      VARCHAR(128) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_login_at  TIMESTAMP(6) WITH TIME ZONE
);

-- Die Anwendung speichert Adressen bereits kleingeschrieben; der Index auf der
-- Spalte reicht deshalb aus und hält die Eindeutigkeit durch. Verwaltete
-- Konten stören nicht: PostgreSQL behandelt NULL-Werte als paarweise
-- verschieden.
CREATE UNIQUE INDEX ux_auth_user_email ON auth_user (email);

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

CREATE TABLE auth_user_role
(
    user_id BIGINT NOT NULL REFERENCES auth_user (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES auth_role (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

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

-- Der Einlösepfad sucht über den Hash; eindeutig, weil ein Hash nie zweimal
-- vergeben werden darf.
CREATE UNIQUE INDEX ux_auth_token_hash ON auth_token (token_hash);
CREATE INDEX ix_auth_token_user_type ON auth_token (user_id, type);
