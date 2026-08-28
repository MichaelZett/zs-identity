package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.assertj.core.api.Assertions.assertThat;

class ConfirmEmailViewTest extends AbstractViewTest {

    private final FakeRegistrationService registrationService = new FakeRegistrationService();

    private ConfirmEmailView showConfirmView() {
        return showConfirmView(IdentityProperties.defaults());
    }

    private ConfirmEmailView showConfirmView(IdentityProperties properties) {
        return show(new ConfirmEmailView(registrationService, properties, MESSAGES));
    }

    private static void enterWithToken(ConfirmEmailView view, String token) {
        view.beforeEnter(enterEventWithToken(IdentityRoutes.CONFIRM_EMAIL, token));
    }

    /**
     * Der Link wird erst auf Knopfdruck eingelöst — Mail-Scanner rufen ihn
     * vorher per GET ab und würden das Einmal-Token sonst verbrauchen.
     */
    @Test
    void openingTheLinkDoesNotYetRedeemTheToken() {
        ConfirmEmailView view = showConfirmView();

        enterWithToken(view, "token-123");

        assertThat(registrationService.confirmedTokens).isEmpty();
        assertThat(_get(view, H2.class).getText()).isEqualTo("E-Mail bestätigen");
        assertThat(view.getPageTitle()).isEqualTo("E-Mail bestätigen");
    }

    @Test
    void theButtonRedeemsTheTokenAndReportsSuccess() {
        ConfirmEmailView view = showConfirmView();
        enterWithToken(view, "token-123");

        _click(_get(view, Button.class, spec -> spec.withId("confirm-email-button")));

        assertThat(registrationService.confirmedTokens).containsExactly("token-123");
        assertThat(_get(view, H2.class).getText()).isEqualTo("Konto freigeschaltet");
    }

    @Test
    void aLinkWithoutTokenIsReportedAsIncomplete() {
        ConfirmEmailView view = showConfirmView();

        view.beforeEnter(enterEventWith(IdentityRoutes.CONFIRM_EMAIL, Map.of()));

        assertThat(_get(view, H2.class).getText()).isEqualTo("Bestätigung nicht möglich");
        assertThat(_get(view, Paragraph.class).getText()).isEqualTo(
                "Dieser Link ist unvollständig. Bitte öffne ihn direkt aus der E-Mail.");
    }

    @Test
    void aSpentTokenOffersTheWayOut() {
        registrationService.failure = new IdentityException(IdentityMessageKeys.TOKEN_EXPIRED, "expired");
        ConfirmEmailView view = showConfirmView();
        enterWithToken(view, "token-123");

        _click(_get(view, Button.class, spec -> spec.withId("confirm-email-button")));

        assertThat(_get(view, H2.class).getText()).isEqualTo("Bestätigung nicht möglich");
        assertThat(_get(view, Paragraph.class).getText()).startsWith("Dieser Link ist abgelaufen");
        assertThat(_find(view, Button.class)).hasSize(2);
    }

    @Test
    void theResendButtonLeadsToTheResendView() {
        ConfirmEmailView view = showConfirmView();
        view.beforeEnter(enterEventWith(IdentityRoutes.CONFIRM_EMAIL, Map.of()));

        _click(_get(view, Button.class, spec -> spec.withId("confirm-resend-verification-button")));

        assertThat(currentPath()).isEqualTo(IdentityRoutes.RESEND_VERIFICATION);
    }

    @Test
    void theLoginButtonLeadsToTheLogin() {
        ConfirmEmailView view = showConfirmView();
        enterWithToken(view, "token-123");
        _click(_get(view, Button.class, spec -> spec.withId("confirm-email-button")));

        _click(_get(view, Button.class, spec -> spec.withId("confirm-login-button")));

        assertThat(currentPath()).isEqualTo(IdentityRoutes.LOGIN);
    }

    /** Mehrere Werte für {@code token} — der erste gewinnt, statt dass die Seite scheitert. */
    @Test
    void aDuplicatedTokenParameterUsesTheFirstValue() {
        ConfirmEmailView view = showConfirmView();

        view.beforeEnter(enterEventWith(IdentityRoutes.CONFIRM_EMAIL,
                Map.of(IdentityRoutes.TOKEN_PARAMETER, List.of("first", "second"))));
        _click(_get(view, Button.class, spec -> spec.withId("confirm-email-button")));

        assertThat(registrationService.confirmedTokens).containsExactly("first");
    }

    @Test
    void englishIsUsedWhenTheApplicationAsksForIt() {
        ConfirmEmailView view = showConfirmView(properties(IdentityProperties.defaults(), Locale.ENGLISH));

        enterWithToken(view, "token-123");

        assertThat(_get(view, H2.class).getText()).isEqualTo("Confirm e-mail");
        assertThat(_get(view, Paragraph.class).getText()).isEqualTo("One click and your account is active.");
    }
}
