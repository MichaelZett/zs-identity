package de.zettsystems.identity.values;

/**
 * Who may register themselves ({@code zs.identity.registration-mode}, since
 * 1.5.0). Invitations ({@code InvitationService}) are not affected by any of
 * them: an administrator can always invite.
 *
 * <p>The older switch {@code zs.identity.self-registration-enabled} still
 * works: {@code false} means {@link #CLOSED}, {@code true} leaves the mode
 * alone.
 */
public enum RegistrationMode {

    /** Anybody may register. */
    OPEN,

    /**
     * Registration needs an invitation code, which the application hands out
     * and checks through a {@code RegistrationGate} bean. The building block
     * asks the gate before an account is created and hands the code on with
     * {@link AccountRegistered}; what a code means stays with the application.
     */
    CODE,

    /** Nobody may register; accounts come from an administrator only. */
    CLOSED;

    /** Whether the registration form is offered at all. */
    public boolean allowsRegistration() {
        return this != CLOSED;
    }
}
