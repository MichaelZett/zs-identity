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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.NameMode;

/** Selbstregistrierung. */
@Route(value = IdentityRoutes.REGISTER, autoLayout = false)
@PageTitle("Registrieren")
@AnonymousAllowed
public class RegistrationView extends VerticalLayout {

    private final RegistrationService registrationService;
    private final IdentityProperties properties;

    private final TextField firstName = new TextField("Vorname");
    private final TextField lastName = new TextField("Nachname");
    private final TextField displayName = new TextField("Anzeigename");
    private final EmailField email = new EmailField("E-Mail-Adresse");
    private final PasswordField password = new PasswordField("Passwort");
    private final PasswordField passwordRepeat = new PasswordField("Passwort wiederholen");

    public RegistrationView(RegistrationService registrationService, IdentityProperties properties) {
        this.registrationService = registrationService;
        this.properties = properties;

        setMaxWidth("28rem");
        getStyle().set("margin", "0 auto");

        add(new H2("Konto anlegen"));

        if (!registrationService.isSelfRegistrationEnabled()) {
            add(new Paragraph("Die Registrierung ist für diese Anwendung abgeschaltet. "
                    + "Bitte wende dich an die Person, die die Gruppe verwaltet."));
            add(loginButton("Zur Anmeldung"));
            return;
        }

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

        password.setHelperText("Mindestens %d Zeichen".formatted(properties.passwordMinLength()));
        firstName.setRequiredIndicatorVisible(true);
        lastName.setRequiredIndicatorVisible(true);
        displayName.setRequiredIndicatorVisible(true);
        displayName.setHelperText("So sehen dich andere.");
        email.setRequiredIndicatorVisible(true);
        password.setRequiredIndicatorVisible(true);
        passwordRepeat.setRequiredIndicatorVisible(true);

        Button submit = new Button("Registrieren", event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();

        // Welche Namensfelder erscheinen, entscheidet die Anwendung über
        // zs.identity.name-mode: Klarname (Vereine) oder Spielername (Spiele).
        if (fullNameMode()) {
            add(firstName, lastName);
        } else {
            add(displayName);
        }
        add(email, password, passwordRepeat, submit, loginButton("Ich habe schon ein Konto"));
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
            warn("Bitte fülle alle Felder aus.");
            return;
        }
        if (email.isInvalid()) {
            warn("Bitte gib eine gültige E-Mail-Adresse ein.");
            return;
        }
        if (!password.getValue().equals(passwordRepeat.getValue())) {
            warn("Die beiden Passwörter stimmen nicht überein.");
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
        add(new H2("Fast geschafft"));
        if (properties.emailVerificationRequired()) {
            add(new Paragraph("Wir haben dir eine E-Mail an %s geschickt. "
                    .formatted(email.getValue())
                    + "Bitte öffne den Link darin, um dein Konto freizuschalten."));
        } else {
            add(new Paragraph("Dein Konto ist angelegt. Du kannst dich jetzt anmelden."));
        }
        add(loginButton("Zur Anmeldung"));
    }

    /**
     * Übersetzt die Meldungsschlüssel des Bausteins. Der Kern kennt keine
     * Sprachdateien — welche es gibt, weiß nur die Anwendung.
     */
    private String translate(IdentityException e) {
        return switch (e.getMessageKey()) {
            case de.zettsystems.identity.values.IdentityMessageKeys.EMAIL_ALREADY_REGISTERED ->
                    "Zu dieser E-Mail-Adresse gibt es bereits ein Konto.";
            case de.zettsystems.identity.values.IdentityMessageKeys.PASSWORD_TOO_SHORT ->
                    "Das Passwort muss mindestens %d Zeichen lang sein.".formatted(properties.passwordMinLength());
            case de.zettsystems.identity.values.IdentityMessageKeys.SELF_REGISTRATION_DISABLED ->
                    "Die Registrierung ist für diese Anwendung abgeschaltet.";
            default -> "Die Registrierung hat nicht geklappt. Bitte versuche es später erneut.";
        };
    }

    private static void warn(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
