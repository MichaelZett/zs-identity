package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

/**
 * An account was deleted.
 *
 * <p>The building block's own tables clear themselves through the foreign keys;
 * what an application stores about the person does not. The event carries the
 * id and the address because after the commit neither can be looked up any
 * more -- a listener that asks {@code findById} afterwards finds nothing.
 *
 * @param userId the account that has just gone
 * @param email  the name it signed in under, {@code null} for a managed account
 */
public record AccountDeleted(Long userId, @Nullable String email) implements IdentityAccountEvent {
}
