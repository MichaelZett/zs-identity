# zs-identity

A reusable identity building block for Spring Boot applications: user accounts,
self-registration with email verification, sign-in, password reset, roles and
permissions.

| Artifact                          | Contents                                                      |
|-----------------------------------|---------------------------------------------------------------|
| `de.zettsystems:identity-core`    | Domain, services, Spring Security integration, auto-configuration, Flyway migrations. No UI. |
| `de.zettsystems:identity-vaadin`  | Vaadin Flow views: sign-in, registration, verification, forgot/reset password. Optional. |

Stack: Java 25, Spring Boot 4.1, Spring Data JPA, Spring Security, Vaadin 25
(`identity-vaadin` only), PostgreSQL.

## Embedding it

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

`gpr.user`/`gpr.key` (a personal access token with `read:packages`) belong in
`~/.gradle/gradle.properties`, never in the project.

After that the application has to do exactly three things; the
auto-configuration takes care of everything else:

1. **Declare roles**: a `RoleCatalog` bean holding the domain roles. Without it
   there are only `SYSTEM_ADMIN` and `USER`. The `RoleSynchronizer` mirrors the
   catalog into the database at startup.
2. **Configure the security filter chain.** For Vaadin:
   ```java
   http.with(VaadinSecurityConfigurer.vaadin(), c -> c.loginView(LoginView.class));
   ```
   and open the public paths (`IdentityPaths.*` from the core, equivalently
   `IdentityRoutes.*` in Vaadin applications) with `permitAll()`.
3. **Make the Vaadin routes visible** (only with `identity-vaadin`): add
   `de.zettsystems.identity` to `vaadin.allowed-packages`.
4. **Forced password change** (since 0.3.0, only with `identity-vaadin`):
   `UserAccountService#requirePasswordChange(userId)`, after creating an
   account with an initial password for example. The building block then sends
   the account to `IdentityRoutes.CHANGE_PASSWORD` (`password/change`) on every
   navigation until a new password is set; the view is `@PermitAll`, so the
   application's filter chain does not have to open it for signed-in users
   separately.
5. **Token cleanup run** (since 0.5.0): `TokenCleanupScheduler` runs daily at
   03:15 as soon as the application sets `@EnableScheduling`; it can be turned
   off with `zs.identity.token-cleanup.enabled=false`. Member lists load their
   accounts in one query with `UserAccountService#findAllById(ids)`.
6. **Deleting an account** (since 0.4.0): `UserAccountService#deleteAccount(userId)`
   removes the account, its role assignments and its tokens for good. Clear up
   the application's own data for that id beforehand -- the building block
   knows nothing about it.
7. **Invitations** (since 0.6.0): `InvitationService` guides a person to their
   account through a link in a mail instead of sending out an initial password.
   `inviteToClaim(userId, email)` for an existing managed account, where the
   `userId` and therefore everything the application attached to it stays
   stable; `inviteNewAccount(email, name)` creates one. With
   `self-registration-enabled=false` this is the only way in. The view lives in
   `identity-vaadin` under `IdentityRoutes.CLAIM_ACCOUNT`; who may invite is
   decided by the application, and the building block does not check it.
   Anyone bringing their **own `IdentityMailSender`** implements
   `sendInvitation(..)` for this; until then delivery fails with a clear
   message rather than silently doing nothing.

## Configuration (`zs.identity.*`)

