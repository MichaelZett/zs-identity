package de.zettsystems.identity.values;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Locale;

/**
 * Einstellungen des Identity-Bausteins, Präfix {@code zs.identity}.
 *
 * @param selfRegistrationEnabled  ob sich Personen selbst registrieren dürfen.
 *                                 Aus bedeutet: Konten legt nur eine
 *                                 Administration an.
 * @param emailVerificationRequired ob ein Konto erst nach bestätigter Adresse
 *                                 nutzbar ist. Aus bedeutet: sofort freigeschaltet
 *                                 — nur sinnvoll, wenn kein Mailversand da ist.
 * @param tokenValidity            wie lange Bestätigungs- und Reset-Links gelten
 * @param invitationValidity       wie lange Einladungen gelten. Eigene Frist,
 *                                 weil eine Einladung niemand angefordert hat:
 *                                 Sie liegt im Postfach, bis jemand Zeit hat,
 *                                 und darf nicht über Nacht verfallen.
 * @param passwordMinLength        Mindestlänge neuer Passwörter
 * @param fromAddress              Absender der Bausteinsmails
 * @param fromName                 Anzeigename des Absenders
 * @param baseUrl                  öffentliche Basis-URL für die Links in den
 *                                 Mails, ohne abschließenden Schrägstrich
 * @param defaultRoleCode          Rolle, die neue Konten bekommen
 * @param nameMode                 welche Namensangaben die Registrierung
 *                                 verlangt (siehe {@link NameMode})
 * @param locale                   Sprache der Mails und Rückfallsprache der
 *                                 Oberfläche. Bringt die Anwendung eine eigene
 *                                 Sprachwahl mit (Vaadins {@code I18NProvider}),
 *                                 folgen die Ansichten dieser; sonst gilt diese
 *                                 Einstellung. Mitgeliefert sind {@code de} und
 *                                 {@code en}, bei allem anderen greift Englisch.
 *                                 Ein Konto kann eine eigene Sprache tragen —
 *                                 dann gilt dessen (siehe
 *                                 {@code UserAccountDto#locale()}).
 * @param ui                       Aussehen der mitgelieferten Ansichten
 *                                 (siehe {@link UiSettings}); ohne Vaadin im
 *                                 Klassenpfad ohne Wirkung
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
                                 @DefaultValue UiSettings ui) {

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

    /** Baut eine absolute Adresse für den Mailversand. */
    public String urlFor(String path) {
        return path.startsWith("/") ? baseUrl + path : baseUrl + "/" + path;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Dieselben Einstellungen in einer anderen Sprache. Der Record hat viele
     * Komponenten, und ein Test, der nur die Sprache wechseln will, soll sie
     * nicht alle abschreiben müssen — sonst kostet jede neue Einstellung eine
     * Änderung in jedem Test, auch in denen einbindender Anwendungen.
     */
    public IdentityProperties withLocale(Locale newLocale) {
        return new IdentityProperties(selfRegistrationEnabled, emailVerificationRequired, tokenValidity,
                invitationValidity, passwordMinLength, fromAddress, fromName, baseUrl, defaultRoleCode,
                nameMode, newLocale, ui);
    }

    /** Dieselben Einstellungen mit anderem Erscheinungsbild — siehe {@link #withLocale(Locale)}. */
    public IdentityProperties withUi(UiSettings newUi) {
        return new IdentityProperties(selfRegistrationEnabled, emailVerificationRequired, tokenValidity,
                invitationValidity, passwordMinLength, fromAddress, fromName, baseUrl, defaultRoleCode,
                nameMode, locale, newUi);
    }

    /** Voreinstellungen für Tests, die den Record von Hand bauen. */
    public static IdentityProperties defaults() {
        return new IdentityProperties(true, true, Duration.ofHours(24), Duration.ofDays(7), 12,
                "noreply@localhost", "Application", "http://localhost:8080", "USER", NameMode.FULL_NAME,
                Locale.GERMAN, UiSettings.defaults());
    }
}
