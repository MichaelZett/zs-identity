package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.Scope;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Reading and managing user accounts: the interface through which an
 * application talks to the identity building block.
 *
 * <p>It hands out {@link UserAccountDto} and nothing else. The JPA entity stays
 * inside the building block; applications link their own objects through the
 * {@code id}.
 */
public interface UserAccountService {

    Optional<UserAccountDto> findById(Long id);

    Optional<UserAccountDto> findByEmail(String email);

    List<UserAccountDto> findAll();

    /**
     * Several accounts in one query, for lists that show one account per row
     * (member lists), instead of one {@link #findById} per row. Unknown ids are
     * absent from the result, and the order is unspecified.
     */
    List<UserAccountDto> findAllById(Collection<Long> ids);

    /** Creates an account without self-registration, by an administrator for example. */
    UserAccountDto createAccount(String email, String rawPassword, AccountName name, boolean alreadyVerified);

    /** Real-name variant of {@link #createAccount(String, String, AccountName, boolean)}. */
    default UserAccountDto createAccount(String email, String rawPassword, String firstName, String lastName,
                                         boolean alreadyVerified) {
        return createAccount(email, rawPassword, AccountName.of(firstName, lastName), alreadyVerified);
    }

    /**
     * Creates a managed account: no email, no password, no way to sign in.
     * Intended for people an application keeps without them registering
     * themselves; the application links through the {@code id} as always.
     */
    UserAccountDto createManagedAccount(AccountName name);

    /** Real-name variant of {@link #createManagedAccount(AccountName)}. */
    default UserAccountDto createManagedAccount(String firstName, String lastName) {
        return createManagedAccount(AccountName.of(firstName, lastName));
    }

    /** Changes the name of an account. */
    UserAccountDto rename(Long userId, AccountName newName);

    /**
     * Sets the language the account is addressed in, above all in the mails of
     * this building block, which are created without a browser and therefore
     * cannot ask a session. {@code null} withdraws the choice, after which
     * {@code zs.identity.locale} applies again.
     *
     * <p>The building block brings no view for this: where the language
     * selection belongs (account settings, header, sign-in form) is decided by
     * the application. It calls this method from there -- and passes the same
     * language to
     * {@link RegistrationService#register(String, String, AccountName, Locale)}
     * during registration.
     *
     * <p>Deliberately a {@code default} method that fails rather than an
     * abstract one: an application's own {@code UserAccountService} must not
     * stop compiling because of this addition. Silently doing nothing would be
     * worse than the error -- the language selection would have no effect and
     * nobody would notice.
     *
     * @throws IdentityException if the account does not exist
     * @throws UnsupportedOperationException as long as a custom service does not
     *                                       override it
     */
    default UserAccountDto changeLocale(Long userId, @Nullable Locale locale) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not implement changeLocale(..)");
    }

    /** Grants the role <strong>globally</strong>, so that it applies everywhere. */
    UserAccountDto grantRole(Long userId, String roleCode);

    /** Revokes the global role; scoped ones are left untouched. */
    UserAccountDto revokeRole(Long userId, String roleCode);

    /**
     * Grants the role for a {@link Scope}: "admin of club 17".
     *
     * <p>What a scope means is known only to the application; the building
     * block merely stores and compares it. Who may grant a role is likewise
     * decided by the application -- nothing is checked here.
     *
     * <p>Deliberately a {@code default} method that fails rather than an
     * abstract one: an application's own {@code UserAccountService} must not
     * stop compiling because of this addition.
     *
     * @throws IdentityException if the account or the role does not exist
     * @throws UnsupportedOperationException as long as a custom service does not
     *                                       override it
     */
    default UserAccountDto grantRole(Long userId, String roleCode, Scope scope) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not implement grantRole(.., Scope)");
    }

    /**
     * Revokes the role in exactly this scope. A <em>global</em> role with the
     * same code remains: it is a different assignment, and silently revoking it
     * here as well would come as a surprise.
     *
     * @throws UnsupportedOperationException as long as a custom service does not
     *                                       override it
     */
    default UserAccountDto revokeRole(Long userId, String roleCode, Scope scope) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not implement revokeRole(.., Scope)");
    }

    /**
     * The role codes that apply to this account in this scope, global ones
     * included, since those apply everywhere.
     *
     * <p>Empty when the account does not exist: answering the question "what
     * may this person do here" with an exception helps no caller.
     */
    default Set<String> rolesOf(Long userId, Scope scope) {
        return findById(userId).map(user -> user.rolesIn(scope)).orElseGet(Set::of);
    }

    /** "All clubs of this person": the scopes of this kind in which they hold a role. */
    default Set<Scope> scopesOf(Long userId, String scopeType) {
        return findById(userId).map(user -> user.scopesOf(scopeType)).orElseGet(Set::of);
    }

    UserAccountDto setEnabled(Long userId, boolean enabled);

    /**
     * Lifts a temporary lock after too many wrong passwords and forgets the
     * failures, for an administrator who has spoken to the owner. The lock
     * runs out by itself too, and "forgot password" lifts it as well; this
     * is for when neither can wait. Unrelated to {@link #setEnabled}: an
     * account disabled there stays disabled.
     *
     * <p>Deliberately a {@code default} method that fails rather than an
     * abstract one: an application's own {@code UserAccountService} must not
     * stop compiling because of this addition.
     *
     * @throws IdentityException if the account does not exist
     * @throws UnsupportedOperationException as long as a custom service does not
     *                                       override it
     * @since 1.1.0
     */
    default UserAccountDto unlock(Long userId) {
        throw new UnsupportedOperationException(getClass().getName() + " does not implement unlock(..)");
    }

    /**
     * Sets a new password. At the same time it clears a pending
     * {@code mustChangePassword} and refreshes the running session when this is
     * the signed-in account.
     */
    void changePassword(Long userId, String newRawPassword);

    /**
     * Requires the account to change its password at the next sign-in, for
     * initial passwords handed out by an administrator or for accounts reset by
     * hand. With {@code identity-vaadin}, every route then leads to the
     * change-password view until the password has been changed.
     */
    UserAccountDto requirePasswordChange(Long userId);

    /**
     * Deletes an account for good, together with its role assignments and
     * tokens. The application clears up its own data for that id
     * <strong>beforehand</strong>; the building block knows nothing about it.
     * Intended for administration, for accounts created twice by accident for
     * example.
     *
     * @throws IdentityException if the account does not exist
     */
    void deleteAccount(Long userId);
}
