package de.zettsystems.identity.values;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Locale;

/**
 * Settings of the identity building block, prefix {@code zs.identity}.
 *
 * @param selfRegistrationEnabled  whether people may register themselves. Off
 *                                 means accounts are created by an
 *                                 administrator only.
 * @param emailVerificationRequired whether an account becomes usable only once
 *                                 its address is confirmed. Off means enabled
 *                                 immediately, which makes sense only when
 *                                 there is no mail delivery.
 * @param tokenValidity            how long verification and reset links are valid
 * @param invitationValidity       how long invitations are valid. It has a
 *                                 deadline of its own because nobody asked for
 *                                 an invitation: it sits in the inbox until
 *                                 someone has time, and must not expire
 *                                 overnight.
 * @param passwordMinLength        minimum length of new passwords
 * @param fromAddress              sender of the mails this block sends
 * @param fromName                 display name of the sender
 * @param baseUrl                  public base URL for the links in the mails,
 *                                 without a trailing slash
 * @param defaultRoleCode          role that new accounts receive
 * @param nameMode                 which name the registration requires (see
 *                                 {@link NameMode})
 * @param locale                   language of the mails and fallback language
 *                                 of the UI. If the application brings its own
 *                                 language selection (Vaadin's
 *                                 {@code I18NProvider}), the views follow that
 *                                 one; otherwise this setting applies. Shipped
 *                                 are {@code de} and {@code en}; anything else
 *                                 falls back to English. An account may carry a
 *                                 language of its own, which then wins (see
 *                                 {@code UserAccountDto#locale()}).
 * @param ui                       appearance of the shipped views (see
 *                                 {@link UiSettings}); without Vaadin on the
 *                                 classpath it has no effect
 * @param migrations               how the building block's own database
 *                                 migrations run (see {@link MigrationSettings})
 * @param passkeys                 sign-in with passkeys, off unless switched
 *                                 on (see {@link PasskeySettings})
 */
@ConfigurationProperties(prefix = "zs.identity")
public record IdentityProperties(@DefaultValue("true") boolean selfRegistrationEnabled,
                                 @DefaultValue("true") boolean emailVerificationRequired,
                                 @DefaultValue("24h") Duration tokenValidity,
                                 @DefaultValue("7d") Duration invitationValidity,
                                 @DefaultValue("12") int passwordMinLength,
                                 @DefaultValue("noreply@localhost") String fromAddress,
                                 @DefaultValue("Application") String fromName,
                                 @DefaultValue("http://localhost:8080") String baseUrl,
                                 @DefaultValue("USER") String defaultRoleCode,
                                 @DefaultValue("FULL_NAME") NameMode nameMode,
                                 @DefaultValue("de") Locale locale,
                                 @DefaultValue UiSettings ui,
                                 @DefaultValue MigrationSettings migrations,
                                 @DefaultValue PasskeySettings passkeys) {

    /**
     * The shape before 0.11.0, kept so that applications and tests that build
     * the record by hand keep compiling. Passkeys stay off, as they were.
     */
    public IdentityProperties(boolean selfRegistrationEnabled, boolean emailVerificationRequired,
                              Duration tokenValidity, Duration invitationValidity, int passwordMinLength,
                              String fromAddress, String fromName, String baseUrl, String defaultRoleCode,
                              NameMode nameMode, Locale locale, UiSettings ui, MigrationSettings migrations) {
        this(selfRegistrationEnabled, emailVerificationRequired, tokenValidity, invitationValidity,
                passwordMinLength, fromAddress, fromName, baseUrl, defaultRoleCode, nameMode, locale, ui,
                migrations, PasskeySettings.defaults());
    }

    /**
     * The shape before 0.8.0, kept for the same reason. Migrations run
     * automatically, as they always did.
     */
    public IdentityProperties(boolean selfRegistrationEnabled, boolean emailVerificationRequired,
                              Duration tokenValidity, Duration invitationValidity, int passwordMinLength,
                              String fromAddress, String fromName, String baseUrl, String defaultRoleCode,
                              NameMode nameMode, Locale locale, UiSettings ui) {
        this(selfRegistrationEnabled, emailVerificationRequired, tokenValidity, invitationValidity,
                passwordMinLength, fromAddress, fromName, baseUrl, defaultRoleCode, nameMode, locale, ui,
                MigrationSettings.defaults(), PasskeySettings.defaults());
    }

    @ConstructorBinding
    public IdentityProperties {
        if (passwordMinLength < 8) {
            throw new IllegalArgumentException(
                    "zs.identity.password-min-length must be at least 8, was " + passwordMinLength);
        }
        if (tokenValidity.isZero() || tokenValidity.isNegative()) {
            throw new IllegalArgumentException("zs.identity.token-validity must be positive");
        }
        if (invitationValidity.isZero() || invitationValidity.isNegative()) {
            throw new IllegalArgumentException("zs.identity.invitation-validity must be positive");
        }
        baseUrl = stripTrailingSlash(baseUrl);
    }

    /** Builds an absolute address for the mails. */
    public String urlFor(String path) {
        return path.startsWith("/") ? baseUrl + path : baseUrl + "/" + path;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * The same settings in another language. The record has many components,
     * and a test that only wants to switch the language should not have to
     * copy them all. Otherwise every new setting costs a change in every test,
     * including those of embedding applications.
     */
    public IdentityProperties withLocale(Locale newLocale) {
        return new IdentityProperties(selfRegistrationEnabled, emailVerificationRequired, tokenValidity,
                invitationValidity, passwordMinLength, fromAddress, fromName, baseUrl, defaultRoleCode,
                nameMode, newLocale, ui, migrations, passkeys);
    }

    /** The same settings with a different appearance; see {@link #withLocale(Locale)}. */
    public IdentityProperties withUi(UiSettings newUi) {
        return new IdentityProperties(selfRegistrationEnabled, emailVerificationRequired, tokenValidity,
                invitationValidity, passwordMinLength, fromAddress, fromName, baseUrl, defaultRoleCode,
                nameMode, locale, newUi, migrations, passkeys);
    }

    /** The same settings with the migrations run differently; see {@link #withLocale(Locale)}. */
    public IdentityProperties withMigrations(MigrationSettings newMigrations) {
        return new IdentityProperties(selfRegistrationEnabled, emailVerificationRequired, tokenValidity,
                invitationValidity, passwordMinLength, fromAddress, fromName, baseUrl, defaultRoleCode,
                nameMode, locale, ui, newMigrations, passkeys);
    }

    /** The same settings with passkeys set up differently; see {@link #withLocale(Locale)}. */
    public IdentityProperties withPasskeys(PasskeySettings newPasskeys) {
        return new IdentityProperties(selfRegistrationEnabled, emailVerificationRequired, tokenValidity,
                invitationValidity, passwordMinLength, fromAddress, fromName, baseUrl, defaultRoleCode,
                nameMode, locale, ui, migrations, newPasskeys);
    }

    /** Defaults for tests that build the record by hand. */
    public static IdentityProperties defaults() {
        return new IdentityProperties(true, true, Duration.ofHours(24), Duration.ofDays(7), 12,
                "noreply@localhost", "Application", "http://localhost:8080", "USER", NameMode.FULL_NAME,
                Locale.GERMAN, UiSettings.defaults(), MigrationSettings.defaults(), PasskeySettings.defaults());
    }
}
