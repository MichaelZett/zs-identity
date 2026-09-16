package de.zettsystems.identity.ui;

import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.InvitationService;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Siehe {@link FakeRegistrationService}. */
class FakeInvitationService implements InvitationService {

    /** Wenn gesetzt, scheitert der nächste {@link #claim} damit. */
    @Nullable
    IdentityException failure;

    /** Wenn leer, kennt der Dienst das Token nicht — die Ansicht zeigt dann die Sackgasse. */
    @Nullable
    UserAccountDto invitee = new UserAccountDto(7L, "ida@example.com",
            AccountName.of("Ida", "Beispiel"), true, false, Instant.EPOCH, Set.of("USER"));

    final List<String> lookedUpTokens = new ArrayList<>();
    final List<String> usedTokens = new ArrayList<>();
    final List<String> newPasswords = new ArrayList<>();

    @Override
    public UserAccountDto inviteToClaim(Long userId, String email) {
        throw new UnsupportedOperationException("not used by the view");
    }

    @Override
    public void resendInvitation(Long userId) {
        throw new UnsupportedOperationException("not used by the view");
    }

    @Override
    public UserAccountDto inviteNewAccount(String email, AccountName name) {
        throw new UnsupportedOperationException("not used by the view");
    }

    @Override
    public Optional<UserAccountDto> findInvitee(String token) {
        lookedUpTokens.add(token);
        return Optional.ofNullable(invitee);
    }

    @Override
    public UserAccountDto claim(String token, String rawPassword) {
        if (failure != null) {
            throw failure;
        }
        usedTokens.add(token);
        newPasswords.add(rawPassword);
        return Optional.ofNullable(invitee).orElseThrow();
    }
}
