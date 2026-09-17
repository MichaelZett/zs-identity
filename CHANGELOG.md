# Changelog

Nennenswerte Änderungen an zs-identity. Format nach
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach
[SemVer](https://semver.org/lang/de/). Beim Release wird `## Unreleased` in
`## <version> - <Datum>` umbenannt.

## 0.7.0 - 2026-09-17

> Sprache am Konto, Oberflächen-Regeln und Rollen mit Geltungsbereich sind
> zusammen entstanden, ohne ein Release dazwischen.

### Added
- **Rollen mit Geltungsbereich** (Migration **V1_5**). Eine Rolle kann jetzt
  für einen Bereich gelten statt überall: „Admin **von Verein 17**".
  Hintergrund sind mehrere einbindende Anwendungen, die mandantenfähig werden;
  ohne den Bereich baut jede von ihnen dieselbe Zuordnungstabelle nach.
  - **`Scope(type, id)`** (`club:17`) als Wert. Der Baustein **deutet** ihn
    nie — er speichert, gibt zurück und vergleicht. Verboten sind `@`, `:`
    und Leerzeichen: Aus Rolle und Bereich wird ein Authority-Name, und eine
    Kennung mit diesen Zeichen könnte eine Berechtigung erfinden.
  - **`grantRole(userId, code, scope)`** / **`revokeRole(..)`**,
    **`rolesOf(userId, scope)`**, **`scopesOf(userId, type)`** — alle als
    `default`-Methoden, eigene Dienste kompilieren unverändert weiter. Eine
    globale Rolle bleibt beim Entzug im Bereich bestehen; sie ist eine andere
    Zuweisung.
  - **`UserAccountDto.roleAssignments()`** mit `hasRole(code, scope)`,
    `rolesIn(scope)` und `scopesOf(type)`. Globale Rollen zählen überall mit.
  - **Authorities sind qualifiziert**: `ROLE_ADMIN@club:17`, feingranulare
    Berechtigungen ebenso (`season:read@club:17`).
  - **`ActiveScopeService`**: Der Bereich, in dem die Person gerade arbeitet.
    Dessen Rollen gelten zusätzlich **ohne** Zusatz — nur deshalb bedeutet
    `@RolesAllowed("ADMIN")` weiterhin etwas Lesbares, nämlich „hier". Beim
    Anmelden ist kein Bereich aktiv; welcher es sein soll, weiß nur die
    Anwendung.
  - `auth_user_role` ist damit eine eigene Entity (`RoleAssignment`) statt
    einer `@ManyToMany`-Tabelle. Bestehende Zuweisungen wandern auf den
    leeren Bereich und verhalten sich unverändert.
- **Sprache am Konto** (`auth_user.locale`, Migration **V1_4**). Bisher ging
  jede Mail in `zs.identity.locale` hinaus, auch an Konten, die sich in einer
  anderen Sprache registriert hatten — beim Versand gibt es keinen Browser,
  den man fragen könnte. Jetzt entscheidet die Sprache des Kontos, und erst
  ohne eigene Wahl (`null`) gilt wieder die der Anwendung.
  Neu dafür: `UserAccountDto.locale()` und `localeOr(fallback)`,
  `UserAccountService#changeLocale(userId, locale)` — der Weg, den eine
  Anwendung ihren Kontoeinstellungen unterlegt —, sowie die Überladungen
  `RegistrationService#register(email, password, name, locale)`,
  `InvitationService#inviteNewAccount(email, name, locale)` und
  `InvitationService#claim(token, password, locale)`. Alle drei sind
  `default`-Methoden: Ein eigener Dienst einer Anwendung kompiliert unverändert
  weiter. `RegistrationView` und `ClaimAccountView` reichen ihre Sprache von
  selbst durch. Eine **Ansicht** für die Sprachwahl bringt der Baustein nicht
  mit — wo sie hingehört, weiß nur die Anwendung.
- **Oberflächen-Regeln für `identity-vaadin`** (`IdentityFormView`). Alle
  Ansichten des Bausteins erben jetzt eine gemeinsame Gestalt: eine zentrierte
  Spalte mit Höchstbreite, `border-box`, keine festen Pixelbreiten, jedes Feld
  und jeder Knopf über die volle Spaltenbreite — die Untergrenze ist „am
  Telefon und am Rechner brauchbar, kein Querscrollen bei 375 px", und ein
  Test hält sie für jede Ansicht fest. Dazu feste CSS-Klassen zum Mitstylen:
  `identity-view`, je Ansicht `identity-view--<name>`, in der Anmeldung innen
  `identity-view__column`.
- **`zs.identity.ui.*`**: `max-width` (Default `28rem`), `class-names` (eigene
  CSS-Klassen an jeder Ansicht — der Andockpunkt für das Theme der Anwendung)
  und `notification-duration` (Default `5s`, `0` lässt Hinweise stehen).
- **`IdentityProperties#withLocale(..)` und `#withUi(..)`**: Damit ein Test,
  der nur eine Einstellung wechseln will, nicht den ganzen Record abschreiben
  muss.
- **`ForgotPasswordView`/`ResendVerificationView`** kennzeichnen ihr
  Adressfeld als `autocomplete="username"` — der Passwortmanager bietet die
  Adresse damit an, statt sie tippen zu lassen.

### Build
- **Abhängigkeiten aktualisiert**: Vaadin 25.2.8, Karibu-Testing 2.7.3,
  NullAway 0.14.1 (Plugin 3.2.0), ErrorProne-Plugin 5.1.1, SpotBugs-Plugin
  6.5.11, ben-manes 0.64.0, Sonar-Plugin 7.5.0, Gradle-Wrapper 9.7.1.
- **OpenRewrite eingebunden** (`org.openrewrite.rewrite`, nur auf Zuruf): die
  eigenen Rezepte aus `de.zettsystems:zettsystems-recipes:1.0.0` plus
  `staticanalysis.CodeCleanup` und `RemoveUnusedImports` aus dem
  `rewrite-recipe-bom`. Einmal über den Bestand gelaufen: Import-Blöcke
  vereinheitlicht, qualifizierte Klassennamen durch Importe ersetzt,
  `x.equals("literal")` umgedreht, ein toter Import entfernt. Welche
  Rezeptgruppen geprüft und bewusst abgelehnt wurden, steht im `rewrite`-Block
  der `build.gradle`.
- **Boot-4-Testabhängigkeit modularisiert**: `spring-security-test` →
  `org.springframework.boot:spring-boot-starter-security-test` (Befund aus
  `spring.boot4.MigrateToModularStarters`).
- **`spring-boot-starter-validation` entfernt.** Der Baustein benutzt keine
  einzige Bean-Validation-Annotation; er zwang jeder einbindenden Anwendung
  Hibernate Validator auf. Wer ihn selbst braucht, nimmt ihn direkt auf.
- **`Automatic-Module-Name` in beiden Artefakten**: `de.zettsystems.identity`
  und `de.zettsystems.identity.ui`. Anwendungen, die JPMS benutzen, bekommen
  damit einen stabilen Modulnamen statt eines aus dem Dateinamen abgeleiteten.
  Ein `module-info.java` bleibt bewusst aus — Begründung im README.

### Changed
- **`spring-boot-starter-mail` ist optional geworden** (`compileOnly`).
  **Anwendungen, die Mails verschicken, nehmen ihn selbst auf** — eine Zeile
  im Build. Ohne ihn startet der Baustein weiterhin und fällt auf den
  Log-Versand zurück; eine Anwendung mit eigenem `IdentityMailSender` zahlt
  jakarta.mail gar nicht mehr mit. Dafür ist die Mail-Auto-Konfiguration
  zweigeteilt (`@ConditionalOnClass`), und `IdentityMailFactory.javaMail(..)`
  ist nach `JavaMailFactory.javaMail(..)` gewandert: In einer Anwendung ohne
  Mail-Bibliothek darf keine erreichbare Signatur einen Mail-Typ nennen.
  `IdentityMailAutoConfigurationTest` prüft das mit einem
  `FilteredClassLoader`, der die Bibliothek ausblendet.
- **`IdentityProperties` hat eine Komponente mehr** (`ui`, an letzter Stelle).
  Wer den Record von Hand baut — in Tests üblich —, passt den Aufruf an oder
  nimmt künftig `IdentityProperties.defaults().withLocale(..)`. Grund für den
  Minor-Sprung, wie schon bei 0.6.0.
- **`UserAccountDto` hat zwei Komponenten mehr** (`locale`; `roleCodes` ist
  zu `roleAssignments` geworden). Die Konstruktoren mit einfachen
  Rollencodes bleiben — ihre Rollen gelten dann global —, und `roleCodes()`
  antwortet weiterhin, jetzt mit den globalen Rollen. Anwendungen ohne
  Mandanten merken nichts davon.
- **`UserAccount#getRoles()`** liefert die **globalen** Rollen; alle
  Zuweisungen gibt es über `getRoleAssignments()`. `replaceRoles(..)` setzt
  nur die globalen neu — bereichsgebundene bleiben stehen, weil der Aufruf
  über Mandanten nichts aussagt. (Entity, verlässt das Modul nicht.)

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
