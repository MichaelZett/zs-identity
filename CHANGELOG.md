# Changelog

Notable changes to zs-identity. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows
[SemVer](https://semver.org/). On release, `## Unreleased` is renamed to
`## <version> - <date>`.

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
