package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Verbindet die Konten dieses Bausteins mit Spring Security.
 *
 * <p>Jede Rolle liefert zwei Arten von Authorities: {@code ROLE_<code>} für
 * {@code hasRole(...)} und {@code @RolesAllowed}, dazu die feingranularen
 * Berechtigungen im Klartext für {@code hasAuthority(...)}.
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

        // Verwaltete Konten (ohne E-Mail) findet die Suche gar nicht erst.
        // Die Prüfung auf den Hash ist die zweite Verteidigungslinie: Ein
        // Konto ohne Passwort darf Spring Security nie erreichen.
        String email = user.getEmail();
        String passwordHash = user.getPasswordHash();
        if (email == null || passwordHash == null) {
            throw new UsernameNotFoundException("No credentials for " + username);
        }
        Long userId = user.getId();
        if (userId == null) {
            throw new UsernameNotFoundException("Account without ID for " + username);
        }
        return new IdentityUserDetails(userId, email, user.getDisplayName(), passwordHash, user.isEnabled(),
                toAuthorities(user));
    }

    private static Set<GrantedAuthority> toAuthorities(UserAccount user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Role role : user.getRoles()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
            for (String authority : role.getAuthorities()) {
                authorities.add(new SimpleGrantedAuthority(authority));
            }
        }
        return authorities;
    }
}
