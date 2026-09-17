package de.zettsystems.identity.application;

import de.zettsystems.identity.values.Scope;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ActiveScopeServiceTest {

    private static final Scope CLUB_17 = Scope.of("club", "17");

    private final ActiveScopeService testee = new ActiveScopeService();

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn() {
        IdentityUserDetails user = new IdentityUserDetails(7L, "anna@example.com", "Anna", "hash", true, false,
                List.of(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_GROUP_ADMIN@club:17")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                user, "credentials", user.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void afterTheSwitchTheScopedRoleCountsInTheSession() {
        signIn();

        testee.switchTo(CLUB_17);

        assertThat(testee.current()).hasValue(CLUB_17);
        assertThat(authorityNames())
                .as("only this makes @RolesAllowed(\"GROUP_ADMIN\") apply in this club")
                .contains("ROLE_GROUP_ADMIN", "ROLE_GROUP_ADMIN@club:17");
    }

    /**
     * The authorities of the <em>authentication</em> have to travel along, not
     * only those in the principal: Spring Security checks against
     * {@code Authentication#getAuthorities()}.
     */
    @Test
    void theAuthenticationItselfCarriesTheNewAuthorities() {
        signIn();

        testee.switchTo(CLUB_17);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull()
                .extracting(auth -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList())
                .asInstanceOf(InstanceOfAssertFactories.list(String.class))
                .contains("ROLE_GROUP_ADMIN");
    }

    @Test
    void givingUpTheScopeLeavesOnlyTheGlobalRoles() {
        signIn();
        testee.switchTo(CLUB_17);

        testee.switchTo(null);

        assertThat(testee.current()).isEmpty();
        assertThat(authorityNames()).contains("ROLE_USER").doesNotContain("ROLE_GROUP_ADMIN");
    }

    /** Without a sign-in the switch has no effect, and is not worth an exception. */
    @Test
    void withoutASignedInAccountNothingHappens() {
        assertThatCode(() -> testee.switchTo(CLUB_17)).doesNotThrowAnyException();
        assertThat(testee.current()).isEmpty();
    }

    private static List<String> authorityNames() {
        SecurityContext context = SecurityContextHolder.getContext();
        return context.getAuthentication() == null ? List.of()
                : context.getAuthentication().getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).toList();
    }
}
