package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.RoleAssignment;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.ScopedRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Connects the accounts of this building block to Spring Security.
 *
 * <p>Every role yields two kinds of authority: {@code ROLE_<code>} for
 * {@code hasRole(...)} and {@code @RolesAllowed}, plus the fine-grained
 * permissions verbatim for {@code hasAuthority(...)}.
 *
 * <p>An assignment with a scope carries both kinds in qualified form
 * ({@code ROLE_ADMIN@club:17}). An <strong>active</strong> scope is not set
 * here yet: only the application knows which one that should be at sign-in
 * time, and it sets it through {@code ActiveScopeService}. Until then the
 * global roles apply, which is the safe default.
 */
class IdentityUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userRepository;

    IdentityUserDetailsService(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        UserAccount user = userRepository.findByEmail(UserAccount.normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + username));
        return toUserDetails(user);
    }

    /**
     * The principal for an account that can sign in.
     *
     * <p>Managed accounts (without an email address) are not found by the
     * lookup in the first place. Asking for a claimed account is the second
     * line of defence: an open invitation has an address but no way in yet
     * and must never reach Spring Security. A claimed account without a
     * password -- one that signs in through an external provider -- is handed
     * out: remember-me, passkeys and the session refresh all load through
     * here. The password sign-in turns it down on its own (see
     * {@code IdentityBeans}), and a {@code DaoAuthenticationProvider} fails
     * on the missing hash anyway.
     *
     * @throws UsernameNotFoundException if the account cannot sign in at all
     */
    static IdentityUserDetails toUserDetails(UserAccount user) {
        String email = user.getEmail();
        if (email == null || !user.isClaimed()) {
            throw new UsernameNotFoundException("No credentials for account " + user.getId());
        }
        Long userId = user.getId();
        if (userId == null) {
            throw new UsernameNotFoundException("Account without ID for " + email);
        }
        return new IdentityUserDetails(userId, email, user.getDisplayName(), user.getPasswordHash(),
                user.isEnabled(), user.isMustChangePassword(), toAuthorities(user));
    }

    private static Set<GrantedAuthority> toAuthorities(UserAccount user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (RoleAssignment assignment : user.getRoleAssignments()) {
            ScopedRole scopedRole = new ScopedRole(assignment.getRole().getCode(), assignment.getScope());
            authorities.add(new SimpleGrantedAuthority(scopedRole.authorityName()));
            for (String authority : assignment.getRole().getAuthorities()) {
                authorities.add(new SimpleGrantedAuthority(scopedRole.qualify(authority)));
            }
        }
        return authorities;
    }
}
