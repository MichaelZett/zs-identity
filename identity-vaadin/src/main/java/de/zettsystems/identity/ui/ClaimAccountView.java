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
import de.zettsystems.identity.application.InvitationService;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Receives the link from the invitation mail: set the first password, take the
 * account over.
 *
 * <p>Close to {@code ResetPasswordView}, with one difference: it shows the name
 * of the account being taken over. Whoever was invited did not ask for any of
 * this and should see what it is about.
 */
@Route(value = IdentityRoutes.CLAIM_ACCOUNT, autoLayout = false)
@AnonymousAllowed
public class ClaimAccountView extends IdentityFormView implements BeforeEnterObserver {

    private final InvitationService invitationService;
    private final int passwordMinLength;

    private final PasswordField password = new PasswordField();
    private final PasswordField passwordRepeat = new PasswordField();

    private @Nullable String token;

    public ClaimAccountView(InvitationService invitationService, IdentityProperties properties,
                            IdentityMessages messages) {
        super(messages, properties, "claim-account");
        this.invitationService = invitationService;
        this.passwordMinLength = properties.passwordMinLength();
        password.setLabel(text("identity.claim.password"));
        passwordRepeat.setLabel(text("identity.claim.passwordRepeat"));
        // As in RegistrationView: only with NEW_PASSWORD do browsers offer to
        // generate a password and remember it.
        password.setAutocomplete(Autocomplete.NEW_PASSWORD);
        passwordRepeat.setAutocomplete(Autocomplete.NEW_PASSWORD);
    }

    @Override
    public String getPageTitle() {
        return text("identity.claim.pageTitle");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();

        List<String> tokens = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault(IdentityRoutes.TOKEN_PARAMETER, List.of());
        if (tokens.isEmpty()) {
            showDeadEnd("identity.claim.incomplete.title", "identity.claim.incomplete.message");
            return;
        }
        this.token = tokens.getFirst();

        Optional<UserAccountDto> invitee = invitationService.findInvitee(this.token);
        if (invitee.isEmpty()) {
            // An unknown token: no form helps here, and there is no "request a
            // new link" -- invitations come from an administration screen.
            showDeadEnd("identity.claim.unknown.title", "identity.claim.unknown.message");
            return;
        }

        password.setHelperText(text("identity.common.passwordHelper", passwordMinLength));
        password.setRequiredIndicatorVisible(true);
        passwordRepeat.setRequiredIndicatorVisible(true);

        Button submit = primaryButton("identity.claim.submit", "claim-submit-button", e -> submit());

        add(heading("identity.claim.title"));
        add(paragraph("identity.claim.intro", invitee.get().displayName()));
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
            // The language of this view is the first thing the invited person
            // says about it; the service only accepts it when none was chosen
            // while inviting.
            invitationService.claim(token, password.getValue(), texts().locale());
            showConfirmation();
        } catch (IdentityException e) {
            warn(translate(e));
        }
    }

    private void showConfirmation() {
        removeAll();
        add(heading("identity.claim.done.title"));
        add(paragraph("identity.claim.done.message"));
        addFullWidth(navigationButton("identity.common.toLogin", IdentityRoutes.LOGIN));
    }

    private void showDeadEnd(String titleKey, String messageKey) {
        add(heading(titleKey));
        add(paragraph(messageKey));
        addFullWidth(navigationButton("identity.common.toLogin", IdentityRoutes.LOGIN));
    }
}
