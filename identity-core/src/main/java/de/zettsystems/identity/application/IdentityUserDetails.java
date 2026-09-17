package de.zettsystems.identity.application;

import de.zettsystems.identity.values.Scope;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The security principal of this building block.
 *
 * <p>Besides the sign-in name (the email address) it carries the stable
 * account id. Applications link their own objects through that id -- the
 * address can change, the id cannot -- and take it from the
 * {@code SecurityContext} without asking the database for it.
 *
 * <h2>Roles with a scope</h2>
 *
 * <p>The authorities are held in <strong>qualified</strong> form: global as
 * {@code ROLE_ADMIN}, scoped as {@code ROLE_ADMIN@club:17}. On top of that
 * there is an <strong>active scope</strong> ({@link #activeScope()}), whose
 * authorities appear <em>additionally</em> in unqualified form.
 *
 * <p>The reason is that the checks stay readable. With club 17 active:
 *
 * <pre>{@code
 * @RolesAllowed("ADMIN")                          // im aktiven Verein
 * hasAuthority("ROLE_ADMIN@club:4")               // gezielt anderswo, etwa bei einem Deep-Link
 * }</pre>
 *
 * <p>Without the active scope, every check in every application would have to
 * assemble the scope itself -- and a forgotten qualification would then not
 * check too strictly but <strong>not at all</strong>. Without the qualified
 * form, in turn, a link into another tenant could only be checked after
 * switching.
 */
public final class IdentityUserDetails implements UserDetails {

    // Lives in the HTTP session; without a fixed UID every change to this
    // class breaks a session that is still open.
    @Serial
    private static final long serialVersionUID = 2L;

    private final Long userId;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean mustChangePassword;
    /** Qualified: global without a suffix, scoped with {@code @club:17}. */
    private final List<GrantedAuthority> grantedAuthorities;
    private final @Nullable Scope activeScope;

    /**
     * Public so that embedding applications can build the principal in their
     * tests; in production only the {@code IdentityUserDetailsService} creates
     * it.
     *
     * <p>A principal is always created <strong>without</strong> an active
     * scope. Which one it should be is decided by the application later
     * ({@link #withActiveScope(Scope)}), not already at sign-in time.
     *
     * @param authorities the authorities in qualified form: {@code ROLE_USER}
     *                    when global, {@code ROLE_ADMIN@club:17} when scoped
     */
    public IdentityUserDetails(Long userId, String email, String displayName, String passwordHash,
                               boolean enabled, boolean mustChangePassword,
                               Collection<? extends GrantedAuthority> authorities) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.email = Objects.requireNonNull(email, "email");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
        this.grantedAuthorities = List.copyOf(authorities);
        this.activeScope = null;
    }

    /** Copy with a different active scope; see {@link #withActiveScope(Scope)}. */
    private IdentityUserDetails(IdentityUserDetails source, @Nullable Scope newActiveScope) {
        this.userId = source.userId;
        this.email = source.email;
        this.displayName = source.displayName;
        this.passwordHash = source.passwordHash;
        this.enabled = source.enabled;
        this.mustChangePassword = source.mustChangePassword;
        this.grantedAuthorities = source.grantedAuthorities;
        this.activeScope = newActiveScope;
    }

    /** Stable identifier of the account. */
    public Long userId() {
        return userId;
    }

    /** Display name as of sign-in time. */
    public String displayName() {
        return displayName;
    }

    /**
     * The account has to change its password before using the application.
     * Kept in the session so that enforcing it does not query the database on
     * every page load; after the change the service refreshes the session.
     */
    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    /** The scope the person is currently working in; empty when none is chosen. */
    public Optional<Scope> activeScope() {
        return Optional.ofNullable(activeScope);
    }

    /**
     * The same principal with a different active scope. The assignments do not
     * change in the process; only which of them apply unqualified does.
     *
     * <p>Switching alone is not enough: for the running session to notice, the
     * route goes through {@code ActiveScopeService}.
     */
    public IdentityUserDetails withActiveScope(@Nullable Scope newActiveScope) {
        return new IdentityUserDetails(this, newActiveScope);
    }

    /** The authorities as granted, without resolving the active scope. */
    public Collection<GrantedAuthority> grantedAuthorities() {
        return grantedAuthorities;
    }

    /**
     * The qualified authorities and, for the active scope, the same ones once
     * more without the qualification.
     */
    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        if (activeScope == null) {
            return grantedAuthorities;
        }
        String suffix = Scope.AUTHORITY_SEPARATOR + activeScope.toString();
        Set<GrantedAuthority> effective = new LinkedHashSet<>(grantedAuthorities);
        for (GrantedAuthority authority : grantedAuthorities) {
            // By contract getAuthority() may return null (for authorities
            // that cannot be expressed as a string, say). We have none of
            // those, and they are left untouched.
            String name = authority.getAuthority();
            if (name != null && name.endsWith(suffix)) {
                effective.add(new SimpleGrantedAuthority(name.substring(0, name.length() - suffix.length())));
            }
        }
        return new ArrayList<>(effective);
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
