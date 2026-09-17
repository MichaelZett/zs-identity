package de.zettsystems.identity.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.spring.security.AuthenticationContext;
import de.zettsystems.identity.application.IdentityException;
import de.zettsystems.identity.application.IdentityUserDetails;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Changing the password and the forced change: whoever carries the flag ends up
 * here on every navigation until the password is new.
 */
class ChangePasswordViewTest extends AbstractViewTest {

    private final FakeUserAccountService userAccountService = new FakeUserAccountService();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(boolean mustChangePassword) {
        IdentityUserDetails user = new IdentityUserDetails(7L, "anna@example.com", "Anna Beispiel",
                "hash", true, mustChangePassword, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user, "credentials", user.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private ChangePasswordView showView() {
        ChangePasswordView view = show(new ChangePasswordView(userAccountService,
                new AuthenticationContext(), IdentityProperties.defaults(), MESSAGES));
        view.beforeEnter(enterEventWith(IdentityRoutes.CHANGE_PASSWORD, Map.of()));
        return view;
    }

    private static void enterPasswords(ChangePasswordView view, String password, String repeat) {
        _setValue(_get(view, PasswordField.class, spec -> spec.withLabel("Neues Passwort")), password);
        _setValue(_get(view, PasswordField.class, spec -> spec.withLabel("Neues Passwort wiederholen")), repeat);
    }

    private static void submit(ChangePasswordView view) {
        _click(_get(view, Button.class, spec -> spec.withId("change-password-submit-button")));
    }

    @Test
    void aFlaggedAccountSeesWhyThePageAppears() {
        signIn(true);

        ChangePasswordView view = showView();

        assertThat(_get(view, H2.class).getText()).isEqualTo("Passwort ändern");
        assertThat(_get(view, Paragraph.class).getText()).contains("neues Passwort fällig");
        assertThat(_find(view, PasswordField.class)).hasSize(2);
        assertThat(_get(view, Button.class, spec -> spec.withId("change-password-logout-button"))).isNotNull();
        assertThat(view.getPageTitle()).isEqualTo("Passwort ändern");
    }

    @Test
    void aVoluntaryChangeComesWithoutTheExplanation() {
        signIn(false);

        ChangePasswordView view = showView();

        assertThat(_find(view, Paragraph.class)).isEmpty();
        assertThat(_find(view, PasswordField.class)).hasSize(2);
    }

    @Test
    void twoDifferentPasswordsAreRefused() {
        signIn(true);
        ChangePasswordView view = showView();
        enterPasswords(view, "sicheres-passwort", "anderes-passwort");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Die beiden Passwörter stimmen nicht überein.");
        assertThat(userAccountService.changedUserIds).isEmpty();
    }

    @Test
    void theNewPasswordGoesToTheSignedInAccount() {
        signIn(true);
        ChangePasswordView view = showView();
        enterPasswords(view, "sicheres-passwort", "sicheres-passwort");

        submit(view);

        assertThat(userAccountService.changedUserIds).containsExactly(7L);
        assertThat(userAccountService.newPasswords).containsExactly("sicheres-passwort");
        assertThat(_get(view, H2.class).getText()).isEqualTo("Passwort geändert");
        assertThat(_find(view, PasswordField.class)).isEmpty();

        // In production the service refreshes the session; here the test does
        // that itself, or the guard would send "continue" straight back here.
        signIn(false);
        UI.getCurrent().navigate("somewhere");
        _click(_get(view, Button.class, spec -> spec.withId("change-password-proceed-button")));
        assertThat(currentPath()).isEmpty();
    }

    @Test
    void aTooShortPasswordNamesTheMinimumLength() {
        signIn(true);
        userAccountService.failure = new IdentityException(IdentityMessageKeys.PASSWORD_TOO_SHORT, "too short");
        ChangePasswordView view = showView();
        enterPasswords(view, "kurz", "kurz");

        submit(view);

        assertThat(notificationTexts()).containsExactly("Das Passwort muss mindestens 12 Zeichen lang sein.");
    }

    /**
     * The guard is already attached to the UI: Karibu loads the
     * {@code VaadinServiceInitListener} from {@code META-INF/services} just as
     * the real server does, and that registration is checked here along the
     * way.
     */
    @Test
    void theGuardForwardsAFlaggedAccountToThisView() {
        signIn(true);

        UI.getCurrent().navigate("somewhere");

        assertThat(currentPath()).isEqualTo(IdentityRoutes.CHANGE_PASSWORD);
    }

    @Test
    void theGuardLetsEveryoneElsePass() {
        UI.getCurrent().navigate("somewhere");
        assertThat(currentPath()).isEqualTo("somewhere");

        signIn(false);
        UI.getCurrent().navigate("");
        assertThat(currentPath()).isEmpty();
    }
}
