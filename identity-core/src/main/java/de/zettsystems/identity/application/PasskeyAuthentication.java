package de.zettsystems.identity.application;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.Objects;

/**
 * A session signed in with a passkey.
 *
 * <p>Spring Security's own {@code WebAuthnAuthentication} carries the WebAuthn
 * user entity as its principal -- a name and an opaque handle. Everything in
 * this building block and in the applications built on it expects the
 * {@link IdentityUserDetails} there instead: the account id for linking, the
 * forced password change, the active scope. So the passkey sign-in ends in
 * this token, whose principal is the same {@link UserDetails} the password
 * sign-in produces. An application that wants to know <em>how</em> someone
 * signed in checks the token's type; one that does not never notices a
 * difference.
 *
 * <p>Counts as fully authenticated, unlike a remember-me session: whoever
 * just used Face ID may register another passkey.
 */
public final class PasskeyAuthentication extends AbstractAuthenticationToken {

    // Lives in the HTTP session; without a fixed UID every change to this
    // class breaks a session that is still open.
    @Serial
    private static final long serialVersionUID = 1L;

    private final UserDetails principal;

    /** An authenticated token; there is no unauthenticated shape of it. */
    public PasskeyAuthentication(UserDetails principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = Objects.requireNonNull(principal, "principal");
        super.setAuthenticated(true);
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException("A PasskeyAuthentication is authenticated from the start");
        }
        super.setAuthenticated(false);
    }

    /** There are none: the signature was checked and is not kept. */
    @Override
    public @Nullable Object getCredentials() {
        return null;
    }

    @Override
    public UserDetails getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.getUsername();
    }
}
