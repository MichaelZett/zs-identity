package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.IdentitySchema;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.Scope;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A user account. The email address doubles as the sign-in name and is always
 * stored in lower case, so that nobody creates a second account with the same
 * address in a different spelling.
 *
 * <p>The name exists in two shapes (see {@link AccountName}): the display name
 * is always set, first and last name only in applications that keep real
 * names.
 *
 * <p>The <strong>language</strong> is optional: {@code null} means "no choice
 * of its own", and then {@code zs.identity.locale} applies. It is needed above
 * all for mails, which are created without a browser and therefore cannot ask
 * a session.
 *
 * <p>There are also <strong>managed accounts</strong> ({@link #managed}): an
 * application creates them for people who do not (yet) register themselves,
 * without an email address and without a password. They cannot sign in: the
 * sign-in path looks up by email address, and without a password hash the
 * {@code IdentityUserDetailsService} never hands them out either.
 */
@Entity
@Table(name = "auth_user", schema = IdentitySchema.NAME)
@Getter
public class UserAccount extends AbstractAuthEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_user_seq")
    @SequenceGenerator(name = "auth_user_seq", sequenceName = "auth_user_seq", schema = IdentitySchema.NAME, allocationSize = 20)
    private @Nullable Long id;

    // Nullable: managed accounts have neither an address nor a password.
    @Column(unique = true, length = 320)
    private @Nullable String email;

    @Column(name = "password_hash", length = 128)
    private @Nullable String passwordHash;

    @Column(name = "display_name", nullable = false, length = 260)
    @SuppressWarnings("NullAway.Init")
    private String displayName;

    // Nullable since V1_2: applications with freely chosen display names keep
    // no real names.
    @Column(name = "first_name", length = 128)
    private @Nullable String firstName;

    @Column(name = "last_name", length = 128)
    private @Nullable String lastName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    @SuppressWarnings("NullAway.Init")
    private Instant createdAt;

    @Column(name = "last_login_at")
    private @Nullable Instant lastLoginAt;

    // Since V1_4. Nullable means "no choice of its own", not "English".
    @Column(length = 35)
    @Convert(converter = LocaleAttributeConverter.class)
    private @Nullable Locale locale;

    /**
     * The account has to change its password before using the application, for
     * example after an initial password handed out by an administrator. The
     * flag is cleared in the same place where the password is set
     * ({@link #changePassword}), so it can never survive a change -- not even
     * along the "forgot password" route.
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /**
     * The opaque id WebAuthn knows this account by (the "user handle"), since
     * V1_6. Random, assigned the first time a passkey is registered, and never
     * the email address: the authenticator stores it and the specification
     * forbids personal data in it. {@code null} until the first passkey.
     */
    @Column(name = "passkey_user_handle", length = 128)
    private @Nullable String passkeyUserHandle;

    // LAZY is mandatory: the EAGER default of JPA fetches the roles one by one
    // for every list of users (N+1).
    //
    // orphanRemoval: an assignment without an account is nothing. It goes away
    // when the role is revoked, not only on an explicit delete call.
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<RoleAssignment> roleAssignments = new LinkedHashSet<>();

    protected UserAccount() {
        // for JPA
    }

    /**
     * Creates an account. The constructor takes mandatory data only; whether
     * the account is usable right away is decided by the registration through
     * {@link #activateAfterEmailVerification()}.
     */
    public UserAccount(String email, String passwordHash, AccountName name, Instant createdAt) {
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        // Deliberately not through applyName(): otherwise Sonar (java:S2637)
        // does not see the @NullMarked field initialised in the constructor.
        Objects.requireNonNull(name, "name");
        this.displayName = name.displayName();
        this.firstName = name.firstName();
        this.lastName = name.lastName();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.enabled = false;
        this.emailVerified = false;
    }

    /**
     * Creates a managed account: no email, no password, active straight away.
     * Active here only means "belongs to us", not "can sign in"; without an
     * address and a hash there is no sign-in path.
     */
    public static UserAccount managed(AccountName name, Instant createdAt) {
        UserAccount account = new UserAccount();
        account.applyName(name);
        account.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        account.enabled = true;
        account.emailVerified = false;
        return account;
    }

    /**
     * Sets the language the account wants to be addressed in. {@code null}
     * withdraws the choice; the language of the application applies again
     * afterwards.
     *
     * <p>{@link Locale#ROOT} is not a language but the absence of one, so it is
     * treated like {@code null} rather than ending up as an empty language tag
     * in the database.
     */
    public void changeLocale(@Nullable Locale newLocale) {
        this.locale = newLocale == null || "und".equals(newLocale.toLanguageTag()) ? null : newLocale;
    }

    /** Managed = created by an application, without credentials of its own. */
    public boolean isManaged() {
        return email == null;
    }

    /**
     * Claimed = the account belongs to a person who can sign in with it:
     * registered, or an invitation redeemed. Today that is exactly "a password
     * is set"; with sign-in through external providers (issue #1) an account
     * without a password will count as well, so callers ask this rather than
     * looking at the password.
     */
    public boolean isClaimed() {
        return passwordHash != null;
    }

    public static String normalizeEmail(String email) {
        // Locale.ROOT: without it, on a system with a Turkish locale an "I"
        // would turn into a dotless "i" and the address would be a different one.
        return Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    /** The name in both shapes, as an immutable value. */
    public AccountName getName() {
        return new AccountName(displayName, firstName, lastName);
    }

    /**
     * The <strong>global</strong> roles, the ones that apply everywhere.
     *
     * <p>Before V1_5 those were the only ones, so for applications without
     * scopes this is still the whole answer. Anyone who needs the scoped ones
     * as well takes {@link #getRoleAssignments()}.
     */
    public Set<Role> getRoles() {
        return rolesIn(null);
    }

    /** Every assignment, global as well as scoped. */
    public Set<RoleAssignment> getRoleAssignments() {
        return Collections.unmodifiableSet(roleAssignments);
    }

    /**
     * The roles in exactly this scope; {@code null} asks for the global ones.
     *
     * <p>Unmodifiable like {@link #getRoleAssignments()}, and for the same
     * reason: a silent copy that swallows changes would be worse than an
     * exception. Whoever wants to change something uses {@link #grant} and
     * {@link #revoke}.
     */
    public Set<Role> rolesIn(@Nullable Scope scope) {
        Set<Role> roles = roleAssignments.stream()
                .filter(assignment -> assignment.appliesTo(scope))
                .map(RoleAssignment::getRole)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(roles);
    }

    /** The scopes of this kind in which the account holds any role at all. */
    public Set<Scope> scopesOf(String type) {
        Objects.requireNonNull(type, "type");
        Set<Scope> scopes = roleAssignments.stream()
                .map(RoleAssignment::getScope)
                .filter(scope -> scope != null && scope.type().equals(type))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(scopes);
    }

    /** Enables the account once its email address is confirmed. */
    public void activateAfterEmailVerification() {
        this.emailVerified = true;
        this.enabled = true;
    }

    /** Enables the account without email verification (verification turned off). */
    public void activateWithoutVerification() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    /**
     * Adds the address to a managed account, the first step when the real
     * person claims it. Until the invitation is redeemed the account stays
     * without a password and therefore without a sign-in path; V1_1 describes
     * exactly this intermediate state ("email set, password not yet").
     *
     * @throws IllegalStateException if an address is already attached --
     *                               overwriting it would mean taking over
     *                               somebody else's account
     */
    public void assignEmail(String newEmail) {
        if (this.email != null) {
            throw new IllegalStateException("Account " + id + " already has an email address");
        }
        this.email = normalizeEmail(newEmail);
    }

    /**
     * Redeems the invitation: first password, address counts as confirmed,
     * account is usable. The link went to exactly this address, so a second
     * verification mail would only be a detour.
     */
    public void claimWithPassword(String newPasswordHash) {
        changePassword(newPasswordHash);
        activateAfterEmailVerification();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        this.mustChangePassword = false;
    }

    /** Requires a password change at the next sign-in. */
    public void requirePasswordChange() {
        this.mustChangePassword = true;
    }

    public void rename(AccountName newName) {
        applyName(newName);
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = Objects.requireNonNull(at, "at");
    }

    /**
     * Gives the account its WebAuthn user handle, once. The same handle again
     * is harmless; a different one is refused, because every passkey already
     * registered points at the old one and would be orphaned.
     *
     * @throws IllegalStateException if the account already has a different handle
     */
    public void assignPasskeyUserHandle(String handle) {
        Objects.requireNonNull(handle, "handle");
        if (this.passkeyUserHandle != null && !this.passkeyUserHandle.equals(handle)) {
            throw new IllegalStateException("Account " + id + " already has a passkey user handle");
        }
        this.passkeyUserHandle = handle;
    }

    /** Takes the handle back; only meaningful once every passkey is gone. */
    public void withdrawPasskeyUserHandle() {
        this.passkeyUserHandle = null;
    }

    /** Grants the role globally, so that it applies everywhere. */
    public void grant(Role role) {
        grant(role, null);
    }

    /**
     * Grants the role for a scope ({@code null} = global). The same assignment
     * never exists twice; a repeated call simply has no effect.
     */
    public void grant(Role role, @Nullable Scope scope) {
        Objects.requireNonNull(role, "role");
        if (!hasRole(role, scope)) {
            this.roleAssignments.add(new RoleAssignment(this, role, scope));
        }
    }

    public void revoke(Role role) {
        revoke(role, null);
    }

    /**
     * Revokes the role in exactly this scope. A global role stays untouched,
     * because it is a different assignment.
     */
    public void revoke(Role role, @Nullable Scope scope) {
        Objects.requireNonNull(role, "role");
        this.roleAssignments.removeIf(
                assignment -> assignment.getRole().equals(role) && assignment.appliesTo(scope));
    }

    public boolean hasRole(Role role, @Nullable Scope scope) {
        return roleAssignments.stream()
                .anyMatch(assignment -> assignment.getRole().equals(role) && assignment.appliesTo(scope));
    }

    /**
     * Replaces the <strong>global</strong> roles. Scoped assignments stay:
     * they belong to a tenant that this call says nothing about.
     */
    public void replaceRoles(Set<Role> newRoles) {
        Objects.requireNonNull(newRoles, "newRoles");
        this.roleAssignments.removeIf(RoleAssignment::isGlobal);
        newRoles.forEach(role -> grant(role, null));
    }

    private void applyName(AccountName name) {
        Objects.requireNonNull(name, "name");
        this.displayName = name.displayName();
        this.firstName = name.firstName();
        this.lastName = name.lastName();
    }
}
