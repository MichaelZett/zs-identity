# zs-identity

Wiederverwendbarer Identity-Baustein für Spring-Boot-Anwendungen:
Benutzerkonto, Selbstregistrierung mit E-Mail-Bestätigung, Anmeldung,
Passwort-Reset, Rollen und Berechtigungen.

| Artefakt                          | Inhalt                                                        |
|-----------------------------------|---------------------------------------------------------------|
| `de.zettsystems:identity-core`    | Domäne, Services, Spring-Security-Anbindung, Auto-Konfiguration, Flyway-Migrationen. Ohne UI. |
| `de.zettsystems:identity-vaadin`  | Vaadin-Flow-Views: Login, Registrierung, Bestätigung, Passwort vergessen/zurücksetzen. Optional. |

Stack: Java 25, Spring Boot 4.1, Spring Data JPA, Spring Security, Vaadin 25
(nur `identity-vaadin`), PostgreSQL.

## Einbinden

```groovy
repositories {
    mavenCentral()
    maven {
        name = 'GitHubPackages'
        url = uri('https://maven.pkg.github.com/MichaelZett/zs-identity')
        credentials {
            username = project.findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = project.findProperty('gpr.key') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation "de.zettsystems:identity-core:${identityVersion}"
    implementation "de.zettsystems:identity-vaadin:${identityVersion}"   // optional
}
```

`gpr.user`/`gpr.key` (Personal Access Token mit `read:packages`) gehören in
`~/.gradle/gradle.properties`, nie ins Projekt.

Die Anwendung muss danach genau drei Dinge tun — alles andere erledigt die
Auto-Konfiguration:

1. **Rollen deklarieren**: eine `RoleCatalog`-Bean mit den fachlichen Rollen.
   Ohne sie gibt es nur `SYSTEM_ADMIN` und `USER`. Der `RoleSynchronizer`
   spiegelt den Katalog beim Start in die Datenbank.
2. **Security-Kette** konfigurieren. Für Vaadin:
   ```java
   http.with(VaadinSecurityConfigurer.vaadin(), c -> c.loginView(LoginView.class));
   ```
   und die öffentlichen Pfade (`IdentityPaths.*` aus dem Kern, in Vaadin-Apps
   gleichbedeutend `IdentityRoutes.*`) per `permitAll()` freigeben.
3. **Vaadin-Routen sichtbar machen** (nur mit `identity-vaadin`):
   `vaadin.allowed-packages` um `de.zettsystems.identity` ergänzen.
4. **Erzwungener Passwortwechsel** (ab 0.3.0, nur mit `identity-vaadin`):
   `UserAccountService#requirePasswordChange(userId)` — etwa nach dem Anlegen
   eines Kontos mit Startpasswort. Der Baustein führt das Konto danach bei
   jeder Navigation auf `IdentityRoutes.CHANGE_PASSWORD` (`password/change`),
   bis ein neues Passwort gesetzt ist; die Ansicht ist `@PermitAll`, die
   Security-Kette der Anwendung muss sie also für Angemeldete nicht eigens
   freigeben. Die Migration `V1_3` ist niedriger nummeriert als App-Migrationen
   — `spring.flyway.out-of-order: true` bleibt Pflicht.
5. **Token-Aufräumlauf** (ab 0.5.0): `TokenCleanupScheduler` läuft täglich
   um 03:15, sobald die Anwendung `@EnableScheduling` setzt; abschaltbar mit
   `zs.identity.token-cleanup.enabled=false`. Mitgliederlisten laden ihre
   Konten mit `UserAccountService#findAllById(ids)` in einer Abfrage.
6. **Konto löschen** (ab 0.4.0): `UserAccountService#deleteAccount(userId)`
   entfernt Konto, Rollenzuordnung und Tokens endgültig. Eigene Daten der
   Anwendung zu dieser Kennung vorher selbst aufräumen — der Baustein kennt
   sie nicht.
7. **Einladen** (ab 0.6.0): `InvitationService` führt eine Person über einen
   Mail-Link zu ihrem Konto, statt ein Startpasswort zu verschicken.
   `inviteToClaim(userId, email)` für ein bestehendes verwaltetes Konto — die
   `userId` und damit alles, was die Anwendung daran hängt, bleibt stabil;
   `inviteNewAccount(email, name)` legt eines an. Mit
   `self-registration-enabled=false` ist das der einzige Weg herein. Die
   Ansicht liegt in `identity-vaadin` unter `IdentityRoutes.CLAIM_ACCOUNT`; wer
   einladen darf, entscheidet die Anwendung — der Baustein prüft es nicht.
   Wer einen **eigenen `IdentityMailSender`** mitbringt, setzt dafür
   `sendInvitation(..)` um; bis dahin scheitert der Versand mit einer klaren
   Meldung, statt still nichts zu tun.

