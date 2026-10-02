package de.zettsystems.identity.application;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Builds the session of an impersonation without going through the service,
 * for tests outside this package that only need the principal.
 */
public final class ImpersonatedUsers {

    private ImpersonatedUsers() {
    }

    /** A session in which the actor acts as the target, which has no roles. */
    public static Authentication session(Long targetUserId, Long actorUserId, Authentication actor) {
        ImpersonatedUser user = new ImpersonatedUser(targetUserId, "Managed", List.of(), actorUserId, actor);
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
