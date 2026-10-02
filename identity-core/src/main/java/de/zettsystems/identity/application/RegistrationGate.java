package de.zettsystems.identity.application;

/**
 * Decides which invitation codes open the registration (since 1.5.0).
 *
 * <p>Only the application knows its codes: what one stands for, how long it
 * holds, how often it may be used. The building block asks this bean before
 * it creates an account and hands an admitted code back with
 * {@link de.zettsystems.identity.values.AccountRegistered} once the account is
 * usable. An example from an application:
 *
 * <pre>
 * &#64;Component
 * class InvitationCodes implements RegistrationGate {
 *     &#64;Override
 *     public boolean admits(String code) {
 *         return codeRepository.findActive(code).isPresent();
 *     }
 * }
 * </pre>
 *
 * <p>With {@code zs.identity.registration-mode=CODE} the bean is mandatory --
 * the application does not start without one. In the mode {@code OPEN} a code
 * is optional, but one that is handed in is checked all the same, and without
 * a bean it is turned down: a code that reaches the event has always been
 * admitted here.
 *
 * <p>The answer is all the building block learns. Whatever the application
 * wants to remember about the use of a code (a counter, a log) it does in its
 * listener for {@code AccountRegistered}, not here: this method also runs for
 * registrations that fail afterwards, an address already taken, say.
 */
@FunctionalInterface
public interface RegistrationGate {

    /**
     * Whether the code opens the registration.
     *
     * @param code as entered, without surrounding blanks, never empty and at
     *             most 100 characters long
     */
    boolean admits(String code);
}
