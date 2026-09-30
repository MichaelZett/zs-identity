package de.zettsystems.identity.ui;

import com.vaadin.flow.spring.security.AuthenticationContext;
import de.zettsystems.identity.application.IdentityUserDetails;

import java.util.Optional;

/**
 * The signed-in account, without the trap in Vaadin's own lookup.
 *
 * <p>{@code AuthenticationContext#getAuthenticatedUser(Class)} casts the
 * principal hard and throws a {@code ClassCastException} when it is something
 * else -- it does <em>not</em> answer empty. An application with a second way
 * in (HTTP Basic for a demo, an actuator user) has principals that are plain
 * Spring {@code User}s, and every view that asks for
 * {@link IdentityUserDetails} that way fails for them. This asks for any
 * principal and keeps it only if it is one of ours.
 */
public final class IdentityUsers {

    private IdentityUsers() {
    }

    /**
     * The signed-in {@link IdentityUserDetails}; empty when nobody is signed in
     * or when the principal comes from somewhere else.
     */
    public static Optional<IdentityUserDetails> current(AuthenticationContext authenticationContext) {
        return authenticationContext.getAuthenticatedUser(Object.class)
                .filter(IdentityUserDetails.class::isInstance)
                .map(IdentityUserDetails.class::cast);
    }
}
