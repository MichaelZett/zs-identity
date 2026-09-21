package de.zettsystems.identity.application;

import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Checks a passkey assertion and turns it into a {@link PasskeyAuthentication}
 * with the account's {@link UserDetails} as principal.
 *
 * <p>The verification itself is Spring Security's ({@code
 * WebAuthnRelyingPartyOperations}): signature, challenge, origin, signature
 * counter. What this provider adds over Spring's own is the principal (see
 * {@link PasskeyAuthentication}) and the account checks a password sign-in
 * gets for free from {@code DaoAuthenticationProvider}: a disabled account
 * fails with {@code DisabledException}, so that the sign-in page can say so
 * instead of "unknown passkey".
 *
 * <p>Everything the relying party throws is a {@code RuntimeException}
 * without a security type -- unknown credential, bad signature, wrong
 * origin. All of it becomes {@code BadCredentialsException}, which is what
 * the failure handler and the event listeners understand.
 */
public final class PasskeyAuthenticationProvider implements AuthenticationProvider {

    private final WebAuthnRelyingPartyOperations relyingParty;
    private final UserDetailsService userDetailsService;
    private final UserDetailsChecker accountChecks = new AccountStatusUserDetailsChecker();

    public PasskeyAuthenticationProvider(WebAuthnRelyingPartyOperations relyingParty,
                                         UserDetailsService userDetailsService) {
        this.relyingParty = Objects.requireNonNull(relyingParty, "relyingParty");
        this.userDetailsService = Objects.requireNonNull(userDetailsService, "userDetailsService");
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof WebAuthnAuthenticationRequestToken request)) {
            throw new IllegalArgumentException("Only WebAuthnAuthenticationRequestToken is supported, not "
                    + authentication.getClass().getName());
        }
        PublicKeyCredentialUserEntity userEntity;
        try {
            userEntity = relyingParty.authenticate(request.getWebAuthnRequest());
        } catch (RuntimeException e) {
            throw new BadCredentialsException("The passkey could not be verified", e);
        }
        UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(userEntity.getName());
        } catch (UsernameNotFoundException e) {
            // The passkey is real, but its account is gone or can no longer
            // sign in. From the outside that is an unknown passkey.
            throw new BadCredentialsException("No account for the passkey", e);
        }
        accountChecks.check(user);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(user.getAuthorities());
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY));
        return new PasskeyAuthentication(user, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return WebAuthnAuthenticationRequestToken.class.isAssignableFrom(authentication);
    }
}
