package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.Scope;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ein Benutzerkonto. Die E-Mail-Adresse ist zugleich der Anmeldename und wird
 * immer klein geschrieben gespeichert, damit sich niemand mit derselben Adresse
 * in anderer Schreibweise ein zweites Konto anlegt.
 *
 * <p>Der Name liegt in zwei Gestalten vor (siehe {@link AccountName}): Der
 * Anzeigename ist immer gesetzt; Vor- und Nachname nur bei Anwendungen, die
 * Klarnamen führen.
 *
 * <p>Die <strong>Sprache</strong> ist optional: {@code null} heißt „keine
 * eigene Wahl" — dann gilt {@code zs.identity.locale}. Gebraucht wird sie vor
 * allem für Mails, die ohne Browser entstehen und deshalb keine Sitzung fragen
 * können.
 *
 * <p>Daneben gibt es <strong>verwaltete Konten</strong> ({@link #managed}):
 * Eine Anwendung legt sie für Personen an, die sich (noch) nicht selbst
 * registrieren — ohne E-Mail und ohne Passwort. Anmelden können sie sich
 * nicht: Der Anmeldeweg sucht per E-Mail-Adresse, und ohne Passwort-Hash gibt
 * der {@code IdentityUserDetailsService} sie zusätzlich nie heraus.
 */
@Entity
@Table(name = "auth_user")
@Getter
public class UserAccount extends AbstractAuthEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_user_seq")
    @SequenceGenerator(name = "auth_user_seq", sequenceName = "auth_user_seq", allocationSize = 20)
    private @Nullable Long id;

    // Nullable: verwaltete Konten haben weder Adresse noch Passwort.
    @Column(unique = true, length = 320)
    private @Nullable String email;

    @Column(name = "password_hash", length = 128)
    private @Nullable String passwordHash;

    @Column(name = "display_name", nullable = false, length = 260)
    @SuppressWarnings("NullAway.Init")
    private String displayName;

    // Seit V1_2 nullable: Anwendungen mit frei gewähltem Anzeigenamen führen
    // keine Klarnamen.
    @Column(name = "first_name", length = 128)
    private @Nullable String firstName;

    @Column(name = "last_name", length = 128)
    private @Nullable String lastName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    @SuppressWarnings("NullAway.Init")
    private Instant createdAt;

    @Column(name = "last_login_at")
    private @Nullable Instant lastLoginAt;

    // Seit V1_4. Nullbar heißt "keine eigene Wahl" — nicht "Englisch".
    @Column(length = 35)
    @Convert(converter = LocaleAttributeConverter.class)
    private @Nullable Locale locale;

    /**
     * Das Konto muss sein Passwort ändern, bevor es die Anwendung benutzt —
     * etwa nach einem Startpasswort aus einer Verwaltung. Gelöscht wird das
     * Flag an derselben Stelle, an der das Passwort neu gesetzt wird
     * ({@link #changePassword}), damit es nach einem Wechsel nie stehen
     * bleiben kann — auch nicht auf dem Weg über „Passwort vergessen".
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    // LAZY ist Pflicht: Der EAGER-Default von JPA lädt bei jeder Benutzerliste
    // die Rollen einzeln nach (N+1).
    //
    // orphanRemoval: Eine Zuweisung ohne Konto ist nichts — sie verschwindet
    // mit dem Entzug der Rolle, nicht erst mit einem eigenen Löschaufruf.
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<RoleAssignment> roleAssignments = new LinkedHashSet<>();

    protected UserAccount() {
        // for JPA
    }

    /**
     * Legt ein Konto an. Der Konstruktor nimmt nur Pflichtangaben; ob das Konto
     * sofort nutzbar ist, entscheidet die Registrierung über
     * {@link #activateAfterEmailVerification()}.
     */
    public UserAccount(String email, String passwordHash, AccountName name, Instant createdAt) {
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        // Bewusst nicht ueber applyName(): Sonar (java:S2637) sieht die
        // Initialisierung des @NullMarked-Felds sonst nicht im Konstruktor.
        Objects.requireNonNull(name, "name");
        this.displayName = name.displayName();
        this.firstName = name.firstName();
        this.lastName = name.lastName();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.enabled = false;
        this.emailVerified = false;
    }

    /**
     * Legt ein verwaltetes Konto an: ohne E-Mail, ohne Passwort, sofort
     * aktiv — aktiv heißt hier nur „gehört dazu", nicht „kann sich anmelden";
     * ohne Adresse und Hash gibt es keinen Anmeldeweg.
     */
    public static UserAccount managed(AccountName name, Instant createdAt) {
        UserAccount account = new UserAccount();
        account.applyName(name);
        account.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        account.enabled = true;
        account.emailVerified = false;
        return account;
    }

    /**
     * Legt die Sprache des Kontos fest, in der es angesprochen werden möchte.
     * {@code null} nimmt die Wahl zurück; danach gilt wieder die Sprache der
     * Anwendung.
     *
     * <p>{@link Locale#ROOT} ist keine Sprache, sondern die Abwesenheit einer
     * — es wird deshalb wie {@code null} behandelt, statt später als leeres
     * Sprachkennzeichen in der Datenbank zu stehen.
     */
    public void changeLocale(@Nullable Locale newLocale) {
        this.locale = newLocale == null || "und".equals(newLocale.toLanguageTag()) ? null : newLocale;
    }

    /** Verwaltet = von einer Anwendung angelegt, ohne eigene Anmeldedaten. */
    public boolean isManaged() {
        return email == null;
    }

    public static String normalizeEmail(String email) {
        // Locale.ROOT: Ohne das würde auf einem System mit türkischem Locale
        // aus "I" ein punktloses "ı" und die Adresse wäre eine andere.
        return Objects.requireNonNull(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    /** Der Name in beiden Gestalten als unveränderlicher Wert. */
    public AccountName getName() {
        return new AccountName(displayName, firstName, lastName);
    }

    /**
     * Die <strong>globalen</strong> Rollen — die, die überall gelten.
     *
     * <p>Vor V1_5 gab es nur solche; für Anwendungen ohne Geltungsbereiche ist
     * das also unverändert die ganze Antwort. Wer auch die bereichsgebundenen
     * braucht, nimmt {@link #getRoleAssignments()}.
     */
    public Set<Role> getRoles() {
        return rolesIn(null);
    }

    /** Alle Zuweisungen, global wie bereichsgebunden. */
    public Set<RoleAssignment> getRoleAssignments() {
        return Collections.unmodifiableSet(roleAssignments);
    }

    /**
     * Die Rollen in genau diesem Bereich; {@code null} fragt nach den globalen.
     *
     * <p>Unveränderlich wie {@link #getRoleAssignments()} — und aus demselben
     * Grund: Eine stille Kopie, die Änderungen schluckt, wäre schlimmer als
     * eine Ausnahme. Wer etwas ändern will, nimmt {@link #grant} und
     * {@link #revoke}.
     */
    public Set<Role> rolesIn(@Nullable Scope scope) {
        Set<Role> roles = roleAssignments.stream()
                .filter(assignment -> assignment.appliesTo(scope))
                .map(RoleAssignment::getRole)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(roles);
    }

    /** Die Bereiche dieser Art, in denen das Konto überhaupt eine Rolle hat. */
    public Set<Scope> scopesOf(String type) {
        Objects.requireNonNull(type, "type");
        Set<Scope> scopes = roleAssignments.stream()
                .map(RoleAssignment::getScope)
                .filter(scope -> scope != null && scope.type().equals(type))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return Collections.unmodifiableSet(scopes);
    }

    /** Schaltet das Konto nach bestätigter E-Mail-Adresse frei. */
    public void activateAfterEmailVerification() {
        this.emailVerified = true;
        this.enabled = true;
    }

    /** Schaltet das Konto ohne E-Mail-Bestätigung frei (Verifikation abgeschaltet). */
    public void activateWithoutVerification() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    /**
     * Trägt die Adresse an einem verwalteten Konto nach — der erste Schritt,
     * wenn die echte Person es beansprucht. Das Konto bleibt bis zum Einlösen
     * der Einladung ohne Passwort und damit ohne Anmeldeweg; genau diesen
     * Zwischenzustand beschreibt V1_1 („E-Mail gesetzt, Passwort noch nicht").
     *
     * @throws IllegalStateException wenn schon eine Adresse dranhängt — sie zu
     *                               überschreiben hieße, ein fremdes Konto zu
     *                               übernehmen
     */
    public void assignEmail(String newEmail) {
        if (this.email != null) {
            throw new IllegalStateException("Account " + id + " already has an email address");
        }
        this.email = normalizeEmail(newEmail);
    }

    /**
     * Löst die Einladung ein: erstes Passwort, Adresse gilt als bestätigt,
     * Konto ist nutzbar. Der Link ging an genau diese Adresse — eine zweite
     * Bestätigungsmail wäre nur ein Umweg.
     */
    public void claimWithPassword(String newPasswordHash) {
        changePassword(newPasswordHash);
        activateAfterEmailVerification();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        this.mustChangePassword = false;
    }

    /** Verlangt einen Passwortwechsel bei der nächsten Anmeldung. */
    public void requirePasswordChange() {
        this.mustChangePassword = true;
    }

    public void rename(AccountName newName) {
        applyName(newName);
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = Objects.requireNonNull(at, "at");
    }

    /** Vergibt die Rolle global — sie gilt dann überall. */
    public void grant(Role role) {
        grant(role, null);
    }

    /**
     * Vergibt die Rolle für einen Bereich ({@code null} = global). Zweimal
     * dieselbe Zuweisung gibt es nicht; der Aufruf ist dann wirkungslos.
     */
    public void grant(Role role, @Nullable Scope scope) {
        Objects.requireNonNull(role, "role");
        if (!hasRole(role, scope)) {
            this.roleAssignments.add(new RoleAssignment(this, role, scope));
        }
    }

    public void revoke(Role role) {
        revoke(role, null);
    }

    /**
     * Nimmt die Rolle in genau diesem Bereich zurück. Eine globale Rolle
     * bleibt dabei unangetastet — sie ist eine andere Zuweisung.
     */
    public void revoke(Role role, @Nullable Scope scope) {
        Objects.requireNonNull(role, "role");
        this.roleAssignments.removeIf(
                assignment -> assignment.getRole().equals(role) && assignment.appliesTo(scope));
    }

    public boolean hasRole(Role role, @Nullable Scope scope) {
        return roleAssignments.stream()
                .anyMatch(assignment -> assignment.getRole().equals(role) && assignment.appliesTo(scope));
    }

    /**
     * Setzt die <strong>globalen</strong> Rollen neu. Bereichsgebundene
     * Zuweisungen bleiben stehen: Sie gehören zu einem Mandanten, über den
     * dieser Aufruf nichts aussagt.
     */
    public void replaceRoles(Set<Role> newRoles) {
        Objects.requireNonNull(newRoles, "newRoles");
        this.roleAssignments.removeIf(RoleAssignment::isGlobal);
        newRoles.forEach(role -> grant(role, null));
    }

    private void applyName(AccountName name) {
        Objects.requireNonNull(name, "name");
        this.displayName = name.displayName();
        this.firstName = name.firstName();
        this.lastName = name.lastName();
    }
}
