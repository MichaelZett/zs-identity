package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Self-registration and confirmation of the email address. */
public interface RegistrationService {

    /** Whether the UI should offer registration. */
    boolean isSelfRegistrationEnabled();

    /**
     * Whether an account becomes usable only once its address is confirmed.
     * Only then does the UI offer to request the verification mail again;
     * without a confirmation requirement that route would lead nowhere.
     */
    boolean isEmailVerificationRequired();

    /**
     * Creates an account and, where confirmation is required, sends the
     * verification mail. Until then the account is blocked.
     *
     * @throws IdentityException if self-registration is switched off, the
     *                           address is already taken or the password is too
     *                           short
     */
    UserAccountDto register(String email, String rawPassword, AccountName name);

    /** Real-name variant of {@link #register(String, String, AccountName)}. */
    default UserAccountDto register(String email, String rawPassword, String firstName, String lastName) {
        return register(email, rawPassword, AccountName.of(firstName, lastName));
    }

    /**
     * Like {@link #register(String, String, AccountName)}, but remembers the
     * language the person registered in. The UI knows it; the mail delivery
     * that happens later does not.
     *
     * <p>Deliberately a {@code default} method that discards the language
     * rather than an abstract one: an application's own
     * {@code RegistrationService} must not stop compiling because of this
     * addition. Unlike with {@code IdentityMailSender#sendInvitation},
     * discarding is the right thing here -- without a remembered language
     * {@code zs.identity.locale} applies again, which is exactly the previous
     * behaviour.
     *
     * @param locale language of the account, {@code null} for "no choice of its own"
     */
    default UserAccountDto register(String email, String rawPassword, AccountName name,
                                    @Nullable Locale locale) {
        return register(email, rawPassword, name);
    }

    /**
     * Redeems the link from the verification mail and enables the account.
     *
     * @throws IdentityException if the token is unknown, already used or expired
     */
    UserAccountDto confirmEmail(String token);

    /**
     * Sends the verification mail again. Deliberately reports no error when the
     * address does not exist or is already confirmed: otherwise this could be
     * used to find out who has an account.
     */
    void resendVerification(String email);
}
