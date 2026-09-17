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
 * Verbindet die Konten dieses Bausteins mit Spring Security.
 *
 * <p>Jede Rolle liefert zwei Arten von Authorities: {@code ROLE_<code>} für
 * {@code hasRole(...)} und {@code @RolesAllowed}, dazu die feingranularen
 * Berechtigungen im Klartext für {@code hasAuthority(...)}.
 *
 * <p>Eine Zuweisung mit Geltungsbereich trägt beide Arten qualifiziert
 * ({@code ROLE_ADMIN@club:17}). Ein <strong>aktiver</strong> Bereich wird hier
 * noch nicht gesetzt: Welcher das beim Anmelden sein soll, weiß nur die
 * Anwendung — sie setzt ihn über {@code ActiveScopeService}. Bis dahin gelten
 * die globalen Rollen, und das ist die sichere Vorgabe.
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
                user.isMustChangePassword(), toAuthorities(user));
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
