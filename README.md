# zs-identity

[![Build](https://github.com/MichaelZett/zs-identity/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/MichaelZett/zs-identity/actions/workflows/build.yml?query=branch%3Amain)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=MichaelZett_zs-identity&metric=alert_status)](https://sonarcloud.io/project/overview?id=MichaelZett_zs-identity)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=MichaelZett_zs-identity&metric=coverage)](https://sonarcloud.io/component_measures?id=MichaelZett_zs-identity&metric=coverage)
[![Maven Central](https://img.shields.io/maven-central/v/de.zettsystems/identity-core)](https://central.sonatype.com/namespace/de.zettsystems)

A reusable identity building block for Spring Boot applications: user accounts,
self-registration with email verification, sign-in with password or passkey,
password reset, and roles that can be global or scoped to a tenant.

| Artifact                          | Contents                                                      |
|-----------------------------------|---------------------------------------------------------------|
| `de.zettsystems:identity-core`    | Domain, services, Spring Security integration, auto-configuration, Flyway migrations. No UI. |
| `de.zettsystems:identity-vaadin`  | Vaadin Flow views: sign-in (password or passkey), registration, verification, forgot/reset password, passkeys. Optional. |

Stack: Java 25, Spring Boot 4.1, Spring Data JPA, Spring Security, Vaadin 25
(`identity-vaadin` only), PostgreSQL.

## Embedding it

The artefacts are on Maven Central, so no extra repository and no credentials
are needed:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'de.zettsystems:identity-core:1.0.0'
    implementation 'de.zettsystems:identity-vaadin:1.0.0'   // optional
}
```

Versions before 1.0.0 were published to GitHub Packages only.

After that the application has to do three things (1 to 3); the rest of the
list is optional, and the auto-configuration takes care of everything else:

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
   `resendInvitation(userId)` sends a fresh link as long as the invitation is
   open; `UserAccountDto.claimed()` (since 0.10.0) tells beforehand whether it still is, so
   an "invite again" button can be disabled instead of failing.
   Anyone bringing their **own `IdentityMailSender`** implements
   `sendInvitation(..)` for this; until then delivery fails with a clear
   message rather than silently doing nothing. An own `IdentityMailTransport`
   (see "Mail delivery") gets invitations for free.
8. **Passkeys** (since 0.11.0): sign-in with Face ID, Touch ID or the device
   lock, as an option per person next to the password. Three things on the
   application's side, one of them per environment:
   ```groovy
   implementation 'org.springframework.security:spring-security-webauthn'
   ```
   ```java
   http.with(IdentityPasskeyConfigurer.passkeys(), Customizer.withDefaults());
   ```
   ```yaml
   zs:
     identity:
       passkeys:
         enabled: true                       # false by default; the configurer then does nothing
         rp-id: orgaapp.example.com          # the domain the passkeys are bound to, no scheme, no port
         rp-name: OrgaApp                    # what the authenticator shows when a passkey is created
         allowed-origins: [ https://orgaapp.example.com ]   # locally http://localhost:8090
   ```
   The configurer opens Spring Security's WebAuthn endpoints
   (`IdentityPaths.PASSKEY_*`): signing in is open to everyone, registering
   needs a *fresh* sign-in with the password (`fullyAuthenticated()`; a
   remember-me session is sent to sign in first). It also gives the sign-in
   filter the application's `RememberMeServices`, so a passkey sign-in sets
   the remember-me cookie like the form login, and the shared
   `AuthenticationManager`, so the last sign-in is recorded. The access
   rules take effect where the call sits: put it before an `anyRequest()`
   of your own; next to a Vaadin configurer the order does not matter, since
   that one adds its `anyRequest()` last by itself. After a passkey sign-in the session
   holds a `PasskeyAuthentication` whose principal is the same
   `IdentityUserDetails` as after a password sign-in.
   With `identity-vaadin`, the sign-in page offers the passkey in its
   username field as soon as the browser has one for the domain (WebAuthn
   conditional mediation, since 0.13.0 -- nothing to configure, and a browser
   that cannot do it simply shows nothing), keeps the button "Sign in with
   passkey" for everyone the offer does not reach (`passkeys.login-button`
   takes it away), and the view
   `IdentityRoutes.PASSKEYS` (`passkeys`) lets a
   signed-in account add, list and remove its passkeys -- link it from the
   application's settings; it is the one view that lives inside the
   application's `@Layout`. `PasskeyService#countFor(userId)` /
   `accountsWithPasskeys(ids)` tell a settings page or a member list who has
   one. A passkey is bound to its domain: switch passkeys on only once the
   domain is final, and check registration and sign-in on a real phone under
   HTTPS (`localhost` counts as secure for WebAuthn, but Face ID needs the
   device).
9. **Protection against password guessing** (since 1.1.0): on without a line
   of configuration. After three wrong passwords in a row the account is
   locked for 15 minutes, every further lock twice as long up to 24 hours;
   and from the first failure every sign-in waits before its password is
   checked (1 s, 2 s, 4 s, up to 8 s), counted per sign-in name and per
   client address, so that trying one password on many addresses is slowed
   down too. The delay is kept in memory, bounded, and at most
   `max-delayed-requests` sign-ins wait at a time; the next one is turned
   down unchecked rather than parked on a thread.
   - **No account enumeration.** Unknown address, locked account and wrong
     password end in the same failure with the same message after the same
     time -- a locked account even with the *right* password, so that the
     answer never says a guess was correct.
   - **Never permanent.** A lock runs out by itself, and **"Forgot password"
     lifts it**: the link in the mail proves ownership. So does any new
     password, `UserAccountService#unlock(userId)` for an administrator, and
     a successful sign-in with a **passkey**. Failed passkey sign-ins are not
     counted -- there is nothing to guess -- and a locked account may still
     sign in with one: the lock is there to stop guessing at the password,
     not to keep out someone who proves possession more strongly than the
     reset mail does. `UserAccountDto.lockedUntil()` / `lockedAt(now)` show
     the lock; it is separate from `setEnabled(false)`.
   - **The owner is told** by mail (`notify-by-mail`), since the sign-in page
     says the same for every failure. An application with its own
     `IdentityMailSender` implements `sendAccountTemporarilyLocked(..)` for
     it; until then no notice goes out, and the lock works all the same.
     Each lock is also published as `AccountTemporarilyLocked` and logged as
     a WARN line with account id and client address.
   - **How it gets in.** The building block declares an
     `AuthenticationProvider` bean, which Spring Security puts into its
     global `AuthenticationManager` in place of the `DaoAuthenticationProvider`
     it would otherwise build from the `UserDetailsService`; every form login
     uses it. It steps back when the application declares an
     `AuthenticationProvider` of its own.
   - **Behind a reverse proxy** the client address is the proxy's unless
     Spring reads the forwarded headers
     (`server.forward-headers-strategy: native` or `framework`). Without that
     every client shares one address and one delay. A rate limit on the
     sign-in path at the proxy remains a good idea on top.

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
| `migrations.enabled`          | `true`                   | the building block migrates its schema `identity` itself, before the application's Flyway; `false` = the application calls `IdentityMigrations#migrate` (see Database) |
| `passkeys.enabled`            | `false`                  | sign-in with passkeys; needs `spring-security-webauthn` and `IdentityPasskeyConfigurer` in the filter chain (see step 8) |
| `passkeys.rp-id`              | `localhost`              | the domain the passkeys are bound to, without scheme or port |
| `passkeys.rp-name`            | `Application`            | the name the authenticator shows when a passkey is created |
| `passkeys.allowed-origins`    | `http://localhost:8080`  | origins the browser may sign in from, with scheme and port; each must belong to `rp-id` or a subdomain of it |
| `passkeys.login-button`       | `true`                   | show the button "Sign in with passkey"; switch off where the offer in the username field is enough -- but it is the only way in for a browser without conditional mediation |
| `login-protection.enabled`    | `true`                   | temporary lock and delay against password guessing (see step 9) |
| `login-protection.max-attempts` | `3`                    | wrong passwords in a row that lock the account |
| `login-protection.lock-duration` | `15m`                 | length of the first lock; each further one lasts twice as long |
| `login-protection.max-lock-duration` | `24h`             | longest lock; failures older than this are forgotten |
| `login-protection.delay`      | `1s`                     | wait before the password is checked after the first failure, doubling with each further one; `0` switches the delay off |
| `login-protection.max-delay`  | `8s`                     | longest wait |
| `login-protection.max-delayed-requests` | `50`           | sign-ins that may wait at the same time; beyond that they are turned down unchecked |
| `login-protection.notify-by-mail` | `true`               | mail the account when it is locked |

**Mail delivery is optional** (since 0.7.0). `spring-boot-starter-mail` only
hangs `compileOnly` off the building block -- whoever wants to send mails takes
it themselves:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-mail'
```

The application starts without it all the same: the building block falls back
to delivery through the log. If the starter is present and `spring.mail.*` is
configured, real mails go out with the sender from `from-address`/`from-name`.
Every mail goes out as `multipart/alternative` -- text, and next to it a plain
HTML part in which the link is a real `<a href>`. The reason is Outlook: in
plain-text mails it wraps long lines and turns a link longer than 76 characters
into one that can no longer be clicked.

**Two seams around mail** (the finer one since 0.9.0):

- **`IdentityMailTransport`** -- *delivery only*. The building block renders
  subject, text and HTML part (language of the account, link, validity, HTML
  escaping) into an `IdentityMail` and hands it to the transport together with
  the `UserAccountDto`. An application whose mail account depends on the
  recipient -- one SMTP account per club, another per tournament, each with a
  sender address its relay accepts -- implements only this and never touches
  the texts. `IdentityMail.type()` (`EMAIL_VERIFICATION`, `PASSWORD_RESET`,
  `INVITATION`) is there for routing; what the transport needs to know about
  the account it reads from its own tables by `user.id()`. A transport should
  log a failed delivery rather than throw; what does escape is caught and
  logged by the sender, so a registration never rolls back over a mail.
- **`IdentityMailSender`** -- *texts and delivery*. For an application that
  wants mails of its own design; `sendInvitation(..)` has to be implemented
  along with the other two.

Either bean displaces the default; the defaults themselves are
`JavaMailFactory.transport(..)` and `IdentityMailFactory.logOnlyTransport()`.

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

## Events, and "keep me signed in"

Four things that happen to an account are published as Spring application
events (since 0.12.0, all in `de.zettsystems.identity.values`, all
implementing the sealed `IdentityAccountEvent`):

| Event             | Published when | Carries |
|-------------------|----------------|---------|
| `PasswordChanged` | `changePassword`, `resetPassword`, and the first password of a redeemed invitation | `userId`, `email` |
| `EmailChanged`    | an address is put on an account (`inviteToClaim`) | `userId`, `previousEmail`, `email` |
| `AccountLocked`   | `setEnabled(id, false)` | `userId`, `email` |
| `AccountDeleted`  | `deleteAccount` | `userId`, `email` |

`AccountTemporarilyLocked` (since 1.1.0; `userId`, `email`, `lockedUntil`,
`clientAddress`) is published too, when too many wrong passwords lock an
account for a while, but it is deliberately not one of these shapes and
touches no remember-me token: anybody who knows an address can cause it, and
it must not sign the owner out everywhere.

They are published **inside** the transaction that makes the change. Listen
with `@TransactionalEventListener` if you must not act on a change that is
rolled back afterwards, and with `@EventListener` if you only want to be told:

```java
@TransactionalEventListener
void forget(AccountDeleted deleted) {
    pushSubscriptions.deleteByUserId(deleted.userId());
}
```

**The building block already uses them for remember-me.** An application that
keeps its people signed in with Spring Security's
`PersistentTokenRepository` (`persistent_logins`, keyed by the sign-in name)
needs no line for this: whoever changes or resets their password -- typically
because a phone is gone -- has the tokens of *all* their devices discarded,
and so does an account that is locked, deleted or given a different address.
Without that the cookie on the lost phone would keep working for its full
lifetime; it never asks for the password that has just been replaced.

The session of whoever makes the change stays: it hangs off the HTTP session,
not off the cookie. It is the *other* devices that are meant to be out.

An application that brings no `PersistentTokenRepository` bean notices
nothing, and one that would rather act on the events itself switches the
cleanup off:

```yaml
zs:
  identity:
    remember-me-cleanup:
      enabled: false
```

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
`--claim-account`, `--confirm-email`, `--passkeys`); sign-in additionally has
`identity-view__column` inside plus `identity-view__footer`,
`identity-view__footer-link` for the
quiet line of ways out below the buttons, and the passkey list has
`identity-view__passkey-row`, `__passkey`, `__passkey-label` and
`__passkey-dates` per entry. Classes of your own reach every view through
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

**A head above the form** (since 0.9.1) -- a club's logo, a title -- comes from
the application through an optional bean:

```java
@Bean
IdentityViewHeader clubLogo() {
    return viewName -> new Image("images/club-logo.svg", "TC Beispiel");
}
```

Every shipped view places the created component as its first element above the
form (on the sign-in page inside the centred column) and gives it the full
column width plus the class `identity-view__header`; nothing else, so the
appearance stays yours. `viewName` is the identifier from the CSS class, so the
head may differ per view. The component must be a fresh one per call.

One consequence to know about: since 0.7.2 the sign-in page sets
`--vaadin-login-form-padding: 0` and `--vaadin-login-form-width: 100%` on the
login form, so that its fields line up with the buttons below. An application
that draws a card around the form through `vaadin-login-form::part(form)`
therefore has to add the inner padding itself.

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

Tables: `auth_user` (since V1_6 with `passkey_user_handle`, the opaque id
WebAuthn knows an account by), `auth_role`, `auth_role_authority`,
`auth_user_role` (with `scope_type`/`scope_id`, empty = global),
`auth_token`, `auth_passkey` (since V1_6; one row per registered passkey,
hanging off `auth_user` with `ON DELETE CASCADE`).

A fresh database takes the baseline `B1_6__identity_schema.sql`: the whole
schema in one script. An existing installation never sees it and applies only
the `V` scripts above its version, which is why they stay in the artefact.
Their comments are German -- the one place left untranslated on purpose,
because Flyway checksums the whole file and editing an applied migration would
make every existing installation fail validation.

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
`gradle.properties` without `-SNAPSHOT` and push to `main`. CI uploads to Maven
Central (the deployment is released by hand in the Central Portal, because a
version there can never be replaced) and to GitHub Packages, creates the tag
and the GitHub release `v<version>`, and then raises the version to the next
patch `-SNAPSHOT` itself. SNAPSHOTs are not
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
