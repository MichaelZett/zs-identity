package de.zettsystems.identity.application;

/**
 * Builds the mail implementations that work without a mail library. It exists
 * only so that the auto-configuration does not have to reach for
 * package-private classes: {@link LoggingIdentityMailSender} stays enclosed in
 * the package this way.
 *
 * <p><strong>No mail type in this class.</strong> As of 0.7.0
 * {@code spring-boot-starter-mail} is an optional dependency; whoever does not
 * bring it gets the fallback here. If a method with {@code JavaMailSender} in
 * its signature sat next to it, loading this class would depend on a library
 * that then does not exist. The mail variant therefore lives in
 * {@link JavaMailFactory}.
 */
public final class IdentityMailFactory {

    private IdentityMailFactory() {
        // Factory methods
    }

    public static IdentityMailSender logOnly() {
        return new LoggingIdentityMailSender();
    }
}
