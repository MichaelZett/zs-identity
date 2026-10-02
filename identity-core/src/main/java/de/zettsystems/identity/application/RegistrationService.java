package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.RegistrationMode;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Self-registration and confirmation of the email address. */
public interface RegistrationService {

    /** Whether the UI should offer registration; true for {@link RegistrationMode#CODE} as well. */
    boolean isSelfRegistrationEnabled();

    /**
     * Who may register (since 1.5.0). The UI asks for an invitation code
     * exactly when this is {@link RegistrationMode#CODE}.
     *
     * <p>The {@code default} derives the answer from
     * {@link #isSelfRegistrationEnabled()} and never says {@code CODE}: an
     * application's own implementation knows nothing of codes, and the UI
     * must not ask for one it would not pass on.
     */
    default RegistrationMode registrationMode() {
        return isSelfRegistrationEnabled() ? RegistrationMode.OPEN : RegistrationMode.CLOSED;
    }

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
     * @throws IdentityException if self-registration is switched off or
     *                           needs a code, the address is already taken (or
     *                           an invitation went to it) or the password is
     *                           too short
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
     * Like {@link #register(String, String, AccountName, Locale)}, with the
     * invitation code the person entered (since 1.5.0).
     *
     * <p>The code is checked with the application's
     * {@link RegistrationGate} before anything else happens, and it is
     * mandatory in {@link RegistrationMode#CODE}. Once the account is usable
     * it comes back with {@link de.zettsystems.identity.values.AccountRegistered}.
     *
     * <p>The {@code default} passes a call without a code on and turns one
     * with a code down: dropping it silently would let the code's meaning
     * get lost without anybody noticing.
     *
     * @param code as entered; {@code null} or blank for none
     * @throws IdentityException {@code INVITATION_CODE_REQUIRED} or
     *                           {@code INVITATION_CODE_INVALID} on top of the
     *                           reasons of the other variants
     */
    default UserAccountDto register(String email, String rawPassword, AccountName name,
                                    @Nullable Locale locale, @Nullable String code) {
        if (code == null || code.isBlank()) {
            return register(email, rawPassword, name, locale);
        }
        throw new UnsupportedOperationException("This RegistrationService does not support invitation codes");
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
