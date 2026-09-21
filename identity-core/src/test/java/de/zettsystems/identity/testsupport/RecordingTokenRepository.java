package de.zettsystems.identity.testsupport;

import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Stands in for the {@code PersistentTokenRepository} an application brings
 * along for "keep me signed in": one row per device, keyed by the sign-in name.
 *
 * <p>A test double rather than a Mockito mock, for the same reason as
 * {@link RecordingMailSender}: the tests want to see whether a token is still
 * there after a password change, not whether a method was called.
 */
public class RecordingTokenRepository implements PersistentTokenRepository {

    private final List<PersistentRememberMeToken> tokens = new ArrayList<>();

    /** Hands the named account a token, as signing in with "keep me signed in" would. */
    public String rememberDevice(String username, String series) {
        tokens.add(new PersistentRememberMeToken(username, series, "token-" + series, new Date(0)));
        return series;
    }

    /** The series still valid for this account. */
    public List<String> seriesOf(String username) {
        return tokens.stream()
                .filter(token -> token.getUsername().equalsIgnoreCase(username))
                .map(PersistentRememberMeToken::getSeries)
                .toList();
    }

    public void clear() {
        tokens.clear();
    }

    @Override
    public void createNewToken(PersistentRememberMeToken token) {
        tokens.add(token);
    }

    @Override
    public void updateToken(String series, String tokenValue, Date lastUsed) {
        tokens.replaceAll(token -> token.getSeries().equals(series)
                ? new PersistentRememberMeToken(token.getUsername(), series, tokenValue, lastUsed)
                : token);
    }

    @Override
    public PersistentRememberMeToken getTokenForSeries(String seriesId) {
        return tokens.stream()
                .filter(token -> token.getSeries().equals(seriesId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Exactly what the building block calls. Spring compares the name as it
     * was stored; the building block hands over the address in the spelling of
     * the account, which is lower case throughout.
     */
    @Override
    public void removeUserTokens(String username) {
        tokens.removeIf(token -> token.getUsername().equalsIgnoreCase(username));
    }
}
