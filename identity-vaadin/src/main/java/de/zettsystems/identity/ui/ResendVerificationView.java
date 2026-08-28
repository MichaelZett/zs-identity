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
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.values.IdentityProperties;

/**
 * Fordert die Bestätigungsmail erneut an.
 *
 * <p>Ohne diesen Weg ist ein Konto verloren, dessen Bestätigungsmail nicht
 * angekommen ist: Die Anmeldung scheitert am gesperrten Konto, und der Token
 * liegt nur als Hash in der Datenbank — der Link lässt sich also nirgends
 * nachschlagen.
 *
 * <p>Wie beim Zurücksetzen des Passworts ist die Rückmeldung immer dieselbe,
 * egal ob es zu der Adresse ein Konto gibt. Der Dienst schweigt aus demselben
 * Grund: Sonst ließe sich hier durchprobieren, wer registriert ist.
 */
@Route(value = IdentityRoutes.RESEND_VERIFICATION, autoLayout = false)
@AnonymousAllowed
public class ResendVerificationView extends VerticalLayout implements HasDynamicTitle {

    private final RegistrationService registrationService;
    private final IdentityTexts texts;
    private final EmailField email = new EmailField();

    public ResendVerificationView(RegistrationService registrationService, IdentityProperties properties,
                                  IdentityMessages messages) {
        this.registrationService = registrationService;
        this.texts = new IdentityTexts(messages, properties);

        setMaxWidth("28rem");
        getStyle().set("margin", "0 auto");

        add(new H2(texts.get("identity.resend.title")));
        add(new Paragraph(texts.get("identity.resend.intro")));

        email.setLabel(texts.get("identity.common.email"));
        email.setRequiredIndicatorVisible(true);
        email.setWidthFull();
        email.setId("resend-email-field");

        Button submit = new Button(texts.get("identity.resend.submit"), event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();
        submit.setId("resend-submit-button");

        add(email, submit, backToLoginButton());
    }

    @Override
    public String getPageTitle() {
        return texts.get("identity.resend.pageTitle");
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

        registrationService.resendVerification(email.getValue());
        showConfirmation();
    }

    private void showConfirmation() {
        removeAll();
        add(new H2(texts.get("identity.common.mailSent.title")));
        add(new Paragraph(texts.get("identity.resend.sent", email.getValue())
                + " " + texts.get("identity.common.spamHint")));
        add(backToLoginButton());
    }
}
