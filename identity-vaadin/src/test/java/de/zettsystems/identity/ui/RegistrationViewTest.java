package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.NameMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Locale;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;

class RegistrationViewTest extends AbstractViewTest {

    private final FakeRegistrationService registrationService = new FakeRegistrationService();

    private RegistrationView showRegistrationView(IdentityProperties properties) {
        return show(new RegistrationView(registrationService, properties, MESSAGES));
    }

    private static IdentityProperties propertiesWith(NameMode nameMode, boolean verificationRequired) {
        return new IdentityProperties(true, verificationRequired, Duration.ofHours(24), Duration.ofDays(7), 12,
                "noreply@localhost", "Test", "http://localhost:8080", "USER", nameMode, Locale.GERMAN);
    }

    private void fillCredentials(RegistrationView view, String email, String password, String repeat) {
        _setValue(_get(view, EmailField.class), email);
        _setValue(_get(view, PasswordField.class, spec -> spec.withLabel("Passwort")), password);
        _setValue(_get(view, PasswordField.class, spec -> spec.withLabel("Passwort wiederholen")), repeat);
    }

    private static void submit(RegistrationView view) {
        _click(_get(view, Button.class, spec -> spec.withId("registration-submit-button")));
    }

    @Test
    void theFullNameModeAsksForFirstAndLastName() {
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.FULL_NAME, true));

        assertThat(_find(view, TextField.class)).extracting(TextField::getLabel)
                .containsExactly("Vorname", "Nachname");
        assertThat(view.getPageTitle()).isEqualTo("Registrieren");
    }

    @Test
    void theDisplayNameModeAsksForOneNameOnly() {
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.DISPLAY_NAME, true));

        assertThat(_find(view, TextField.class)).extracting(TextField::getLabel)
                .containsExactly("Anzeigename");
    }

    @Test
    void theMinimumPasswordLengthIsSpelledOut() {
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.FULL_NAME, true));

        assertThat(_get(view, PasswordField.class, spec -> spec.withLabel("Passwort")).getHelperText())
                .isEqualTo("Mindestens 12 Zeichen");
    }

    @Test
    void anEmptyFormIsRefusedBeforeTheServiceIsCalled() {
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.FULL_NAME, true));

        submit(view);

        assertThat(notificationTexts()).containsExactly("Bitte fülle alle Felder aus.");
        assertThat(registrationService.registeredEmails).isEmpty();
    }

    @Test
    void aMissingDisplayNameIsRefused() {
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.DISPLAY_NAME, true));
        fillCredentials(view, "anna@example.com", "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Bitte fülle alle Felder aus.");
    }

    @Test
    void aMalformedEmailAddressIsRefused() {
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.DISPLAY_NAME, true));
        _setValue(_get(view, TextField.class), "Anna");
        fillCredentials(view, "keine-adresse", "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Bitte gib eine gültige E-Mail-Adresse ein.");
        assertThat(registrationService.registeredEmails).isEmpty();
    }

    @Test
    void twoDifferentPasswordsAreRefused() {
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.DISPLAY_NAME, true));
        _setValue(_get(view, TextField.class), "Anna");
        fillCredentials(view, "anna@example.com", "sicheres-passwort", "anderes-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Die beiden Passwörter stimmen nicht überein.");
        assertThat(registrationService.registeredEmails).isEmpty();
    }

    @Test
    void aSuccessfulRegistrationHandsTheEnteredNameToTheService() {
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.FULL_NAME, true));
        _setValue(_get(view, TextField.class, spec -> spec.withLabel("Vorname")), "Anna");
        _setValue(_get(view, TextField.class, spec -> spec.withLabel("Nachname")), "Beispiel");
        fillCredentials(view, "anna@example.com", "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(registrationService.registeredEmails).containsExactly("anna@example.com");
        assertThat(registrationService.registeredNames).containsExactly(AccountName.of("Anna", "Beispiel"));
        assertThat(_get(view, H2.class).getText()).isEqualTo("Fast geschafft");
        assertThat(_get(view, Paragraph.class).getText())
                .as("die Adresse gehört in die Bestätigung, damit ein Tippfehler auffällt")
                .contains("anna@example.com");
    }

    @Test
    void withoutVerificationTheAccountIsUsableStraightAway() {
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.DISPLAY_NAME, false));
        _setValue(_get(view, TextField.class), "Anna");
        fillCredentials(view, "anna@example.com", "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(registrationService.registeredNames).containsExactly(AccountName.display("Anna"));
        assertThat(_get(view, Paragraph.class).getText())
                .isEqualTo("Dein Konto ist angelegt. Du kannst dich jetzt anmelden.");
    }

    @Test
    void aKnownFailureIsShownAsPlainText() {
        registrationService.failure = new IdentityException(IdentityMessageKeys.EMAIL_ALREADY_REGISTERED,
                "email already registered");
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.DISPLAY_NAME, true));
        _setValue(_get(view, TextField.class), "Anna");
        fillCredentials(view, "anna@example.com", "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Zu dieser E-Mail-Adresse gibt es bereits ein Konto.");
    }

    @Test
    void theMinimumLengthIsRepeatedInTheFailureMessage() {
        registrationService.failure = new IdentityException(IdentityMessageKeys.PASSWORD_TOO_SHORT, "too short");
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.DISPLAY_NAME, true));
        _setValue(_get(view, TextField.class), "Anna");
        fillCredentials(view, "anna@example.com", "kurz-genug-zwoelf", "kurz-genug-zwoelf");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Das Passwort muss mindestens 12 Zeichen lang sein.");
    }

    /** Ein eigener {@code RegistrationService} darf keinen rohen Schlüssel auf den Bildschirm bringen. */
    @Test
    void anUnknownMessageKeyBecomesTheGeneralMessage() {
        registrationService.failure = new IdentityException("app.something.went.wrong", "custom failure");
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.DISPLAY_NAME, true));
        _setValue(_get(view, TextField.class), "Anna");
        fillCredentials(view, "anna@example.com", "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Das hat nicht geklappt. Bitte versuche es später erneut.");
    }

    @Test
    void aSwitchedOffRegistrationShowsNoFormAtAll() {
        registrationService.selfRegistrationEnabled = false;
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.FULL_NAME, true));

        assertThat(_find(view, TextField.class)).isEmpty();
        assertThat(_get(view, Paragraph.class).getText()).startsWith("Die Registrierung ist für diese Anwendung");

        _click(_get(view, Button.class));
        assertThat(currentPath()).isEqualTo(IdentityRoutes.LOGIN);
    }

    @Test
    void theSideRouteLeadsToTheLogin() {
        RegistrationView view = showRegistrationView(propertiesWith(NameMode.DISPLAY_NAME, true));

        _click(_get(view, Button.class, spec -> spec.withText("Ich habe schon ein Konto")));

        assertThat(currentPath()).isEqualTo(IdentityRoutes.LOGIN);
    }
}
