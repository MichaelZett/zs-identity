package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.ExternalSignInService;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.values.IdentityPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Spring's resolver for the redirect to the provider, plus what the building
 * block needs back when the provider returns: the invitation to redeem, or
 * the account to link.
 *
 * <p>Both travel as attributes of the authorization request, which Spring
 * keeps in the session between the redirect and the callback and hands back
 * with the provider's answer. Nothing of it goes to the provider.
 *
 * <p>The invitation is taken from the session, where the redemption view put
 * it, never from the address: a link carrying somebody else's invitation
 * would otherwise tie the identity of whoever follows it to that account
 * (see {@code ExternalSignInService#PENDING_INVITATION_SESSION_ATTRIBUTE}).
 *
 * <p>Linking is only taken on from a <em>fresh</em> sign-in, as registering a
 * passkey is: a remembered session on a shared computer must not be enough to
 * add a way into the account. The resolver cannot turn the request down --
 * Spring's redirect filter sits in front of every access rule -- so it
 * notes whom to link only when the sign-in is fresh, and the callback refuses
 * the link when that note is missing.
 */
final class ExternalAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String INVITATION_ATTRIBUTE = "de.zettsystems.identity.oauth2.invitation";
    static final String LINK_ATTRIBUTE = "de.zettsystems.identity.oauth2.link";
    static final String LINK_USER_ATTRIBUTE = "de.zettsystems.identity.oauth2.linkUser";
    /**
     * In the session while a link is under way, so that the handlers send the
     * browser back to the linked-accounts view rather than the sign-in page.
     */
    static final String LINKING_SESSION_ATTRIBUTE = ExternalAuthorizationRequestResolver.class.getName() + ".linking";

    private final OAuth2AuthorizationRequestResolver delegate;
    private final SecurityContextHolderStrategy securityContextHolderStrategy;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    ExternalAuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate,
                                         SecurityContextHolderStrategy securityContextHolderStrategy) {
        this.delegate = delegate;
        this.securityContextHolderStrategy = securityContextHolderStrategy;
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withAttributes(request, delegate.resolve(request));
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withAttributes(request, delegate.resolve(request, clientRegistrationId));
    }

    /** {@code null} for every request that is not a redirect to a provider: Spring asks for each one. */
    private @Nullable OAuth2AuthorizationRequest withAttributes(HttpServletRequest request,
                                                                @Nullable OAuth2AuthorizationRequest resolved) {
        if (resolved == null) {
            return null;
        }
        boolean link = request.getParameter(IdentityPaths.LINK_PARAMETER) != null;
        String invitation = request.getParameter(IdentityPaths.INVITATION_PARAMETER) != null && !link
                ? pendingInvitation(request)
                : null;
        Long linkUser = link ? freshlySignedInUser() : null;
        rememberLinking(request, link);
        return OAuth2AuthorizationRequest.from(resolved)
                .attributes(attributes -> {
                    if (link) {
                        attributes.put(LINK_ATTRIBUTE, true);
                        if (linkUser != null) {
                            attributes.put(LINK_USER_ATTRIBUTE, linkUser);
                        }
                    } else if (invitation != null) {
                        attributes.put(INVITATION_ATTRIBUTE, invitation);
                    }
                })
                .build();
    }

    /** Taken out, so that it is used for this one round trip only. */
    private static @Nullable String pendingInvitation(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object token = session.getAttribute(ExternalSignInService.PENDING_INVITATION_SESSION_ATTRIBUTE);
        session.removeAttribute(ExternalSignInService.PENDING_INVITATION_SESSION_ATTRIBUTE);
        return token instanceof String text && !text.isBlank() ? text : null;
    }

    private @Nullable Long freshlySignedInUser() {
        Authentication current = securityContextHolderStrategy.getContext().getAuthentication();
        if (current != null && trustResolver.isFullyAuthenticated(current)
                && current.getPrincipal() instanceof IdentityUserDetails user) {
            return user.userId();
        }
        return null;
    }

    /** Set for a link, cleared for anything else: a link given up half-way must not colour the next sign-in. */
    private static void rememberLinking(HttpServletRequest request, boolean link) {
        if (link) {
            request.getSession(true).setAttribute(LINKING_SESSION_ATTRIBUTE, true);
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(LINKING_SESSION_ATTRIBUTE);
        }
    }
}
