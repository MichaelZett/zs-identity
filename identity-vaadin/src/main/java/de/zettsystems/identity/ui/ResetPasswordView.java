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
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.PasswordResetService;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Nimmt den Link aus der Reset-Mail entgegen und setzt das neue Passwort. */
@Route(value = IdentityRoutes.RESET_PASSWORD, autoLayout = false)
@AnonymousAllowed
public class ResetPasswordView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private final PasswordResetService passwordResetService;
    private final IdentityProperties properties;
    private final IdentityTexts texts;

    private final PasswordField password = new PasswordField();
    private final PasswordField passwordRepeat = new PasswordField();

    private @Nullable String token;

    public ResetPasswordView(PasswordResetService passwordResetService, IdentityProperties properties,
                             IdentityMessages messages) {
        this.passwordResetService = passwordResetService;
        this.properties = properties;
        this.texts = new IdentityTexts(messages, properties);
        setMaxWidth("28rem");
        getStyle().set("margin", "0 auto");
        password.setLabel(texts.get("identity.reset.password"));
        passwordRepeat.setLabel(texts.get("identity.reset.passwordRepeat"));
        // Signal an den Passwortmanager: Hier entsteht ein neues Passwort —
        // erst damit bieten Chrome & Co. die Generierung an (wie in der
        // RegistrationView).
        password.setAutocomplete(Autocomplete.NEW_PASSWORD);
        passwordRepeat.setAutocomplete(Autocomplete.NEW_PASSWORD);
    }

    @Override
    public String getPageTitle() {
        return texts.get("identity.reset.pageTitle");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();

        List<String> tokens = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault(IdentityRoutes.TOKEN_PARAMETER, List.of());
        if (tokens.isEmpty()) {
            add(new H2(texts.get("identity.reset.incomplete.title")));
            add(new Paragraph(texts.get("identity.reset.incomplete.message")));
            add(navigationButton(texts.get("identity.reset.requestNew"), IdentityRoutes.FORGOT_PASSWORD));
            return;
        }
        this.token = tokens.getFirst();

        password.setHelperText(texts.get("identity.common.passwordHelper", properties.passwordMinLength()));
        password.setRequiredIndicatorVisible(true);
        password.setWidthFull();
        passwordRepeat.setRequiredIndicatorVisible(true);
        passwordRepeat.setWidthFull();

        Button submit = new Button(texts.get("identity.reset.submit"), e -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();
        submit.setId("reset-submit-button");

        add(new H2(texts.get("identity.reset.title")), password, passwordRepeat, submit);
    }

    private void submit() {
        if (token == null) {
            return;
        }
        if (password.isEmpty() || !password.getValue().equals(passwordRepeat.getValue())) {
            warn(texts.get("identity.common.passwordMismatch"));
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
        add(new H2(texts.get("identity.reset.done.title")));
        add(new Paragraph(texts.get("identity.reset.done.message")));
        add(navigationButton(texts.get("identity.common.toLogin"), IdentityRoutes.LOGIN));
    }

    /** Navigations-Button — volle Breite, weil die Ansicht schmal ist. */
    private static Button navigationButton(String label, String route) {
        Button button = new Button(label, event -> UI.getCurrent().navigate(route));
        button.setWidthFull();
        return button;
    }

    /** Siehe {@code RegistrationView#translate}: Unbekanntes bekommt den allgemeinen Text. */
    private String translate(IdentityException e) {
        return switch (e.getMessageKey()) {
            case IdentityMessageKeys.PASSWORD_TOO_SHORT ->
                    texts.get(e.getMessageKey(), properties.passwordMinLength());
            case IdentityMessageKeys.TOKEN_EXPIRED, IdentityMessageKeys.TOKEN_INVALID ->
                    texts.get(e.getMessageKey());
            default -> texts.get(IdentityMessageKeys.UNEXPECTED);
        };
    }

    private static void warn(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
