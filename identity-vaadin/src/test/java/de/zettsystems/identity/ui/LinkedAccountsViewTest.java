package de.zettsystems.identity.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.spring.security.AuthenticationContext;
import de.zettsystems.identity.application.ExternalProviders;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.values.ExternalIdentityDto;
import de.zettsystems.identity.values.ExternalProvider;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityPaths;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.OAuth2Settings;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Linked providers: what a fresh sign-in sees, what a remember-me session
 * sees, unlinking, and what the round trip through the provider reports.
 */
class LinkedAccountsViewTest extends AbstractViewTest {

    private static final Instant LINKED = Instant.parse("2026-09-21T10:00:00Z");
    private static final IdentityProperties ENABLED = IdentityProperties.defaults()
            .withOAuth2(OAuth2Settings.defaults().enabled(true));
    private static final List<ExternalProvider> PROVIDERS =
            List.of(new ExternalProvider("google", "Google"), new ExternalProvider("github", "GitHub"));

    private final FakeExternalIdentityService identityService = new FakeExternalIdentityService();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static IdentityUserDetails ida() {
        return new IdentityUserDetails(7L, "ida@example.com", "Ida Beispiel", null, true, false,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static void signInFresh() {
        IdentityUserDetails user = ida();
        install(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    private static void signInByCookie() {
        IdentityUserDetails user = ida();
        install(new RememberMeAuthenticationToken("key", user, user.getAuthorities()));
    }

    private static void install(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private LinkedAccountsView newView(IdentityProperties properties, ExternalProviders providers) {
        return show(new LinkedAccountsView(identityService, providers, new AuthenticationContext(), properties,
                MESSAGES, Clock.fixed(LINKED, ZoneOffset.UTC)));
    }

    private LinkedAccountsView showView(Map<String, List<String>> parameters) {
        LinkedAccountsView view = newView(ENABLED, () -> PROVIDERS);
        view.beforeEnter(enterEventWith(IdentityRoutes.LINKED_ACCOUNTS, parameters));
        return view;
    }

    @Test
    void withoutASignInTheViewForwardsToTheSignIn() {
        LinkedAccountsView view = newView(ENABLED, () -> PROVIDERS);
        BeforeEnterEvent event = enterEventWith(IdentityRoutes.LINKED_ACCOUNTS, Map.of());

        view.beforeEnter(event);

        verify(event).forwardTo(IdentityRoutes.LOGIN);
    }

    @Test
    void switchedOffTheViewSaysSoAndOffersNothing() {
        signInFresh();
        LinkedAccountsView view = newView(IdentityProperties.defaults(), () -> PROVIDERS);

        view.beforeEnter(enterEventWith(IdentityRoutes.LINKED_ACCOUNTS, Map.of()));

        assertThat(_get(view, H2.class).getText()).isEqualTo("Verknüpfte Konten");
        assertThat(_get(view, Paragraph.class).getText()).contains("nicht verfügbar");
        assertThat(_find(view, Button.class)).isEmpty();
    }

    @Test
    void aFreshSignInSeesItsProvidersAndMayLinkTheOthers() {
        signInFresh();
        identityService.identities.add(new ExternalIdentityDto("google", "ida@gmail.com", LINKED, null));

        LinkedAccountsView view = showView(Map.of());

        assertThat(_get(view, Span.class, spec -> spec.withText("Google")).getText()).isEqualTo("Google");
        assertThat(_get(view, Div.class, spec -> spec.withText("ida@gmail.com"))).isNotNull();
        assertThat(_find(view, Button.class, spec -> spec.withId(LinkedAccountsView.UNLINK_BUTTON_ID_PREFIX + "google")))
                .hasSize(1);
        assertThat(_find(view, Button.class, spec -> spec.withId(LinkedAccountsView.LINK_BUTTON_ID_PREFIX + "google")))
                .as("linked already")
                .isEmpty();
        Button github = _get(view, Button.class, spec -> spec.withId(LinkedAccountsView.LINK_BUTTON_ID_PREFIX + "github"));
        assertThat(github.getText()).isEqualTo("GitHub verknüpfen");

        _click(github);

        assertThat(browserCall("window.open").getInvocation().getParameters())
                .contains("oauth2/authorization/github?" + IdentityPaths.LINK_PARAMETER);
    }

    @Test
    void anEmptyListSaysSo() {
        signInFresh();

        LinkedAccountsView view = showView(Map.of());

        assertThat(_get(view, Div.class, spec -> spec.withId(LinkedAccountsView.EMPTY_ID)).getText())
                .isEqualTo("Noch kein Dienst verknüpft.");
    }

    /** Linking needs a fresh sign-in, like adding a passkey; the view says so before anyone tries. */
    @Test
    void aRememberedSessionIsSentToSignInAgainBeforeLinking() {
        signInByCookie();

        LinkedAccountsView view = showView(Map.of());

        assertThat(_find(view, Button.class, spec -> spec.withId(LinkedAccountsView.LINK_BUTTON_ID_PREFIX + "github")))
                .isEmpty();
        _click(_get(view, Button.class, spec -> spec.withId(LinkedAccountsView.SIGN_IN_AGAIN_BUTTON_ID)));
        assertThat(currentPath()).isEqualTo(IdentityRoutes.LOGIN);
    }

    @Test
    void unlinkingRemovesTheRowAndOffersTheProviderAgain() {
        signInFresh();
        identityService.identities.add(new ExternalIdentityDto("google", null, LINKED, LINKED));
        LinkedAccountsView view = showView(Map.of());

        _click(_get(view, Button.class, spec -> spec.withId(LinkedAccountsView.UNLINK_BUTTON_ID_PREFIX + "google")));

        assertThat(identityService.unlinked).containsExactly("google");
        assertThat(notificationTexts()).contains("Der Dienst ist nicht mehr verknüpft.");
        assertThat(_find(view, Button.class, spec -> spec.withId(LinkedAccountsView.LINK_BUTTON_ID_PREFIX + "google")))
                .hasSize(1);
    }

    @Test
    void theLastWayInIsKept() {
        signInFresh();
        identityService.identities.add(new ExternalIdentityDto("google", null, LINKED, null));
        identityService.failure = new IdentityException(IdentityMessageKeys.LAST_SIGN_IN_METHOD, "last one");
        LinkedAccountsView view = showView(Map.of());

        view.executeUnlink("google");

        assertThat(notificationTexts()).anySatisfy(text -> assertThat(text).contains("einzige Weg"));
        assertThat(identityService.identities).hasSize(1);
    }

    @Test
    void theRoundTripReportsALink() {
        signInFresh();

        LinkedAccountsView view = showView(Map.of(IdentityPaths.LINKED_PARAMETER, List.of("github")));

        assertThat(_get(view, Div.class, spec -> spec.withId(LinkedAccountsView.MESSAGE_ID)).getText())
                .isEqualTo("GitHub ist jetzt verknüpft.");
    }

    @Test
    void theRoundTripReportsARefusal() {
        signInFresh();

        LinkedAccountsView view = showView(Map.of(IdentityPaths.EXTERNAL_ERROR_PARAMETER,
                List.of("already-linked")));

        assertThat(_get(view, Div.class, spec -> spec.withId(LinkedAccountsView.MESSAGE_ID)).getText())
                .contains("bereits mit einer anderen Anmeldung verknüpft");
    }
}
