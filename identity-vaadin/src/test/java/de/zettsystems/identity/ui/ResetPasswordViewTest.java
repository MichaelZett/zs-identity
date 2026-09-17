package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;

class ResetPasswordViewTest extends AbstractViewTest {

    private final FakePasswordResetService passwordResetService = new FakePasswordResetService();

    private ResetPasswordView showResetView() {
        return show(new ResetPasswordView(passwordResetService, IdentityProperties.defaults(), MESSAGES));
    }

    private static ResetPasswordView enteredWithToken(ResetPasswordView view, String token) {
        view.beforeEnter(enterEventWithToken(IdentityRoutes.RESET_PASSWORD, token));
        return view;
    }

    private static void enterPasswords(ResetPasswordView view, String password, String repeat) {
        _setValue(_get(view, PasswordField.class, spec -> spec.withLabel("Neues Passwort")), password);
        _setValue(_get(view, PasswordField.class, spec -> spec.withLabel("Neues Passwort wiederholen")), repeat);
    }

    private static void submit(ResetPasswordView view) {
        _click(_get(view, Button.class, spec -> spec.withId("reset-submit-button")));
    }

    @Test
    void theFormAppearsOnlyWithAToken() {
        ResetPasswordView view = enteredWithToken(showResetView(), "token-123");

        assertThat(_get(view, H2.class).getText()).isEqualTo("Neues Passwort setzen");
        assertThat(_find(view, PasswordField.class)).hasSize(2);
        assertThat(view.getPageTitle()).isEqualTo("Neues Passwort");
    }

    @Test
    void theMinimumPasswordLengthIsSpelledOut() {
        ResetPasswordView view = enteredWithToken(showResetView(), "token-123");

        assertThat(_get(view, PasswordField.class, spec -> spec.withLabel("Neues Passwort")).getHelperText())
                .isEqualTo("Mindestens 12 Zeichen");
    }

    @Test
    void aLinkWithoutTokenOffersANewOne() {
        ResetPasswordView view = showResetView();

        view.beforeEnter(enterEventWith(IdentityRoutes.RESET_PASSWORD, Map.of()));

        assertThat(_get(view, H2.class).getText()).isEqualTo("Link unvollständig");
        assertThat(_find(view, PasswordField.class)).isEmpty();

        _click(_get(view, Button.class));
        assertThat(currentPath()).isEqualTo(IdentityRoutes.FORGOT_PASSWORD);
    }

    @Test
    void twoDifferentPasswordsAreRefused() {
        ResetPasswordView view = enteredWithToken(showResetView(), "token-123");
        enterPasswords(view, "sicheres-passwort", "anderes-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Die beiden Passwörter stimmen nicht überein.");
        assertThat(passwordResetService.usedTokens).isEmpty();
    }

    @Test
    void anEmptyPasswordIsRefused() {
        ResetPasswordView view = enteredWithToken(showResetView(), "token-123");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Die beiden Passwörter stimmen nicht überein.");
        assertThat(passwordResetService.usedTokens).isEmpty();
    }

    @Test
    void theNewPasswordIsHandedOverWithItsToken() {
        ResetPasswordView view = enteredWithToken(showResetView(), "token-123");
        enterPasswords(view, "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(passwordResetService.usedTokens).containsExactly("token-123");
        assertThat(passwordResetService.newPasswords).containsExactly("sicheres-passwort");
        assertThat(_get(view, H2.class).getText()).isEqualTo("Passwort geändert");

        _click(_get(view, Button.class));
        assertThat(currentPath()).isEqualTo(IdentityRoutes.LOGIN);
    }

    @Test
    void aSpentTokenIsReportedAsSuch() {
        passwordResetService.failure = new IdentityException(IdentityMessageKeys.TOKEN_EXPIRED, "expired");
        ResetPasswordView view = enteredWithToken(showResetView(), "token-123");
        enterPasswords(view, "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly(
                "Dieser Link ist abgelaufen oder wurde bereits benutzt. Fordere einen neuen an.");
    }

    @Test
    void aTooShortPasswordNamesTheMinimumLength() {
        passwordResetService.failure = new IdentityException(IdentityMessageKeys.PASSWORD_TOO_SHORT, "too short");
        ResetPasswordView view = enteredWithToken(showResetView(), "token-123");
        enterPasswords(view, "kurz", "kurz");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Das Passwort muss mindestens 12 Zeichen lang sein.");
    }

    @Test
    void anUnknownMessageKeyBecomesTheGeneralMessage() {
        passwordResetService.failure = new IdentityException("app.something.went.wrong", "custom failure");
        ResetPasswordView view = enteredWithToken(showResetView(), "token-123");
        enterPasswords(view, "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Das hat nicht geklappt. Bitte versuche es später erneut.");
    }

    /** A second visit without a token must not leave the previous form standing. */
    @Test
    void enteringAgainWithoutTokenClearsTheForm() {
        ResetPasswordView view = enteredWithToken(showResetView(), "token-123");

        view.beforeEnter(enterEventWith(IdentityRoutes.RESET_PASSWORD, Map.of()));

        assertThat(_find(view, PasswordField.class)).isEmpty();
        assertThat(_get(view, H2.class).getText()).isEqualTo("Link unvollständig");
    }

    @Test
    void theConfirmationIsShownWithoutTheEnteredPassword() {
        ResetPasswordView view = enteredWithToken(showResetView(), "token-123");
        enterPasswords(view, "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(_get(view, Paragraph.class).getText())
                .isEqualTo("Du kannst dich jetzt mit deinem neuen Passwort anmelden.");
    }
}
