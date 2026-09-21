package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

/**
 * Something happened to an account that an embedding application may have to
 * react to: the password changed, the address changed, the account was locked
 * or deleted.
 *
 * <p>The building block publishes these as plain Spring application events. It
 * does not care who listens -- that is the point. An application that keeps
 * anything of its own per account (remember-me tokens, push subscriptions,
 * sessions in a {@code SessionRegistry}, a cache) learns here that the
 * foundation it built on has moved. Without them the building block would
 * either have to know about all of that, or every application would have to
 * wrap the services to notice.
 *
 * <p><strong>When they arrive:</strong> the events are published inside the
 * transaction that performs the change. A listener that must not act on a
 * change that is later rolled back takes
 * {@code @TransactionalEventListener} (the building block's own
 * {@code RememberMeTokenCleaner} does); a listener that only wants to be told
 * takes {@code @EventListener}.
 *
 * <p>Sealed on purpose: an application switches over the four shapes and the
 * compiler tells it when a fifth appears.
 */
public sealed interface IdentityAccountEvent
        permits AccountDeleted, AccountLocked, EmailChanged, PasswordChanged {

    /** The account this is about; still readable after {@link AccountDeleted}. */
    Long userId();

    /**
     * The sign-in name of the account after the change, or {@code null} for a
     * managed account that has none. For {@link EmailChanged} this is the
     * <em>new</em> address; the old one is
     * {@link EmailChanged#previousEmail()}.
     */
    @Nullable String email();
}
