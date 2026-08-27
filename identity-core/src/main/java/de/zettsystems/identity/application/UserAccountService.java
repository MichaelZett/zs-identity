package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.UserAccountDto;

import java.util.List;
import java.util.Optional;

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

    UserAccountDto grantRole(Long userId, String roleCode);

    UserAccountDto revokeRole(Long userId, String roleCode);

    UserAccountDto setEnabled(Long userId, boolean enabled);

    void changePassword(Long userId, String newRawPassword);
}
