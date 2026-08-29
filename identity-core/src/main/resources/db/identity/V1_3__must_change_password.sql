-- Erzwungener Passwortwechsel: Ein Konto kann verlangen, dass sein Passwort
-- bei der nächsten Anmeldung geändert wird — etwa ein von einer Verwaltung
-- angelegtes Konto mit Startpasswort oder ein von Hand zurückgesetztes.
-- Das Setzen eines neuen Passworts löscht das Flag an derselben Stelle.
--
-- Niedriger nummeriert als die längst angewendeten App-Migrationen (V2_x):
-- die einbindende Anwendung braucht spring.flyway.out-of-order: true.
ALTER TABLE auth_user
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
