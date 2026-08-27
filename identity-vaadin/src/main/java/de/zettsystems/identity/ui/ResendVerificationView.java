package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.RegistrationService;

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
@PageTitle("Bestätigungsmail anfordern")
@AnonymousAllowed
public class ResendVerificationView extends VerticalLayout {

    private final RegistrationService registrationService;
    private final EmailField email = new EmailField("E-Mail-Adresse");

    public ResendVerificationView(RegistrationService registrationService) {
        this.registrationService = registrationService;

        setMaxWidth("28rem");
        getStyle().set("margin", "0 auto");

        add(new H2("Bestätigungsmail erneut anfordern"));
        add(new Paragraph("Gib die E-Mail-Adresse ein, mit der du dich registriert hast. "
                + "Ist das Konto noch nicht bestätigt, schicken wir den Link erneut."));

        email.setRequiredIndicatorVisible(true);
        email.setWidthFull();
        email.setId("resend-email-field");

        Button submit = new Button("Mail anfordern", event -> submit());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();
        submit.setId("resend-submit-button");

        add(email, submit, backToLoginButton());
    }

    /** Navigations-Button zur Anmeldung — volle Breite, weil das Formular schmal ist. */
    private static Button backToLoginButton() {
        Button button = new Button("Zurück zur Anmeldung",
                event -> UI.getCurrent().navigate(IdentityRoutes.LOGIN));
        button.setWidthFull();
        return button;
    }

    private void submit() {
        if (email.isEmpty() || email.isInvalid()) {
            email.setErrorMessage("Bitte gib eine gültige E-Mail-Adresse ein.");
            email.setInvalid(true);
            return;
        }

        registrationService.resendVerification(email.getValue());
        showConfirmation();
    }

    private void showConfirmation() {
        removeAll();
        add(new H2("E-Mail unterwegs"));
        add(new Paragraph("Wenn es zu %s ein noch unbestätigtes Konto gibt, ist der Link jetzt "
                .formatted(email.getValue())
                + "unterwegs. Schau auch im Spam-Ordner nach."));
        add(backToLoginButton());
    }
}
