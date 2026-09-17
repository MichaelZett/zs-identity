-- Sprache am Konto. Mails entstehen ohne Browser: Beim Versand gibt es keine
-- Sitzung, deren Spracheinstellung man fragen koennte, und bisher ging jede
-- Mail in zs.identity.locale hinaus — auch an Konten, die sich in einer
-- anderen Sprache registriert hatten.
--
-- Nullbar und ohne Vorbelegung: NULL heisst "keine eigene Wahl getroffen",
-- dann gilt weiterhin zs.identity.locale. Bestehende Konten auf die
-- Anwendungssprache zu setzen waere geraten und liesse sich spaeter nicht
-- mehr von einer echten Wahl unterscheiden.
--
-- 35 Zeichen: Laenge eines BCP-47-Sprachkennzeichens nach RFC 5646 in der
-- Praxis (Sprache-Schrift-Region-Variante); gespeichert wird genau das, was
-- Locale#toLanguageTag liefert.
--
-- Niedriger nummeriert als die laengst angewendeten App-Migrationen (V2_x):
-- die einbindende Anwendung braucht spring.flyway.out-of-order: true.
ALTER TABLE auth_user
    ADD COLUMN locale VARCHAR(35);
