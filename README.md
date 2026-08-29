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

## Konfiguration (`zs.identity.*`)

| Schlüssel                     | Default                  | Bedeutung |
|-------------------------------|--------------------------|-----------|
| `self-registration-enabled`   | `true`                   | Selbstregistrierung erlaubt |
| `email-verification-required` | `true`                   | Konto erst nach bestätigter Adresse nutzbar |
| `token-validity`              | `24h`                    | Gültigkeit von Bestätigungs-/Reset-Links |
| `password-min-length`         | `12`                     | Mindestlänge (≥ 8) |
| `from-address` / `from-name`  | `noreply@localhost` / `Application` | Absender der Mails |
| `base-url`                    | `http://localhost:8080`  | Basis der Links in Mails |
| `default-role-code`           | `USER`                   | Rolle neuer Konten |
| `name-mode`                   | `FULL_NAME`              | `FULL_NAME` (Vor-/Nachname) oder `DISPLAY_NAME` (frei gewählter Name) |
| `locale`                      | `de`                     | Sprache der Mails und Rückfallsprache der Views (siehe Sprachen) |

Mailversand: ist `spring.mail.*` konfiguriert, gehen echte Mails raus; sonst
landen die Links im Log (`LoggingIdentityMailSender`).

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

Mails gehen immer in `zs.identity.locale` — beim Versand gibt es keinen
Browser, und am Konto ist (noch) keine Sprache hinterlegt.

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

## Datenbank

`identity-core` liefert seine Flyway-Migrationen unter `classpath:db/identity`
mit und hängt den Ablageort selbst an `spring.flyway.locations`
(`IdentityFlywayAutoConfiguration`). Versionsraum **V1_x** ist für den
Baustein reserviert; Anwendungen belegen **V2_x** aufwärts. Weil eine neue
Baustein-Migration niedriger nummeriert ist als bereits angewendete
App-Migrationen, braucht die Anwendung `spring.flyway.out-of-order: true`.

Tabellen: `auth_user`, `auth_role`, `auth_role_authority`, `auth_user_role`,
`auth_token`.

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
