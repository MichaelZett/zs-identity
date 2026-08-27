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
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.PasswordResetService;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Nimmt den Link aus der Reset-Mail entgegen und setzt das neue Passwort. */
@Route(value = IdentityRoutes.RESET_PASSWORD, autoLayout = false)
@PageTitle("Neues Passwort")
@AnonymousAllowed
public class ResetPasswordView extends VerticalLayout implements BeforeEnterObserver {

    private final PasswordResetService passwordResetService;
    private final IdentityProperties properties;

    private final PasswordField password = new PasswordField("Neues Passwort");
    private final PasswordField passwordRepeat = new PasswordField("Neues Passwort wiederholen");

    private @Nullable String token;

    public ResetPasswordView(PasswordResetService passwordResetService, IdentityProperties properties) {
        this.passwordResetService = passwordResetService;
        this.properties = properties;
        setMaxWidth("28rem");
        getStyle().set("margin", "0 auto");
        // Signal an den Passwortmanager: Hier entsteht ein neues Passwort —
        // erst damit bieten Chrome & Co. die Generierung an (wie in der
        // RegistrationView).
        password.setAutocomplete(Autocomplete.NEW_PASSWORD);
        passwordRepeat.setAutocomplete(Autocomplete.NEW_PASSWORD);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();

        List<String> tokens = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault(IdentityRoutes.TOKEN_PARAMETER, List.of());
        if (tokens.isEmpty()) {
            add(new H2("Link unvollständig"));
            add(new Paragraph("Bitte öffne den Link direkt aus der E-Mail."));
            add(navigationButton("Neuen Link anfordern", IdentityRoutes.FORGOT_PASSWORD));
            return;
        }
        this.token = tokens.getFirst();

        password.setHelperText("Mindestens %d Zeichen".formatted(properties.passwordMinLength()));
        password.setRequiredIndicatorVisible(true);
        password.setWidthFull();
        passwordRepeat.setRequiredIndicatorVisible(true);
        passwordRepeat.setWidthFull();

        Button submit = new Button("Passwort speichern", e -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();

        add(new H2("Neues Passwort setzen"), password, passwordRepeat, submit);
    }

    private void submit() {
        if (token == null) {
            return;
        }
        if (password.isEmpty() || !password.getValue().equals(passwordRepeat.getValue())) {
            warn("Die beiden Passwörter stimmen nicht überein.");
            return;
        }

        try {
            passwordResetService.resetPassword(token, password.getValue());
            showConfirmation();
        } catch (IdentityException e) {
            warn(translate(e));
        }
    }

    private void showConfirmation() {
        removeAll();
        add(new H2("Passwort geändert"));
        add(new Paragraph("Du kannst dich jetzt mit deinem neuen Passwort anmelden."));
        add(navigationButton("Zur Anmeldung", IdentityRoutes.LOGIN));
    }

    /** Navigations-Button — volle Breite, weil die Ansicht schmal ist. */
    private static Button navigationButton(String label, String route) {
        Button button = new Button(label, event -> UI.getCurrent().navigate(route));
        button.setWidthFull();
        return button;
    }

    private String translate(IdentityException e) {
        return switch (e.getMessageKey()) {
            case IdentityMessageKeys.PASSWORD_TOO_SHORT ->
                    "Das Passwort muss mindestens %d Zeichen lang sein.".formatted(properties.passwordMinLength());
            case IdentityMessageKeys.TOKEN_EXPIRED, IdentityMessageKeys.TOKEN_INVALID ->
                    "Dieser Link ist abgelaufen oder wurde bereits benutzt. Fordere einen neuen an.";
            default -> "Das hat nicht geklappt. Bitte versuche es später erneut.";
        };
    }

    private static void warn(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
