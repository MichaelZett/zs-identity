package de.zettsystems.identity.application;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * Builds the session of an impersonation for the view tests. It sits in the
 * package of {@link ImpersonatedUser} because its constructor is
 * package-private: only the {@code ImpersonationService} creates one.
 */
public final class ImpersonatedUsers {

    private ImpersonatedUsers() {
    }

    /** A session in which account 1 acts as the managed account "Kai Kind". */
    public static Authentication session(Long targetUserId) {
        Authentication admin = UsernamePasswordAuthenticationToken.authenticated("admin@example.com", null, List.of());
        ImpersonatedUser user = new ImpersonatedUser(targetUserId, "Kai Kind", List.of(), 1L, admin);
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }
}
