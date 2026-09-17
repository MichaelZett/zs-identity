package de.zettsystems.identity.application;

/** Forgotten passwords and setting a new one. */
public interface PasswordResetService {

    /**
     * Sends a link for resetting the password.
     *
     * <p>Deliberately reports no error when the address does not exist:
     * otherwise this form could be used to find out who has an account here.
     * The UI shows the same confirmation in both cases.
     */
    void requestReset(String email);

    /**
     * Sets a new password using the token from the mail.
     *
     * @throws IdentityException if the token is unknown, already used or
     *                           expired, or the password is too short
     */
    void resetPassword(String token, String newRawPassword);
}
