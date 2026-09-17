package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.IdentityProperties;

import java.util.List;
import java.util.Map;

/**
 * Receives the link from the verification mail and enables the account.
 *
 * <p>The token arrives as a query parameter, not as a path segment: that keeps
 * the path readable in logs and history, and the link can be assembled without
 * special handling.
 *
 * <p>It is redeemed on a button press and not while the page is being built:
 * mail scanners and link previews fetch the link by GET before the human
 * clicks. With automatic redemption they would consume the one-time token, and
 * the real click would run into "link already used" (seen in the group test on
 * 2026-08-19).
 */
@Route(value = IdentityRoutes.CONFIRM_EMAIL, autoLayout = false)
@AnonymousAllowed
public class ConfirmEmailView extends IdentityFormView implements BeforeEnterObserver {

    private final RegistrationService registrationService;

    public ConfirmEmailView(RegistrationService registrationService, IdentityProperties properties,
                            IdentityMessages messages) {
        super(messages, properties, "confirm-email");
        this.registrationService = registrationService;
    }

    @Override
    public String getPageTitle() {
        return text("identity.confirm.pageTitle");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();

        Map<String, List<String>> parameters = event.getLocation().getQueryParameters().getParameters();
        List<String> tokens = parameters.getOrDefault(IdentityRoutes.TOKEN_PARAMETER, List.of());
        if (tokens.isEmpty()) {
            showFailure(text("identity.confirm.incompleteLink"));
            return;
        }

        showPrompt(tokens.getFirst());
    }

    private void showPrompt(String token) {
        add(heading("identity.confirm.title"));
        add(paragraph("identity.confirm.prompt"));
        addFullWidth(primaryButton("identity.confirm.submit", "confirm-email-button",
                event -> confirm(token)));
    }

    private void confirm(String token) {
        removeAll();
        try {
            registrationService.confirmEmail(token);
            add(heading("identity.confirm.done.title"));
            add(paragraph("identity.confirm.done.message"));
            addFullWidth(loginButton());
        } catch (IdentityException _) {
            showFailure(text("identity.confirm.failed.message"));
        }
    }

    private void showFailure(String message) {
        add(heading("identity.confirm.failed.title"));
        add(new Paragraph(message));
        addFullWidth(resendButton(), loginButton());
    }

    /** The way out of an expired link; otherwise the account stays blocked. */
    private Button resendButton() {
        Button button = navigationButton("identity.confirm.resend", IdentityRoutes.RESEND_VERIFICATION);
        button.setId("confirm-resend-verification-button");
        return button;
    }

    private Button loginButton() {
        Button button = navigationButton("identity.common.toLogin", IdentityRoutes.LOGIN);
        button.setId("confirm-login-button");
        return button;
    }
}
