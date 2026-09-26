package de.zettsystems.identity.application;

import de.zettsystems.identity.application.ExternalSignInException.Reason;
import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.AuthTokenType;
import de.zettsystems.identity.domain.ExternalIdentity;
import de.zettsystems.identity.domain.ExternalIdentityRepository;
import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.ExternalIdentityClaims;
import de.zettsystems.identity.values.ExternalIdentityLinked;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.PasswordChanged;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

class ExternalSignInServiceImpl implements ExternalSignInService {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalSignInServiceImpl.class);

    private final UserAccountRepository userRepository;
    private final ExternalIdentityRepository identityRepository;
    private final RoleRepository roleRepository;
    private final AuthTokenIssuer tokenIssuer;
    private final AuthTokenRepository tokenRepository;
    private final IdentityProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    /** Only there while the protection against guessing is on. */
    private final ObjectProvider<LoginThrottle> throttle;

    @SuppressWarnings("java:S107") // one collaborator per concern, as in the other services
    ExternalSignInServiceImpl(UserAccountRepository userRepository, ExternalIdentityRepository identityRepository,
                              RoleRepository roleRepository, AuthTokenIssuer tokenIssuer,
                              AuthTokenRepository tokenRepository, IdentityProperties properties, Clock clock,
                              ApplicationEventPublisher events, ObjectProvider<LoginThrottle> throttle) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.tokenIssuer = tokenIssuer;
        this.tokenRepository = tokenRepository;
        this.properties = properties;
        this.clock = clock;
        this.events = events;
        this.throttle = throttle;
    }

    @Override
    @Transactional
    public IdentityUserDetails signIn(ExternalIdentityClaims claims, @Nullable String invitationToken) {
        Objects.requireNonNull(claims, "claims");
        Instant now = clock.instant();
        Optional<ExternalIdentity> known =
                identityRepository.findByRegistrationIdAndSubject(claims.registrationId(), claims.subject());

        UserAccount user;
        if (invitationToken != null) {
            user = redeemInvitation(invitationToken, claims, known, now);
        } else if (known.isPresent()) {
            user = known.get().getUser();
        } else {
            user = joinOrCreate(claims, now);
        }
        return signedIn(user, claims, now);
    }

    @Override
    @Transactional
    public IdentityUserDetails link(Long userId, ExternalIdentityClaims claims) {
        Objects.requireNonNull(claims, "claims");
        Instant now = clock.instant();
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new ExternalSignInException(Reason.FAILED, "No account with id " + userId));
        Optional<ExternalIdentity> known =
                identityRepository.findByRegistrationIdAndSubject(claims.registrationId(), claims.subject());
        if (known.isPresent() && !known.get().getUser().equals(user)) {
            throw alreadyLinked(claims);
        }
        link(user, claims, now);
        return signedIn(user, claims, now);
    }

    /**
     * The token proves the invited mailbox, so the provider's address does not
     * have to match: whoever was invited as {@code anna@club.example} may
     * well sign in with a Google account under another address. The account
     * keeps the invited one.
     */
    private UserAccount redeemInvitation(String invitationToken, ExternalIdentityClaims claims,
                                         Optional<ExternalIdentity> known, Instant now) {
        UserAccount invited;
        try {
            invited = tokenIssuer.redeem(invitationToken, AuthTokenType.INVITATION);
        } catch (IdentityException e) {
            throw new ExternalSignInException(Reason.INVITATION_INVALID, "The invitation cannot be redeemed", e);
        }
        if (known.isPresent() && !known.get().getUser().equals(invited)) {
            throw alreadyLinked(claims);
        }
        link(invited, claims, now);
        // As when an invitation is redeemed with a password: the link went to
        // exactly this address, so it counts as confirmed.
        invited.activateAfterEmailVerification();
        if (invited.getLocale() == null) {
            invited.changeLocale(claims.locale());
        }
        return invited;
    }

    private UserAccount joinOrCreate(ExternalIdentityClaims claims, Instant now) {
        String email = claims.verifiedEmail();
        if (email == null) {
            throw new ExternalSignInException(Reason.EMAIL_UNVERIFIED,
                    "%s does not vouch for an address of subject %s".formatted(claims.registrationId(),
                            claims.subject()));
        }
        Optional<UserAccount> existing = userRepository.findByEmail(UserAccount.normalizeEmail(email));
        if (existing.isPresent()) {
            return join(existing.get(), claims, now);
        }
        if (!properties.createsExternalAccounts()) {
            throw new ExternalSignInException(Reason.NO_ACCOUNT,
                    "No account for the address from %s, and none is created".formatted(claims.registrationId()));
        }
        UserAccount created = UserAccount.external(email, claims.accountName(properties.nameMode()), now);
        created.changeLocale(claims.locale());
        created.grant(defaultRole());
        userRepository.save(created);
        link(created, claims, now);
        LOG.info("Account {} created on the first sign-in through {}", created.getId(), claims.registrationId());
        return created;
    }

    /**
     * Joins an account that has the address the provider vouches for.
     *
     * <p>An open invitation (address, no way in yet) is redeemed by it: the
     * provider proves the mailbox as the link in the mail would. Joining a
     * claimed account can be switched off; a password on it that was set
     * before the address was ever confirmed is thrown away, because it may
     * belong to someone who registered with this address to wait for its
     * owner.
     */
    private UserAccount join(UserAccount user, ExternalIdentityClaims claims, Instant now) {
        boolean ownerSettledNow = !user.isClaimed() || !user.isEmailVerified();
        if (user.isClaimed()) {
            if (!properties.oauth2().linkByEmail()) {
                throw new ExternalSignInException(Reason.NOT_LINKED,
                        "Account %d is not linked to %s, and joining by address is off"
                                .formatted(user.getId(), claims.registrationId()));
            }
            if (user.externalIdentity(claims.registrationId()).isPresent()) {
                throw alreadyLinked(claims);
            }
        }
        if (!user.isEmailVerified()) {
            if (user.hasPassword()) {
                user.dropUnconfirmedPassword();
                LOG.warn("Account {}: password set before the address was confirmed dropped on the first "
                        + "sign-in through {}", user.getId(), claims.registrationId());
                events.publishEvent(new PasswordChanged(idOf(user),
                        user.getEmail()));
            }
            user.activateAfterEmailVerification();
        }
        if (ownerSettledNow) {
            // Verification and invitation links sent before lead nowhere any
            // more: the provider has settled who owns the address. A reset
            // link the confirmed owner asked for stays untouched.
            tokenRepository.invalidateAllOpenTokens(idOf(user), now);
        }
        link(user, claims, now);
        return user;
    }

    private void link(UserAccount user, ExternalIdentityClaims claims, Instant now) {
        boolean known = user.externalIdentity(claims.registrationId()).isPresent();
        try {
            user.linkExternalIdentity(claims.registrationId(), claims.subject(), claims.email(), now);
        } catch (IllegalStateException _) {
            throw alreadyLinked(claims);
        }
        if (!known) {
            Long userId = idOf(user);
            LOG.info("Account {} linked to {}", userId, claims.registrationId());
            events.publishEvent(new ExternalIdentityLinked(userId, user.getEmail(), claims.registrationId()));
        }
    }

    /**
     * The end of every way in: the time, the address the provider reports
     * now, and the failures of the password sign-in forgotten -- the provider
     * proved possession more strongly than a reset mail, which lifts a lock
     * too.
     */
    private IdentityUserDetails signedIn(UserAccount user, ExternalIdentityClaims claims, Instant now) {
        // Every way here ends in a claimed account with an address; a
        // failure would be a bug in this class, not a refusal.
        IdentityUserDetails details = IdentityUserDetailsService.toUserDetails(user)
                .orElseThrow(() -> new IllegalStateException("Account " + user.getId() + " cannot sign in"));
        if (!details.isEnabled()) {
            throw new ExternalSignInException(Reason.DISABLED, "Account " + user.getId() + " is disabled");
        }
        user.externalIdentity(claims.registrationId()).ifPresent(identity -> identity.recordUse(claims.email(), now));
        user.recordLogin(now);
        user.clearFailedLogins();
        LoginThrottle loginThrottle = throttle.getIfAvailable();
        if (loginThrottle != null) {
            loginThrottle.recordSuccess(details.getUsername());
        }
        return details;
    }

    private static Long idOf(UserAccount user) {
        return Objects.requireNonNull(user.getId(), "user.id");
    }

    private static ExternalSignInException alreadyLinked(ExternalIdentityClaims claims) {
        return new ExternalSignInException(Reason.ALREADY_LINKED,
                "The identity at %s belongs to another account, or the account has another one there"
                        .formatted(claims.registrationId()));
    }

    private Role defaultRole() {
        return roleRepository.findByCode(properties.defaultRoleCode())
                .orElseThrow(() -> new IdentityException(IdentityMessageKeys.DEFAULT_ROLE_MISSING,
                        "No role with code %s — is it declared in a RoleCatalog?"
                                .formatted(properties.defaultRoleCode())));
    }
}
