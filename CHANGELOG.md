# Changelog

Notable changes to zs-identity. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows
[SemVer](https://semver.org/). On release, `## Unreleased` is renamed to
`## <version> - <date>`.

## 0.11.0 - 2026-09-21

### Added
- **Sign-in with passkeys** (WebAuthn: Face ID, Touch ID, the device lock),
  as an option per person next to the password, never instead of it. Asked
  for by `terminplanung-halle`, whose members use the application as an
  installed PWA on the phone. Off by default: a passkey is bound to the
  domain it was created on, so an application switches it on per environment
  under `zs.identity.passkeys.*` (`enabled`, `rp-id`, `rp-name`,
  `allowed-origins`) once its domain is settled.
  - **`IdentityPasskeyConfigurer.passkeys()`** for the security filter chain:
    `http.with(IdentityPasskeyConfigurer.passkeys(), Customizer.withDefaults())`.
    Opens Spring Security's WebAuthn endpoints -- signing in to everyone,
    registering only to a *fresh* sign-in (`fullyAuthenticated()`, which a
    remember-me session does not satisfy) -- and wires the sign-in filter
    with what Spring's own `http.webAuthn(..)` leaves out: the application's
    `RememberMeServices` (a passkey sign-in sets the remember-me cookie like
    the form login), the session strategy, the shared `AuthenticationManager`
    (so the `LoginRecorder` sees the sign-in), and JSON answers for a page
    that calls the endpoints through `fetch`. Does nothing while
    `zs.identity.passkeys.enabled` is `false`. The access rules take effect
    where the `with(..)` call sits: before an `anyRequest()` of the
    application's own, anywhere in relation to a Vaadin configurer.
  - **`PasskeyAuthentication`**: the session token after a passkey sign-in,
    with the account's `UserDetails` as principal -- the same
    `IdentityUserDetails` a password sign-in leaves, so that guards, views
    and applications see no difference. Spring's own token would carry the
    WebAuthn user entity instead. A disabled account fails with
    `DisabledException`, and the sign-in page says so.
  - **Persistence in the `identity` schema** (V1_6): table `auth_passkey`,
    hanging off `auth_user` with `ON DELETE CASCADE`, so `deleteAccount(..)`
    takes the passkeys along; column `auth_user.passkey_user_handle` for the
    opaque id WebAuthn knows the account by. The building block's own JPA
    stores stand in for Spring's `JdbcUserCredentialRepository` and
    `JdbcPublicKeyCredentialUserEntityRepository`; both are beans under
    `@ConditionalOnMissingBean`.
  - **`PasskeyService`** (`findAllOf`, `countFor`, `hasPasskey`,
    `accountsWithPasskeys`, `delete`) with **`PasskeyDto`** (`id`, `label`,
    `createdAt`, `lastUsedAt`), for settings pages and member lists. Deleting
    checks the owner.
  - `identity-vaadin`: a button **"Sign in with passkey"** on the sign-in
    page (`login-passkey-button`), shown only when passkeys are on, and the
    view **`PasskeyView`** under `IdentityRoutes.PASSKEYS` (`passkeys`) to
    add, list and remove passkeys -- for signed-in accounts, linked from an
    application's settings. The one view of the building block that lives
    inside the application's `@Layout`. A remember-me session sees the list
    but is sent to sign in with the password before it may add one. Element
    ids for end-to-end tests: `passkeys-register-button`,
    `passkeys-sign-in-again-button`, `passkeys-delete-button-<id>`,
    `passkeys-list`, `passkeys-empty`. The browser side runs through two
    small scripts via `executeJs`, no frontend build involved.
  - `IdentityPaths.PASSKEYS` and the four endpoint constants
    (`PASSKEY_AUTHENTICATION_OPTIONS`, `PASSKEY_LOGIN`,
    `PASSKEY_REGISTRATION_OPTIONS`, `PASSKEY_REGISTRATION`);
    `IdentityMessageKeys.PASSKEY_NOT_FOUND`; texts in both languages.
- `spring-security-webauthn` hangs `compileOnly` off `identity-core`, like
  the mail library: an application that offers passkeys takes it itself,
  every other one stays without webauthn4j. `IdentityBeansPasskeyTest` holds
  that with a `FilteredClassLoader`.

### Changed
- `IdentityProperties` gains a last component `passkeys`. The shape up to
  0.10.x stays as a constructor, as does the shape before 0.8.0;
  `withPasskeys(..)` next to the other `with` methods.

## 0.10.0 - 2026-09-18

### Added
- **`UserAccountDto.claimed()`**: whether the account belongs to a person who
  can sign in with it -- registered, or an invitation redeemed. `false` for
  managed accounts and for invitations still open, so an application can
  offer "invite again" only where `resendInvitation(..)` will not refuse
  with `ACCOUNT_ALREADY_CLAIMED`. Deliberately not "has a password": with
  sign-in through external providers (issue #1) there will be claimed
  accounts without one. Asked for by `terminplanung-halle`.

  The record gains a last component. The shape up to 0.9.x (nine
  components, ending in `locale`) stays as a constructor and derives
  `claimed` from the address, so records built by hand keep compiling.

## 0.9.1 - 2026-09-18

### Added
- **A head above the forms: `IdentityViewHeader`** (`identity-vaadin`). An
  application provides a bean with `Component create(String viewName)` and
  every shipped view places the component as its first element above the
  form -- a club's logo, say; on the sign-in page inside the centred column,
  so that it shares the column's width with the form. The building block
  gives it the full column width and the class `identity-view__header`, and
  nothing else: no colours, no sizes, no markup of its own. Without the bean
  nothing changes. `viewName` is the identifier already used in the CSS
  class (`login`, `claim-account`, ...), so the head may differ per view. A
  bean rather than a `logo-url` property, because a logo per tenant stays
  possible and the building block need not know how images are served.
  Asked for by `tennistournament`, whose old sign-in page showed the club's
  logo.

## 0.9.0 - 2026-09-18

### Added
- **A seam for delivery alone: `IdentityMailTransport`.** Until now an
  application that wanted to send the building block's mails through a mail
  account of its own choosing had to replace the whole `IdentityMailSender`
  -- and with it rebuild every text. Now the building block renders subject,
  text and HTML part itself (language of the account, link, validity, HTML
  escaping) into the new record **`IdentityMail`** (`type`, `to`, `subject`,
  `text`, `html`) and hands it to the transport together with the
  `UserAccountDto`. An application implements `send(mail, user)`, reads what
  it needs about the account from its own tables and picks server and sender
  address -- one SMTP account per club, another per tournament, with a
  sender the relay accepts. **`IdentityMailType`** (`EMAIL_VERIFICATION`,
  `PASSWORD_RESET`, `INVITATION`) is part of the record so that a transport
  can route by it. Found while embedding into `tennistournament`.

  The defaults are unchanged in behaviour: `JavaMailSender` with the sender
  from `zs.identity.from-address`/`from-name` (`JavaMailFactory.transport`),
  or the log (`IdentityMailFactory.logOnlyTransport`). A failure in a
  transport is caught by the sender and logged, so a registration never rolls
  back over a mail. An application's own `IdentityMailSender` bean keeps
  working as before; the log fallback is not created next to it.

### Changed
- `IdentityMailFactory.logOnly()` now takes `(IdentityProperties,
  IdentityMessages)`: the log fallback renders the real texts too, so the
  link in the log is the one the mail would carry. `JavaMailFactory.javaMail(..)`
  is unchanged.

## 0.8.0 - 2026-09-18

> 0.8.0: the building block's tables move to a schema of their own. One
> restart does the move; what an application has to change is listed below.

### Changed
- **Own database schema `identity`, own Flyway history.** Until now the
  building block's migrations were appended to the application's Flyway run
  and shared its history under a reserved version space (V1_x here, V2_x
  upwards in the application). That broke the moment an application drew a
  baseline over its own history: with a `B20` in the application, V1_1 to V1_5
  are `BELOW_BASELINE` and never run -- on a fresh database as on an existing
  one, `out-of-order` or not (found while embedding into `tennistournament`,
  spike on 2026-09-18). Two independently growing products cannot share one
  linear version line.

  Now `IdentityMigrations` runs the scripts under `classpath:db/identity`
  itself: schema `identity`, history `identity.flyway_schema_history`,
  **before** the application's Flyway and against the same data source. The
  hook is still a `FlywayConfigurationCustomizer`, so the application does
  nothing for it. Entities are mapped with `schema = IdentitySchema.NAME`.

  **What changes for an application:**
  - `spring.flyway.out-of-order: true` is no longer needed; remove it.
  - The database user must be allowed to create a schema (the owner of the
    database is).
  - Own migrations may reference `identity.auth_user` -- for a foreign key, or
    to copy existing accounts over -- because the identity schema is always
    migrated first.
  - An application that runs Flyway by hand sets
    `zs.identity.migrations.enabled: false` and calls
    `IdentityMigrations#migrate(dataSource)` itself; the bean is
    `@ConditionalOnMissingBean` and can be replaced.
  - An application that still lists `classpath:db/identity` in
    `spring.flyway.locations` may leave it: the customizer strips it.

  **Existing installations** (layout before 0.8.0: tables in the
  application's schema, versions 1.1 to 1.5 in its history) are moved
  **once, at the first start**: `ALTER TABLE ... SET SCHEMA identity` for the
  five tables and four sequences (data, indexes and foreign keys from
  application tables stay intact), our rows leave the application's history
  (its validation would otherwise stop on "applied migration not resolved
  locally"), and our own history begins with a baseline at the version the
  tables actually have -- read off the columns, so an installation that
  stopped at 1.3 gets 1.4 and 1.5 right after the move. The move runs in one
  transaction; if it fails, nothing has changed and the application does not
  start. Take a backup before the first start with 0.8.0 all the same.
- **`IdentityProperties` has a new component `migrations`**
  (`MigrationSettings`, `zs.identity.migrations.enabled`, default `true`). The
  twelve-argument constructor stays as an overload, `withMigrations(..)` comes
  alongside `withLocale(..)`/`withUi(..)`.

### Added
- **`IdentitySchema`** with the schema name (`NAME`) and the qualified user
  table (`USER_TABLE`), for SQL and mappings in applications.
- **Two rehearsal tests for the move**, skipped unless `PROBE_JDBC_URL` names
  a copy of a real database: `LegacyLayoutRehearsalIT` (the migrator alone,
  then the application's Flyway validates the cleaned history) and
  `LegacyLayoutFirstStartIT` (the whole Spring Boot start, twice). Run them
  against a copy before the first start with 0.8.0; both passed against a
  production dump of `terminplanung-halle`.

## 0.7.2 - 2026-09-18

### Fixed
- **Sign-in page: the fields were narrower than the buttons below them.**
  Found at phone width (430 px) in `terminplanung-halle` after the move to
  0.7.1: three edges, two widths. `LoginForm` has no `HasSize`, so the
  full-width rule never reached it, and inside its shadow DOM Vaadin's
  `vaadin-login-form-wrapper` keeps a width of its own (`360px`) plus a
  padding all around. `LoginView` now sets the element's width and the
  component's public custom properties `--vaadin-login-form-width` and
  `--vaadin-login-form-padding`. Measured afterwards at 430 px and 1280 px:
  fields, submit button and the two navigation buttons share the same two
  edges. No stylesheet of the building block is involved, so an application's
  theme stays untouched.

## 0.7.1 - 2026-09-17

### Fixed
- **Granting the same role a second time created a second assignment** and
  broke at commit time with
  `duplicate key value violates unique constraint "ux_auth_user_role"`.
  Reported from `terminplanung-halle` while moving to 0.7.0 (10 failing
  integration tests). Up to 0.6.0 such a call simply had no effect; only the
  unique index from V1_5 makes it visible.

  The cause was `AbstractAuthEntity.equals`: it compared types through
  `getClass()`. Since V1_5, `RoleAssignment.role` is mapped LAZY, so
  `getRole()` returns a Hibernate proxy, and a proxy delegates `equals` to its
  target -- what got compared was `Role` against `Role$HibernateProxy`. So
  `UserAccount.hasRole` took the very same row for a different one, and the
  supposedly idempotent `grant` added another. It now uses
  `Hibernate.getClass(..)` on both sides: the type check remains -- every
  entity has its own sequence, so ids do collide across types -- it just sees
  through the proxy.

  **The bug only shows** when the same account was loaded earlier in the
  *same* transaction; the assignment and its proxy are then already in the
  session. That is exactly why `ScopedRolesIT` did not reveal it.
  `UserAccountServiceIT.grantingAgainAfterTheAccountWasLoadedChangesNothing`
  now pins that sequence down.

### Added
- **`LICENSE` (Apache-2.0).** Until now the building block was formally "all
  rights reserved" -- nobody was allowed to use it, and Maven Central would not
  have accepted it. Apache-2.0 allows use in commercial applications too and
  contains the patent clause that MIT lacks.
- **`SECURITY.md`** with the reporting route for security flaws: privately
  through the security tab, never as a public issue. For a building block that
  manages sign-in and permissions, an issue publishes the flaw before a fix
  exists.
- **`CONTRIBUTING.md`** -- how to build, and the non-negotiable rules of the
  building block, for contributions from outside.
- **Issue templates** under `.github/ISSUE_TEMPLATE/` for bugs and feature
  requests; blank issues are switched off and the security route is linked.
- **Analysis through SonarQube Cloud** in CI, next to the local instance. The
  build picks between them by the organization (`SONAR_ORGANIZATION`); the step
  stays dormant until `SONAR_TOKEN` exists, so a fork's pull request does not
  fail on a missing secret.

### Changed
- **Comments and Javadoc are now English** throughout both modules, as are
  README, CHANGELOG, the build scripts and the CI workflow. The message bundles
  stay bilingual -- that is their purpose. The one exception is the comments
  inside the Flyway migration scripts: Flyway checksums the whole file, so
  editing an applied migration would make every existing installation fail
  validation.

## 0.7.0 - 2026-09-17

> The language on the account, the UI rules and roles with a scope grew
> together, without a release in between.

### Added
- **Roles with a scope** (migration **V1_5**). A role can now apply to one area
  instead of everywhere: "admin **of club 17**". The background is several
  embedding applications becoming multi-tenant; without the scope each of them
  rebuilds the same mapping table.
  - **`Scope(type, id)`** (`club:17`) as a value. The building block never
    **interprets** it -- it stores, returns and compares. Forbidden are `@`,
    `:` and whitespace: role and scope are combined into an authority name, and
    an identifier containing those characters could invent a permission.
  - **`grantRole(userId, code, scope)`** / **`revokeRole(..)`**,
    **`rolesOf(userId, scope)`**, **`scopesOf(userId, type)`** -- all as
    `default` methods, so custom services keep compiling unchanged. A global
    role survives a revoke within a scope; it is a different assignment.
  - **`UserAccountDto.roleAssignments()`** with `hasRole(code, scope)`,
    `rolesIn(scope)` and `scopesOf(type)`. Global roles count everywhere.
  - **Authorities are qualified**: `ROLE_ADMIN@club:17`, and fine-grained
    permissions likewise (`season:read@club:17`).
  - **`ActiveScopeService`**: the scope the person is currently working in. Its
    roles additionally apply **without** the suffix -- only that keeps
    `@RolesAllowed("ADMIN")` meaning something readable, namely "here". At
    sign-in time no scope is active; which one it should be is known only to
    the application.
  - `auth_user_role` is therefore an entity of its own (`RoleAssignment`)
    rather than a `@ManyToMany` table. Existing assignments move to the empty
    scope and behave unchanged.
- **The language on the account** (`auth_user.locale`, migration **V1_4**).
  Until now every mail went out in `zs.identity.locale`, even to accounts that
  had registered in another language -- at delivery time there is no browser to
  ask. Now the language of the account decides, and only without a choice of
  its own (`null`) does the application's language apply again.
  New for this: `UserAccountDto.locale()` and `localeOr(fallback)`,
  `UserAccountService#changeLocale(userId, locale)` -- the route an application
  puts behind its account settings -- plus the overloads
  `RegistrationService#register(email, password, name, locale)`,
  `InvitationService#inviteNewAccount(email, name, locale)` and
  `InvitationService#claim(token, password, locale)`. All three are `default`
  methods: an application's own service keeps compiling unchanged.
  `RegistrationView` and `ClaimAccountView` pass their language through by
  themselves. The building block brings no **view** for the language
  selection -- where it belongs is known only to the application.
- **UI rules for `identity-vaadin`** (`IdentityFormView`). Every view of the
  building block now inherits a shared shape: a centred column with a maximum
  width, `border-box`, no fixed pixel widths, every field and every button
  across the full column width. The lower bound is "usable on a phone and on a
  desktop, no horizontal scrolling at 375 px", and a test pins it down for
  every view. Alongside that, fixed CSS classes for styling along:
  `identity-view`, `identity-view--<name>` per view, and
  `identity-view__column` inside the sign-in page.
- **`zs.identity.ui.*`**: `max-width` (default `28rem`), `class-names` (your
  own CSS classes on every view -- the hook for the application's theme) and
  `notification-duration` (default `5s`, `0` leaves notifications standing).
- **`IdentityProperties#withLocale(..)` and `#withUi(..)`**: so that a test
  wanting to change a single setting does not have to copy out the whole
  record.
- **`ForgotPasswordView`/`ResendVerificationView`** mark their address field as
  `autocomplete="username"`, so the password manager offers the address instead
  of making it be typed.

### Build
- **Dependencies updated**: Vaadin 25.2.8, Karibu-Testing 2.7.3, NullAway
  0.14.1 (plugin 3.2.0), ErrorProne plugin 5.1.1, SpotBugs plugin 6.5.11,
  ben-manes 0.64.0, Sonar plugin 7.5.0, Gradle wrapper 9.7.1.
- **OpenRewrite wired in** (`org.openrewrite.rewrite`, on demand only): our own
  recipes from `de.zettsystems:zettsystems-recipes:1.0.0` plus
  `staticanalysis.CodeCleanup` and `RemoveUnusedImports` from the
  `rewrite-recipe-bom`. Run once across the codebase: import blocks unified,
  qualified class names replaced by imports, `x.equals("literal")` flipped
  around, one dead import removed. Which recipe groups were checked and
  deliberately rejected is written down in the `rewrite` block of
  `build.gradle`.
- **Boot 4 test dependency modularised**: `spring-security-test` ->
  `org.springframework.boot:spring-boot-starter-security-test` (a finding from
  `spring.boot4.MigrateToModularStarters`).
- **`spring-boot-starter-validation` removed.** The building block uses not a
  single Bean Validation annotation; it forced Hibernate Validator on every
  embedding application. Whoever needs it takes it directly.
- **`Automatic-Module-Name` in both artifacts**: `de.zettsystems.identity` and
  `de.zettsystems.identity.ui`. Applications using JPMS get a stable module
  name instead of one derived from the file name. A `module-info.java` stays
  out on purpose -- the reasoning is in the README.

### Changed
- **`spring-boot-starter-mail` has become optional** (`compileOnly`).
  **Applications that send mails take it themselves** -- one line in the build.
  Without it the building block still starts and falls back to delivery through
  the log; an application with its own `IdentityMailSender` no longer pays for
  jakarta.mail at all. For this the mail auto-configuration is split in two
  (`@ConditionalOnClass`), and `IdentityMailFactory.javaMail(..)` has moved to
  `JavaMailFactory.javaMail(..)`: in an application without a mail library, no
  reachable signature may name a mail type.
  `IdentityMailAutoConfigurationTest` checks this with a `FilteredClassLoader`
  that hides the library.
- **`IdentityProperties` has one more component** (`ui`, in last position).
  Whoever builds the record by hand -- common in tests -- adjusts the call or
  uses `IdentityProperties.defaults().withLocale(..)` from now on. The reason
  for the minor bump, as with 0.6.0 before it.
- **`UserAccountDto` has two more components** (`locale`; `roleCodes` has
  become `roleAssignments`). The constructors taking plain role codes remain --
  their roles are then global -- and `roleCodes()` still answers, now with the
  global roles. Applications without tenants notice none of it.
- **`UserAccount#getRoles()`** returns the **global** roles; every assignment
  is available through `getRoleAssignments()`. `replaceRoles(..)` replaces the
  global ones only -- scoped ones stay, because the call says nothing about
  tenants. (An entity; it never leaves the module.)

## 0.6.0 - 2026-09-16

### Added
- **Invitations** (`InvitationService`). One mechanism for two cases.
  **Claiming a managed account** -- `inviteToClaim(userId, email)` adds the
  address to an account without credentials and sends the link; the `userId`
  stays stable and the application's domain data stays attached to it. And
  **invitation-only registration** -- `inviteNewAccount(email, name)` creates
  an account **without a password** and invites it; together with
  `zs.identity.self-registration-enabled=false` only those who were invited get
  in. Nobody has to transmit an initial password any more.
  Along with it: `AuthTokenType.INVITATION`, the view `ClaimAccountView` under
  `IdentityRoutes.CLAIM_ACCOUNT` (`invitation`), `resendInvitation(userId)`
  (which voids the previous invitation) and `findInvitee(token)`, which the
  view uses to show the name of the invited account.
- **`zs.identity.invitation-validity`** (default `7d`): a deadline of its own
  for invitations. Nobody asked for one -- it sits in the inbox until someone
  has time, and must not expire overnight like a reset link. The mail now
  states whole deadlines above a day in days
  (`identity.mail.validity.days`); `24h` still reads as "24 hours".
- **`IdentityMailSender#sendInvitation`** as a `default` method that fails
  rather than silently doing nothing. Applications with their own delivery path
  keep compiling unchanged and implement it once they want to invite.

### Changed
- **`IdentityProperties` has one more component** (`invitationValidity`, in
  fourth position). Whoever builds the record by hand -- common in tests --
  adjusts the call. A second constructor would have been the more convenient
  route but makes the binding of `@ConfigurationProperties` ambiguous ("No
  default constructor found"). Hence the minor bump to 0.6.0.

### New message keys
`identity.error.accountAlreadyClaimed`, `identity.mail.invitation.subject`,
`identity.mail.invitation.body`, `identity.mail.invitation.body.html`,
`identity.mail.validity.days`, `identity.claim.*` (the view). Whoever sets the
texts themselves adds them.

## 0.5.1 - 2026-09-09

### Fixed
- **A clickable link in the verification and reset mails.** Both mails now go
  out as `multipart/alternative`: the same text as before, and next to it an
  HTML part with a real `<a href>`. In plain-text messages Outlook wraps long
  lines and turned the link -- always longer than 76 characters with its
  43-character token -- into one that could no longer be clicked. New keys
  `identity.mail.verification.body.html` and `identity.mail.reset.body.html`;
  whoever sets the texts themselves adds them.

## 0.5.0 - 2026-08-30

### Added
- **Bulk lookup.** `UserAccountService#findAllById(Collection<Long>)` loads
  several accounts including their roles in one query -- for member lists,
  instead of one `findById` per row.
- **Token cleanup run.** `TokenCleanupScheduler` deletes redeemed tokens and
  those expired for more than 7 days every day at 03:15
  (`AuthTokenRepository#deleteObsolete`). It runs only when the application
  sets `@EnableScheduling`; it can be switched off with
  `zs.identity.token-cleanup.enabled=false`.

## 0.4.0 - 2026-08-30

### Added
- **Deleting an account.** `UserAccountService#deleteAccount(userId)` removes
  an account for good, along with its role assignments and tokens (foreign keys
  with `ON DELETE CASCADE`). The application clears up its own data for that id
  beforehand. Intended for administration, for accounts created twice for
  example.

## 0.3.0 - 2026-08-29

### Added
- **Forced password change.** `UserAccountService#requirePasswordChange(userId)`
  sets the `must_change_password` flag (migration `V1_3`); setting any new
  password -- `changePassword` as well as the reset through "forgot password"
  -- clears it. `UserAccountDto` and `IdentityUserDetails` carry
  `mustChangePassword()`. In `identity-vaadin`, `PasswordChangeGuard` (attached
  through a `VaadinServiceInitListener` via the ServiceLoader) sends every
  route to the new `ChangePasswordView` (`IdentityRoutes.CHANGE_PASSWORD` =
  `password/change`, `@PermitAll`, without a layout, with a sign-out button)
  until the password has been changed. After the change the building block
  refreshes the running session.
- New texts `identity.change.*` in both message bundles.

### Changed
- `UserAccountDto` has the new component `mustChangePassword` at the end; the
  previous constructor remains as an overload (with the value `false`), so
  applications need to change nothing.
- `IdentityUserDetails` has a public constructor with the new parameter
  `mustChangePassword` -- for the tests of embedding applications.
- The view needs Vaadin's `AuthenticationContext`; the bean comes from
  `vaadin-spring` by itself.


## 0.2.0 - 2026-08-28

### Added
- All texts -- views, mails, the messages behind `IdentityMessageKeys` -- come
  from shipped message bundles in **German and English**. Resolution goes
  through the new port `IdentityMessages`; a bean of your own replaces
  individual texts or all of them (`IdentityMessages.resourceBundles()` as the
  fallback).
- The setting `zs.identity.locale` (default `de`): the language of the mails
  and the fallback language of the views. If the application brings an
  `I18NProvider`, the views follow the language of the `UI` instead.
- Browserless tests for `identity-vaadin` with Karibu; the coverage threshold
  raised to 78 % line / 80 % branch, and the views are no longer excluded.

### Changed
- **Breaking:** `IdentityProperties` has the component `locale`, and the view
  constructors and `IdentityMailFactory.javaMail(...)` each have one more
  parameter, `IdentityMessages`. Applications that embed through the
  auto-configuration alone notice nothing.
- Page titles through `HasDynamicTitle` instead of `@PageTitle` -- an
  annotation cannot translate.
- Unknown message keys show the generic text instead of the raw key
  (`IdentityMessageKeys.UNEXPECTED`).

## 0.1.0 - 2026-08-28

### Added
- First release: user accounts, self-registration with email verification,
  sign-in, password reset, roles and permissions, as `identity-core` (without
  UI) and `identity-vaadin`.
- Flyway migrations in the version space `V1_x`, auto-configuration without a
  component scan, publishing to GitHub Packages through the pipeline.
