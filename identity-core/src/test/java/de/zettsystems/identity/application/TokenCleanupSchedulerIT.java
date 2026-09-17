package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthToken;
import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.AuthTokenType;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.values.AccountName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The cleanup run: redeemed and long-expired tokens go, valid and recently expired ones stay. */
class TokenCleanupSchedulerIT extends AbstractIdentityIntegrationTest {

    @Autowired
    private TokenCleanupScheduler scheduler;
    @Autowired
    private AuthTokenRepository tokens;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private Clock clock;

    @BeforeEach
    void clear() {
        tokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void obsoleteTokensAreRemovedAndTheRestStays() {
        Instant now = clock.instant();
        UserAccount user = users.save(new UserAccount("token@example.com", "hash", AccountName.of("To", "Ken"), now));
        AuthToken valid = tokens.save(new AuthToken(user, "valid", AuthTokenType.PASSWORD_RESET, now.plus(Duration.ofHours(1))));
        AuthToken justExpired = tokens.save(new AuthToken(user, "fresh", AuthTokenType.PASSWORD_RESET, now.minus(Duration.ofDays(1))));
        AuthToken longExpired = tokens.save(new AuthToken(user, "old", AuthTokenType.PASSWORD_RESET, now.minus(Duration.ofDays(30))));
        AuthToken used = new AuthToken(user, "used", AuthTokenType.EMAIL_VERIFICATION, now.plus(Duration.ofHours(1)));
        used.markUsed(now);
        used = tokens.save(used);

        int removed = scheduler.cleanUp();

        assertThat(removed).isEqualTo(2);
        assertThat(tokens.findAll()).extracting(AuthToken::getId)
                .containsExactlyInAnyOrder(valid.getId(), justExpired.getId())
                .doesNotContain(longExpired.getId(), used.getId());
    }
}
