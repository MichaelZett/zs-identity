package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The view of a user account for everything outside this building block.
 *
 * <p>The JPA entity deliberately never leaves the module: applications are
 * meant to link through the {@code id}, not through an object reference. That
 * keeps the building block replaceable and avoids detached entities inside
 * someone else's transaction.
 *
 * @param email {@code null} for managed accounts, that is people an
 *              application keeps without self-registration ({@link #managed()})
 * @param name  display name, plus first and last name in applications that use
 *              real names
 * @param roleAssignments every role of the account, each with the scope it
 *                        applies in ({@code null} = global). For applications
 *                        without scopes, {@link #roleCodes()} is the simpler
 *                        view of the same thing.
 * @param mustChangePassword the account has to change its password before
 *                           using the application (initial password, manual
 *                           reset)
 * @param locale language the account wants to be addressed in; {@code null}
 *               means "no choice of its own", and then {@code zs.identity.locale}
 *               applies. For the common case "some language, whichever" there
 *               is {@link #localeOr(Locale)}.
 * @param claimed the account belongs to a person who can sign in with it:
 *                registered, or an invitation redeemed. {@code false} for
 *                managed accounts and for invitations still open -- the
 *                moment an application may offer "invite again"
 *                ({@code InvitationService#resendInvitation}). Deliberately
 *                not "has a password": with external providers there will be
 *                claimed accounts without one.
 * @param lockedUntil end of a temporary lock after too many wrong passwords
 *                    (since 1.1.0); {@code null} when the account was never
 *                    locked or has been unlocked since. A time in the past
 *                    means the lock has run out -- {@link #lockedAt(Instant)}
 *                    saves the comparison. Not the same as {@link #enabled()}.
 */
public record UserAccountDto(Long id,
                             @Nullable String email,
                             AccountName name,
                             boolean enabled,
                             boolean emailVerified,
                             Instant createdAt,
                             Set<ScopedRole> roleAssignments,
                             boolean mustChangePassword,
                             @Nullable Locale locale,
                             boolean claimed,
                             @Nullable Instant lockedUntil) {

    public UserAccountDto {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(roleAssignments, "roleAssignments");
        roleAssignments = Set.copyOf(roleAssignments);
    }

    /**
     * The shape before 1.1.0, without {@code lockedUntil}. It stays so that
     * applications building the record by hand (common in tests) do not
     * break; the account is then not locked.
     */
    public UserAccountDto(Long id, @Nullable String email, AccountName name, boolean enabled,
                          boolean emailVerified, Instant createdAt, Set<ScopedRole> roleAssignments,
                          boolean mustChangePassword, @Nullable Locale locale, boolean claimed) {
        this(id, email, name, enabled, emailVerified, createdAt, roleAssignments, mustChangePassword,
                locale, claimed, null);
    }

    /**
     * The shape before 0.10.0, without {@code claimed}. It stays so that
     * applications building the record by hand (common in tests) do not break.
     * {@code claimed} is then derived from the address: an account with one
     * counts as registered. An open invitation has to be built with the full
     * shape.
     */
    public UserAccountDto(Long id, @Nullable String email, AccountName name, boolean enabled,
                          boolean emailVerified, Instant createdAt, Set<ScopedRole> roleAssignments,
                          boolean mustChangePassword, @Nullable Locale locale) {
        this(id, email, name, enabled, emailVerified, createdAt, roleAssignments, mustChangePassword,
                locale, email != null);
    }

    /**
     * The shape before 0.3.0, without {@code mustChangePassword}. It stays so
     * that applications building the record by hand (common in tests) do not
     * break.
     */
    public UserAccountDto(Long id, @Nullable String email, AccountName name, boolean enabled,
                          boolean emailVerified, Instant createdAt, Set<String> roleCodes) {
        this(id, email, name, enabled, emailVerified, createdAt, roleCodes, false);
    }

    /**
     * The shape with plain role codes; all roles are then global. It stays for
     * the same reason: applications without scopes still build the record this
     * way.
     */
    public UserAccountDto(Long id, @Nullable String email, AccountName name, boolean enabled,
                          boolean emailVerified, Instant createdAt, Set<String> roleCodes,
                          boolean mustChangePassword) {
        this(id, email, name, enabled, emailVerified, createdAt,
                roleCodes.stream().map(ScopedRole::global).collect(Collectors.toUnmodifiableSet()),
                mustChangePassword, null);
    }

    /** The publicly visible name, set in both name shapes. */
    public String displayName() {
        return name.displayName();
    }

    /**
     * First name; empty when the application only keeps display names. Not
     * {@code null}, so that applications using real names can work without a
     * case distinction.
     */
    public String firstName() {
        String firstName = name.firstName();
        return firstName != null ? firstName : "";
    }

    /** Last name; empty when the application only keeps display names. */
    public String lastName() {
        String lastName = name.lastName();
        return lastName != null ? lastName : "";
    }

    /**
     * Whether a temporary lock is in force at this moment: a sign-in with
     * the password would be refused, even with the right one.
     */
    public boolean lockedAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Managed = created by the application, without credentials of its own. */
    public boolean managed() {
        return email == null;
    }

    /**
     * The language of the account, or the given one if it has not chosen any.
     * This saves the case distinction everywhere a language is needed anyway,
     * such as when sending mail.
     */
    public Locale localeOr(Locale fallback) {
        return locale != null ? locale : Objects.requireNonNull(fallback, "fallback");
    }

    /**
     * The codes of the <strong>global</strong> roles, the ones that apply
     * everywhere.
     *
     * <p>Before 0.7.0 those were the only ones, so for applications without
     * scopes this is still the whole answer. Multi-tenant applications ask
     * through {@link #rolesIn(Scope)}.
     */
    public Set<String> roleCodes() {
        return codesIn(null);
    }

    /** Whether the account holds this role <strong>globally</strong>. */
    public boolean hasRole(String roleCode) {
        return roleCodes().contains(roleCode);
    }

    /**
     * Whether the account holds this role in this scope. A global role counts:
     * it applies everywhere, hence here as well.
     */
    public boolean hasRole(String roleCode, Scope scope) {
        Objects.requireNonNull(scope, "scope");
        return roleAssignments.stream()
                .anyMatch(assignment -> assignment.roleCode().equals(roleCode)
                        && (assignment.isGlobal() || scope.equals(assignment.scope())));
    }

    /** The role codes that apply in this scope, global ones included. */
    public Set<String> rolesIn(Scope scope) {
        Objects.requireNonNull(scope, "scope");
        return roleAssignments.stream()
                .filter(assignment -> assignment.isGlobal() || scope.equals(assignment.scope()))
                .map(ScopedRole::roleCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The scopes of this kind in which the account holds a role: "all clubs of
     * this person". Global roles do not show up here, as they belong to no
     * scope.
     */
    public Set<Scope> scopesOf(String type) {
        Objects.requireNonNull(type, "type");
        return roleAssignments.stream()
                .map(ScopedRole::scope)
                .filter(scope -> scope != null && scope.type().equals(type))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> codesIn(@Nullable Scope scope) {
        return roleAssignments.stream()
                .filter(assignment -> Objects.equals(assignment.scope(), scope))
                .map(ScopedRole::roleCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}
