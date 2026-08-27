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
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Stellt Einmal-Token aus und löst sie wieder ein.
 *
 * <p>Der Klartext des Tokens verlässt diese Klasse genau einmal — als
 * Rückgabewert von {@link #issue}, damit er in die Mail wandern kann. In der
 * Datenbank liegt nur sein SHA-256-Hash. Wer die Datenbank liest, kann daraus
 * keinen gültigen Link bauen.
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
     * Stellt ein neues Token aus und entwertet alle offenen Token desselben Typs
     * für diesen Benutzer — so gilt immer nur der zuletzt verschickte Link.
     *
     * @return der Klartext, der in die Mail gehört
     */
    @Transactional
    String issue(UserAccount user, AuthTokenType type) {
        Long userId = Objects.requireNonNull(user.getId(), "user.id");
        Instant now = clock.instant();
        tokenRepository.invalidateOpenTokens(userId, type, now);

        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        AuthToken token = new AuthToken(user, hash(plainToken), type, now.plus(properties.tokenValidity()));
        tokenRepository.save(token);
        return plainToken;
    }

    /**
     * Löst ein Token ein und entwertet es dabei.
     *
     * @throws IdentityException wenn das Token unbekannt, schon benutzt oder
     *                           abgelaufen ist
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
     * Schlägt nach, wem ein Token gehört — ohne es einzulösen und unabhängig
     * davon, ob es noch gültig ist. Damit kann ein Aufrufer nach einem
     * abgelehnten {@link #redeem} entscheiden, ob das Ziel des Tokens (etwa
     * die E-Mail-Bestätigung) längst erreicht ist.
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
            // SHA-256 ist in jeder Java-Plattform Pflicht; hier kommt nie jemand an.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
