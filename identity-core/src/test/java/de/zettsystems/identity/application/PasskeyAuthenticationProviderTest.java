package de.zettsystems.identity.application;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The provider around Spring's verification: the principal it produces, and
 * how the failures are told apart.
 */
class PasskeyAuthenticationProviderTest {

    private final WebAuthnRelyingPartyOperations relyingParty = mock(WebAuthnRelyingPartyOperations.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final PasskeyAuthenticationProvider provider =
            new PasskeyAuthenticationProvider(relyingParty, userDetailsService);

    private static final PublicKeyCredentialUserEntity ANNA = ImmutablePublicKeyCredentialUserEntity.builder()
            .id(Bytes.random()).name("anna@example.com").displayName("Anna").build();

    private static WebAuthnAuthenticationRequestToken request() {
        WebAuthnAuthenticationRequestToken token = mock(WebAuthnAuthenticationRequestToken.class);
        when(token.getWebAuthnRequest()).thenReturn(mock(RelyingPartyAuthenticationRequest.class));
        return token;
    }

    private static IdentityUserDetails anna(boolean enabled) {
        return new IdentityUserDetails(7L, "anna@example.com", "Anna Beispiel", "hash", enabled, false,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void aVerifiedPasskeyYieldsTheAccountAsPrincipal() {
        when(relyingParty.authenticate(any())).thenReturn(ANNA);
        IdentityUserDetails user = anna(true);
        when(userDetailsService.loadUserByUsername("anna@example.com")).thenReturn(user);

        Authentication result = provider.authenticate(request());

        assertThat(result).isInstanceOf(PasskeyAuthentication.class);
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getPrincipal()).as("what every view and guard expects").isSameAs(user);
        assertThat(result.getName()).isEqualTo("anna@example.com");
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
    }

    /** Unknown credential, bad signature, wrong origin: all the same from the outside. */
    @Test
    void whatTheRelyingPartyRejectsBecomesBadCredentials() {
        when(relyingParty.authenticate(any())).thenThrow(new IllegalArgumentException("Unable to find CredentialRecord"));

        assertThatThrownBy(() -> provider.authenticate(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPasskeyWhoseAccountIsGoneIsBadCredentialsToo() {
        when(relyingParty.authenticate(any())).thenReturn(ANNA);
        when(userDetailsService.loadUserByUsername("anna@example.com"))
                .thenThrow(new UsernameNotFoundException("gone"));

        assertThatThrownBy(() -> provider.authenticate(request())).isInstanceOf(BadCredentialsException.class);
    }

    /** The one failure the sign-in page names: the account may not sign in. */
    @Test
    void aDisabledAccountFailsAsDisabledNotAsUnknown() {
        when(relyingParty.authenticate(any())).thenReturn(ANNA);
        when(userDetailsService.loadUserByUsername("anna@example.com")).thenReturn(anna(false));

        assertThatThrownBy(() -> provider.authenticate(request())).isInstanceOf(DisabledException.class);
    }

    @Test
    void onlyWebAuthnRequestsAreSupported() {
        assertThat(provider.supports(WebAuthnAuthenticationRequestToken.class)).isTrue();
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }

    @Test
    void theTokenCannotBeMarkedTrustedAfterwards() {
        PasskeyAuthentication token = new PasskeyAuthentication(anna(true), List.of());

        assertThatThrownBy(() -> token.setAuthenticated(true)).isInstanceOf(IllegalArgumentException.class);
        token.setAuthenticated(false);
        assertThat(token.isAuthenticated()).isFalse();
    }
}
