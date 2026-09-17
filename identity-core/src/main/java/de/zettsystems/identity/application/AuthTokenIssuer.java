package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthToken;
import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.AuthTokenType;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Issues one-time tokens and redeems them again.
 *
 * <p>The plain text of a token leaves this class exactly once, as the return
 * value of {@link #issue}, so that it can travel into the mail. Only its
 * SHA-256 hash is stored in the database. Whoever reads the database cannot
 * build a valid link from it.
 */
class AuthTokenIssuer {

    private static final int TOKEN_BYTES = 32;

    private final AuthTokenRepository tokenRepository;
    private final IdentityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    AuthTokenIssuer(AuthTokenRepository tokenRepository, IdentityProperties properties, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Issues a new token and voids every open token of the same type for this
     * user, so that only the most recently sent link is ever valid.
     *
     * @return the plain text that belongs in the mail
     */
    @Transactional
    String issue(UserAccount user, AuthTokenType type) {
        Long userId = Objects.requireNonNull(user.getId(), "user.id");
        Instant now = clock.instant();
        tokenRepository.invalidateOpenTokens(userId, type, now);

        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        AuthToken token = new AuthToken(user, hash(plainToken), type, now.plus(validityOf(type)));
        tokenRepository.save(token);
        return plainToken;
    }

    /**
     * How long a freshly issued token of this type is valid.
     *
     * <p>Invitations get a longer deadline of their own: verification and
     * password reset are requested by the person and read immediately, whereas
     * an invitation arrives unannounced.
     */
    Duration validityOf(AuthTokenType type) {
        return type == AuthTokenType.INVITATION ? properties.invitationValidity() : properties.tokenValidity();
    }

    /**
     * Redeems a token and voids it in the process.
     *
     * @throws IdentityException if the token is unknown, already used or
     *                           expired
     */
    @Transactional
    UserAccount redeem(String plainToken, AuthTokenType type) {
        AuthToken token = tokenRepository.findByTokenHashAndType(hash(plainToken), type)
                .orElseThrow(() -> new IdentityException(IdentityMessageKeys.TOKEN_INVALID,
                        "No %s token matches the given value".formatted(type)));

        Instant now = clock.instant();
        if (!token.isUsable(now)) {
            throw new IdentityException(IdentityMessageKeys.TOKEN_EXPIRED,
                    "%s token %d is expired or already used".formatted(type, token.getId()));
        }

        token.markUsed(now);
        return token.getUser();
    }

    /**
     * Looks up whom a token belongs to, without redeeming it and regardless of
     * whether it is still valid. This lets a caller decide, after a rejected
     * {@link #redeem}, whether the purpose of the token (email verification,
     * say) has long since been reached.
     */
    @Transactional(readOnly = true)
    Optional<UserAccount> peekUser(String plainToken, AuthTokenType type) {
        return tokenRepository.findByTokenHashAndType(hash(plainToken), type)
                .map(AuthToken::getUser);
    }

    private static String hash(String plainToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(plainToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory on every Java platform; nobody ever gets here.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
