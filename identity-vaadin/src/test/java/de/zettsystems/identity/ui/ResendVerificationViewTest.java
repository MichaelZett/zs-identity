package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.EmailField;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.Test;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;

class ResendVerificationViewTest extends AbstractViewTest {

    private final FakeRegistrationService registrationService = new FakeRegistrationService();

    private ResendVerificationView showResendView() {
        return show(new ResendVerificationView(registrationService, IdentityProperties.defaults(), MESSAGES));
    }

    private static void submit(ResendVerificationView view) {
        _click(_get(view, Button.class, spec -> spec.withId("resend-submit-button")));
    }

    @Test
    void theFormIsLabelledInTheConfiguredLanguage() {
        ResendVerificationView view = showResendView();

        assertThat(_get(view, H2.class).getText()).isEqualTo("Bestätigungsmail erneut anfordern");
        assertThat(_get(view, EmailField.class).getLabel()).isEqualTo("E-Mail-Adresse");
        assertThat(view.getPageTitle()).isEqualTo("Bestätigungsmail anfordern");
    }

    @Test
    void anEmptyFormIsRefusedBeforeTheServiceIsCalled() {
        ResendVerificationView view = showResendView();

        submit(view);

        assertThat(_get(view, EmailField.class).isInvalid()).isTrue();
        assertThat(registrationService.resendRequests).isEmpty();
    }

    @Test
    void aMalformedAddressIsRefused() {
        ResendVerificationView view = showResendView();
        _setValue(_get(view, EmailField.class), "keine-adresse");

        submit(view);

        assertThat(_get(view, EmailField.class).getErrorMessage())
                .isEqualTo("Bitte gib eine gültige E-Mail-Adresse ein.");
        assertThat(registrationService.resendRequests).isEmpty();
    }

    /** As with the password reset: the same answer whether or not the account exists. */
    @Test
    void aValidAddressIsPassedOnAndConfirmedNeutrally() {
        ResendVerificationView view = showResendView();
        _setValue(_get(view, EmailField.class), "anna@example.com");

        submit(view);

        assertThat(registrationService.resendRequests).containsExactly("anna@example.com");
        assertThat(_get(view, H2.class).getText()).isEqualTo("E-Mail unterwegs");
        assertThat(_get(view, Paragraph.class).getText())
                .isEqualTo("Wenn es zu anna@example.com ein noch unbestätigtes Konto gibt, "
                        + "ist der Link jetzt unterwegs. Schau auch im Spam-Ordner nach.");
    }

    @Test
    void theBackButtonLeadsToTheLogin() {
        ResendVerificationView view = showResendView();

        _click(_get(view, Button.class, spec -> spec.withText("Zurück zur Anmeldung")));

        assertThat(currentPath()).isEqualTo(IdentityRoutes.LOGIN);
    }
}
