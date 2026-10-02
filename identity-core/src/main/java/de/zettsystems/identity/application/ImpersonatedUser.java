package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.Impersonation;
import de.zettsystems.identity.values.Scope;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serial;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * The principal of a session in which an administrator acts as a managed
 * account (since 1.5.0; see {@link ImpersonationService}).
 *
 * <p>Id, display name and roles are the target's, so that everything reading
 * the current account sees the target. It remembers the administrator's own
 * authentication, which {@link ImpersonationService#stop()} puts back
 * unchanged -- with its sign-in route, its active scope and its provider
 * attributes.
 *
 * <p>{@link #getUsername()} is {@code managed:<id>}: a managed account has no
 * address, and the paths of the building block that look an account up by
 * name (passkeys, linking a provider, the session refresh after a role
 * change) turn an impersonated session down explicitly rather than relying on
 * that name finding nothing.
 */
public final class ImpersonatedUser extends IdentityUserDetails {

    // Lives in the HTTP session; without a fixed UID every change to this
    // class breaks a session that is still open.
    @Serial
    private static final long serialVersionUID = 1L;

    private static final String USERNAME_PREFIX = "managed:";

    private final Long actorUserId;
    private final Authentication actorAuthentication;

    ImpersonatedUser(Long targetUserId, String targetDisplayName,
                     Collection<? extends GrantedAuthority> targetAuthorities,
                     Long actorUserId, Authentication actorAuthentication) {
        super(targetUserId, USERNAME_PREFIX + targetUserId, targetDisplayName, null, true, false,
                targetAuthorities);
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.actorAuthentication = Objects.requireNonNull(actorAuthentication, "actorAuthentication");
    }

    private ImpersonatedUser(IdentityUserDetails withScope, ImpersonatedUser source) {
        super(withScope);
        this.actorUserId = source.actorUserId;
        this.actorAuthentication = source.actorAuthentication;
    }

    /** The administrator at the keyboard. */
    public Long actorUserId() {
        return actorUserId;
    }

    /** The administrator's own authentication, as it was before the impersonation started. */
    Authentication actorAuthentication() {
        return actorAuthentication;
    }

    /** The running impersonation as a value. */
    public Impersonation impersonation() {
        return new Impersonation(actorUserId, userId(), displayName());
    }

    /**
     * Stays an impersonation: switching the active scope must not hand the
     * session to the target for good.
     */
    @Override
    public ImpersonatedUser withActiveScope(@Nullable Scope newActiveScope) {
        return new ImpersonatedUser(super.withActiveScope(newActiveScope), this);
    }

    /** The impersonation of the current thread's session, if one is running. */
    public static Optional<ImpersonatedUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof ImpersonatedUser user
                ? Optional.of(user)
                : Optional.empty();
    }

    /**
     * Turns a change of the account's settings down while the current session
     * acts as it: password, passkeys, providers, address, deletion.
     *
     * @throws IdentityException {@code IMPERSONATION_RESTRICTED}
     */
    static void requireNotImpersonated(Long userId) {
        if (current().filter(user -> user.userId().equals(userId)).isPresent()) {
            throw new IdentityException(IdentityMessageKeys.IMPERSONATION_RESTRICTED,
                    "Account %d cannot be changed while acting as it".formatted(userId));
        }
    }
}
