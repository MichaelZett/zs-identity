package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimAccountViewTest extends AbstractViewTest {

    private final FakeInvitationService invitationService = new FakeInvitationService();

    private ClaimAccountView showClaimView() {
        return show(new ClaimAccountView(invitationService, IdentityProperties.defaults(), MESSAGES));
    }

    private ClaimAccountView enteredWithToken(String token) {
        ClaimAccountView view = showClaimView();
        view.beforeEnter(enterEventWithToken(IdentityRoutes.CLAIM_ACCOUNT, token));
        return view;
    }

    private static void enterPasswords(ClaimAccountView view, String password, String repeat) {
        _setValue(_get(view, PasswordField.class, spec -> spec.withLabel("Passwort")), password);
        _setValue(_get(view, PasswordField.class, spec -> spec.withLabel("Passwort wiederholen")), repeat);
    }

    private static void submit(ClaimAccountView view) {
        _click(_get(view, Button.class, spec -> spec.withId("claim-submit-button")));
    }

    /** Wer eingeladen wurde, hat nichts bestellt — der Name sagt ihm, worum es geht. */
    @Test
    void theFormNamesTheAccountItBelongsTo() {
        ClaimAccountView view = enteredWithToken("token-123");

        assertThat(_get(view, H2.class).getText()).isEqualTo("Zugang einrichten");
        assertThat(_get(view, Paragraph.class).getText())
                .isEqualTo("Der Zugang für Ida Beispiel ist vorbereitet. Setze jetzt dein Passwort.");
        assertThat(_find(view, PasswordField.class)).hasSize(2);
        assertThat(view.getPageTitle()).isEqualTo("Zugang einrichten");
    }

    @Test
    void theMinimumPasswordLengthIsSpelledOut() {
        ClaimAccountView view = enteredWithToken("token-123");

        assertThat(_get(view, PasswordField.class, spec -> spec.withLabel("Passwort")).getHelperText())
                .isEqualTo("Mindestens 12 Zeichen");
    }

    @Test
    void aLinkWithoutTokenLeadsToTheLogin() {
        ClaimAccountView view = showClaimView();

        view.beforeEnter(enterEventWith(IdentityRoutes.CLAIM_ACCOUNT, Map.of()));

        assertThat(_get(view, H2.class).getText()).isEqualTo("Link unvollständig");
        assertThat(_find(view, PasswordField.class)).isEmpty();

        _click(_get(view, Button.class));
        assertThat(currentPath()).isEqualTo(IdentityRoutes.LOGIN);
    }

    /** „Neuen Link anfordern" gibt es hier nicht — eingeladen wird aus der Verwaltung. */
    @Test
    void anUnknownTokenSaysWhomToAsk() {
        invitationService.invitee = null;

        ClaimAccountView view = enteredWithToken("token-123");

        assertThat(_get(view, H2.class).getText()).isEqualTo("Einladung nicht gefunden");
        assertThat(_get(view, Paragraph.class).getText())
                .isEqualTo("Diesen Link kennen wir nicht. Bitte wende dich an die Person, die dich eingeladen hat.");
        assertThat(_find(view, PasswordField.class)).isEmpty();
    }

    @Test
    void twoDifferentPasswordsAreRefused() {
        ClaimAccountView view = enteredWithToken("token-123");
        enterPasswords(view, "sicheres-passwort", "anderes-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Die beiden Passwörter stimmen nicht überein.");
        assertThat(invitationService.usedTokens).isEmpty();
    }

    @Test
    void thePasswordIsHandedOverWithItsToken() {
        ClaimAccountView view = enteredWithToken("token-123");
        enterPasswords(view, "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(invitationService.usedTokens).containsExactly("token-123");
        assertThat(invitationService.newPasswords).containsExactly("sicheres-passwort");
        assertThat(invitationService.claimedLocales)
                .as("die Sprache der Einlöse-Ansicht ist die erste Aussage des Eingeladenen dazu")
                .containsExactly(Locale.GERMAN);
        assertThat(_get(view, H2.class).getText()).isEqualTo("Zugang eingerichtet");

        _click(_get(view, Button.class));
        assertThat(currentPath()).isEqualTo(IdentityRoutes.LOGIN);
    }

    @Test
    void aSpentInvitationIsReportedAsSuch() {
        invitationService.failure = new IdentityException(IdentityMessageKeys.TOKEN_EXPIRED, "expired");
        ClaimAccountView view = enteredWithToken("token-123");
        enterPasswords(view, "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly(
                "Dieser Link ist abgelaufen oder wurde bereits benutzt. Fordere einen neuen an.");
    }

    @Test
    void anAlreadyClaimedAccountIsReportedAsSuch() {
        invitationService.failure =
                new IdentityException(IdentityMessageKeys.ACCOUNT_ALREADY_CLAIMED, "already claimed");
        ClaimAccountView view = enteredWithToken("token-123");
        enterPasswords(view, "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Dieses Konto gehört bereits jemandem.");
    }

    @Test
    void aTooShortPasswordNamesTheMinimumLength() {
        invitationService.failure = new IdentityException(IdentityMessageKeys.PASSWORD_TOO_SHORT, "too short");
        ClaimAccountView view = enteredWithToken("token-123");
        enterPasswords(view, "kurz", "kurz");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Das Passwort muss mindestens 12 Zeichen lang sein.");
    }

    @Test
    void anUnknownMessageKeyBecomesTheGeneralMessage() {
        invitationService.failure = new IdentityException("app.something.went.wrong", "custom failure");
        ClaimAccountView view = enteredWithToken("token-123");
        enterPasswords(view, "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Das hat nicht geklappt. Bitte versuche es später erneut.");
    }

    /** Ein erneuter Aufruf ohne Token darf das Formular des vorigen nicht stehen lassen. */
    @Test
    void enteringAgainWithoutTokenClearsTheForm() {
        ClaimAccountView view = enteredWithToken("token-123");

        view.beforeEnter(enterEventWith(IdentityRoutes.CLAIM_ACCOUNT, Map.of()));

        assertThat(_find(view, PasswordField.class)).isEmpty();
        assertThat(_get(view, H2.class).getText()).isEqualTo("Link unvollständig");
    }
}
