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
   und die öffentlichen Pfade (`IdentityRoutes.*`) per `permitAll()` freigeben.
3. **Vaadin-Routen sichtbar machen** (nur mit `identity-vaadin`):
   `vaadin.allowed-packages` um `de.zettsystems.identity` ergänzen.

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

Mailversand: ist `spring.mail.*` konfiguriert, gehen echte Mails raus; sonst
landen die Links im Log (`LoggingIdentityMailSender`).

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
./gradlew build                 # kompiliert, testet (Testcontainers → Docker nötig)
./gradlew publishToMavenLocal   # lokale Iteration mit einer App (mavenLocal() dort eintragen)
./gradlew dependencyUpdates     # Versionen prüfen
```

Release: Version in `gradle.properties` ohne `-SNAPSHOT` setzen, committen,
Tag `v<version>` pushen — die CI publiziert nach GitHub Packages. Danach
wieder auf die nächste `-SNAPSHOT` hochziehen.
