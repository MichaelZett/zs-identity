-- Geltungsbereich an der Rollenzuweisung: "Admin VON VEREIN 17" statt nur
-- "Admin". Noetig, weil mehrere einbindende Anwendungen mandantenfaehig
-- werden und die Zuordnung Person->Mandant sonst jede fuer sich nachbaut.
--
-- Leere Spalten = globale Rolle, also genau das bisherige Verhalten. Bewusst
-- NOT NULL DEFAULT '' statt nullbar: PostgreSQL behandelt NULL-Werte in einem
-- eindeutigen Index als paarweise verschieden — mit NULL liessen sich
-- dieselbe globale Rolle beliebig oft vergeben.
--
-- Aus der reinen Verknuepfungstabelle wird damit eine Tabelle mit eigener
-- Bedeutung; sie bekommt deshalb einen eigenen Schluessel (und die Entity
-- RoleAssignment). Die fachliche Eindeutigkeit traegt der Index darunter.
--
-- INCREMENT BY 20 muss zur allocationSize des @SequenceGenerator passen.
CREATE SEQUENCE auth_user_role_seq START WITH 1 INCREMENT BY 20;

ALTER TABLE auth_user_role
    ADD COLUMN scope_type VARCHAR(32) NOT NULL DEFAULT '',
    ADD COLUMN scope_id   VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN id         BIGINT,
    ADD COLUMN version    BIGINT NOT NULL DEFAULT 0;

-- Bestehende Zuweisungen bekommen ihre Kennung; die Sequenz startet danach
-- oberhalb des Vergebenen.
UPDATE auth_user_role SET id = nextval('auth_user_role_seq') WHERE id IS NULL;

ALTER TABLE auth_user_role DROP CONSTRAINT auth_user_role_pkey;
ALTER TABLE auth_user_role ALTER COLUMN id SET NOT NULL;
ALTER TABLE auth_user_role ADD PRIMARY KEY (id);

CREATE UNIQUE INDEX ux_auth_user_role ON auth_user_role (user_id, role_id, scope_type, scope_id);

-- Die Frage "welche Bereiche hat diese Person" laeuft ueber den Benutzer,
-- die Frage "wer gehoert zu Verein 17" ueber den Bereich.
CREATE INDEX ix_auth_user_role_scope ON auth_user_role (scope_type, scope_id);
