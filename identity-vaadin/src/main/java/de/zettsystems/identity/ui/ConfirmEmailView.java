package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.RegistrationService;

import java.util.List;
import java.util.Map;

/**
 * Nimmt den Link aus der Bestätigungsmail entgegen und schaltet das Konto frei.
 *
 * <p>Das Token kommt als Abfrageparameter, nicht als Pfadsegment: So bleibt der
 * Pfad in Logs und Verlauf lesbar, und der Link lässt sich ohne
 * Sonderbehandlung zusammenbauen.
 *
 * <p>Eingelöst wird erst auf Knopfdruck, nicht beim Seitenaufbau: Mail-Scanner
 * und Link-Vorschauen rufen den Link per GET ab, bevor der Mensch klickt — bei
 * automatischer Einlösung verbrauchen sie das Einmal-Token, und der echte Klick
 * läuft in „Link bereits benutzt" (Praxisfall Gruppentest 19.08.2026).
 */
@Route(value = IdentityRoutes.CONFIRM_EMAIL, autoLayout = false)
@PageTitle("E-Mail bestätigen")
@AnonymousAllowed
public class ConfirmEmailView extends VerticalLayout implements BeforeEnterObserver {

    private final RegistrationService registrationService;

    public ConfirmEmailView(RegistrationService registrationService) {
        this.registrationService = registrationService;
        setMaxWidth("32rem");
        getStyle().set("margin", "0 auto");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();

        Map<String, List<String>> parameters = event.getLocation().getQueryParameters().getParameters();
        List<String> tokens = parameters.getOrDefault(IdentityRoutes.TOKEN_PARAMETER, List.of());
        if (tokens.isEmpty()) {
            showFailure("Dieser Link ist unvollständig. Bitte öffne ihn direkt aus der E-Mail.");
            return;
        }

        showPrompt(tokens.getFirst());
    }

    private void showPrompt(String token) {
        add(new H2("E-Mail bestätigen"));
        add(new Paragraph("Ein Klick, und dein Konto ist freigeschaltet."));
        Button confirm = new Button("E-Mail-Adresse bestätigen", event -> confirm(token));
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setId("confirm-email-button");
        confirm.setWidthFull();
        add(confirm);
    }

    private void confirm(String token) {
        removeAll();
        try {
            registrationService.confirmEmail(token);
            add(new H2("Konto freigeschaltet"));
            add(new Paragraph("Deine E-Mail-Adresse ist bestätigt. Du kannst dich jetzt anmelden."));
            add(loginButton());
        } catch (IdentityException _) {
            showFailure("Dieser Link ist abgelaufen oder wurde bereits benutzt. "
                    + "Du kannst dir die Bestätigungsmail erneut schicken lassen.");
        }
    }

    private void showFailure(String message) {
        add(new H2("Bestätigung nicht möglich"));
        add(new Paragraph(message));
        add(resendButton(), loginButton());
    }

    /** Der Ausweg aus einem abgelaufenen Link — sonst bleibt das Konto gesperrt. */
    private static Button resendButton() {
        Button button = new Button("Bestätigungsmail erneut anfordern",
                event -> UI.getCurrent().navigate(IdentityRoutes.RESEND_VERIFICATION));
        button.setId("confirm-resend-verification-button");
        button.setWidthFull();
        return button;
    }

    /** Navigations-Button zur Anmeldung — volle Breite, weil die Ansicht schmal ist. */
    private static Button loginButton() {
        Button button = new Button("Zur Anmeldung",
                event -> UI.getCurrent().navigate(IdentityRoutes.LOGIN));
        button.setWidthFull();
        return button;
    }
}