## Konfiguration (`zs.identity.*`)

| Schlüssel                     | Default                  | Bedeutung |
|-------------------------------|--------------------------|-----------|
| `self-registration-enabled`   | `true`                   | Selbstregistrierung erlaubt |
| `email-verification-required` | `true`                   | Konto erst nach bestätigter Adresse nutzbar |
| `token-validity`              | `24h`                    | Gültigkeit von Bestätigungs-/Reset-Links |
| `invitation-validity`         | `7d`                     | Gültigkeit von Einladungslinks |
| `password-min-length`         | `12`                     | Mindestlänge (≥ 8) |
| `from-address` / `from-name`  | `noreply@localhost` / `Application` | Absender der Mails |
| `base-url`                    | `http://localhost:8080`  | Basis der Links in Mails |
| `default-role-code`           | `USER`                   | Rolle neuer Konten |
| `name-mode`                   | `FULL_NAME`              | `FULL_NAME` (Vor-/Nachname) oder `DISPLAY_NAME` (frei gewählter Name) |
| `locale`                      | `de`                     | Rückfallsprache für Mails und Views (siehe Sprachen) |
| `ui.max-width`                | `28rem`                  | Breite, ab der die Formularspalte nicht weiter mitwächst |
| `ui.class-names`              | —                        | zusätzliche CSS-Klassen an jeder Ansicht (Andockpunkt fürs eigene Theme) |
| `ui.notification-duration`    | `5s`                     | Standzeit der Hinweise; `0` = bis zum Wegklicken |

**Mailversand ist optional** (seit 0.7.0). `spring-boot-starter-mail` hängt nur
noch `compileOnly` am Baustein — wer Mails verschicken will, nimmt ihn selbst
auf:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-mail'
```

Ohne ihn startet die Anwendung trotzdem: Der Baustein fällt auf den
Log-Versand zurück (`LoggingIdentityMailSender`), und eine Anwendung mit
eigenem Versandweg (Transaktionsmail-Dienst) stellt einfach eine eigene
`IdentityMailSender`-Bean bereit. Ist der Starter da und `spring.mail.*`
konfiguriert, gehen echte Mails raus. Jede Mail geht als
`multipart/alternative` hinaus — Text und daneben ein schlichter HTML-Teil, in
dem der Link ein echtes `<a href>` ist. Grund ist Outlook: In Nur-Text-Mails
bricht es lange Zeilen um und macht aus einem Link über 76 Zeichen keinen
anklickbaren mehr.

## Sprachen

Alle Texte — Views, Mails und die Meldungen zu `IdentityMessageKeys` — kommen
aus mitgelieferten Sprachdateien: **Deutsch** und **Englisch**, unter
`de/zettsystems/identity/messages/` (`core*` in `identity-core`, `ui*` in
`identity-vaadin`). Die Datei ohne Sprachkürzel ist die Rückfallebene und ist
englisch; eine Sprache ohne eigene Datei bekommt also Englisch, nie die
Spracheinstellung des Servers.

Welche Sprache eine Ansicht spricht:

* Bringt die Anwendung eine Sprachwahl mit (Vaadins `I18NProvider`), folgen
  die Views der Sprache der `UI` — also dem Browser bzw. der Umschaltung in
  der Anwendung.
* Ohne `I18NProvider` gilt `zs.identity.locale`. Grund: Vaadin setzt die
  UI-Sprache dann auf die Voreinstellung der Server-JVM, und die sagt nichts
  über die Anwendung aus.

**Die Sprache eines Kontos** steht seit 0.7.0 am Konto selbst
(`auth_user.locale`, Migration V1_4) — nötig, weil eine Mail ohne Browser
entsteht und dort niemand nach der Spracheinstellung zu fragen ist. Sie
entscheidet über die Sprache **jeder Mail** dieses Bausteins; hat das Konto
keine gewählt, gilt weiterhin `zs.identity.locale`.

Sie füllt sich von selbst: Die `RegistrationView` übergibt die Sprache, in der
registriert wurde, die `ClaimAccountView` die der Einlöse-Ansicht. Eine
Anwendung, die selbst registriert oder einlädt, reicht sie mit durch:

```java
registrationService.register(email, password, name, UI.getCurrent().getLocale());
invitationService.inviteNewAccount(email, name, Locale.GERMAN);
```

Später ändern — etwa in den Kontoeinstellungen der Anwendung:

```java
userAccountService.changeLocale(userId, Locale.ENGLISH);  // null nimmt die Wahl zurueck
```

Gelesen wird sie über `UserAccountDto.locale()` (`null` = keine Wahl) oder
bequemer über `localeOr(fallback)`. Eine **Ansicht** dafür bringt der Baustein
nicht mit: Wo die Sprachwahl hingehört — Kontoeinstellungen, Kopfzeile,
Anmeldeformular —, weiß nur die Anwendung.

Eigene Texte: eine eigene `IdentityMessages`-Bean verdrängt die Voreinstellung.
Wer nur einzelne Schlüssel ersetzen will, beantwortet diese selbst und reicht
den Rest an `IdentityMessages.resourceBundles()` weiter.

```java
@Bean
IdentityMessages identityMessages() {
    IdentityMessages fallback = IdentityMessages.resourceBundles();
    return (key, locale, args) -> switch (key) {
        case IdentityMessageKeys.SELF_REGISTRATION_DISABLED -> "Bitte wende dich an die Turnierleitung.";
        default -> fallback.get(key, locale, args);
    };
}
```

## Rollen, global und je Mandant

Rollen kommen aus dem `RoleCatalog` der Anwendung und werden einem Konto
zugeteilt — entweder **global** oder für einen **Geltungsbereich** (seit
0.7.0):

```java
userAccountService.grantRole(userId, "USER");                            // gilt ueberall
userAccountService.grantRole(userId, "GROUP_ADMIN", Scope.of("club", "17"));
```

Ein `Scope` besteht aus Art und Kennung (`club:17`). **Der Baustein deutet ihn
nie** — was ein `club` ist, weiß allein die Anwendung; gespeichert und
verglichen wird er nur. Das hält die Regel „kein Wissen über einbindende
Anwendungen" aufrecht und funktioniert deshalb für Vereine, Turniere, Spiele
oder was sonst ein Mandant sein soll.

Fragen an ein Konto (`UserAccountDto`, oder direkt am `UserAccountService`):

| Frage | Aufruf |
|---|---|
| Welche Rollen gelten überall? | `roleCodes()` |
| Darf sie das hier? | `hasRole("GROUP_ADMIN", scope)` — globale zählen mit |
| Was gilt in diesem Bereich? | `rolesIn(scope)` / `userAccountService.rolesOf(userId, scope)` |
| Alle Vereine dieser Person? | `scopesOf("club")` / `userAccountService.scopesOf(userId, "club")` |

**In der Anmeldung** liegen bereichsgebundene Rollen qualifiziert an
(`ROLE_GROUP_ADMIN@club:17`), globale wie bisher (`ROLE_USER`). Dazu gibt es
einen **aktiven Bereich**: Dessen Rollen gelten zusätzlich unqualifiziert,
sodass die gewohnten Prüfungen lesbar bleiben.

```java
activeScopeService.switchTo(Scope.of("club", "17"));   // etwa beim Betreten eines Vereins

