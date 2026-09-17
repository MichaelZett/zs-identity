package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.PasswordResetService;
import de.zettsystems.identity.values.IdentityProperties;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Nimmt den Link aus der Reset-Mail entgegen und setzt das neue Passwort. */
@Route(value = IdentityRoutes.RESET_PASSWORD, autoLayout = false)
@AnonymousAllowed
public class ResetPasswordView extends IdentityFormView implements BeforeEnterObserver {

    private final PasswordResetService passwordResetService;
    private final int passwordMinLength;

    private final PasswordField password = new PasswordField();
    private final PasswordField passwordRepeat = new PasswordField();

    private @Nullable String token;

    public ResetPasswordView(PasswordResetService passwordResetService, IdentityProperties properties,
                             IdentityMessages messages) {
        super(messages, properties, "reset-password");
        this.passwordResetService = passwordResetService;
        this.passwordMinLength = properties.passwordMinLength();
        password.setLabel(text("identity.reset.password"));
        passwordRepeat.setLabel(text("identity.reset.passwordRepeat"));
        // Signal an den Passwortmanager: Hier entsteht ein neues Passwort —
        // erst damit bieten Chrome & Co. die Generierung an (wie in der
        // RegistrationView).
        password.setAutocomplete(Autocomplete.NEW_PASSWORD);
        passwordRepeat.setAutocomplete(Autocomplete.NEW_PASSWORD);
    }

    @Override
    public String getPageTitle() {
        return text("identity.reset.pageTitle");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();

        List<String> tokens = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault(IdentityRoutes.TOKEN_PARAMETER, List.of());
        if (tokens.isEmpty()) {
            add(heading("identity.reset.incomplete.title"));
            add(paragraph("identity.reset.incomplete.message"));
            addFullWidth(navigationButton("identity.reset.requestNew", IdentityRoutes.FORGOT_PASSWORD));
            return;
        }
        this.token = tokens.getFirst();

        password.setHelperText(text("identity.common.passwordHelper", passwordMinLength));
        password.setRequiredIndicatorVisible(true);
        passwordRepeat.setRequiredIndicatorVisible(true);

        Button submit = primaryButton("identity.reset.submit", "reset-submit-button", e -> submit());

        add(heading("identity.reset.title"));
        addFullWidth(password, passwordRepeat, submit);
    }

    private void submit() {
        if (token == null) {
            return;
        }
        if (password.isEmpty() || !password.getValue().equals(passwordRepeat.getValue())) {
            warn(text("identity.common.passwordMismatch"));
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
        add(heading("identity.reset.done.title"));
        add(paragraph("identity.reset.done.message"));
        addFullWidth(navigationButton("identity.common.toLogin", IdentityRoutes.LOGIN));
    }
}
