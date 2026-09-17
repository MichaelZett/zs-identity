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

/** Self-registration. */
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

        // Without these hints, Chrome and friends offer neither autofill nor
        // password generation: "new-password" is the signal "an account is
        // being created here, suggest a strong password", and the email address
        // is the identifier the password manager files the pair under.
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

        // Which name fields appear is decided by the application through
        // zs.identity.name-mode: real name (clubs) or player name (games).
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
            // The language of this view becomes the language of the account:
            // it is the only thing the person says about it, and when mail is
            // sent later there is no browser left to ask.
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
     * Replaces the form with a confirmation. Deliberately no redirect to
     * sign-in: the account is blocked until the address is confirmed, so an
     * attempt to sign in would only fail.
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