| Key                           | Default                  | Meaning |
|-------------------------------|--------------------------|---------|
| `self-registration-enabled`   | `true`                   | self-registration allowed |
| `email-verification-required` | `true`                   | account usable only once its address is confirmed |
| `token-validity`              | `24h`                    | validity of verification and reset links |
| `invitation-validity`         | `7d`                     | validity of invitation links |
| `password-min-length`         | `12`                     | minimum length (at least 8) |
| `from-address` / `from-name`  | `noreply@localhost` / `Application` | sender of the mails |
| `base-url`                    | `http://localhost:8080`  | base of the links in the mails |
| `default-role-code`           | `USER`                   | role of new accounts |
| `name-mode`                   | `FULL_NAME`              | `FULL_NAME` (first and last name) or `DISPLAY_NAME` (freely chosen name) |
| `locale`                      | `de`                     | fallback language for mails and views (see Languages) |
| `ui.max-width`                | `28rem`                  | width beyond which the form column stops growing |
| `ui.class-names`              | --                       | additional CSS classes on every view (the hook for your own theme) |
| `ui.notification-duration`    | `5s`                     | how long notifications stay; `0` = until dismissed |

**Mail delivery is optional** (since 0.7.0). `spring-boot-starter-mail` only
hangs `compileOnly` off the building block -- whoever wants to send mails takes
it themselves:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-mail'
```

The application starts without it all the same: the building block falls back
to delivery through the log (`LoggingIdentityMailSender`), and an application
with a delivery path of its own (a transactional mail service) simply provides
its own `IdentityMailSender` bean. If the starter is present and
`spring.mail.*` is configured, real mails go out. Every mail goes out as
`multipart/alternative` -- text, and next to it a plain HTML part in which the
link is a real `<a href>`. The reason is Outlook: in plain-text mails it wraps
long lines and turns a link longer than 76 characters into one that can no
longer be clicked.

## Languages

All texts -- views, mails and the messages behind `IdentityMessageKeys` -- come
from shipped message bundles: **German** and **English**, under
`de/zettsystems/identity/messages/` (`core*` in `identity-core`, `ui*` in
`identity-vaadin`). The file without a language suffix is the fallback and is
English, so a language without a bundle of its own gets English, never the
language setting of the server.

Which language a view speaks:

* If the application brings a language selection (Vaadin's `I18NProvider`), the
  views follow the language of the `UI`, that is the browser or the switch
  inside the application.
* Without an `I18NProvider`, `zs.identity.locale` applies. The reason: Vaadin
  then sets the UI language to the default of the server JVM, and that says
  nothing about the application.

**The language of an account** has been stored on the account itself since
0.7.0 (`auth_user.locale`, migration V1_4). It is needed because a mail is
created without a browser, and there is nobody there to ask about a language
setting. It decides the language of **every mail** this building block sends;
if the account has chosen none, `zs.identity.locale` still applies.

It fills itself in: the `RegistrationView` passes on the language someone
registered in, the `ClaimAccountView` the one of the redemption view. An
application that registers or invites on its own passes it through as well:

```java
registrationService.register(email, password, name, UI.getCurrent().getLocale());
invitationService.inviteNewAccount(email, name, Locale.GERMAN);
```

Changing it later, in the account settings of the application for example:

```java
userAccountService.changeLocale(userId, Locale.ENGLISH);  // null withdraws the choice
```

It is read through `UserAccountDto.locale()` (`null` = no choice) or more
conveniently through `localeOr(fallback)`. The building block brings no
**view** for it: where the language selection belongs -- account settings,
header, sign-in form -- is known only to the application.

Custom texts: a bean of your own displaces the default `IdentityMessages`.
Whoever wants to replace individual keys only answers those and passes the rest
on to `IdentityMessages.resourceBundles()`.

```java
@Bean
IdentityMessages identityMessages() {
    IdentityMessages fallback = IdentityMessages.resourceBundles();
    return (key, locale, args) -> switch (key) {
        case IdentityMessageKeys.SELF_REGISTRATION_DISABLED -> "Please contact the tournament office.";
        default -> fallback.get(key, locale, args);
    };
}
```

## Roles, global and per tenant

Roles come from the application's `RoleCatalog` and are granted to an account,
either **globally** or for a **scope** (since 0.7.0):

```java
userAccountService.grantRole(userId, "USER");                            // applies everywhere
userAccountService.grantRole(userId, "GROUP_ADMIN", Scope.of("club", "17"));
```

A `Scope` consists of a kind and an identifier (`club:17`). **The building
block never interprets it** -- what a `club` is, only the application knows; it
is merely stored and compared. That upholds the rule "no knowledge about
embedding applications" and therefore works for clubs, tournaments, games or
whatever else a tenant is supposed to be.

Questions to ask an account (on `UserAccountDto`, or directly on
`UserAccountService`):

| Question | Call |
|---|---|
| Which roles apply everywhere? | `roleCodes()` |
| May they do this here? | `hasRole("GROUP_ADMIN", scope)` -- global ones count |
| What applies in this scope? | `rolesIn(scope)` / `userAccountService.rolesOf(userId, scope)` |
| All clubs of this person? | `scopesOf("club")` / `userAccountService.scopesOf(userId, "club")` |

**In the authentication**, scoped roles are present in qualified form
(`ROLE_GROUP_ADMIN@club:17`), global ones as before (`ROLE_USER`). On top of
that there is an **active scope**: its roles additionally apply unqualified, so
that the familiar checks stay readable.

```java
activeScopeService.switchTo(Scope.of("club", "17"));   // when entering a club, say

