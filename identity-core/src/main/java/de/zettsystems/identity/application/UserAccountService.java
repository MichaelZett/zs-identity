package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.Scope;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Lesen und Verwalten von Benutzerkonten — die Schnittstelle, über die eine
 * Anwendung mit dem Identity-Baustein spricht.
 *
 * <p>Gibt ausschließlich {@link UserAccountDto} heraus. Die JPA-Entity bleibt
 * im Baustein, Anwendungen verknüpfen ihre Fachobjekte über die {@code id}.
 */
public interface UserAccountService {

    Optional<UserAccountDto> findById(Long id);

    Optional<UserAccountDto> findByEmail(String email);

    List<UserAccountDto> findAll();

    /**
     * Mehrere Konten in einer Abfrage — für Listen, die je Zeile ein Konto
     * zeigen (Mitgliederlisten), statt eines {@link #findById} je Zeile.
     * Unbekannte Kennungen fehlen im Ergebnis; die Reihenfolge ist offen.
     */
    List<UserAccountDto> findAllById(Collection<Long> ids);

    /** Legt ein Konto ohne Selbstregistrierung an, etwa durch eine Administration. */
    UserAccountDto createAccount(String email, String rawPassword, AccountName name, boolean alreadyVerified);

    /** Klarnamen-Variante von {@link #createAccount(String, String, AccountName, boolean)}. */
    default UserAccountDto createAccount(String email, String rawPassword, String firstName, String lastName,
                                         boolean alreadyVerified) {
        return createAccount(email, rawPassword, AccountName.of(firstName, lastName), alreadyVerified);
    }

    /**
     * Legt ein verwaltetes Konto an: ohne E-Mail, ohne Passwort, ohne
     * Anmeldemöglichkeit. Gedacht für Personen, die eine Anwendung führt,
     * ohne dass sie sich selbst registrieren — die Anwendung verknüpft wie
     * immer über die {@code id}.
     */
    UserAccountDto createManagedAccount(AccountName name);

    /** Klarnamen-Variante von {@link #createManagedAccount(AccountName)}. */
    default UserAccountDto createManagedAccount(String firstName, String lastName) {
        return createManagedAccount(AccountName.of(firstName, lastName));
    }

    /** Ändert den Namen eines Kontos. */
    UserAccountDto rename(Long userId, AccountName newName);

    /**
     * Legt die Sprache fest, in der das Konto angesprochen wird — vor allem in
     * den Mails dieses Bausteins, die ohne Browser entstehen und deshalb keine
     * Sitzung fragen können. {@code null} nimmt die Wahl zurück; dann gilt
     * wieder {@code zs.identity.locale}.
     *
     * <p>Der Baustein bringt keine Ansicht dafür mit: Wo die Sprachwahl
     * hingehört (Kontoeinstellungen, Kopfzeile, Anmeldeformular), entscheidet
     * die Anwendung. Sie ruft von dort aus diese Methode auf — und übergibt
     * bei der Registrierung dieselbe Sprache an
     * {@link RegistrationService#register(String, String, AccountName, Locale)}.
     *
     * <p>Bewusst eine {@code default}-Methode, die scheitert, statt einer
     * abstrakten: Ein eigener {@code UserAccountService} einer Anwendung soll
     * durch diese Ergänzung nicht die Übersetzung verlieren. Still nichts zu
     * tun wäre schlimmer als der Fehler — die Sprachwahl bliebe wirkungslos,
     * und niemand merkte es.
     *
     * @throws IdentityException wenn es das Konto nicht gibt
     * @throws UnsupportedOperationException solange ein eigener Dienst sie nicht überschreibt
     */
    default UserAccountDto changeLocale(Long userId, @Nullable Locale locale) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not implement changeLocale(..)");
    }

    /** Vergibt die Rolle <strong>global</strong> — sie gilt dann überall. */
    UserAccountDto grantRole(Long userId, String roleCode);

    /** Nimmt die globale Rolle zurück; bereichsgebundene bleiben unberührt. */
    UserAccountDto revokeRole(Long userId, String roleCode);

    /**
     * Vergibt die Rolle für einen {@link Scope} — „Admin von Verein 17".
     *
     * <p>Was ein Bereich bedeutet, weiß allein die Anwendung; der Baustein
     * speichert und vergleicht ihn nur. Wer eine Rolle vergeben darf,
     * entscheidet ebenfalls die Anwendung — hier wird nichts geprüft.
     *
     * <p>Bewusst eine {@code default}-Methode, die scheitert, statt einer
     * abstrakten: Ein eigener {@code UserAccountService} einer Anwendung soll
     * durch diese Ergänzung nicht die Übersetzung verlieren.
     *
     * @throws IdentityException wenn Konto oder Rolle nicht existieren
     * @throws UnsupportedOperationException solange ein eigener Dienst sie nicht überschreibt
     */
    default UserAccountDto grantRole(Long userId, String roleCode, Scope scope) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not implement grantRole(.., Scope)");
    }

    /**
     * Nimmt die Rolle in genau diesem Bereich zurück. Eine <em>globale</em>
     * Rolle desselben Codes bleibt bestehen — sie ist eine andere Zuweisung,
     * und sie hier stillschweigend mit zu entziehen wäre eine Überraschung.
     *
     * @throws UnsupportedOperationException solange ein eigener Dienst sie nicht überschreibt
     */
    default UserAccountDto revokeRole(Long userId, String roleCode, Scope scope) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not implement revokeRole(.., Scope)");
    }

    /**
     * Die Rollencodes, die für dieses Konto in diesem Bereich gelten — die
     * globalen eingeschlossen, denn sie gelten überall.
     *
     * <p>Leer, wenn es das Konto nicht gibt: Die Frage „was darf diese Person
     * hier" mit einer Ausnahme zu beantworten, hilft keinem Aufrufer.
     */
    default Set<String> rolesOf(Long userId, Scope scope) {
        return findById(userId).map(user -> user.rolesIn(scope)).orElseGet(Set::of);
    }

    /** „Alle Vereine dieser Person": die Bereiche dieser Art, in denen sie eine Rolle hat. */
    default Set<Scope> scopesOf(Long userId, String scopeType) {
        return findById(userId).map(user -> user.scopesOf(scopeType)).orElseGet(Set::of);
    }

    UserAccountDto setEnabled(Long userId, boolean enabled);

    /**
     * Setzt ein neues Passwort. Löscht zugleich ein gesetztes
     * {@code mustChangePassword} und frischt die laufende Sitzung auf, wenn es
     * das angemeldete Konto ist.
     */
    void changePassword(Long userId, String newRawPassword);

    /**
     * Verlangt vom Konto, sein Passwort bei der nächsten Anmeldung zu ändern
     * — für Startpasswörter aus einer Verwaltung oder von Hand zurückgesetzte
     * Konten. Mit {@code identity-vaadin} führt danach jede Route auf die
     * Passwort-ändern-Ansicht, bis das Passwort gewechselt ist.
     */
    UserAccountDto requirePasswordChange(Long userId);

    /**
     * Löscht ein Konto endgültig — samt Rollenzuordnungen und Tokens. Die
     * Anwendung räumt ihre eigenen Daten zur Kennung <strong>vorher</strong>
     * selbst auf; der Baustein kennt sie nicht. Gedacht für die Verwaltung,
     * etwa für versehentlich doppelt angelegte Konten.
     *
     * @throws IdentityException wenn es das Konto nicht gibt
     */
    void deleteAccount(Long userId);
}
