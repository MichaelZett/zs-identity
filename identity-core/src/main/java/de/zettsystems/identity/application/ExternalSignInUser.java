package de.zettsystems.identity.application;

import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The principal of a sign-in through an external provider (since 1.2.0): the
 * account's {@link IdentityUserDetails}, and at the same time the
 * {@link OAuth2User} Spring's OAuth2 sign-in insists on.
 *
 * <p>Spring's {@code OAuth2AuthenticationToken} takes nothing but an
 * {@code OAuth2User} as principal. Everything in this building block and in
 * the applications built on it expects an {@code IdentityUserDetails} there
 * instead -- the account id for linking, the forced password change, the
 * active scope. This class is both, so the sign-in through a provider ends in
 * Spring's own token type and every {@code instanceof IdentityUserDetails}
 * still holds. An application that wants to know <em>how</em> someone signed
 * in checks for {@code OAuth2AuthenticationToken}; one that does not never
 * notices a difference.
 *
 * <p>{@link #getName()} is the account's sign-in name, not the provider's id
 * for the person: remember-me and the session refresh look the account up by
 * it. What the provider said is kept in {@link #getAttributes()}.
 *
 * <p>Only loaded when the OAuth2 client is on the classpath.
 */
public final class ExternalSignInUser extends IdentityUserDetails implements OAuth2User {

    // Lives in the HTTP session; without a fixed UID every change to this
    // class breaks a session that is still open.
    @Serial
    private static final long serialVersionUID = 1L;

    /** A copy of the provider's answer; the values are JSON values and serialise with the session. */
    @SuppressWarnings("serial")
    private final Map<String, Object> attributes;

    public ExternalSignInUser(IdentityUserDetails account, Map<String, Object> attributes) {
        super(Objects.requireNonNull(account, "account"));
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /** What the provider reported about the person at sign-in. */
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /** The account's sign-in name, the same as {@link #getUsername()}. */
    @Override
    public String getName() {
        return getUsername();
    }
}
