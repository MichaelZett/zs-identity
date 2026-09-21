package de.zettsystems.identity.ui;

import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.spring.security.AuthenticationContext;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.PasskeySettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Managing passkeys: what a fresh sign-in sees, what a remember-me session
 * sees, and the answers of the browser played back into the view.
 */
class PasskeyViewTest extends AbstractViewTest {

    private static final Instant CREATED = Instant.parse("2026-09-21T10:00:00Z");
    private static final IdentityProperties ENABLED = IdentityProperties.defaults()
            .withPasskeys(PasskeySettings.defaults().enabled(true));

    private final FakePasskeyService passkeyService = new FakePasskeyService();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static IdentityUserDetails anna() {
        return new IdentityUserDetails(7L, "anna@example.com", "Anna Beispiel", "hash", true, false,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static void signInFresh() {
        IdentityUserDetails user = anna();
        install(UsernamePasswordAuthenticationToken.authenticated(user, "credentials", user.getAuthorities()));
    }

    private static void signInByCookie() {
        IdentityUserDetails user = anna();
        install(new RememberMeAuthenticationToken("key", user, user.getAuthorities()));
    }

    private static void install(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private PasskeyView showView(IdentityProperties properties) {
        PasskeyView view = show(new PasskeyView(passkeyService, new AuthenticationContext(), properties, MESSAGES,
                Clock.fixed(CREATED, ZoneOffset.UTC)));
        view.beforeEnter(enterEventWith(IdentityRoutes.PASSKEYS, Map.of()));
        return view;
    }

    @Test
    void withoutASignInTheViewForwardsToTheSignIn() {
        PasskeyView view = show(new PasskeyView(passkeyService, new AuthenticationContext(), ENABLED, MESSAGES,
                Clock.fixed(CREATED, ZoneOffset.UTC)));
        BeforeEnterEvent event = enterEventWith(IdentityRoutes.PASSKEYS, Map.of());

        view.beforeEnter(event);

        verify(event).forwardTo(IdentityRoutes.LOGIN);
    }

    @Test
    void withPasskeysOffTheViewSaysSoAndOffersNothing() {
        signInFresh();

        PasskeyView view = showView(IdentityProperties.defaults());

        assertThat(_get(view, H2.class).getText()).isEqualTo("Passkeys");
        assertThat(_get(view, Paragraph.class).getText()).contains("nicht zur Verfügung");
        assertThat(_find(view, Button.class)).isEmpty();
        assertThat(_find(view, TextField.class)).isEmpty();
    }

    @Test
    void aFreshSignInMayAddAPasskeyAndSeesTheEmptyList() {
        signInFresh();

        PasskeyView view = showView(ENABLED);

        assertThat(view.getPageTitle()).isEqualTo("Passkeys");
        assertThat(view.getClassNames()).contains(IdentityFormView.VIEW_CLASS + "--passkeys");
        TextField label = _get(view, TextField.class);
        Button register = _get(view, Button.class, spec -> spec.withId(PasskeyView.REGISTER_BUTTON_ID));
        assertThat(label.getLabel()).isEqualTo("Name des Passkeys");
        assertThat(register.getText()).isEqualTo("Passkey hinzufügen");
        assertThat(((HasSize) label).getWidth()).isEqualTo("100%");
        assertThat(register.getWidth()).isEqualTo("100%");
        assertThat(_get(view, Div.class, spec -> spec.withId(PasskeyView.EMPTY_ID)).getText())
                .isEqualTo("Noch kein Passkey eingerichtet.");
        assertThat(_find(view, Button.class, spec -> spec.withId(PasskeyView.SIGN_IN_AGAIN_BUTTON_ID))).isEmpty();
    }

    /** The cookie alone is not a fresh sign-in; the list is shown, the form is not. */
    @Test
    void aRememberMeSessionIsSentToSignInWithThePasswordFirst() {
        signInByCookie();
        passkeyService.add(1L, "iPhone", CREATED, null);

        PasskeyView view = showView(ENABLED);

        assertThat(PasskeyView.isRememberMeSession()).isTrue();
        assertThat(_find(view, Button.class, spec -> spec.withId(PasskeyView.REGISTER_BUTTON_ID))).isEmpty();
        assertThat(_find(view, TextField.class)).isEmpty();
        assertThat(_find(view, Paragraph.class)).extracting(Paragraph::getText)
                .anyMatch(text -> text.contains("zuerst mit deinem Passwort"));
        assertThat(_find(view, Button.class, spec -> spec.withId(PasskeyView.DELETE_BUTTON_ID_PREFIX + 1)))
                .as("the list stays usable").hasSize(1);

        _click(_get(view, Button.class, spec -> spec.withId(PasskeyView.SIGN_IN_AGAIN_BUTTON_ID)));
        assertThat(currentPath()).isEqualTo(IdentityRoutes.LOGIN);
    }

    @Test
    void theListShowsLabelAndDatesInTheLanguageOfTheView() {
        signInFresh();
        passkeyService.add(1L, "iPhone", CREATED, null);
        passkeyService.add(2L, "Laptop", CREATED, CREATED.plusSeconds(86_400));

        PasskeyView view = showView(ENABLED);

        List<String> rows = _find(view, Div.class, spec -> spec.withClasses(IdentityFormView.VIEW_CLASS + "__passkey"))
                .stream().map(row -> row.getElement().getTextRecursively()).toList();
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).contains("iPhone").contains("angelegt am 21.09.2026").doesNotContain("zuletzt");
        assertThat(rows.get(1)).contains("Laptop").contains("zuletzt benutzt am 22.09.2026");
    }

    @Test
    void removingAPasskeyGoesToTheServiceAndRefreshesTheList() {
        signInFresh();
        passkeyService.add(1L, "iPhone", CREATED, null);
        PasskeyView view = showView(ENABLED);

        _click(_get(view, Button.class, spec -> spec.withId(PasskeyView.DELETE_BUTTON_ID_PREFIX + 1)));

        assertThat(passkeyService.deleted).containsExactly(1L);
        assertThat(notificationTexts()).containsExactly("Passkey entfernt.");
        assertThat(_find(view, Div.class, spec -> spec.withId(PasskeyView.EMPTY_ID))).hasSize(1);
    }

    @Test
    void aPasskeyThatIsAlreadyGoneIsReportedNotSwallowed() {
        signInFresh();
        PasskeyView view = showView(ENABLED);

        view.executeDelete(99L);

        assertThat(notificationTexts()).containsExactly("Diesen Passkey gibt es nicht mehr.");
    }

    /**
     * The button starts the ceremony in the browser with the label from the
     * field (or the default); the browser's "ok" refreshes the list.
     */
    @Test
    void theRegisterButtonStartsTheCeremonyInTheBrowserAndTheAnswerRefreshesTheList() {
        signInFresh();
        PasskeyView view = showView(ENABLED);

        _click(_get(view, Button.class, spec -> spec.withId(PasskeyView.REGISTER_BUTTON_ID)));

        PendingJavaScriptInvocation call = browserCall("navigator.credentials.create");
        // Vaadin appends the element itself as the last parameter ($this).
        assertThat(call.getInvocation().getParameters().subList(0, 4))
                .as("context path, CSRF header, CSRF token, label")
                .containsExactly("", "", "", "Passkey");

        // What the browser stored through Spring's endpoint is now in the service.
        passkeyService.add(1L, "Passkey", CREATED, null);
        browserResolves(call, "ok");

        assertThat(notificationTexts()).containsExactly("Passkey hinzugefügt.");
        assertThat(_find(view, Button.class, spec -> spec.withId(PasskeyView.DELETE_BUTTON_ID_PREFIX + 1))).hasSize(1);
    }

    @Test
    void theLabelFromTheFieldGoesToTheBrowser() {
        signInFresh();
        PasskeyView view = showView(ENABLED);
        _setValue(_get(view, TextField.class), "  Mein iPhone  ");

        _click(_get(view, Button.class, spec -> spec.withId(PasskeyView.REGISTER_BUTTON_ID)));

        PendingJavaScriptInvocation call = browserCall("navigator.credentials.create");
        assertThat(call.getInvocation().getParameters().get(3)).isEqualTo("Mein iPhone");

        browserRejects(call, "cancelled");
        assertThat(notificationTexts()).containsExactly("Das Hinzufügen des Passkeys wurde abgebrochen.");
    }

    @Test
    void theBrowsersAnswersArePlayedBackAsTexts() {
        signInFresh();
        PasskeyView view = showView(ENABLED);

        passkeyService.add(1L, "iPhone", CREATED, null);
        view.onRegistered();
        assertThat(_find(view, Button.class, spec -> spec.withId(PasskeyView.DELETE_BUTTON_ID_PREFIX + 1)))
                .as("the list is refreshed after a registration").hasSize(1);

        view.onRegistrationError("Error: cancelled");
        view.onRegistrationError("unsupported");
        view.onRegistrationError("something else entirely");

        assertThat(notificationTexts()).containsExactly(
                "Passkey hinzugefügt.",
                "Das Hinzufügen des Passkeys wurde abgebrochen.",
                "Dieser Browser unterstützt keine Passkeys.",
                "Der Passkey konnte nicht hinzugefügt werden. Bitte versuche es noch einmal.");
    }
}
