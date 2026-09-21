package de.zettsystems.identity.application;

import de.zettsystems.identity.testsupport.RecordingTokenRepository;
import de.zettsystems.identity.values.AccountDeleted;
import de.zettsystems.identity.values.AccountLocked;
import de.zettsystems.identity.values.EmailChanged;
import de.zettsystems.identity.values.PasswordChanged;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Which of the events clears which name -- and that nothing blows up when there is nothing to clear. */
class RememberMeTokenCleanerTest {

    private final RecordingTokenRepository tokens = new RecordingTokenRepository();
    private final RememberMeTokenCleaner cleaner = new RememberMeTokenCleaner(new FixedProvider(tokens));

    @Test
    void aPasswordChangeThrowsTheOtherDevicesOut() {
        tokens.rememberDevice("eva@example.com", "phone");
        tokens.rememberDevice("eva@example.com", "tablet");
        tokens.rememberDevice("otto@example.com", "laptop");

        cleaner.onAccountEvent(new PasswordChanged(1L, "eva@example.com"));

        assertThat(tokens.seriesOf("eva@example.com")).isEmpty();
        assertThat(tokens.seriesOf("otto@example.com"))
                .as("somebody else's devices are none of this account's business")
                .containsExactly("laptop");
    }

    @Test
    void lockingAndDeletingClearTheTokensJustTheSame() {
        tokens.rememberDevice("eva@example.com", "phone");
        cleaner.onAccountEvent(new AccountLocked(1L, "eva@example.com"));
        assertThat(tokens.seriesOf("eva@example.com")).isEmpty();

        tokens.rememberDevice("otto@example.com", "laptop");
        cleaner.onAccountEvent(new AccountDeleted(2L, "otto@example.com"));
        assertThat(tokens.seriesOf("otto@example.com")).isEmpty();
    }

    /**
     * The point of {@code EmailChanged}: the tokens sit under the name that was
     * valid so far. Cleaning the new one would clean nothing and leave the rows
     * behind.
     */
    @Test
    void aChangeOfAddressClearsTheOldName() {
        tokens.rememberDevice("alt@example.com", "phone");

        cleaner.onAccountEvent(new EmailChanged(1L, "alt@example.com", "neu@example.com"));

        assertThat(tokens.seriesOf("alt@example.com")).isEmpty();
    }

    /** A managed account has no sign-in name, so it can have no tokens either. */
    @Test
    void anAccountWithoutAnAddressIsLeftAlone() {
        tokens.rememberDevice("eva@example.com", "phone");

        cleaner.onAccountEvent(new PasswordChanged(1L, null));
        cleaner.onAccountEvent(new EmailChanged(1L, null, "eva@example.com"));

        assertThat(tokens.seriesOf("eva@example.com"))
                .as("nothing was said about this account, so nothing is cleared")
                .containsExactly("phone");
    }

    /** An application without "keep me signed in" has no such bean. */
    @Test
    void withoutARepositoryNothingHappens() {
        RememberMeTokenCleaner withoutRepository = new RememberMeTokenCleaner(new FixedProvider(null));

        assertThatCode(() -> withoutRepository.onAccountEvent(new PasswordChanged(1L, "eva@example.com")))
                .doesNotThrowAnyException();
    }

    /**
     * The change is committed by the time this runs. An exception here would
     * present a password change that did work as a failure, and the person
     * would try it again.
     */
    @Test
    void aBrokenRepositoryDoesNotTurnASucceededChangeIntoAFailure() {
        RememberMeTokenCleaner overABrokenRepository =
                new RememberMeTokenCleaner(new FixedProvider(new FailingTokenRepository()));

        assertThatCode(() -> overABrokenRepository.onAccountEvent(new PasswordChanged(1L, "eva@example.com")))
                .doesNotThrowAnyException();
    }

    /**
     * What Spring hands the bean: a provider that either holds the repository
     * or reports that there is none. Deliberately the real
     * {@code getIfAvailable()} of the interface rather than a stubbed one --
     * that is the path the production code walks.
     */
    private record FixedProvider(PersistentTokenRepository repository)
            implements ObjectProvider<PersistentTokenRepository> {

        @Override
        public PersistentTokenRepository getObject() throws BeansException {
            if (repository == null) {
                throw new NoSuchBeanDefinitionException(PersistentTokenRepository.class);
            }
            return repository;
        }

        @Override
        public PersistentTokenRepository getObject(Object... args) throws BeansException {
            return getObject();
        }
    }

    private static final class FailingTokenRepository implements PersistentTokenRepository {

        @Override
        public void createNewToken(PersistentRememberMeToken token) {
            throw new IllegalStateException("no database");
        }

        @Override
        public void updateToken(String series, String tokenValue, Date lastUsed) {
            throw new IllegalStateException("no database");
        }

        @Override
        public PersistentRememberMeToken getTokenForSeries(String seriesId) {
            throw new IllegalStateException("no database");
        }

        @Override
        public void removeUserTokens(String username) {
            throw new IllegalStateException("no database");
        }
    }
}
