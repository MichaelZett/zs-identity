package de.zettsystems.identity.ui;

import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A test double rather than a Mockito stand-in: the view tests want to see what
 * actually arrives at the service, and to control when it fails.
 */
class FakeRegistrationService implements RegistrationService {

    private static final UserAccountDto ACCOUNT = new UserAccountDto(1L, "anna@example.com",
            AccountName.of("Anna", "Beispiel"), false, true,
            Instant.parse("2026-09-01T10:00:00Z"), Set.of("USER"));

    boolean selfRegistrationEnabled = true;
    boolean emailVerificationRequired = true;

    /** When set, the next call fails with it. */
    IdentityException failure;

    final List<String> registeredEmails = new ArrayList<>();
    final List<AccountName> registeredNames = new ArrayList<>();
    final List<@Nullable Locale> registeredLocales = new ArrayList<>();
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
        return register(email, rawPassword, name, null);
    }

    @Override
    public UserAccountDto register(String email, String rawPassword, AccountName name,
                                   @Nullable Locale locale) {
        throwIfConfigured();
        registeredEmails.add(email);
        registeredNames.add(name);
        registeredLocales.add(locale);
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
