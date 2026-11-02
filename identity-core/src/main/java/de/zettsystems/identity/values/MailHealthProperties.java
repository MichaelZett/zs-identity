package de.zettsystems.identity.values;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings of the optional health component {@code mail}, prefix
 * {@code zs.identity.mail-health}. A record of its own rather than a part of
 * {@link IdentityProperties}: it is bound only when the feature is in play,
 * and that record already carries a constructor per past shape.
 *
 * @param enabled  whether the component exists. Off by default: an application
 *                 that does nothing keeps its health endpoint exactly as it
 *                 was, and no login at the mail server happens on its behalf.
 * @param interval how old the last result may get before a health request
 *                 starts a new check in the background
 */
@ConfigurationProperties(prefix = "zs.identity.mail-health")
public record MailHealthProperties(@DefaultValue("false") boolean enabled, @DefaultValue("3h") Duration interval) {
}
