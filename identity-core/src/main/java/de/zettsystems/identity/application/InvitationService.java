package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * Invitations: guiding someone to an account through a link in a mail instead
 * of letting them register themselves.
 *
 * <p>One mechanism covers two cases:
 *
 * <ol>
 *   <li><strong>Claiming a managed account</strong> -- the application has
 *       kept a person for a while already (without an address, without a
 *       password), and the real person takes the account over later. The
 *       {@code userId} stays stable in the process: everything the application
 *       attached to it stays attached ({@link #inviteToClaim}).</li>
 *   <li><strong>Invitation-only registration</strong> -- with
 *       {@code zs.identity.self-registration-enabled=false} nobody gets in on
 *       their own; an administrator invites addresses
 *       ({@link #inviteNewAccount}).</li>
 * </ol>
 *
 * <p>Who may invite is decided by the application: the building block knows no
 * domain roles and checks nothing here.
 */
public interface InvitationService {

    /**
     * Invites a person to take over an existing managed account: adds the
     * address and sends the link.
     *
     * <p>Until it is redeemed the account stays without a password and
     * therefore without a sign-in path. Inviting the same address again is
     * possible (when the first mail ended up in spam, say) and voids the
     * previous invitation.
     *
     * @throws IdentityException if the account does not exist
     *                           ({@code ACCOUNT_NOT_FOUND}), already belongs to
     *                           someone ({@code ACCOUNT_ALREADY_CLAIMED}) or the
     *                           address is already attached to another account
     *                           ({@code EMAIL_ALREADY_REGISTERED})
     */
    UserAccountDto inviteToClaim(Long userId, String email);

    /** Sends the invitation again to an account that already carries the address. */
    void resendInvitation(Long userId);

    /**
     * Creates an account without a password and invites the address. This is
     * the route for invitation-only registration: the person sets their own
     * password, and nobody has to transmit an initial one.
     *
     * @throws IdentityException if the address is already taken
     */
    UserAccountDto inviteNewAccount(String email, AccountName name);

    /** Real-name variant of {@link #inviteNewAccount(String, AccountName)}. */
    default UserAccountDto inviteNewAccount(String email, String firstName, String lastName) {
        return inviteNewAccount(email, AccountName.of(firstName, lastName));
    }

    /**
     * Like {@link #inviteNewAccount(String, AccountName)}, but sets the
     * language of the account right away, so that even the invitation mail
     * arrives in the right language. Whoever invites almost always knows which
     * language they are addressing the person in; otherwise the building block
     * does not learn it until the invitation is redeemed.
     *
     * @param locale language of the account, {@code null} for "no choice of its own"
     */
    default UserAccountDto inviteNewAccount(String email, AccountName name, @Nullable Locale locale) {
        return inviteNewAccount(email, name);
    }

    /**
     * Whom an invitation is for, without redeeming it. The redemption view uses
     * it to show the name, so that it is visible which account is being taken
     * over.
     *
     * @return empty when the token is unknown
     */
    Optional<UserAccountDto> findInvitee(String token);

    /**
     * Redeems the invitation: sets the first password, thereby confirms the
     * address and enables the account.
     *
     * @throws IdentityException if the token is unknown, already used or
     *                           expired, or the password is too short
     */
    UserAccountDto claim(String token, String rawPassword);

    /**
     * Like {@link #claim(String, String)}, but additionally records the
     * language the person used the redemption view in -- the first moment they
     * say anything about it themselves.
     *
     * <p>A language that is <strong>already set</strong> stays untouched: if an
     * administrator chose one when inviting, that was a decision about this
     * account, whereas the view only knows the language of the browser, which
     * is often just that of the device in hand.
     *
     * @param locale language of the redemption view, {@code null} when unknown
     */
    default UserAccountDto claim(String token, String rawPassword, @Nullable Locale locale) {
        return claim(token, rawPassword);
    }
}
