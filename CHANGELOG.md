# Changelog

Nennenswerte Änderungen an zs-identity. Format nach
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach
[SemVer](https://semver.org/lang/de/). Beim Release wird `## Unreleased` in
`## <version> - <Datum>` umbenannt.

## Unreleased

## 0.2.0 - 2026-08-28

### Added
- Alle Texte — Ansichten, Mails, Meldungen zu `IdentityMessageKeys` — kommen
  aus mitgelieferten Sprachdateien in **Deutsch und Englisch**. Auflösung über
  den neuen Port `IdentityMessages`; eine eigene Bean ersetzt einzelne oder
  alle Texte (`IdentityMessages.resourceBundles()` als Rückfall).
- Einstellung `zs.identity.locale` (Default `de`): Sprache der Mails und
  Rückfallsprache der Ansichten. Bringt die Anwendung einen `I18NProvider`
  mit, folgen die Ansichten stattdessen der Sprache der `UI`.
- Browserlose Tests für `identity-vaadin` mit Karibu; Coverage-Schwelle auf
  78 % Line / 80 % Branch angehoben, die Ansichten sind nicht mehr
  ausgenommen.

### Changed
- **Bruch:** `IdentityProperties` hat die Komponente `locale`, die
  View-Konstruktoren und `IdentityMailFactory.javaMail(…)` je einen Parameter
  `IdentityMessages` mehr. Anwendungen, die nur über die Auto-Konfiguration
  einbinden, merken davon nichts.
- Seitentitel über `HasDynamicTitle` statt `@PageTitle` — eine Annotation
  kann nicht übersetzen.
- Unbekannte Meldungsschlüssel zeigen den allgemeinen Text statt des rohen
  Schlüssels (`IdentityMessageKeys.UNEXPECTED`).

## 0.1.0 - 2026-08-28

### Added
- Erstes Release: Benutzerkonto, Selbstregistrierung mit E-Mail-Bestätigung,
  Anmeldung, Passwort-Reset, Rollen und Berechtigungen als
  `identity-core` (ohne UI) und `identity-vaadin`.
- Flyway-Migrationen im Versionsraum `V1_x`, Auto-Konfiguration ohne
  Komponentensuche, Veröffentlichung nach GitHub Packages über die Pipeline.
