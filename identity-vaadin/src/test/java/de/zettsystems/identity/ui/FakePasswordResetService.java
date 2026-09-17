package de.zettsystems.identity.ui;

import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.PasswordResetService;

import java.util.ArrayList;
import java.util.List;

/** See {@link FakeRegistrationService}. */
class FakePasswordResetService implements PasswordResetService {

    /** When set, the next call fails with it. */
    IdentityException failure;

    final List<String> resetRequests = new ArrayList<>();
    final List<String> usedTokens = new ArrayList<>();
    final List<String> newPasswords = new ArrayList<>();

    @Override
    public void requestReset(String email) {
        throwIfConfigured();
        resetRequests.add(email);
    }

    @Override
    public void resetPassword(String token, String newRawPassword) {
        throwIfConfigured();
        usedTokens.add(token);
        newPasswords.add(newRawPassword);
    }

    private void throwIfConfigured() {
        if (failure != null) {
            throw failure;
        }
    }
}
