package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityProperties;

/**
 * Builds the mail implementations that work without a mail library. It exists
 * only so that the auto-configuration does not have to reach for
 * package-private classes: {@link TransportingIdentityMailSender} and
 * {@link LoggingMailTransport} stay enclosed in the package this way.
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

    /**
     * The default sender over the given transport: the building block renders,
     * the transport delivers. Since 0.9.0 the seam for an application that
     * sends through mail accounts of its own choosing.
     */
    public static IdentityMailSender viaTransport(IdentityMailTransport transport, IdentityProperties properties,
                                                  IdentityMessages messages) {
        return new TransportingIdentityMailSender(transport, properties, messages);
    }

    /** The transport that writes to the log instead of delivering. */
    public static IdentityMailTransport logOnlyTransport() {
        return new LoggingMailTransport();
    }

    /** Sender and transport for an application without any mail delivery: everything goes to the log. */
    public static IdentityMailSender logOnly(IdentityProperties properties, IdentityMessages messages) {
        return viaTransport(logOnlyTransport(), properties, messages);
    }
}
