package de.zettsystems.identity.application;

/**
 * The application's rule on who may act as which managed account (since
 * 1.5.0), say "administrators of the same division".
 *
 * <p>The building block asks this bean on top of its own rules, which no
 * policy can lift: the target is a managed account (no address, no way to
 * sign in), it is not the actor, and the actor is not acting as somebody
 * already. Without a bean nobody may act as anybody. An example:
 *
 * <pre>
 * &#64;Component
 * class SameDivision implements ImpersonationPolicy {
 *     &#64;Override
 *     public boolean mayImpersonate(long actorUserId, long targetUserId) {
 *         return divisions.adminOfDivisionOf(actorUserId, targetUserId);
 *     }
 * }
 * </pre>
 *
 * <p>Asked when an impersonation starts and again whenever it is checked
 * ({@link ImpersonationService#verify()}): an administrator who loses the
 * right in between does not keep it until signing out.
 */
@FunctionalInterface
public interface ImpersonationPolicy {

    boolean mayImpersonate(long actorUserId, long targetUserId);
}
