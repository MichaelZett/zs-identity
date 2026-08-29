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
import com.vaadin.flow.spring.security.AuthenticationContext;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.application.UserAccountService;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import jakarta.annotation.security.PermitAll;
import org.jspecify.annotations.Nullable;

/**
 * Passwort ändern — für angemeldete Konten, und die einzige Ansicht, die ein
 * Konto mit {@code mustChangePassword} erreicht (siehe
 * {@link PasswordChangeGuard}).
 *
 * <p>Eigene Zugriffsannotation und {@code autoLayout = false}: Vaadin prüft
 * das Layout getrennt, und eine Passwort-ändern-Seite mit Navigationsmenü
 * wäre falsch — dorthin soll man gerade nicht. Der Weg hinaus bleibt offen:
 * Wer nicht wechseln will, meldet sich hier ab, sonst säße er fest.
 */
@Route(value = IdentityRoutes.CHANGE_PASSWORD, autoLayout = false)
@PermitAll
public class ChangePasswordView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private final UserAccountService userAccountService;
    private final AuthenticationContext authenticationContext;
    private final IdentityProperties properties;
    private final IdentityTexts texts;

    private final PasswordField password = new PasswordField();
    private final PasswordField passwordRepeat = new PasswordField();

    public ChangePasswordView(UserAccountService userAccountService,
                              AuthenticationContext authenticationContext,
                              IdentityProperties properties, IdentityMessages messages) {
        this.userAccountService = userAccountService;
        this.authenticationContext = authenticationContext;
        this.properties = properties;
        this.texts = new IdentityTexts(messages, properties);
        setMaxWidth("28rem");
        getStyle().set("margin", "0 auto");
        password.setLabel(texts.get("identity.change.password"));
        passwordRepeat.setLabel(texts.get("identity.change.passwordRepeat"));
        // Signal an den Passwortmanager: Hier entsteht ein neues Passwort.
        password.setAutocomplete(Autocomplete.NEW_PASSWORD);
        passwordRepeat.setAutocomplete(Autocomplete.NEW_PASSWORD);
    }

    @Override
    public String getPageTitle() {
        return texts.get("identity.change.pageTitle");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        IdentityUserDetails user = currentUser();
        if (user == null) {
            // Ohne Anmeldung gibt es nichts zu ändern; die Zugriffsregel hält
            // das ohnehin fern, dies ist die zweite Verteidigungslinie.
            event.forwardTo(IdentityRoutes.LOGIN);
            return;
        }

        password.setHelperText(texts.get("identity.common.passwordHelper", properties.passwordMinLength()));
        password.setRequiredIndicatorVisible(true);
        password.setWidthFull();
        passwordRepeat.setRequiredIndicatorVisible(true);
        passwordRepeat.setWidthFull();

        Button submit = new Button(texts.get("identity.change.submit"), e -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();
        submit.setId("change-password-submit-button");

        Button logout = new Button(texts.get("identity.change.logout"), e -> authenticationContext.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logout.setWidthFull();
        logout.setId("change-password-logout-button");

        add(new H2(texts.get("identity.change.title")));
        if (user.mustChangePassword()) {
            add(new Paragraph(texts.get("identity.change.required")));
        }
        add(password, passwordRepeat, submit, logout);
    }

    private void submit() {
        IdentityUserDetails user = currentUser();
        if (user == null) {
            return;
        }
        if (password.isEmpty() || !password.getValue().equals(passwordRepeat.getValue())) {
            warn(texts.get("identity.common.passwordMismatch"));
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
     * Nach dem Wechsel führt der Knopf auf die Startseite der Anwendung — der
     * Baustein kennt sie nicht, darum die Wurzel. Die Sitzung ist zu diesem
     * Zeitpunkt bereits aufgefrischt ({@code AuthenticationRefresher}), der
     * {@link PasswordChangeGuard} lässt also durch.
     */
    private void showConfirmation() {
        removeAll();
        add(new H2(texts.get("identity.change.done.title")));
        add(new Paragraph(texts.get("identity.change.done.message")));
        Button proceed = new Button(texts.get("identity.change.done.proceed"),
                event -> UI.getCurrent().navigate(""));
        proceed.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        proceed.setWidthFull();
        proceed.setId("change-password-proceed-button");
        add(proceed);
    }

    private @Nullable IdentityUserDetails currentUser() {
        return authenticationContext.getAuthenticatedUser(IdentityUserDetails.class).orElse(null);
    }

    /** Siehe {@code RegistrationView#translate}: Unbekanntes bekommt den allgemeinen Text. */
    private String translate(IdentityException e) {
        return switch (e.getMessageKey()) {
            case IdentityMessageKeys.PASSWORD_TOO_SHORT ->
                    texts.get(e.getMessageKey(), properties.passwordMinLength());
            default -> texts.get(IdentityMessageKeys.UNEXPECTED);
        };
    }

    private static void warn(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
