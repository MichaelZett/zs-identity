package de.zettsystems.identity.application;

import de.zettsystems.identity.values.IdentityMail;
import de.zettsystems.identity.values.IdentityMailType;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Turns "this account, this link, this kind of mail" into subject, text and
 * HTML part.
 *
 * <p>Deliberately plain mails without a templating engine: the building block
 * should hold no opinion about which template library an application uses.
 * Whoever wants designed mails provides an {@link IdentityMailSender} of their
 * own.
 *
 * <p>The texts come from {@link IdentityMessages}. The language is that of the
 * account, or {@code zs.identity.locale} if it has chosen none: at delivery
 * time there is no browser whose language setting could be asked, which is why
 * it sits on the account since V1_4.
 */
final class IdentityMailRenderer {

    private final IdentityProperties properties;
    private final IdentityMessages messages;

    IdentityMailRenderer(IdentityProperties properties, IdentityMessages messages) {
        this.properties = properties;
        this.messages = messages;
    }

    IdentityMail render(IdentityMailType type, UserAccountDto user, String url) {
        Duration linkValidity = type == IdentityMailType.INVITATION
                ? properties.invitationValidity()
                : properties.tokenValidity();
        return render(type, user, url, linkValidity);
    }

    /**
     * @param duration what the third placeholder says: how long the link is
     *                 valid, or for the lock notice how long the lock lasts
     */
    IdentityMail render(IdentityMailType type, UserAccountDto user, String url, Duration duration) {
        Locale locale = user.localeOr(properties.locale());
        String prefix = switch (type) {
            case EMAIL_VERIFICATION -> "identity.mail.verification.";
            case PASSWORD_RESET -> "identity.mail.reset.";
            case INVITATION -> "identity.mail.invitation.";
            case ACCOUNT_TEMPORARILY_LOCKED -> "identity.mail.locked.";
        };

        String subject = messages.get(prefix + "subject", locale);
        String validity = humanReadableValidity(locale, duration);
        String text = messages.get(prefix + "body", locale, user.displayName(), url, validity);
        // The placeholders are escaped before being inserted: MessageFormat
        // knows no HTML, and a display name must not break out of the markup.
        String html = messages.get(prefix + "body.html", locale,
                escapeHtml(user.displayName()), escapeHtml(url), escapeHtml(validity));
        // Managed accounts have no address, but they go through neither
        // registration nor password reset; arriving here would be a
        // programming error, not a runtime case.
        return new IdentityMail(type, Objects.requireNonNull(user.email(), "email"), subject, text, html);
    }

    /**
     * The deadline in the largest unit that comes out even: writing a
     * seven-day invitation as "168 hours" would be correct and unreadable.
     *
     * <p>Only <strong>above</strong> a day, and only for whole days: the
     * existing deadline of 24 hours should keep reading "24 hours" in the
     * existing mails, and "25 hours" is more honest than "one day".
     */
    private String humanReadableValidity(Locale locale, Duration validity) {
        long hours = validity.toHours();
        if (hours > 24 && validity.toHoursPart() == 0 && validity.toMinutesPart() == 0) {
            return messages.get("identity.mail.validity.days", locale, validity.toDays());
        }
        if (hours >= 1) {
            return messages.get("identity.mail.validity.hours", locale, hours);
        }
        return messages.get("identity.mail.validity.minutes", locale, Math.max(1, validity.toMinutes()));
    }

    /**
     * Escapes the five characters that carry meaning in text content and
     * inside a double-quoted attribute. A library for this would force a
     * dependency on the building block that it otherwise does not need.
     */
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
