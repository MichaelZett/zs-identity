package de.zettsystems.identity.ui;

import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.UserAccountService;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Zeichnet Passwortwechsel auf; alles andere ist für die Ansichtstests ohne Belang. */
class FakeUserAccountService implements UserAccountService {

    private static final UserAccountDto ACCOUNT = new UserAccountDto(1L, "anna@example.com",
            AccountName.of("Anna", "Beispiel"), true, true, Instant.parse("2026-09-01T10:00:00Z"),
            Set.of("USER"), true);

    final List<Long> changedUserIds = new ArrayList<>();
    final List<String> newPasswords = new ArrayList<>();
    @Nullable IdentityException failure;

    @Override
    public void changePassword(Long userId, String newRawPassword) {
        if (failure != null) {
            throw failure;
        }
        changedUserIds.add(userId);
        newPasswords.add(newRawPassword);
    }

    @Override
    public UserAccountDto requirePasswordChange(Long userId) {
        return ACCOUNT;
    }

    @Override
    public List<UserAccountDto> findAllById(Collection<Long> ids) {
        return List.of(ACCOUNT);
    }

    @Override
    public void deleteAccount(Long userId) {
        // Die Ansichten löschen nichts.
    }

    @Override
    public Optional<UserAccountDto> findById(Long id) {
        return Optional.of(ACCOUNT);
    }

    @Override
    public Optional<UserAccountDto> findByEmail(String email) {
        return Optional.of(ACCOUNT);
    }

    @Override
    public List<UserAccountDto> findAll() {
        return List.of(ACCOUNT);
    }

    @Override
    public UserAccountDto createAccount(String email, String rawPassword, AccountName name,
                                        boolean alreadyVerified) {
        return ACCOUNT;
    }

    @Override
    public UserAccountDto createManagedAccount(AccountName name) {
        return ACCOUNT;
    }

    @Override
    public UserAccountDto rename(Long userId, AccountName newName) {
        return ACCOUNT;
    }

    @Override
    public UserAccountDto grantRole(Long userId, String roleCode) {
        return ACCOUNT;
    }

    @Override
    public UserAccountDto revokeRole(Long userId, String roleCode) {
        return ACCOUNT;
    }

    @Override
    public UserAccountDto setEnabled(Long userId, boolean enabled) {
        return ACCOUNT;
    }
}
