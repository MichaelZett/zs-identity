package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.AccountName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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

/**
 * Ein Benutzerkonto. Die E-Mail-Adresse ist zugleich der Anmeldename und wird
 * immer klein geschrieben gespeichert, damit sich niemand mit derselben Adresse
 * in anderer Schreibweise ein zweites Konto anlegt.
 *
 * <p>Der Name liegt in zwei Gestalten vor (siehe {@link AccountName}): Der
 * Anzeigename ist immer gesetzt; Vor- und Nachname nur bei Anwendungen, die
 * Klarnamen führen.
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
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "auth_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

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

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
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

    public void grant(Role role) {
        this.roles.add(Objects.requireNonNull(role, "role"));
    }

    public void revoke(Role role) {
        this.roles.remove(Objects.requireNonNull(role, "role"));
    }

    public void replaceRoles(Set<Role> newRoles) {
        Objects.requireNonNull(newRoles, "newRoles");
        this.roles.clear();
        this.roles.addAll(newRoles);
    }

    private void applyName(AccountName name) {
        Objects.requireNonNull(name, "name");
        this.displayName = name.displayName();
        this.firstName = name.firstName();
        this.lastName = name.lastName();
    }
}
