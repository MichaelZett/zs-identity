package de.zettsystems.identity.values;

import java.util.Objects;

/**
 * A mail of this building block, rendered and ready to leave: the content is
 * ours, the delivery is the application's.
 *
 * <p>Since 0.9.0 the building block renders subject, text and HTML part
 * itself -- in the language of the account, with the link, the validity and
 * the HTML escaping done -- and hands the result to an
 * {@code IdentityMailTransport}. An application that wants to send through a
 * mail account of its own choosing (per club, per tournament, with a sender
 * address the relay accepts) implements only the transport and never touches
 * the texts.
 *
 * <p>Deliberately free of any mail-library type: the record has to be loadable
 * in an application that brings no {@code jakarta.mail}.
 *
 * @param type    what the mail is, for routing in the transport
 * @param to      the recipient's address; never blank
 * @param subject the rendered subject
 * @param text    the plain-text part
 * @param html    the HTML part, an alternative to {@code text} with the link as
 *                a real anchor -- Outlook otherwise breaks long links in plain
 *                text
 */
public record IdentityMail(IdentityMailType type, String to, String subject, String text, String html) {

    public IdentityMail {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(html, "html");
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("an identity mail needs a recipient");
        }
    }
}
