package de.zettsystems.identity.application;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Selbstregistrierung und Bestätigung der E-Mail-Adresse. */
public interface RegistrationService {

    /** Ob die Oberfläche eine Registrierung anbieten soll. */
    boolean isSelfRegistrationEnabled();

    /**
     * Ob ein Konto erst nach bestätigter Adresse nutzbar ist. Die Oberfläche
     * bietet nur dann an, die Bestätigungsmail erneut anzufordern — ohne
     * Bestätigungspflicht führte der Weg ins Leere.
     */
    boolean isEmailVerificationRequired();

    /**
     * Legt ein Konto an und verschickt — sofern Bestätigung verlangt wird — die
     * Bestätigungsmail. Das Konto ist bis dahin gesperrt.
     *
     * @throws IdentityException wenn die Selbstregistrierung abgeschaltet ist,
     *                           die Adresse schon vergeben oder das Passwort zu
     *                           kurz ist
     */
    UserAccountDto register(String email, String rawPassword, AccountName name);

    /** Klarnamen-Variante von {@link #register(String, String, AccountName)}. */
    default UserAccountDto register(String email, String rawPassword, String firstName, String lastName) {
        return register(email, rawPassword, AccountName.of(firstName, lastName));
    }

    /**
     * Wie {@link #register(String, String, AccountName)}, merkt sich aber die
     * Sprache, in der die Person sich registriert hat — die Oberfläche kennt
     * sie, der spätere Mailversand nicht mehr.
     *
     * <p>Bewusst eine {@code default}-Methode, die die Sprache verwirft, statt
     * einer abstrakten: Ein eigener {@code RegistrationService} einer
     * Anwendung soll durch diese Ergänzung nicht die Übersetzung verlieren.
     * Anders als bei {@code IdentityMailSender#sendInvitation} ist Verwerfen
     * hier richtig — ohne gemerkte Sprache gilt wieder
     * {@code zs.identity.locale}, und das ist genau das Verhalten von vorher.
     *
     * @param locale Sprache des Kontos, {@code null} für „keine eigene Wahl"
     */
    default UserAccountDto register(String email, String rawPassword, AccountName name,
                                    @Nullable Locale locale) {
        return register(email, rawPassword, name);
    }

    /**
     * Löst den Link aus der Bestätigungsmail ein und schaltet das Konto frei.
     *
     * @throws IdentityException wenn das Token unbekannt, benutzt oder abgelaufen ist
     */
    UserAccountDto confirmEmail(String token);

    /**
     * Schickt die Bestätigungsmail erneut. Meldet bewusst keinen Fehler, wenn
     * es die Adresse nicht gibt oder sie bereits bestätigt ist — sonst ließe
     * sich darüber herausfinden, wer ein Konto hat.
     */
    void resendVerification(String email);
}
