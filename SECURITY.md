# Security Policy

zs-identity handles authentication, password hashing and role checks. A flaw
here is not a cosmetic bug in a library — it can unlock the applications that
depend on it. Please treat findings accordingly.

## Reporting a vulnerability

**Do not open a public issue.** A public issue publishes the flaw before a fix
exists, which is exactly the situation a fix is supposed to prevent.

Use GitHub's private vulnerability reporting instead: go to the
[Security tab](https://github.com/MichaelZett/zs-identity/security) and choose
**Report a vulnerability**. The report stays visible only to you and the
maintainer until an advisory is published.

If you cannot use that form, contact the maintainer through the address on the
[GitHub profile](https://github.com/MichaelZett) and say only that you have a
security report — no details in that first message.

### What helps

- The affected version of `identity-core` / `identity-vaadin`, plus the Spring
  Boot and Java versions.
- Which entry point is involved: registration, login, password reset, email
  verification, invitation, or role and scope evaluation.
- A minimal way to reproduce it. A failing test against this repository is the
  most useful form; a description of the steps is fine too.
- What an attacker gains — account takeover, privilege escalation across a
  scope, information disclosure.

## What to expect

This is a single-maintainer project, built alongside the applications that use
it. There is no bug bounty and no guaranteed response window. What is promised:

- An acknowledgement that the report arrived.
- An honest assessment of whether it is a vulnerability, and why.
- A fix released as a new version, with the issue named in `CHANGELOG.md`, and
  credit in the advisory unless you prefer otherwise.

Please give a fix a reasonable chance before disclosing publicly.

## Supported versions

Only the latest release receives fixes. Older versions are not patched — the
upgrade path is forward.

| Version | Supported |
|---------|-----------|
| latest  | yes       |
| older   | no        |

## Out of scope

- Findings that require an already compromised database, host, or session.
- Configuration choices made by the application embedding this library — for
  example a security filter chain that leaves a route open, or a role catalog
  that grants too much. The building block cannot override those.
- Reports produced by a scanner without a demonstrated impact on this code.
