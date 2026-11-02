package de.zettsystems.identity.application;

/**
 * Told how each delivery went, for an application that wants to know whether
 * its mail server is working.
 *
 * <p>Entirely optional: without a bean of this type nothing is reported. The
 * default transport tells a unique bean by itself. A transport of the
 * application's own calls it where it likes, or not at all. The type carries
 * no mail or health library in its signature on purpose, so an application
 * can implement it with neither on the classpath.
 *
 * <p>Implementations must return quickly and must not throw: they run inside
 * the delivery, and a mail must never fail over its bookkeeping.
 */
public interface MailDeliveryListener {

    /** The listener used when the application brings none. */
    MailDeliveryListener NONE = new MailDeliveryListener() {
        @Override
        public void delivered() {
            // Nobody is listening
        }

        @Override
        public void failed() {
            // Nobody is listening
        }
    };

    /** The mail server accepted a mail. */
    void delivered();

    /**
     * A delivery failed. That alone does not prove the server is gone: a
     * rejected address fails the same way.
     */
    void failed();
}
