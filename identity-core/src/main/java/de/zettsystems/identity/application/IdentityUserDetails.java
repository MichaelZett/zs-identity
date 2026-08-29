package de.zettsystems.identity.application;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Security-Prinzipal des Bausteins.
 *
 * <p>Neben dem Anmeldenamen (E-Mail) trägt er die stabile Konto-ID. Anwendungen
 * verknüpfen ihre Fachobjekte über diese ID — die Adresse kann sich ändern,
 * die ID nicht — und holen sie sich aus dem {@code SecurityContext}, ohne
 * dafür die Datenbank zu fragen.
 */
public final class IdentityUserDetails implements UserDetails {

    // Liegt in der HTTP-Session; ohne feste UID bricht jeder Klassenwechsel
    // eine noch offene Sitzung.
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final List<GrantedAuthority> authorities;

    /**
     * Öffentlich, damit einbindende Anwendungen den Prinzipal in Tests bauen
     * können; im Betrieb erzeugt ihn allein der {@code IdentityUserDetailsService}.
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
        this.authorities = List.copyOf(authorities);
    }

    /** Stabile Kennung des Kontos. */
    public Long userId() {
        return userId;
    }

    /** Anzeigename zum Zeitpunkt der Anmeldung. */
    public String displayName() {
        return displayName;
    }

    /**
     * Das Konto muss sein Passwort ändern, bevor es die Anwendung benutzt.
     * Steht in der Sitzung, damit die Erzwingung nicht bei jedem Seitenaufruf
     * die Datenbank fragt; nach dem Wechsel frischt der Dienst die Sitzung auf.
     */
    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return authorities;
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
