package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityMessages;
import de.zettsystems.identity.application.PasswordResetService;
import de.zettsystems.identity.values.IdentityProperties;

/** Formular für "Passwort vergessen". */
@Route(value = IdentityRoutes.FORGOT_PASSWORD, autoLayout = false)
@AnonymousAllowed
public class ForgotPasswordView extends VerticalLayout implements HasDynamicTitle {

    private final PasswordResetService passwordResetService;
    private final IdentityTexts texts;
    private final EmailField email = new EmailField();

    public ForgotPasswordView(PasswordResetService passwordResetService, IdentityProperties properties,
                              IdentityMessages messages) {
        this.passwordResetService = passwordResetService;
        this.texts = new IdentityTexts(messages, properties);

        setMaxWidth("28rem");
        getStyle().set("margin", "0 auto");

        add(new H2(texts.get("identity.forgot.title")));
        add(new Paragraph(texts.get("identity.forgot.intro")));

        email.setLabel(texts.get("identity.common.email"));
        email.setRequiredIndicatorVisible(true);
        email.setWidthFull();
        email.setId("forgot-email-field");

        Button submit = new Button(texts.get("identity.forgot.submit"), event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();
        submit.setId("forgot-submit-button");

        add(email, submit, backToLoginButton());
    }

    @Override
    public String getPageTitle() {
        return texts.get("identity.forgot.pageTitle");
    }

    /** Navigations-Button zur Anmeldung — volle Breite, weil das Formular schmal ist. */
    private Button backToLoginButton() {
        Button button = new Button(texts.get("identity.common.backToLogin"),
                event -> UI.getCurrent().navigate(IdentityRoutes.LOGIN));
        button.setWidthFull();
        return button;
    }

    private void submit() {
        if (email.isEmpty() || email.isInvalid()) {
            email.setErrorMessage(texts.get("identity.common.invalidEmail"));
            email.setInvalid(true);
            return;
        }

        passwordResetService.requestReset(email.getValue());
        showConfirmation();
    }

    /**
     * Zeigt dieselbe Bestätigung, egal ob es zu der Adresse ein Konto gibt.
     * Eine ehrlichere Meldung wäre hier ein Sicherheitsproblem: Über dieses
     * Formular ließe sich sonst durchprobieren, wer bei uns registriert ist.
     */
    private void showConfirmation() {
        removeAll();
        add(new H2(texts.get("identity.common.mailSent.title")));
        add(new Paragraph(texts.get("identity.forgot.sent", email.getValue())
                + " " + texts.get("identity.common.spamHint")));
        add(backToLoginButton());
    }
}
