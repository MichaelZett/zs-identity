package de.zettsystems.identity.values;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /**
     * The sign-in button is on unless an application says otherwise, and the
     * shape without the flag still compiles -- an application that built the
     * record by hand before 0.14.0 keeps working.
     */
    @Test
    void theSignInButtonIsOnUnlessSwitchedOff() {
        assertThat(PasskeySettings.defaults().loginButton()).isTrue();
        assertThat(new PasskeySettings(true, "example.com", "App", List.of("https://example.com")).loginButton())
                .as("the four-argument shape up to 0.13.x")
                .isTrue();
        assertThat(PasskeySettings.defaults().loginButton(false).loginButton()).isFalse();
        assertThat(PasskeySettings.defaults().loginButton(false).enabled(true))
                .as("the withers do not undo each other")
                .isEqualTo(new PasskeySettings(true, "localhost", "Application",
                        List.of("http://localhost:8080"), false));
    }

    /**
     * The trap the second constructor sprang: with more than one, Spring
     * cannot tell which binds, and the whole {@code zs.identity} tree fails
     * -- every application, at startup, not just passkeys. The canonical one
     * carries {@code @ConstructorBinding}; this pins it down.
     */
    @Test
    void thePropertiesStillBindAlthoughTheRecordHasTwoConstructors() {
        Map<String, Object> values = Map.of(
                "zs.identity.passkeys.enabled", "true",
                "zs.identity.passkeys.rp-id", "orgaapp.example.com",
                "zs.identity.passkeys.rp-name", "OrgaApp",
                "zs.identity.passkeys.allowed-origins", "https://orgaapp.example.com",
                "zs.identity.passkeys.login-button", "false");
        Binder binder = new Binder(new MapConfigurationPropertySource(values));

        IdentityProperties bound = binder.bind("zs.identity", IdentityProperties.class).get();

        assertThat(bound.passkeys().enabled()).isTrue();
        assertThat(bound.passkeys().rpId()).isEqualTo("orgaapp.example.com");
        assertThat(bound.passkeys().loginButton()).isFalse();
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
