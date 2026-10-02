package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.NameMode;
import de.zettsystems.identity.values.RegistrationMode;

import java.util.List;
import java.util.Set;

/**
 * Self-registration. With {@code zs.identity.registration-mode=CODE} the form
 * asks for an invitation code first; a link like
 * {@code /register?code=ABC123} fills it in, so that handing out the link is
 * enough.
 */
@Route(value = IdentityRoutes.REGISTER, autoLayout = false)
@AnonymousAllowed
public class RegistrationView extends IdentityFormView implements BeforeEnterObserver {

    /** The failures that belong to the code field rather than the whole form. */
    private static final Set<String> CODE_KEYS = Set.of(
            IdentityMessageKeys.INVITATION_CODE_REQUIRED,
            IdentityMessageKeys.INVITATION_CODE_INVALID);

    private final RegistrationService registrationService;
    private final IdentityProperties properties;
    private final boolean codeRequired;

    private final TextField code = new TextField();
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
        this.codeRequired = registrationService.registrationMode() == RegistrationMode.CODE;

        add(heading("identity.registration.title"));

        if (!registrationService.isSelfRegistrationEnabled()) {
            add(paragraph("identity.registration.disabled"));
            addFullWidth(navigationButton("identity.common.toLogin", IdentityRoutes.LOGIN));
            return;
        }

        code.setLabel(text("identity.registration.code"));
        code.setHelperText(text("identity.registration.codeHelper"));
        code.setRequiredIndicatorVisible(true);
        // A code is no credential a password manager should keep or offer.
        code.setAutocomplete(Autocomplete.OFF);
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

        // The code first: without it the rest of the form is in vain.
        if (codeRequired) {
            addFullWidth(code);
        }
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

    /** Fills in the code from the link, when the form asks for one. */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> codes = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault(IdentityRoutes.CODE_PARAMETER, List.of());
        if (codeRequired && !codes.isEmpty() && code.isEmpty()) {
            code.setValue(codes.getFirst().strip());
        }
    }

    private void submit() {
        code.setInvalid(false);
        boolean codeMissing = codeRequired && code.getValue().isBlank();
        boolean nameMissing = fullNameMode()
                ? firstName.isEmpty() || lastName.isEmpty()
                : displayName.isEmpty();
        if (codeMissing || nameMissing || email.isEmpty() || password.isEmpty()) {
            if (codeMissing) {
                markCode(text(IdentityMessageKeys.INVITATION_CODE_REQUIRED));
            }
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
                    texts().locale(), codeRequired ? code.getValue() : null);
            showConfirmation();
        } catch (IdentityException e) {
            if (CODE_KEYS.contains(e.getMessageKey())) {
                markCode(translate(e));
            } else {
                warn(translate(e));
            }
        }
    }

    private void markCode(String message) {
        code.setErrorMessage(message);
        code.setInvalid(true);
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
