package de.zettsystems.identity.values;

/**
 * An administrator started acting as a managed account (since 1.5.0). From now
 * on the session belongs to {@code targetUserId}, while
 * {@code ImpersonationService#currentImpersonator()} still names who is really
 * at the keyboard.
 *
 * <p>Published when the session has been switched. There is no transaction to
 * wait for -- nothing in the database changes -- so an
 * {@code @EventListener} is the right listener, for a log entry, say.
 *
 * @param actorUserId  the administrator
 * @param targetUserId the managed account
 */
public record ImpersonationStarted(Long actorUserId, Long targetUserId) {
}
