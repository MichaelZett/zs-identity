package de.zettsystems.identity.values;

/**
 * Acting as a managed account has ended and the session belongs to the
 * administrator again (since 1.5.0): ended on purpose
 * ({@code ImpersonationService#stop()}), or by the building block because the
 * account no longer allows it -- its person has redeemed the invitation, say.
 *
 * @param actorUserId  the administrator, whose session it is again
 * @param targetUserId the account acted as
 */
public record ImpersonationEnded(Long actorUserId, Long targetUserId) {
}
