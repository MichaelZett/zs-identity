package de.zettsystems.identity.ui;

import com.vaadin.flow.spring.security.AuthenticationContext;
import de.zettsystems.identity.application.IdentityUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityUsersTest {

    private final AuthenticationContext context = new AuthenticationContext();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anIdentityPrincipalIsReturned() {
        IdentityUserDetails user = new IdentityUserDetails(7L, "anna@example.com", "Anna Beispiel",
                "hash", true, false, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        signIn(user);

        assertThat(IdentityUsers.current(context)).containsSame(user);
    }

    @Test
    void aForeignPrincipalIsEmptyInsteadOfAClassCastException() {
        signIn(new User("demo", "secret", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThat(IdentityUsers.current(context)).isEmpty();
    }

    @Test
    void nobodySignedInIsEmpty() {
        assertThat(IdentityUsers.current(context)).isEmpty();
    }

    private static void signIn(Object principal) {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, "credentials", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(securityContext);
    }
}
