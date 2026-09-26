package de.zettsystems.identity.application;

import org.springframework.security.core.AuthenticationException;

import java.io.Serial;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A sign-in through an external provider that the building block turned down,
 * with the reason (since 1.2.0).
 *
 * <p>An {@link AuthenticationException}, so that it travels the way every
 * failed sign-in does: to the failure handler, which sends the browser back
 * to the sign-in page with the {@linkplain Reason#code() reason} in the
 * address, where the page picks a text for it. None of the reasons names an
 * account, so the page tells nobody who is registered.
 */
public final class ExternalSignInException extends AuthenticationException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Why the sign-in was turned down. */
    public enum Reason {
        /**
         * The provider does not vouch for an address, and nothing links its
         * identity to an account yet -- the address is the only thing that
         * could, and an unconfirmed one proves nothing.
         */
        EMAIL_UNVERIFIED,
        /** Nobody has an account for this address, and new ones are not created this way. */
        NO_ACCOUNT,
        /**
         * An account with this address exists but does not know this provider,
         * and joining it by address is switched off: the person links the
         * provider from within the account.
         */
        NOT_LINKED,
        /**
         * The identity belongs to another account, or the account already has
         * a different identity at this provider.
         */
        ALREADY_LINKED,
        /** The invitation to redeem is unknown, used or expired. */
        INVITATION_INVALID,
        /** Linking a provider needs a fresh sign-in, not a remembered one. */
        REAUTHENTICATION_REQUIRED,
        /** The account is disabled. */
        DISABLED,
        /** Anything else: the provider refused, or the answer could not be read. */
        FAILED;

        /** The reason as it travels in an address: {@code email-unverified}. */
        public String code() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        /** The reason behind a {@link #code()}; empty for anything unknown. */
        public static Optional<Reason> fromCode(String code) {
            return Arrays.stream(values()).filter(reason -> reason.code().equals(code)).findFirst();
        }
    }

    private final Reason reason;

    public ExternalSignInException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public ExternalSignInException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason getReason() {
        return reason;
    }
}
