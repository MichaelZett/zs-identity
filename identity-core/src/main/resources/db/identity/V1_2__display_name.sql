-- Anzeigename als eigene Spalte. Bisher war er aus Vor- und Nachname
-- abgeleitet; Anwendungen mit frei gewähltem Namen (zs.identity.name-mode =
-- DISPLAY_NAME) führen keine Klarnamen, deshalb werden first_name/last_name
-- nullbar und display_name wird die einzige Pflichtangabe.
--
-- Bestehende Konten behalten ihre Klarnamen; der Anzeigename wird einmalig
-- daraus gebildet — dieselbe Regel, die der Code bisher zur Laufzeit anwandte.

ALTER TABLE auth_user ADD COLUMN display_name VARCHAR(260);

UPDATE auth_user SET display_name = first_name || ' ' || last_name;

ALTER TABLE auth_user ALTER COLUMN display_name SET NOT NULL;
ALTER TABLE auth_user ALTER COLUMN first_name DROP NOT NULL;
ALTER TABLE auth_user ALTER COLUMN last_name DROP NOT NULL;