@RolesAllowed("GROUP_ADMIN")                           // now means: in club 17
@PreAuthorize("hasAuthority('ROLE_GROUP_ADMIN@club:4')")  // deliberately elsewhere, e.g. a deep link
```

At sign-in time **no** scope is active -- which one it should be is known only
to the application (address bar, last selection, start page). "Automatically
take the only one" would be convenient and would silently behave differently
once a second membership appears. Without an active scope the global roles
apply, which is the safe default.

Applications without tenants notice none of this: granted without a `Scope`,
every role is global, and `roleCodes()`, `hasRole(code)` and `@RolesAllowed`
behave exactly as before 0.7.0.

## The shipped views

Sign-in, registration, password reset and the redemption view are often the
**first** pages a new member sees -- yet they belong to the building block and
cannot know the theme of the application. They therefore keep to a lower bound
that works everywhere (`IdentityFormView`):

* **One column, centred, with a maximum width** (`zs.identity.ui.max-width`).
  On a phone it fills the width; on a desktop it does not grow into
  unreadability.
* **No horizontal scrolling at 375 px.** `border-box`, no fixed pixel widths,
  every field and every button across the full column width. A test pins this
  down for all views (`IdentityViewLayoutTest`).
* **No colour set by hand** -- emphasis only through Vaadin's variants, so that
  nothing clashes with an application's dark appearance.

**Styling along** works through fixed CSS classes rather than through settings:
every view carries `identity-view` and an identifier of its own
(`identity-view--login`, `--registration`, `--forgot-password`,
`--resend-verification`, `--reset-password`, `--change-password`,
`--claim-account`, `--confirm-email`); sign-in additionally has
`identity-view__column` inside. Classes of your own reach every view through
`zs.identity.ui.class-names`:

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

Whoever needs more than that writes a view of their own -- but then under a
path of its own and with the package scan for `de.zettsystems.identity.ui`
switched off: Vaadin rejects two `@Route` annotations on the same path.

## Java modules (JPMS)

Both artifacts carry an **`Automatic-Module-Name`** in their manifest:

| Artifact | Module name |
|---|---|
| `identity-core` | `de.zettsystems.identity` |
| `identity-vaadin` | `de.zettsystems.identity.ui` |

This gives an application that uses JPMS itself a stable name for `requires`;
without the entry, Java derives one from the file name, and that changes with
every version.

The building block does **not** ship a real `module-info.java`, and that is
deliberate: Spring, Hibernate and Vaadin run on the classpath, not on the
module path, so a module descriptor would have no effect there. It would also
force `opens` for the reflection of Spring and Hibernate, and the message
bundles of both artifacts live in the same resource package
(`de/zettsystems/identity/messages/`), which JPMS rejects as a split package.
Once the Spring ecosystem arrives on the module path, that is the moment to
catch up; before then it only costs.

## Database

`identity-core` keeps its tables in a schema of their own, **`identity`**, with
a Flyway history of their own (`identity.flyway_schema_history`). The scripts
live under `classpath:db/identity` and run through `IdentityMigrations` --
**before** the application's Flyway, against the same data source
(`IdentityFlywayAutoConfiguration` hooks into the application's Flyway setup
through a `FlywayConfigurationCustomizer`). The application does nothing for
that; in particular it needs no `spring.flyway.out-of-order` and no entry in
`spring.flyway.locations`. The two histories are independent: the application
may number and baseline its own migrations as it likes.

Because the identity schema is always migrated first, an application migration
may reference **`identity.auth_user`** -- for a foreign key, or to copy
accounts from a table of its own (`IdentitySchema.USER_TABLE` holds the
qualified name). PostgreSQL only, like the migrations; the database user must
be allowed to create a schema (the owner of the database is). One name to
avoid: a database **user called `identity`**. PostgreSQL's default search path
is `"$user", public`, so once the schema exists, that user's unqualified
tables -- the application's own -- would silently land in it.

An application that runs Flyway by hand sets
`zs.identity.migrations.enabled: false` and calls
`IdentityMigrations#migrate(dataSource)` at a point of its own choosing;
without the migrations Hibernate's schema validation fails at startup.

