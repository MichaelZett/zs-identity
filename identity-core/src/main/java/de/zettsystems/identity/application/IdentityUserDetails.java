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
 * Security-Prinzipal des Bausteins.
 *
 * <p>Neben dem Anmeldenamen (E-Mail) trägt er die stabile Konto-ID. Anwendungen
 * verknüpfen ihre Fachobjekte über diese ID — die Adresse kann sich ändern,
 * die ID nicht — und holen sie sich aus dem {@code SecurityContext}, ohne
 * dafür die Datenbank zu fragen.
 *
 * <h2>Rollen mit Geltungsbereich</h2>
 *
 * <p>Die Berechtigungen liegen in <strong>qualifizierter</strong> Gestalt vor:
 * global als {@code ROLE_ADMIN}, bereichsgebunden als
 * {@code ROLE_ADMIN@club:17}. Dazu kommt ein <strong>aktiver Bereich</strong>
 * ({@link #activeScope()}); dessen Berechtigungen erscheinen
 * <em>zusätzlich</em> unqualifiziert.
 *
 * <p>Der Grund ist die Lesbarkeit der Prüfungen. Mit aktivem Verein 17 gilt:
 *
 * <pre>{@code
 * @RolesAllowed("ADMIN")                          // im aktiven Verein
 * hasAuthority("ROLE_ADMIN@club:4")               // gezielt anderswo, etwa bei einem Deep-Link
 * }</pre>
 *
 * <p>Ohne den aktiven Bereich müsste jede Prüfung in jeder Anwendung den
 * Bereich selbst zusammensetzen — und eine vergessene Qualifizierung prüfte
 * dann nicht etwa zu streng, sondern <strong>gar nichts</strong>. Ohne die
 * qualifizierte Gestalt wiederum wäre ein Verweis in einen anderen Mandanten
 * nur nach einem Wechsel prüfbar.
 */
public final class IdentityUserDetails implements UserDetails {

    // Liegt in der HTTP-Session; ohne feste UID bricht jeder Klassenwechsel
    // eine noch offene Sitzung.
    @Serial
    private static final long serialVersionUID = 2L;

    private final Long userId;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final boolean enabled;
    private final boolean mustChangePassword;
    /** Qualifiziert: global ohne Zusatz, bereichsgebunden mit {@code @club:17}. */
    private final List<GrantedAuthority> grantedAuthorities;
    private final @Nullable Scope activeScope;

    /**
     * Öffentlich, damit einbindende Anwendungen den Prinzipal in Tests bauen
     * können; im Betrieb erzeugt ihn allein der {@code IdentityUserDetailsService}.
     *
     * <p>Ein Prinzipal entsteht immer <strong>ohne</strong> aktiven Bereich —
     * welcher es sein soll, entscheidet die Anwendung erst später
     * ({@link #withActiveScope(Scope)}) und nicht schon beim Anmelden.
     *
     * @param authorities die Berechtigungen in qualifizierter Gestalt:
     *                    {@code ROLE_USER} global, {@code ROLE_ADMIN@club:17}
     *                    mit Bereich
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

    /** Kopie mit anderem aktivem Bereich — siehe {@link #withActiveScope(Scope)}. */
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

    /** Der Bereich, in dem die Person gerade arbeitet — leer, wenn keiner gewählt ist. */
    public Optional<Scope> activeScope() {
        return Optional.ofNullable(activeScope);
    }

    /**
     * Derselbe Prinzipal mit einem anderen aktiven Bereich. Die Zuweisungen
     * ändern sich dabei nicht — nur, welche von ihnen unqualifiziert gelten.
     *
     * <p>Wechseln allein genügt nicht: Damit die laufende Sitzung es merkt,
     * geht der Weg über {@code ActiveScopeService}.
     */
    public IdentityUserDetails withActiveScope(@Nullable Scope newActiveScope) {
        return new IdentityUserDetails(this, newActiveScope);
    }

    /** Die Berechtigungen, wie sie vergeben wurden — ohne die Auflösung des aktiven Bereichs. */
    public Collection<GrantedAuthority> grantedAuthorities() {
        return grantedAuthorities;
    }

    /**
     * Die qualifizierten Berechtigungen und, für den aktiven Bereich,
     * dieselben noch einmal ohne Qualifizierung.
     */
    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        if (activeScope == null) {
            return grantedAuthorities;
        }
        String suffix = Scope.AUTHORITY_SEPARATOR + activeScope.toString();
        Set<GrantedAuthority> effective = new LinkedHashSet<>(grantedAuthorities);
        for (GrantedAuthority authority : grantedAuthorities) {
            // getAuthority() darf laut Vertrag null liefern (etwa bei
            // Berechtigungen, die sich nicht als Zeichenkette ausdrücken
            // lassen) — solche kennen wir nicht, sie bleiben unangetastet.
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
