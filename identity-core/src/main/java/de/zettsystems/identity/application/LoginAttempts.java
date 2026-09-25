package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.AccountTemporarilyLocked;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.LoginProtectionSettings;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * The lock half of the protection against guessing: the failures counted on
 * the account, and the temporary lock they lead to.
 *
 * <p>Only accounts that exist and can sign in with a password are counted;
 * for anything else there is nothing to lock. That the answer does not give
 * this away is the business of {@link LoginProtectionAuthenticationProvider}.
 */
class LoginAttempts {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAttempts.class);

    private final UserAccountRepository userRepository;
    private final IdentityProperties properties;
    private final IdentityMailSender mailSender;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Executor mailExecutor;

    /**
     * @param mailExecutor where the notice mail is sent from. Not the thread
     *                     of the sign-in: a mail server that takes a second
     *                     would make exactly the attempt that locks an
     *                     existing account slower than one at an unknown
     *                     address.
     */
    LoginAttempts(UserAccountRepository userRepository, IdentityProperties properties,
                  IdentityMailSender mailSender, ApplicationEventPublisher events, Clock clock,
                  Executor mailExecutor) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.mailSender = mailSender;
        this.events = events;
        this.clock = clock;
        this.mailExecutor = mailExecutor;
    }

    /** Whether the account behind this sign-in name is locked right now; {@code false} if there is none. */
    @Transactional(readOnly = true)
    public boolean isLocked(String username) {
        Instant now = clock.instant();
        return userRepository.findByEmail(UserAccount.normalizeEmail(username))
                .map(user -> user.isLockedAt(now))
                .orElse(false);
    }

    /**
     * Counts a wrong password and, once there are enough, locks the account:
     * WARN line, {@link AccountTemporarilyLocked}, and the notice mail unless
     * switched off.
     */
    @Transactional
    public void recordFailure(String username, @Nullable String clientAddress) {
        Optional<UserAccount> found = account(username);
        if (found.isEmpty()) {
            return;
        }
        UserAccount user = found.get();
        LoginProtectionSettings settings = properties.loginProtection();
        Instant now = clock.instant();
        Instant lockedUntil = user.recordFailedLogin(now, settings);
        if (lockedUntil == null) {
            return;
        }
        UserAccountDto account = UserAccountMapper.toDto(user);
        String email = account.email();
        if (email == null) {
            return;
        }
        LOG.warn("Account {} temporarily locked until {} after {} failed sign-ins, the last from {}",
                account.id(), lockedUntil, user.getFailedLoginCount(), clientAddress);
        events.publishEvent(new AccountTemporarilyLocked(account.id(), email, lockedUntil, clientAddress));
        if (settings.notifyByMail()) {
            sendNotice(account, Duration.between(now, lockedUntil));
        }
    }

    /** Forgets the failures and lifts a lock after a sign-in that got through. */
    @Transactional
    public void recordSuccess(String username) {
        account(username).ifPresent(UserAccount::clearFailedLogins);
    }

    private void sendNotice(UserAccountDto account, Duration lockDuration) {
        String forgotPasswordUrl = properties.urlFor(IdentityPaths.FORGOT_PASSWORD);
        mailExecutor.execute(() -> {
            try {
                mailSender.sendAccountTemporarilyLocked(account, forgotPasswordUrl, lockDuration);
            } catch (RuntimeException e) {
                // The lock stands whether or not the mail arrives; a failure
                // here must not surface anywhere near the sign-in.
                LOG.error("Could not send the lock notice to account {}", account.id(), e);
            }
        });
    }

    /**
     * Only accounts that can sign in with a password, row locked: a managed
     * one or an open invitation is never asked for one, so there is nothing
     * to guess.
     */
    private Optional<UserAccount> account(String username) {
        return userRepository.findForUpdateByEmail(UserAccount.normalizeEmail(username))
                .filter(UserAccount::isClaimed);
    }
}
