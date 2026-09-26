package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.ExternalSignInException;
import de.zettsystems.identity.application.ExternalSignInException.Reason;
import de.zettsystems.identity.application.ExternalSignInService;
import de.zettsystems.identity.application.ExternalSignInUser;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.values.ExternalIdentityClaims;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The last step of Spring's OAuth2 sign-in: the provider's answer, checked
 * by Spring, becomes a session of one of the building block's accounts.
 *
 * <p>Here and not in a user service, because only here is the authorization
 * request at hand, and with it what the building block asked to be carried
 * across the round trip (see {@link ExternalAuthorizationRequestResolver}).
 * Whatever goes wrong leaves as an {@link AuthenticationException}, so that
 * Spring's failure handling takes it like any failed sign-in.
 */
final class ExternalSignInConverter implements Converter<OAuth2LoginAuthenticationToken, OAuth2AuthenticationToken> {

    private final ExternalClaimsReader claimsReader;
    private final ExternalSignInService signInService;
    private final SecurityContextHolderStrategy securityContextHolderStrategy;

    ExternalSignInConverter(ExternalClaimsReader claimsReader, ExternalSignInService signInService,
                            SecurityContextHolderStrategy securityContextHolderStrategy) {
        this.claimsReader = claimsReader;
        this.signInService = signInService;
        this.securityContextHolderStrategy = securityContextHolderStrategy;
    }

    @Override
    public OAuth2AuthenticationToken convert(OAuth2LoginAuthenticationToken authentication) {
        String registrationId = authentication.getClientRegistration().getRegistrationId();
        OAuth2AuthorizationRequest request = authentication.getAuthorizationExchange().getAuthorizationRequest();
        IdentityUserDetails account;
        try {
            ExternalIdentityClaims claims = claimsReader.read(authentication);
            if (Boolean.TRUE.equals(request.getAttribute(ExternalAuthorizationRequestResolver.LINK_ATTRIBUTE))) {
                account = signInService.link(linkingUser(request), claims);
            } else {
                String invitation = request.getAttribute(ExternalAuthorizationRequestResolver.INVITATION_ATTRIBUTE);
                account = signInService.signIn(claims, invitation);
            }
        } catch (AuthenticationException e) {
            throw e;
        } catch (RuntimeException e) {
            // A provider answer the reader cannot use, a race on the unique
            // index between two first sign-ins, a missing default role: the
            // person sees "did not work", the log says why.
            throw new ExternalSignInException(Reason.FAILED,
                    "The sign-in through %s could not be completed".formatted(registrationId), e);
        }

        Set<GrantedAuthority> authorities = new LinkedHashSet<>(account.getAuthorities());
        authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.AUTHORIZATION_CODE_AUTHORITY));
        return new OAuth2AuthenticationToken(
                new ExternalSignInUser(account, providerUser(authentication).getAttributes()), authorities,
                registrationId);
    }

    /** Set once Spring has checked the provider's answer, which is when this converter runs. */
    static OAuth2User providerUser(OAuth2LoginAuthenticationToken authentication) {
        return Objects.requireNonNull(authentication.getPrincipal(), "the provider's user");
    }

    /**
     * The account to link: the one that asked for it, and only if it is still
     * the one signed in -- a session that changed hands in between, or a
     * link started from a remembered session, gets nothing.
     */
    private Long linkingUser(OAuth2AuthorizationRequest request) {
        Long expected = request.getAttribute(ExternalAuthorizationRequestResolver.LINK_USER_ATTRIBUTE);
        @Nullable Authentication current = securityContextHolderStrategy.getContext().getAuthentication();
        if (expected == null || current == null
                || !(current.getPrincipal() instanceof IdentityUserDetails user)
                || !expected.equals(user.userId())) {
            throw new ExternalSignInException(Reason.REAUTHENTICATION_REQUIRED,
                    "Linking a provider needs a fresh sign-in of the same account");
        }
        return expected;
    }
}
