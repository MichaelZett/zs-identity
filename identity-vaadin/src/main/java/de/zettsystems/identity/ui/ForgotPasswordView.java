package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.PasswordResetService;
import de.zettsystems.identity.values.IdentityProperties;

/** The "forgot password" form. */
@Route(value = IdentityRoutes.FORGOT_PASSWORD, autoLayout = false)
@AnonymousAllowed
public class ForgotPasswordView extends IdentityFormView {

    private final PasswordResetService passwordResetService;
    private final EmailField email = new EmailField();

    public ForgotPasswordView(PasswordResetService passwordResetService, IdentityProperties properties,
                              IdentityMessages messages) {
        super(messages, properties, "forgot-password");
        this.passwordResetService = passwordResetService;

        add(heading("identity.forgot.title"));
        add(paragraph("identity.forgot.intro"));

        email.setLabel(text("identity.common.email"));
        email.setRequiredIndicatorVisible(true);
        // The identifier a password manager files the credentials under, so
        // that it offers the address here instead of making it be typed.
        email.setAutocomplete(Autocomplete.USERNAME);
        email.setId("forgot-email-field");

        Button submit = primaryButton("identity.forgot.submit", "forgot-submit-button", event -> submit());

        addFullWidth(email, submit, backToLoginButton());
    }

    @Override
    public String getPageTitle() {
        return text("identity.forgot.pageTitle");
    }

    private Button backToLoginButton() {
        return navigationButton("identity.common.backToLogin", IdentityRoutes.LOGIN);
    }

    private void submit() {
        if (email.isEmpty() || email.isInvalid()) {
            email.setErrorMessage(text("identity.common.invalidEmail"));
            email.setInvalid(true);
            return;
        }

        passwordResetService.requestReset(email.getValue());
        showConfirmation();
    }

    /**
     * Shows the same confirmation whether or not an account exists for the
     * address. A more honest message would be a security problem here: this
     * form could otherwise be used to work out who is registered with us.
     */
    private void showConfirmation() {
        removeAll();
        add(heading("identity.common.mailSent.title"));
        add(new Paragraph(text("identity.forgot.sent", email.getValue())
                + " " + text("identity.common.spamHint")));
        addFullWidth(backToLoginButton());
    }
}
