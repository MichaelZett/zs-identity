package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.NameMode;

/** Selbstregistrierung. */
@Route(value = IdentityRoutes.REGISTER, autoLayout = false)
@AnonymousAllowed
public class RegistrationView extends IdentityFormView {

    private final RegistrationService registrationService;
    private final IdentityProperties properties;

    private final TextField firstName = new TextField();
    private final TextField lastName = new TextField();
    private final TextField displayName = new TextField();
    private final EmailField email = new EmailField();
    private final PasswordField password = new PasswordField();
    private final PasswordField passwordRepeat = new PasswordField();

    public RegistrationView(RegistrationService registrationService, IdentityProperties properties,
                            IdentityMessages messages) {
        super(messages, properties, "registration");
        this.registrationService = registrationService;
        this.properties = properties;

        add(heading("identity.registration.title"));

        if (!registrationService.isSelfRegistrationEnabled()) {
            add(paragraph("identity.registration.disabled"));
            addFullWidth(navigationButton("identity.common.toLogin", IdentityRoutes.LOGIN));
            return;
        }

        firstName.setLabel(text("identity.registration.firstName"));
        lastName.setLabel(text("identity.registration.lastName"));
        displayName.setLabel(text("identity.registration.displayName"));
        email.setLabel(text("identity.common.email"));
        password.setLabel(text("identity.common.password"));
        passwordRepeat.setLabel(text("identity.registration.passwordRepeat"));

        // Ohne diese Kennzeichnung bieten Chrome & Co. weder das Ausfüllen
        // noch die Passwort-Generierung an: „new-password" ist das Signal
        // „hier wird ein Konto angelegt — schlag ein starkes Passwort vor",
        // und die E-Mail-Adresse ist die Kennung, unter der der
        // Passwortmanager das Paar ablegt.
        firstName.setAutocomplete(Autocomplete.GIVEN_NAME);
        lastName.setAutocomplete(Autocomplete.FAMILY_NAME);
        displayName.setAutocomplete(Autocomplete.NICKNAME);
        email.setAutocomplete(Autocomplete.USERNAME);
        password.setAutocomplete(Autocomplete.NEW_PASSWORD);
        passwordRepeat.setAutocomplete(Autocomplete.NEW_PASSWORD);

        password.setHelperText(text("identity.common.passwordHelper", properties.passwordMinLength()));
        firstName.setRequiredIndicatorVisible(true);
        lastName.setRequiredIndicatorVisible(true);
        displayName.setRequiredIndicatorVisible(true);
        displayName.setHelperText(text("identity.registration.displayNameHelper"));
        email.setRequiredIndicatorVisible(true);
        password.setRequiredIndicatorVisible(true);
        passwordRepeat.setRequiredIndicatorVisible(true);

        Button submit = primaryButton("identity.registration.submit", "registration-submit-button",
                event -> submit());

        // Welche Namensfelder erscheinen, entscheidet die Anwendung über
        // zs.identity.name-mode: Klarname (Vereine) oder Spielername (Spiele).
        if (fullNameMode()) {
            addFullWidth(firstName, lastName);
        } else {
            addFullWidth(displayName);
        }
        addFullWidth(email, password, passwordRepeat, submit,
                navigationButton("identity.registration.haveAccount", IdentityRoutes.LOGIN));
    }

    @Override
    public String getPageTitle() {
        return text("identity.registration.pageTitle");
    }

    private void submit() {
        boolean nameMissing = fullNameMode()
                ? firstName.isEmpty() || lastName.isEmpty()
                : displayName.isEmpty();
        if (nameMissing || email.isEmpty() || password.isEmpty()) {
            warn(text("identity.registration.missingFields"));
            return;
        }
        if (email.isInvalid()) {
            warn(text("identity.common.invalidEmail"));
            return;
        }
        if (!password.getValue().equals(passwordRepeat.getValue())) {
            warn(text("identity.common.passwordMismatch"));
            return;
        }

        try {
            // Die Sprache dieser Ansicht wird die Sprache des Kontos: Sie ist
            // die einzige Aussage der Person dazu, und beim späteren
            // Mailversand gibt es keinen Browser mehr, den man fragen könnte.
            registrationService.register(email.getValue(), password.getValue(), enteredName(),
                    texts().locale());
            showConfirmation();
        } catch (IdentityException e) {
            warn(translate(e));
        }
    }

    private boolean fullNameMode() {
        return properties.nameMode() == NameMode.FULL_NAME;
    }

    private AccountName enteredName() {
        return fullNameMode()
                ? AccountName.of(firstName.getValue(), lastName.getValue())
                : AccountName.display(displayName.getValue());
    }

    /**
     * Ersetzt das Formular durch eine Bestätigung. Bewusst kein Weiterleiten
     * zur Anmeldung: Das Konto ist bis zur Bestätigung der Adresse gesperrt,
     * ein Anmeldeversuch würde also nur scheitern.
     */
    private void showConfirmation() {
        removeAll();
        add(heading("identity.registration.done.title"));
        if (properties.emailVerificationRequired()) {
            add(paragraph("identity.registration.done.verify", email.getValue()));
        } else {
            add(paragraph("identity.registration.done.ready"));
        }
        addFullWidth(navigationButton("identity.common.toLogin", IdentityRoutes.LOGIN));
    }
}
