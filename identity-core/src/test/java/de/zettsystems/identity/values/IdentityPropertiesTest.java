package de.zettsystems.identity.values;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityPropertiesTest {

    @Test
    void trailingSlashInBaseUrlIsRemovedSoLinksDoNotGetDoubleSlashes() {
        IdentityProperties properties = propertiesWithBaseUrl("http://example.com/");

        assertThat(properties.baseUrl()).isEqualTo("http://example.com");
        assertThat(properties.urlFor("/register/confirm")).isEqualTo("http://example.com/register/confirm");
    }

    @Test
    void urlForAcceptsPathsWithAndWithoutLeadingSlash() {
        IdentityProperties properties = propertiesWithBaseUrl("http://example.com");

        assertThat(properties.urlFor("/a")).isEqualTo("http://example.com/a");
        assertThat(properties.urlFor("a")).isEqualTo("http://example.com/a");
    }

    @Test
    void aPasswordMinimumBelowEightIsRefused() {
        Duration tokenValidity = Duration.ofHours(1);
        Duration invitationValidity = Duration.ofDays(7);
        UiSettings ui = UiSettings.defaults();
        assertThatThrownBy(() -> new IdentityProperties(true, true, tokenValidity, invitationValidity, 6,
                "a@b.c", "Test", "http://example.com", "USER", NameMode.FULL_NAME, Locale.GERMAN, ui))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password-min-length");
    }

    @Test
    void aNonPositiveTokenValidityIsRefused() {
        Duration invitationValidity = Duration.ofDays(7);
        UiSettings ui = UiSettings.defaults();
        assertThatThrownBy(() -> new IdentityProperties(true, true, Duration.ZERO, invitationValidity, 12,
                "a@b.c", "Test", "http://example.com", "USER", NameMode.FULL_NAME, Locale.GERMAN, ui))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token-validity");
    }

    @Test
    void aNonPositiveInvitationValidityIsRefused() {
        Duration tokenValidity = Duration.ofHours(1);
        UiSettings ui = UiSettings.defaults();
        assertThatThrownBy(() -> new IdentityProperties(true, true, tokenValidity, Duration.ZERO, 12,
                "a@b.c", "Test", "http://example.com", "USER", NameMode.FULL_NAME, Locale.GERMAN, ui))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invitation-validity");
    }

    @Test
    void defaultsAreUsable() {
        IdentityProperties defaults = IdentityProperties.defaults();

        assertThat(defaults.selfRegistrationEnabled()).isTrue();
        assertThat(defaults.emailVerificationRequired()).isTrue();
        assertThat(defaults.defaultRoleCode()).isEqualTo("USER");
        assertThat(defaults.locale()).isEqualTo(Locale.GERMAN);
        assertThat(defaults.invitationValidity())
                .as("an invitation sits in the inbox until someone has time")
                .isEqualTo(Duration.ofDays(7));
    }

    /** Off unless switched on: a passkey is bound to a domain the application has to settle first. */
    @Test
    void passkeysAreOffByDefaultAndCanBeSwitchedOnWithoutTouchingTheRest() {
        IdentityProperties defaults = IdentityProperties.defaults();
        assertThat(defaults.passkeys().enabled()).isFalse();

        PasskeySettings live = new PasskeySettings(true, "orgaapp.example.com", "OrgaApp",
                List.of("https://orgaapp.example.com/", "http://localhost:8090"));
        IdentityProperties withPasskeys = defaults.withPasskeys(live);

        assertThat(withPasskeys.passkeys().enabled()).isTrue();
        assertThat(withPasskeys.passkeys().allowedOrigins())
                .as("a trailing slash would never match the origin the browser sends")
                .containsExactly("https://orgaapp.example.com", "http://localhost:8090");
        assertThat(withPasskeys.locale()).isEqualTo(defaults.locale());
        assertThat(withPasskeys.withLocale(Locale.ENGLISH).passkeys()).isEqualTo(live);
    }

    @Test
    void passkeySettingsRefuseABlankRelyingPartyOrNoOrigin() {
        List<String> origins = List.of("https://example.com");
        assertThatThrownBy(() -> new PasskeySettings(true, " ", "App", origins))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rp-id");
        assertThatThrownBy(() -> new PasskeySettings(true, "example.com", "App", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed-origins");
    }

    private static IdentityProperties propertiesWithBaseUrl(String baseUrl) {
        return new IdentityProperties(true, true, Duration.ofHours(24), Duration.ofDays(7), 12,
                "noreply@example.com", "Test", baseUrl, "USER", NameMode.FULL_NAME, Locale.GERMAN,
                UiSettings.defaults());
    }
}
