package de.zettsystems.identity.ui;

import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.UserAccountDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Testdoppel statt Mockito-Attrappe: Die Ansichtstests wollen sehen, was
 * tatsächlich beim Dienst ankommt, und steuern, wann er scheitert.
 */
class FakeRegistrationService implements RegistrationService {

    private static final UserAccountDto ACCOUNT = new UserAccountDto(1L, "anna@example.com",
            AccountName.of("Anna", "Beispiel"), false, true,
            Instant.parse("2026-09-01T10:00:00Z"), Set.of("USER"));

    boolean selfRegistrationEnabled = true;
    boolean emailVerificationRequired = true;

    /** Wenn gesetzt, scheitert der nächste Aufruf damit. */
    IdentityException failure;

    final List<String> registeredEmails = new ArrayList<>();
    final List<AccountName> registeredNames = new ArrayList<>();
    final List<String> confirmedTokens = new ArrayList<>();
    final List<String> resendRequests = new ArrayList<>();

    @Override
    public boolean isSelfRegistrationEnabled() {
        return selfRegistrationEnabled;
    }

    @Override
    public boolean isEmailVerificationRequired() {
        return emailVerificationRequired;
    }

    @Override
    public UserAccountDto register(String email, String rawPassword, AccountName name) {
        throwIfConfigured();
        registeredEmails.add(email);
        registeredNames.add(name);
        return ACCOUNT;
    }

    @Override
    public UserAccountDto confirmEmail(String token) {
        throwIfConfigured();
        confirmedTokens.add(token);
        return ACCOUNT;
    }

    @Override
    public void resendVerification(String email) {
        throwIfConfigured();
        resendRequests.add(email);
    }

    private void throwIfConfigured() {
        if (failure != null) {
            throw failure;
        }
    }
}
