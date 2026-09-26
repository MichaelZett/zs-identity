package de.zettsystems.identity.application;

import de.zettsystems.identity.values.ExternalIdentityClaims;
import org.jspecify.annotations.Nullable;

/**
 * Turns what an external provider says about a person into an account of the
 * building block (since 1.2.0).
 *
 * <p>Called by the OAuth2 sign-in that {@code IdentityOAuth2Configurer} wires
 * up, once the provider's answer has been checked; an application has no
 * reason to call it itself. Free of OAuth2 types, so the rules about
 * accounts can be read and tested without a provider.
 */
public interface ExternalSignInService {

    /**
     * Where the invitation to redeem through a provider waits in the HTTP
     * session: the redemption view puts the token here when the person
     * chooses a provider, and the redirect to the provider takes it out
     * (with {@link de.zettsystems.identity.values.IdentityPaths#INVITATION_PARAMETER}
     * as the flag that it should).
     *
     * <p>In the session and not in the address on purpose. A token in the
     * address would let the holder of an invitation send the link to somebody
     * else, whose identity at the provider would then end up linked to the
     * invited account -- and every later sign-in of theirs with it. From the
     * session it only comes after the person has opened the invitation and
     * seen whose account it is. An application without {@code identity-vaadin}
     * sets it the same way.
     */
    String PENDING_INVITATION_SESSION_ATTRIBUTE = "de.zettsystems.identity.oauth2.pendingInvitation";

    /**
     * Signs in through a provider. In this order:
     *
     * <ol>
     *   <li>With an invitation token: the invitation is redeemed with the
     *       provider's identity, whatever address the provider reports -- the
     *       token proves the invited mailbox.</li>
     *   <li>An identity already linked: its account, whatever address the
     *       provider reports now.</li>
     *   <li>Otherwise only an address the provider vouches for counts. An
     *       account with that address is joined (an open invitation is
     *       redeemed by it; a password set before the address was confirmed
     *       is thrown away), unless {@code zs.identity.oauth2.link-by-email}
     *       is off.</li>
     *   <li>No account: a new one without a password, if
     *       {@link de.zettsystems.identity.values.IdentityProperties#createsExternalAccounts()}.</li>
     * </ol>
     *
     * <p>A sign-in that gets through records the time, and forgets the
     * failed password sign-ins of the account like a passkey sign-in does.
     *
     * @param invitationToken the token of an invitation to redeem, or
     *                        {@code null}
     * @return the principal of the account
     * @throws ExternalSignInException when the sign-in is turned down, with
     *                                 the reason
     */
    IdentityUserDetails signIn(ExternalIdentityClaims claims, @Nullable String invitationToken);

    /**
     * Links a provider's identity to an account whose owner is signed in and
     * asked for it. The address the provider reports plays no part: the
     * person is signed in, and chose to.
     *
     * @return the principal of the account
     * @throws ExternalSignInException with {@code ALREADY_LINKED} when the
     *                                 identity belongs to another account, or
     *                                 this one has a different identity at the
     *                                 provider
     */
    IdentityUserDetails link(Long userId, ExternalIdentityClaims claims);
}