@RolesAllowed("GROUP_ADMIN")                           // heisst jetzt: in Verein 17
@PreAuthorize("hasAuthority('ROLE_GROUP_ADMIN@club:4')")  // gezielt anderswo, z. B. Deep-Link
```

Beim Anmelden ist **kein** Bereich aktiv — welcher es sein soll, weiß nur die
Anwendung (Adresszeile, letzte Auswahl, Startseite). „Automatisch den
einzigen" wäre bequem und würde sich bei der zweiten Mitgliedschaft
stillschweigend anders verhalten. Ohne aktiven Bereich gelten die globalen
Rollen; das ist die sichere Vorgabe.

Anwendungen ohne Mandanten merken von alledem nichts: Ohne `Scope` vergeben,
ist jede Rolle global, und `roleCodes()`, `hasRole(code)` und
`@RolesAllowed` verhalten sich wie vor 0.7.0.

## Die mitgelieferten Ansichten

Anmeldung, Registrierung, Passwort-Reset und die Einlöse-Ansicht sind oft die
**ersten** Seiten, die ein neues Mitglied sieht — sie gehören aber dem
Baustein und können das Theme der Anwendung nicht kennen. Sie halten deshalb
eine Untergrenze ein, die überall trägt (`IdentityFormView`):

* **Eine Spalte, zentriert, mit Höchstbreite** (`zs.identity.ui.max-width`).
  Am Telefon füllt sie die Breite, am Rechner wächst sie nicht ins Unlesbare.
* **Kein Querscrollen bei 375 px.** `border-box`, keine festen Pixelbreiten,
  jedes Feld und jeder Knopf über die volle Spaltenbreite. Ein Test hält das
  für alle Ansichten fest (`IdentityViewLayoutTest`).
* **Keine Farbe von Hand** — Hervorhebung nur über Vaadins Varianten, damit
  nichts mit dem dunklen Erscheinungsbild einer Anwendung kollidiert.

**Mitstylen** geht über feste CSS-Klassen statt über Einstellungen: Jede
Ansicht trägt `identity-view` und eine eigene Kennung
(`identity-view--login`, `--registration`, `--forgot-password`,
`--resend-verification`, `--reset-password`, `--change-password`,
`--claim-account`, `--confirm-email`); die Anmeldung hat zusätzlich innen
`identity-view__column`. Eigene Klassen kommen über
`zs.identity.ui.class-names` an jede Ansicht:

```yaml
zs:
  identity:
    ui:
      max-width: 32rem
      class-names: [ my-app-card ]
