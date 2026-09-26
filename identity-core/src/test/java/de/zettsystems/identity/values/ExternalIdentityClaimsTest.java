package de.zettsystems.identity.values;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalIdentityClaimsTest {

    @Test
    void anAddressCountsOnlyWhenTheProviderVouchesForIt() {
        assertThat(claims("ida@example.com", true, null, null, null).verifiedEmail()).isEqualTo("ida@example.com");
        assertThat(claims("ida@example.com", false, null, null, null).verifiedEmail()).isNull();
    }

    @Test
    void realNamesWhereTheApplicationKeepsThemAndTheProviderHasBoth() {
        AccountName name = claims(null, false, "Ida", "Beispiel", "Ida B.").accountName(NameMode.FULL_NAME);

        assertThat(name.firstName()).isEqualTo("Ida");
        assertThat(name.lastName()).isEqualTo("Beispiel");
    }

    @Test
    void otherwiseADisplayNameFromWhateverTheProviderKnows() {
        assertThat(claims(null, false, "Ida", "Beispiel", "Ida B.").accountName(NameMode.DISPLAY_NAME).displayName())
                .isEqualTo("Ida B.");
        assertThat(claims(null, false, "Ida", null, null).accountName(NameMode.FULL_NAME).displayName())
                .isEqualTo("Ida");
        assertThat(claims("ida.b@example.com", false, null, null, " ").accountName(NameMode.FULL_NAME).displayName())
                .isEqualTo("ida.b");
        assertThat(claims(null, false, null, null, null).accountName(NameMode.FULL_NAME).displayName())
                .as("the subject as a last resort")
                .isEqualTo("sub-1");
    }

    @Test
    void anOverlongNameIsCutToTheColumn() {
        AccountName name = claims(null, false, null, null, "x".repeat(400)).accountName(NameMode.DISPLAY_NAME);

        assertThat(name.displayName()).hasSize(260);
    }

    @Test
    void aBlankSubjectIsRefused() {
        assertThatThrownBy(() -> new ExternalIdentityClaims("google", " ", null, false, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAuthorizationPathIsRelativeLikeTheViews() {
        assertThat(new ExternalProvider("google", "Google").authorizationPath())
                .isEqualTo("oauth2/authorization/google");
    }

    @Test
    void newAccountsFollowSelfRegistrationUnlessSetExplicitly() {
        IdentityProperties open = IdentityProperties.defaults();
        IdentityProperties closed = new IdentityProperties(false, true, open.tokenValidity(),
                open.invitationValidity(), 12, "a@b.c", "App", "http://localhost", "USER", NameMode.FULL_NAME,
                Locale.GERMAN, UiSettings.defaults());

        assertThat(open.createsExternalAccounts()).isTrue();
        assertThat(closed.createsExternalAccounts()).isFalse();
        assertThat(closed.withOAuth2(OAuth2Settings.defaults().createAccounts(true)).createsExternalAccounts())
                .isTrue();
        assertThat(open.withOAuth2(OAuth2Settings.defaults().createAccounts(false)).createsExternalAccounts())
                .isFalse();
    }

    @Test
    void theOAuth2SettingsAreOffAndCopyTheirList() {
        OAuth2Settings settings = OAuth2Settings.defaults();

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.linkByEmail()).isTrue();
        assertThat(settings.registrations(List.of("google")).enabled(true).linkByEmail(false))
                .isEqualTo(new OAuth2Settings(true, List.of("google"), null, false));
        List<String> blank = List.of(" ");
        assertThatThrownBy(() -> settings.registrations(blank))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExternalIdentityClaims claims(String email, boolean verified, String given, String family,
                                                 String full) {
        return new ExternalIdentityClaims("google", "sub-1", email, verified, given, family, full, null);
    }
}
