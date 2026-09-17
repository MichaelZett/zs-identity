package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.EmailField;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;

class ForgotPasswordViewTest extends AbstractViewTest {

    private final FakePasswordResetService passwordResetService = new FakePasswordResetService();

    private ForgotPasswordView showForgotView(IdentityProperties properties) {
        return show(new ForgotPasswordView(passwordResetService, properties, MESSAGES));
    }

    private static void submit(ForgotPasswordView view) {
        _click(_get(view, Button.class, spec -> spec.withId("forgot-submit-button")));
    }

    @Test
    void theFormIsLabelledInTheConfiguredLanguage() {
        ForgotPasswordView view = showForgotView(IdentityProperties.defaults());

        assertThat(_get(view, H2.class).getText()).isEqualTo("Passwort zurücksetzen");
        assertThat(_get(view, EmailField.class).getLabel()).isEqualTo("E-Mail-Adresse");
        assertThat(view.getPageTitle()).isEqualTo("Passwort vergessen");
    }

    @Test
    void anEmptyFormIsRefusedBeforeTheServiceIsCalled() {
        ForgotPasswordView view = showForgotView(IdentityProperties.defaults());

        submit(view);

        EmailField email = _get(view, EmailField.class);
        assertThat(email.isInvalid()).isTrue();
        assertThat(email.getErrorMessage()).isEqualTo("Bitte gib eine gültige E-Mail-Adresse ein.");
        assertThat(passwordResetService.resetRequests).isEmpty();
    }

    @Test
    void aMalformedAddressIsRefused() {
        ForgotPasswordView view = showForgotView(IdentityProperties.defaults());
        _setValue(_get(view, EmailField.class), "keine-adresse");

        submit(view);

        assertThat(_get(view, EmailField.class).isInvalid()).isTrue();
        assertThat(passwordResetService.resetRequests).isEmpty();
    }

    /**
     * The confirmation is the same whether or not the account exists; otherwise
     * this form could be used to work out who is registered.
     */
    @Test
    void aValidAddressIsPassedOnAndConfirmedNeutrally() {
        ForgotPasswordView view = showForgotView(IdentityProperties.defaults());
        _setValue(_get(view, EmailField.class), "anna@example.com");

        submit(view);

        assertThat(passwordResetService.resetRequests).containsExactly("anna@example.com");
        assertThat(_get(view, H2.class).getText()).isEqualTo("E-Mail unterwegs");
        assertThat(_get(view, Paragraph.class).getText())
                .isEqualTo("Wenn es zu anna@example.com ein Konto gibt, ist der Link jetzt unterwegs. "
                        + "Schau auch im Spam-Ordner nach.");
    }

    @Test
    void theBackButtonLeadsToTheLogin() {
        ForgotPasswordView view = showForgotView(IdentityProperties.defaults());

        _click(_get(view, Button.class, spec -> spec.withText("Zurück zur Anmeldung")));

        assertThat(currentPath()).isEqualTo(IdentityRoutes.LOGIN);
    }

    @Test
    void englishIsUsedWhenTheApplicationAsksForIt() {
        ForgotPasswordView view = showForgotView(properties(IdentityProperties.defaults(), Locale.ENGLISH));
        _setValue(_get(view, EmailField.class), "anna@example.com");

        submit(view);

        assertThat(_get(view, Paragraph.class).getText())
                .isEqualTo("If there is an account for anna@example.com, the link is on its way. "
                        + "Please also check your spam folder.");
    }
}