**Coming from a version before 0.8.0** (tables in the application's schema,
versions 1.1 to 1.5 in its history): the first start moves everything once --
tables and sequences into `identity`, our rows out of the application's
history, a baseline for our history at the version the tables have. Data,
indexes and foreign keys from application tables stay intact; the move is one
transaction, so a failure leaves the old layout untouched. Take a backup
before that first start anyway.

Tables: `auth_user`, `auth_role`, `auth_role_authority`, `auth_user_role` (with
`scope_type`/`scope_id`, empty = global), `auth_token`.

The comments inside the migration scripts are German. They are the one place
that was left untranslated on purpose: Flyway checksums the whole file, so
editing a migration that has already been applied would make every existing
installation fail validation.

## The name model

`AccountName` always carries a `displayName`; `firstName`/`lastName` are set
only with `name-mode = FULL_NAME`. `UserAccountDto.firstName()`/`lastName()`
then return empty strings instead of `null`. Applications link their own
objects exclusively through the account id (`UserAccountDto.id()`,
`IdentityUserDetails.userId()` in the `SecurityContext`).

## Development

```
./gradlew build                 # compiles, tests, SpotBugs, JaCoCo (Testcontainers -> Docker required)
./gradlew sonar                 # SonarQube against the local instance (SONAR_TOKEN)
./gradlew publishToMavenLocal   # local iteration with an application (add mavenLocal() there)
./gradlew dependencyUpdates     # check versions (-Punstable / -Pmajor shows RC/major)
```

Releasing: name the section in `CHANGELOG.md`, set the version in
`gradle.properties` without `-SNAPSHOT` and push to `main`. CI publishes to
GitHub Packages, creates the tag and the GitHub release `v<version>`, and then
raises the version to the next patch `-SNAPSHOT` itself. SNAPSHOTs are not
published; applications depend on release versions only (local iteration goes
through `publishToMavenLocal`). For a minor or major jump, set the version by
hand before the release.

## Contributing, reporting bugs, licence

Questions and bug reports belong in the
[issues](https://github.com/MichaelZett/zs-identity/issues); there is a
template each for bug reports and for feature requests. What applies when
contributing -- how to build, the non-negotiable rules of the building block,
the version space of the migrations -- is in
[CONTRIBUTING.md](CONTRIBUTING.md).

**Never report a security flaw as a public issue.** The route for that is in
[SECURITY.md](SECURITY.md): a private report through the security tab of the
repository.

Licence: [Apache-2.0](LICENSE). The building block may therefore be used in
commercial applications as well; the patent clause is explicitly included.
