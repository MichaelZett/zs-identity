package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

/**
 * A self-registered account has become usable (since 1.5.0).
 *
 * <p><strong>When it arrives:</strong> once per account, at the moment the
 * account registered through {@code RegistrationService} first becomes usable
 * -- not when the form is sent. With {@code email-verification-required}
 * (the default) that is when the address is proven: the link in the
 * verification mail, a password reset through the mailed link, or the first
 * sign-in through an external provider that vouches for the address. An
 * administrator enabling the account by hand
 * ({@code UserAccountService#setEnabled}) counts as well. Without verification
 * it is right within {@code register}. Like the other events it is published
 * inside the transaction that makes the change; a listener that must not act
 * on a change that is rolled back takes {@code @TransactionalEventListener}.
 *
 * <p><strong>Not</strong> published for accounts that come about any other
 * way: an invitation that is redeemed, an account an administrator creates,
 * or one created by the first sign-in through an external provider. Those are
 * routes the application starts itself or announces through other events.
 *
 * <p>Deliberately not an {@link IdentityAccountEvent}: that interface is
 * sealed, and a fifth shape would break every exhaustive switch over it.
 *
 * @param userId the account
 * @param email  its sign-in name
 * @param code   the invitation code it registered with, exactly as the
 *               {@code RegistrationGate} admitted it; {@code null} when it
 *               registered without one ({@link RegistrationMode#OPEN}). The
 *               building block only stores it until this event and then
 *               forgets it -- what it means is the application's business.
 */
public record AccountRegistered(Long userId, String email, @Nullable String code) {
}
