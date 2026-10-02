package de.zettsystems.identity.values;

/**
 * The running impersonation of the current session (since 1.5.0): what an
 * application needs for a bar like "You are acting as Anna · Back to me", and
 * for recording who really did something.
 *
 * @param actorUserId       the administrator at the keyboard
 * @param targetUserId      the account the session acts as
 * @param targetDisplayName its display name
 */
public record Impersonation(Long actorUserId, Long targetUserId, String targetDisplayName) {
}
