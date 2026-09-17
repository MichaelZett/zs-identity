package de.zettsystems.identity.values;

import org.junit.jupiter.api.Test;

import java.time.Duration;
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

    private static IdentityProperties propertiesWithBaseUrl(String baseUrl) {
        return new IdentityProperties(true, true, Duration.ofHours(24), Duration.ofDays(7), 12,
                "noreply@example.com", "Test", baseUrl, "USER", NameMode.FULL_NAME, Locale.GERMAN,
                UiSettings.defaults());
    }
}
