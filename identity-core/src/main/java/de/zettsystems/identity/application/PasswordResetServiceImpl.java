package de.zettsystems.identity.application;


import de.zettsystems.identity.domain.AuthTokenType;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.IdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private final UserAccountRepository userRepository;
    private final AuthTokenIssuer tokenIssuer;
    private final IdentityMailSender mailSender;
    private final PasswordHasher passwordHasher;
    private final IdentityProperties properties;

    PasswordResetServiceImpl(UserAccountRepository userRepository,
                             AuthTokenIssuer tokenIssuer,
                             IdentityMailSender mailSender,
                             PasswordHasher passwordHasher,
                             IdentityProperties properties) {
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
        this.mailSender = mailSender;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void requestReset(String email) {
        Optional<UserAccount> found = userRepository.findByEmail(UserAccount.normalizeEmail(email));
        if (found.isEmpty()) {
            LOG.debug("Password reset requested for an address without an account");
            return;
        }

        UserAccount user = found.get();
        String token = tokenIssuer.issue(user, AuthTokenType.PASSWORD_RESET);
        String url = properties.urlFor(
                IdentityPaths.RESET_PASSWORD + "?" + IdentityPaths.TOKEN_PARAMETER + "=" + token);
        mailSender.sendPasswordReset(UserAccountMapper.toDto(user), url);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newRawPassword) {
        if (newRawPassword == null || newRawPassword.length() < properties.passwordMinLength()) {
            throw new IdentityException(IdentityMessageKeys.PASSWORD_TOO_SHORT,
                    "Password must be at least %d characters".formatted(properties.passwordMinLength()));
        }

        UserAccount user = tokenIssuer.redeem(token, AuthTokenType.PASSWORD_RESET);
        user.changePassword(passwordHasher.hash(newRawPassword));

        // Resetting the password through the link in the mail also proves that
        // the address belongs to this person.
        if (!user.isEmailVerified()) {
            user.activateAfterEmailVerification();
        }
    }
}
