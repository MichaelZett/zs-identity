package de.zettsystems.identity.values;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Settings for the shipped views, prefix {@code zs.identity.ui}.
 *
 * <p>The defaults are chosen so that an application does not have to set
 * anything here: a narrow, centred form column that works from a phone up to a
 * desktop. Anyone who wants more can style through the fixed CSS classes of
 * the views ({@code identity-view}, {@code identity-view__form} and one per
 * view), which needs no setting at all.
 *
 * <p>This lives in {@code identity-core} despite its purpose: the building
 * block binds all settings to one record under {@code zs.identity}, and the
 * alternative -- a second {@code @ConfigurationProperties} record in the Vaadin
 * module -- would have required an auto-configuration of its own over there.
 * Vaadin does not appear here; the core stays free of it.
 *
 * @param maxWidth            the width beyond which the form column stops
 *                            growing. A CSS length, so it carries a unit.
 *                            Nobody enjoys reading lines longer than about 60
 *                            characters, and a form spanning the whole screen
 *                            looks lost on a desktop.
 * @param classNames          additional CSS classes on every view of this
 *                            building block. This is where an application's
 *                            theme attaches: it adds its own class (say
 *                            {@code my-app-card}) and styles through it,
 *                            without the building block knowing the theme.
 * @param notificationDuration how long the notifications of the views stay on
 *                            screen. {@link Duration#ZERO} keeps them until
 *                            someone dismisses them, which helps applications
 *                            with long texts or screen readers.
 */
public record UiSettings(@DefaultValue("28rem") String maxWidth,
                         @DefaultValue({}) List<String> classNames,
                         @DefaultValue("5s") Duration notificationDuration) {

    public UiSettings {
        if (maxWidth.isBlank()) {
            throw new IllegalArgumentException("zs.identity.ui.max-width must not be blank");
        }
        if (notificationDuration.isNegative()) {
            throw new IllegalArgumentException("zs.identity.ui.notification-duration must not be negative");
        }
        classNames = List.copyOf(classNames);
    }

    /** Defaults for tests that build the record by hand. */
    public static UiSettings defaults() {
        return new UiSettings("28rem", List.of(), Duration.ofSeconds(5));
    }
}