```

```css
.identity-view.my-app-card {
    border-radius: var(--my-app-radius);
    box-shadow: var(--my-app-shadow);
}
```

Wem das nicht reicht, der schreibt eine eigene Ansicht — dann aber unter einem
eigenen Pfad und mit abgeschalteter Paketsuche für `de.zettsystems.identity.ui`:
Zwei `@Route` auf demselben Pfad lehnt Vaadin ab.

## Java-Module (JPMS)

Beide Artefakte tragen einen **`Automatic-Module-Name`** im Manifest:

| Artefakt | Modulname |
|---|---|
| `identity-core` | `de.zettsystems.identity` |
| `identity-vaadin` | `de.zettsystems.identity.ui` |

Damit hat eine Anwendung, die selbst JPMS benutzt, einen stabilen Namen für
`requires` — ohne den Eintrag leitet Java ihn aus dem Dateinamen ab, und der
ändert sich mit jeder Version.

Ein echtes `module-info.java` bringt der Baustein **nicht** mit, und das ist
Absicht: Spring, Hibernate und Vaadin laufen auf dem Klassenpfad, nicht im
Modulpfad — ein Moduldeskriptor wäre dort wirkungslos. Er würde außerdem
`opens` für die Reflection von Spring und Hibernate erzwingen, und die
Sprachdateien beider Artefakte liegen im selben Ressourcen-Paket
(`de/zettsystems/identity/messages/`), was JPMS als geteiltes Paket ablehnt.
Sobald das Spring-Ökosystem im Modulpfad ankommt, ist das der Moment, das
nachzuholen — vorher kostet es nur.

## Datenbank

`identity-core` liefert seine Flyway-Migrationen unter `classpath:db/identity`
mit und hängt den Ablageort selbst an `spring.flyway.locations`
(`IdentityFlywayAutoConfiguration`). Versionsraum **V1_x** ist für den
Baustein reserviert; Anwendungen belegen **V2_x** aufwärts. Weil eine neue
Baustein-Migration niedriger nummeriert ist als bereits angewendete
App-Migrationen, braucht die Anwendung `spring.flyway.out-of-order: true`.

Tabellen: `auth_user`, `auth_role`, `auth_role_authority`, `auth_user_role`
(mit `scope_type`/`scope_id`, leer = global), `auth_token`.

## Namensmodell

`AccountName` trägt immer einen `displayName`; `firstName`/`lastName` sind nur
bei `name-mode = FULL_NAME` gesetzt. `UserAccountDto.firstName()`/`lastName()`
liefern dann leere Strings statt `null`. Anwendungen verknüpfen ihre
Fachobjekte ausschließlich über die Konto-ID (`UserAccountDto.id()`,
`IdentityUserDetails.userId()` im `SecurityContext`).

## Entwicklung

```
./gradlew build                 # kompiliert, testet, SpotBugs, JaCoCo (Testcontainers → Docker nötig)
./gradlew sonar                 # SonarQube gegen die lokale Instanz (SONAR_TOKEN)
./gradlew publishToMavenLocal   # lokale Iteration mit einer App (mavenLocal() dort eintragen)
./gradlew dependencyUpdates     # Versionen prüfen (-Punstable / -Pmajor zeigt RC/Major)
```

Release: Abschnitt in `CHANGELOG.md` benennen, Version in
`gradle.properties` ohne `-SNAPSHOT` setzen und auf
`main` pushen — die CI publiziert nach GitHub Packages, legt Tag und
GitHub-Release `v<version>` an und hebt die Version anschließend selbst auf
die nächste Patch-`-SNAPSHOT`. SNAPSHOTs werden nicht veröffentlicht;
Anwendungen binden ausschließlich Release-Versionen ein (lokale Iteration
über `publishToMavenLocal`). Minor-/Major-Sprung: Version vor dem Release
von Hand setzen.
