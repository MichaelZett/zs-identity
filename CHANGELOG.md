# Changelog

Nennenswerte Änderungen an zs-identity. Format nach
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach
[SemVer](https://semver.org/lang/de/). Beim Release wird `## Unreleased` in
`## <version> - <Datum>` umbenannt.

## 0.6.0 - 2026-09-16

### Added
- **Einladungen** (`InvitationService`). Ein Mechanismus für zwei Fälle:
  Ein **verwaltetes Konto beanspruchen** — `inviteToClaim(userId, email)`
  trägt die Adresse an einem Konto ohne Anmeldedaten nach und verschickt den
  Link; die `userId` bleibt stabil, die fachlichen Daten der Anwendung hängen
  weiter daran. Und **Registrierung nur auf Einladung** —
  `inviteNewAccount(email, name)` legt ein Konto **ohne Passwort** an und lädt
  es ein; zusammen mit `zs.identity.self-registration-enabled=false` kommt
  damit nur herein, wer eingeladen wurde. Ein Startpasswort muss niemand mehr
  übermitteln.
  Dazu: `AuthTokenType.INVITATION`, die Ansicht `ClaimAccountView` unter
  `IdentityRoutes.CLAIM_ACCOUNT` (`invitation`), `resendInvitation(userId)`
  (entwertet die vorige Einladung) und `findInvitee(token)`, mit dem die
  Ansicht den Namen des eingeladenen Kontos anzeigt.
- **`zs.identity.invitation-validity`** (Default `7d`): eigene Frist für
  Einladungen. Sie hat niemand angefordert — sie liegt im Postfach, bis
  jemand Zeit hat, und darf nicht über Nacht verfallen wie ein Reset-Link.
  Die Mail nennt glatte Fristen über einem Tag jetzt in Tagen
  (`identity.mail.validity.days`); `24h` steht unverändert als „24 Stunden".
- **`IdentityMailSender#sendInvitation`** als `default`-Methode, die scheitert,
  statt still nichts zu tun. Anwendungen mit eigenem Versandweg kompilieren
  unverändert weiter und setzen sie um, sobald sie einladen wollen.

### Changed
- **`IdentityProperties` hat eine Komponente mehr** (`invitationValidity`,
  an vierter Stelle). Wer den Record von Hand baut — in Tests üblich —,
  passt den Aufruf an. Ein zweiter Konstruktor wäre der bequemere Weg gewesen,
  macht die Bindung von `@ConfigurationProperties` aber mehrdeutig
  („No default constructor found“). Deshalb ein Minor-Sprung auf 0.6.0.

### Neue Meldungsschlüssel
`identity.error.accountAlreadyClaimed`, `identity.mail.invitation.subject`,
`identity.mail.invitation.body`, `identity.mail.invitation.body.html`,
`identity.mail.validity.days`, `identity.claim.*` (Ansicht). Wer die Texte
selbst setzt, ergänzt sie.

## 0.5.1 - 2026-09-09

### Fixed
- **Klickbarer Link in Bestätigungs- und Reset-Mail.** Beide Mails gehen jetzt
  als `multipart/alternative` hinaus: derselbe Text wie bisher und daneben ein
  HTML-Teil mit echtem `<a href>`. Outlook bricht in Nur-Text-Nachrichten lange
  Zeilen um und machte aus dem Link — mit dem 43-stelligen Token immer über 76
  Zeichen — keinen anklickbaren mehr. Neue Schlüssel
  `identity.mail.verification.body.html` und `identity.mail.reset.body.html`;
  wer die Texte selbst setzt, ergänzt sie.

## 0.5.0 - 2026-08-30

### Added
- **Bündelabfrage.** `UserAccountService#findAllById(Collection<Long>)` lädt
  mehrere Konten samt Rollen in einer Abfrage — für Mitgliederlisten statt
  eines `findById` je Zeile.
- **Token-Aufräumlauf.** `TokenCleanupScheduler` löscht täglich um 03:15
  eingelöste und seit mehr als 7 Tagen abgelaufene Token
  (`AuthTokenRepository#deleteObsolete`). Läuft nur, wenn die Anwendung
  `@EnableScheduling` setzt; abschaltbar mit
  `zs.identity.token-cleanup.enabled=false`.

## 0.4.0 - 2026-08-30

### Added
- **Konto löschen.** `UserAccountService#deleteAccount(userId)` entfernt ein
  Konto endgültig samt Rollenzuordnung und Tokens (Fremdschlüssel mit
  `ON DELETE CASCADE`). Die Anwendung räumt ihre eigenen Daten zur Kennung
  vorher selbst auf. Gedacht für die Verwaltung, etwa bei doppelt angelegten
  Konten.

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
