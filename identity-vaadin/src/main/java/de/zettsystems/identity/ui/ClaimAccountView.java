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
import de.zettsystems.identity.application.InvitationService;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.UserAccountDto;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Nimmt den Link aus der Einladungsmail entgegen: erstes Passwort setzen,
 * Konto übernehmen.
 *
 * <p>Nah an der {@code ResetPasswordView} — mit einem Unterschied: Sie zeigt
 * den Namen des Kontos an, das hier übernommen wird. Wer eingeladen wurde,
 * hat sich nichts bestellt und soll sehen, worum es geht.
 */
@Route(value = IdentityRoutes.CLAIM_ACCOUNT, autoLayout = false)
@AnonymousAllowed
public class ClaimAccountView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private final InvitationService invitationService;
    private final IdentityProperties properties;
    private final IdentityTexts texts;

    private final PasswordField password = new PasswordField();
    private final PasswordField passwordRepeat = new PasswordField();

    private @Nullable String token;

    public ClaimAccountView(InvitationService invitationService, IdentityProperties properties,
                            IdentityMessages messages) {
        this.invitationService = invitationService;
        this.properties = properties;
        this.texts = new IdentityTexts(messages, properties);
        setMaxWidth("28rem");
        getStyle().set("margin", "0 auto");
        password.setLabel(texts.get("identity.claim.password"));
        passwordRepeat.setLabel(texts.get("identity.claim.passwordRepeat"));
        // Wie in der RegistrationView: Erst mit NEW_PASSWORD bieten die Browser
        // an, ein Passwort zu erzeugen und zu merken.
        password.setAutocomplete(Autocomplete.NEW_PASSWORD);
        passwordRepeat.setAutocomplete(Autocomplete.NEW_PASSWORD);
    }

    @Override
    public String getPageTitle() {
        return texts.get("identity.claim.pageTitle");
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
            // Unbekanntes Token: Hier hilft kein Formular, und „neuen Link
            // anfordern" gibt es nicht — eingeladen wird aus der Verwaltung.
            showDeadEnd("identity.claim.unknown.title", "identity.claim.unknown.message");
            return;
        }

        password.setHelperText(texts.get("identity.common.passwordHelper", properties.passwordMinLength()));
        password.setRequiredIndicatorVisible(true);
        password.setWidthFull();
        passwordRepeat.setRequiredIndicatorVisible(true);
        passwordRepeat.setWidthFull();

        Button submit = new Button(texts.get("identity.claim.submit"), e -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();
        submit.setId("claim-submit-button");

        add(new H2(texts.get("identity.claim.title")),
                new Paragraph(texts.get("identity.claim.intro", invitee.get().displayName())),
                password, passwordRepeat, submit);
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
            invitationService.claim(token, password.getValue());
            showConfirmation();
        } catch (IdentityException e) {
            warn(translate(e));
        }
    }

    private void showConfirmation() {
        removeAll();
        add(new H2(texts.get("identity.claim.done.title")));
        add(new Paragraph(texts.get("identity.claim.done.message")));
        add(navigationButton(texts.get("identity.common.toLogin"), IdentityRoutes.LOGIN));
    }

    private void showDeadEnd(String titleKey, String messageKey) {
        add(new H2(texts.get(titleKey)));
        add(new Paragraph(texts.get(messageKey)));
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
            case IdentityMessageKeys.TOKEN_EXPIRED, IdentityMessageKeys.TOKEN_INVALID,
                 IdentityMessageKeys.ACCOUNT_ALREADY_CLAIMED -> texts.get(e.getMessageKey());
            default -> texts.get(IdentityMessageKeys.UNEXPECTED);
        };
    }

    private static void warn(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
