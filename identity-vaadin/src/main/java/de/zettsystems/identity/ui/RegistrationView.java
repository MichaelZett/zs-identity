package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.NameMode;

/** Selbstregistrierung. */
@Route(value = IdentityRoutes.REGISTER, autoLayout = false)
@AnonymousAllowed
public class RegistrationView extends VerticalLayout implements HasDynamicTitle {

    private final RegistrationService registrationService;
    private final IdentityProperties properties;
    private final IdentityTexts texts;

    private final TextField firstName = new TextField();
    private final TextField lastName = new TextField();
    private final TextField displayName = new TextField();
    private final EmailField email = new EmailField();
    private final PasswordField password = new PasswordField();
    private final PasswordField passwordRepeat = new PasswordField();

    public RegistrationView(RegistrationService registrationService, IdentityProperties properties,
                            IdentityMessages messages) {
        this.registrationService = registrationService;
        this.properties = properties;
        this.texts = new IdentityTexts(messages, properties);

        setMaxWidth("28rem");
        getStyle().set("margin", "0 auto");

        add(new H2(texts.get("identity.registration.title")));

        if (!registrationService.isSelfRegistrationEnabled()) {
            add(new Paragraph(texts.get("identity.registration.disabled")));
            add(loginButton(texts.get("identity.common.toLogin")));
            return;
        }

        firstName.setLabel(texts.get("identity.registration.firstName"));
        lastName.setLabel(texts.get("identity.registration.lastName"));
        displayName.setLabel(texts.get("identity.registration.displayName"));
        email.setLabel(texts.get("identity.common.email"));
        password.setLabel(texts.get("identity.common.password"));
        passwordRepeat.setLabel(texts.get("identity.registration.passwordRepeat"));

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

        password.setHelperText(texts.get("identity.common.passwordHelper", properties.passwordMinLength()));
        firstName.setRequiredIndicatorVisible(true);
        lastName.setRequiredIndicatorVisible(true);
        displayName.setRequiredIndicatorVisible(true);
        displayName.setHelperText(texts.get("identity.registration.displayNameHelper"));
        email.setRequiredIndicatorVisible(true);
        password.setRequiredIndicatorVisible(true);
        passwordRepeat.setRequiredIndicatorVisible(true);

        Button submit = new Button(texts.get("identity.registration.submit"), event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();
        submit.setId("registration-submit-button");

        // Welche Namensfelder erscheinen, entscheidet die Anwendung über
        // zs.identity.name-mode: Klarname (Vereine) oder Spielername (Spiele).
        if (fullNameMode()) {
            add(firstName, lastName);
        } else {
            add(displayName);
        }
        add(email, password, passwordRepeat, submit,
                loginButton(texts.get("identity.registration.haveAccount")));
    }

    @Override
    public String getPageTitle() {
        return texts.get("identity.registration.pageTitle");
    }

    /** Navigations-Button zur Anmeldung — volle Breite, weil das Formular schmal ist. */
    private static Button loginButton(String label) {
        Button button = new Button(label,
                event -> UI.getCurrent().navigate(IdentityRoutes.LOGIN));
        button.setWidthFull();
        return button;
    }

    private void submit() {
        boolean nameMissing = fullNameMode()
                ? firstName.isEmpty() || lastName.isEmpty()
                : displayName.isEmpty();
        if (nameMissing || email.isEmpty() || password.isEmpty()) {
            warn(texts.get("identity.registration.missingFields"));
            return;
        }
        if (email.isInvalid()) {
            warn(texts.get("identity.common.invalidEmail"));
            return;
        }
        if (!password.getValue().equals(passwordRepeat.getValue())) {
            warn(texts.get("identity.common.passwordMismatch"));
            return;
        }

        try {
            registrationService.register(email.getValue(), password.getValue(), enteredName());
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
        add(new H2(texts.get("identity.registration.done.title")));
        if (properties.emailVerificationRequired()) {
            add(new Paragraph(texts.get("identity.registration.done.verify", email.getValue())));
        } else {
            add(new Paragraph(texts.get("identity.registration.done.ready")));
        }
        add(loginButton(texts.get("identity.common.toLogin")));
    }

    /**
     * Übersetzt die Meldungsschlüssel des Bausteins. Nur die hier
     * aufgezählten: Ein unbekannter Schlüssel — etwa aus einem selbst
     * gebauten {@code RegistrationService} — bekommt den allgemeinen Text
     * statt eines rohen Schlüssels auf dem Bildschirm.
     */
    private String translate(IdentityException e) {
        return switch (e.getMessageKey()) {
            case IdentityMessageKeys.EMAIL_ALREADY_REGISTERED,
                 IdentityMessageKeys.SELF_REGISTRATION_DISABLED -> texts.get(e.getMessageKey());
            case IdentityMessageKeys.PASSWORD_TOO_SHORT ->
                    texts.get(e.getMessageKey(), properties.passwordMinLength());
            default -> texts.get(IdentityMessageKeys.UNEXPECTED);
        };
    }

    private static void warn(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
