# Changelog

Nennenswerte Änderungen an zs-identity. Format nach
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach
[SemVer](https://semver.org/lang/de/). Beim Release wird `## Unreleased` in
`## <version> - <Datum>` umbenannt.

## 0.3.0 - 2026-08-29

### Added
- **Erzwungener Passwortwechsel.** `UserAccountService#requirePasswordChange(userId)`
  setzt das Flag `must_change_password` (Migration `V1_3`); jedes Setzen eines
  neuen Passworts — `changePassword` wie der Reset über „Passwort vergessen" —
  löscht es. `UserAccountDto` und `IdentityUserDetails` tragen
  `mustChangePassword()`. In `identity-vaadin` führt `PasswordChangeGuard`
  (angehängt über einen `VaadinServiceInitListener` per ServiceLoader) jede
  Route auf die neue `ChangePasswordView` (`IdentityRoutes.CHANGE_PASSWORD` =
  `password/change`, `@PermitAll`, ohne Layout, mit Abmelden-Knopf), bis das
  Passwort gewechselt ist. Nach dem Wechsel frischt der Baustein die laufende
  Sitzung auf.
- Neue Texte `identity.change.*` in beiden Sprachdateien.

### Changed
- `UserAccountDto` hat die neue Komponente `mustChangePassword` am Ende; der
  bisherige Konstruktor bleibt als Überladung (Wert `false`), Anwendungen
  müssen dafür nichts ändern.
- `IdentityUserDetails` hat einen öffentlichen Konstruktor mit dem neuen
  Parameter `mustChangePassword` — für Tests einbindender Anwendungen.
- Die Ansicht braucht Vaadins `AuthenticationContext`; die Bean kommt aus
  `vaadin-spring` von selbst.


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
