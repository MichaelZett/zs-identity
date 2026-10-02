package de.zettsystems.identity.application;

import de.zettsystems.identity.values.Impersonation;

import java.util.Optional;

/**
 * Lets an administrator act as a managed account in the running session, the
 * way Spring Security's {@code SwitchUserFilter} does, but callable from a
 * Vaadin view (since 1.5.0).
 *
 * <p>The use: during a pilot an administrator keeps people who do not sign in
 * yet -- a parent with two children, say --, uses the application as them to
 * try everything out, and invites them to take their accounts over later.
 *
 * <p><strong>The rules</strong>, which no {@link ImpersonationPolicy} can
 * lift:
 * <ul>
 *   <li>Only a <em>managed</em> account can be acted as: no address, no
 *       password, no external identity. An account a person can sign in with
 *       is never one, not even for a system administrator.</li>
 *   <li>Not oneself, and not from within an impersonation.</li>
 *   <li>Once the account belongs to a person -- the invitation is redeemed --,
 *       or it is deleted or disabled, or the policy no longer allows it, the
 *       impersonation ends at the next check ({@link #verify()}). The shipped
 *       views check on every navigation.</li>
 *   <li>While it runs, the settings of the account are locked: password,
 *       passkeys, linked providers, the address and deleting it. The shipped
 *       views show a notice instead; the services turn such calls for the
 *       account down with {@code IMPERSONATION_RESTRICTED}. Screens of the
 *       application's own (address, "sign out everywhere") ask
 *       {@link #current()} and lock themselves.</li>
 * </ul>
 *
 * <p>The principal of the session is then an {@link ImpersonatedUser}: an
 * {@link IdentityUserDetails} with the id, name and roles of the target, so
 * that everything that reads the current account sees the target. Its
 * {@code getUsername()} is no address -- a managed account has none --, so
 * whoever looks the current account up by address should take
 * {@link IdentityUserDetails#userId()} instead.
 *
 * <p><strong>Signing out</strong> during an impersonation should mean "back to
 * me": a sign-out button calls {@link #stop()} first and signs out only when
 * that returns {@code false}.
 */
public interface ImpersonationService {

    /**
     * Switches the running session to the managed account and publishes
     * {@link de.zettsystems.identity.values.ImpersonationStarted}.
     *
     * @throws IdentityException {@code IMPERSONATION_NOT_ALLOWED} when one of
     *                           the rules or the policy says no,
     *                           {@code ACCOUNT_NOT_FOUND} when there is no
     *                           such account
     */
    Impersonation start(Long targetUserId);

    /**
     * Gives the session back to the administrator and publishes
     * {@link de.zettsystems.identity.values.ImpersonationEnded}.
     *
     * @return whether an impersonation was running
     */
    boolean stop();

    /** The running impersonation of this session; reads the session only. */
    Optional<Impersonation> current();

    /** Who really acts while the session is somebody else's; empty otherwise. */
    default Optional<Long> currentImpersonator() {
        return current().map(Impersonation::actorUserId);
    }

    /**
     * Checks the running impersonation against the database and the policy
     * and ends it when either no longer allows it.
     *
     * @return whether an impersonation is still running
     */
    boolean verify();
}
