package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.Impersonation;
import de.zettsystems.identity.values.ImpersonationEnded;
import de.zettsystems.identity.values.ImpersonationStarted;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

class ImpersonationServiceImpl implements ImpersonationService {

    private static final Logger LOG = LoggerFactory.getLogger(ImpersonationServiceImpl.class);

    private final UserAccountRepository userRepository;
    private final @Nullable ImpersonationPolicy policy;
    private final ApplicationEventPublisher events;
    /** Set in tests only; in production the repository is created when saving. */
    private final @Nullable SecurityContextRepository contextRepository;

    ImpersonationServiceImpl(UserAccountRepository userRepository, @Nullable ImpersonationPolicy policy,
                             ApplicationEventPublisher events) {
        this(userRepository, policy, events, null);
    }

    ImpersonationServiceImpl(UserAccountRepository userRepository, @Nullable ImpersonationPolicy policy,
                             ApplicationEventPublisher events,
                             @Nullable SecurityContextRepository contextRepository) {
        this.userRepository = userRepository;
        this.policy = policy;
        this.events = events;
        this.contextRepository = contextRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Impersonation start(Long targetUserId) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || !current.isAuthenticated()
                || !(current.getPrincipal() instanceof IdentityUserDetails actor)) {
            throw notAllowed("Nobody is signed in to act for anybody");
        }
        if (actor instanceof ImpersonatedUser) {
            throw notAllowed("Already acting as account " + actor.userId());
        }
        Long actorUserId = actor.userId();
        if (actorUserId.equals(targetUserId)) {
            throw notAllowed("Acting as oneself");
        }
        UserAccount target = userRepository.findWithRolesById(targetUserId)
                .orElseThrow(() -> new IdentityException(IdentityMessageKeys.ACCOUNT_NOT_FOUND,
                        "No account with id %d".formatted(targetUserId)));
        if (!target.isManaged() || target.isClaimed() || !target.isEnabled()) {
            throw notAllowed("Account %d is not a managed account".formatted(targetUserId));
        }
        if (!policyAllows(actorUserId, targetUserId)) {
            throw notAllowed("The policy does not let %d act as %d".formatted(actorUserId, targetUserId));
        }

        ImpersonatedUser principal = new ImpersonatedUser(targetUserId, target.getDisplayName(),
                IdentityUserDetailsService.toAuthorities(target), actorUserId, current);
        SecurityContexts.replace(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()), contextRepository);
        LOG.info("Account {} acts as managed account {}", actorUserId, targetUserId);
        events.publishEvent(new ImpersonationStarted(actorUserId, targetUserId));
        return principal.impersonation();
    }

    @Override
    public boolean stop() {
        Optional<ImpersonatedUser> current = ImpersonatedUser.current();
        current.ifPresent(this::end);
        return current.isPresent();
    }

    @Override
    public Optional<Impersonation> current() {
        return ImpersonatedUser.current().map(ImpersonatedUser::impersonation);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verify() {
        Optional<ImpersonatedUser> current = ImpersonatedUser.current();
        if (current.isEmpty()) {
            return false;
        }
        ImpersonatedUser user = current.get();
        // Once the account was invited it has an address, but nobody can sign
        // in with it before the invitation is redeemed: the impersonation may
        // go on until then. Starting one needs a managed account all the same.
        boolean stillAllowed = userRepository.findById(user.userId())
                .filter(target -> !target.isClaimed() && target.isEnabled())
                .isPresent()
                && policyAllows(user.actorUserId(), user.userId());
        if (!stillAllowed) {
            LOG.info("Acting as account {} ended: it no longer allows it", user.userId());
            end(user);
        }
        return stillAllowed;
    }

    private void end(ImpersonatedUser user) {
        SecurityContexts.replace(user.actorAuthentication(), contextRepository);
        LOG.info("Account {} no longer acts as account {}", user.actorUserId(), user.userId());
        events.publishEvent(new ImpersonationEnded(user.actorUserId(), user.userId()));
    }

    private boolean policyAllows(Long actorUserId, Long targetUserId) {
        return policy != null && policy.mayImpersonate(actorUserId, targetUserId);
    }

    private static IdentityException notAllowed(String detail) {
        return new IdentityException(IdentityMessageKeys.IMPERSONATION_NOT_ALLOWED, detail);
    }
}
