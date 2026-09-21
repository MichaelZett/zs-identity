# Contributing

Thanks for looking. zs-identity is a building block extracted from real
applications, not a product with a roadmap — so the most valuable contribution
is usually a precise bug report, and the second most valuable is a small,
focused pull request.

**Security flaws do not belong here.** See [SECURITY.md](SECURITY.md).

## Before you write code

Open an issue first for anything beyond an obvious fix. It costs you nothing
and it avoids the outcome nobody wants: a finished pull request that has to be
turned down because it conflicts with a constraint below.

## What this library will not do

These are the rules the code is built on. A change that breaks one of them will
not be merged, however well it is written.

- **The building block knows nothing about the applications that embed it.**
  No references to their packages, no defaults that only fit one application —
  sender names, business roles, texts tied to a particular domain. Business
  roles arrive through a `RoleCatalog` bean; texts come from message bundles.
- **`identity-core` stays free of Vaadin.** REST applications must be able to
  use it. UI lives in `identity-vaadin`, which is optional.
- **Scopes stay opaque.** A `Scope` is a type and an id (`club:17`). The
  library stores and compares it; it never interprets it. No special case for
  a particular type, no tenant entity.
- **No dependency that not everyone needs.** Anything used by only some
  applications is declared `compileOnly` and reached through
  `@ConditionalOnClass` — Flyway, the servlet types, and mail delivery work
  that way. The fallback must not name a type from the missing library in any
  reachable signature, and a test using `FilteredClassLoader` has to prove the
  module still starts without it.
- **No `@ComponentScan`** in the auto-configuration. Every bean is declared
  explicitly in `IdentityBeans` under `@ConditionalOnMissingBean`, so an
  application can replace any single one. `AutoConfigurationIT` enforces this.
- **Settings live under the `zs.identity` prefix**, and every one of them has a
  sensible default. Convention over configuration: an application that
  configures nothing must still work.
- **What happens to an account is announced, and cleaned up.** The services
  publish `IdentityAccountEvent` (`PasswordChanged`, `EmailChanged`,
  `AccountLocked`, `AccountDeleted`) inside the transaction that makes the
  change, and the building block clears the remember-me tokens of that account
  itself. A new route on which a password is set, an address assigned or an
  account locked or deleted publishes the matching event — otherwise a cookie
  stays valid at exactly that spot when it should not.
- **The public API is a contract.** Changing a signature on
  `UserAccountService`, `RegistrationService`, `PasswordResetService`,
  `UserAccountDto`, `AccountName`, `IdentityProperties`, `IdentityRoutes` or
  `IdentityAccountEvent` and its four records
  breaks every application that depends on it. Extend with `default` methods or
  additional overloads instead; a genuine break needs a major version.

## Building

You need **Java 25**, and **Docker** for the integration tests — they start
PostgreSQL through Testcontainers.

```
./gradlew build
```

That compiles (ErrorProne and NullAway are errors, not warnings), runs the
tests, and enforces SpotBugs and the JaCoCo thresholds (78 % line, 80 %
branch). A single test class:

```
./gradlew test --tests "*.RegistrationServiceIT"
```

To try a change against your own application without publishing:

```
./gradlew publishToMavenLocal
```

## House rules for a pull request

- **Tests come with the change.** A bug fix carries a test that fails without
  it. Behaviour that is not covered will be asked about.
- **Database migrations** live in
  `identity-core/src/main/resources/db/identity` and run in the building
  block's own schema `identity` with a Flyway history of its own — keep the
  **V1_x** numbering, write them schema-less (Flyway sets the search path),
  and never reference an application's tables. Never edit a migration that
  has been released; add a new one. The `INCREMENT BY` of a sequence must
  match the `allocationSize` of its `@SequenceGenerator`.
- **No hard-coded user-facing text.** Everything goes through
  `IdentityMessages` and the bundles under
  `de/zettsystems/identity/messages/`: `core*` in `identity-core`, `ui*` in
  `identity-vaadin`. A new key goes into **both** files of a set; the file
  without a suffix is English and is the fallback.
- **Comments explain why, not what.** The reasoning behind a decision is worth
  writing down; a restatement of the code is not.
- **English** for code, comments, Javadoc and documentation.
- **Add a `CHANGELOG.md` entry** under `## Unreleased` when the change is
  visible to users of the library.
- Keep the pull request to one subject. Unrelated cleanups, however
  reasonable, make a change harder to review and to revert.

Releases and version numbers are handled by the maintainer — please leave
`gradle.properties` alone.
