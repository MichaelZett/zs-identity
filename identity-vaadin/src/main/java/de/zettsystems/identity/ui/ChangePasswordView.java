package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.application.UserAccountService;
import de.zettsystems.identity.values.IdentityProperties;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.Nullable;

/**
 * Changing the password, for signed-in accounts, and the only view an account
 * with {@code mustChangePassword} can reach (see {@link PasswordChangeGuard}).
 *
 * <p>An access annotation of its own and {@code autoLayout = false}: Vaadin
 * checks the layout separately, and a change-password page with a navigation
 * menu would be wrong, since going there is exactly what must not happen. The
 * way out stays open: whoever does not want to change signs out here, as they
 * would otherwise be stuck.
 */
@Route(value = IdentityRoutes.CHANGE_PASSWORD, autoLayout = false)
@PermitAll
public class ChangePasswordView extends IdentityFormView implements BeforeEnterObserver {

    private final UserAccountService userAccountService;
    private final AuthenticationContext authenticationContext;
    private final int passwordMinLength;

    private final PasswordField password = new PasswordField();
    private final PasswordField passwordRepeat = new PasswordField();

    public ChangePasswordView(UserAccountService userAccountService,
                              AuthenticationContext authenticationContext,
                              IdentityProperties properties, IdentityMessages messages) {
        super(messages, properties, "change-password");
        this.userAccountService = userAccountService;
        this.authenticationContext = authenticationContext;
        this.passwordMinLength = properties.passwordMinLength();
        password.setLabel(text("identity.change.password"));
        passwordRepeat.setLabel(text("identity.change.passwordRepeat"));
        // A hint to the password manager that a new password is created here.
        password.setAutocomplete(Autocomplete.NEW_PASSWORD);
        passwordRepeat.setAutocomplete(Autocomplete.NEW_PASSWORD);
    }

    @Override
    public String getPageTitle() {
        return text("identity.change.pageTitle");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        IdentityUserDetails user = currentUser();
        if (user == null) {
            // Without a sign-in there is nothing to change; the access rule
            // keeps that away anyway, and this is the second line of defence.
            event.forwardTo(IdentityRoutes.LOGIN);
            return;
        }

        password.setHelperText(text("identity.common.passwordHelper", passwordMinLength));
        password.setRequiredIndicatorVisible(true);
        passwordRepeat.setRequiredIndicatorVisible(true);

        Button submit = primaryButton("identity.change.submit", "change-password-submit-button",
                e -> submit());
        Button logout = secondaryButton("identity.change.logout", "change-password-logout-button",
                e -> authenticationContext.logout());

        add(heading("identity.change.title"));
        if (user.mustChangePassword()) {
            add(paragraph("identity.change.required"));
        }
        addFullWidth(password, passwordRepeat, submit, logout);
    }

    private void submit() {
        IdentityUserDetails user = currentUser();
        if (user == null) {
            return;
        }
        if (password.isEmpty() || !password.getValue().equals(passwordRepeat.getValue())) {
            warn(text("identity.common.passwordMismatch"));
            return;
        }
        try {
            userAccountService.changePassword(user.userId(), password.getValue());
            showConfirmation();
        } catch (IdentityException e) {
            warn(translate(e));
        }
    }

    /**
     * After the change the button leads to the start page of the application.
     * The building block does not know it, hence the root. By that time the
     * session has already been refreshed ({@code AuthenticationRefresher}), so
     * the {@link PasswordChangeGuard} lets it pass.
     */
    private void showConfirmation() {
        removeAll();
        add(heading("identity.change.done.title"));
        add(paragraph("identity.change.done.message"));
        addFullWidth(primaryButton("identity.change.done.proceed", "change-password-proceed-button",
                event -> UI.getCurrent().navigate("")));
    }

    private @Nullable IdentityUserDetails currentUser() {
        return authenticationContext.getAuthenticatedUser(IdentityUserDetails.class).orElse(null);
    }
}
