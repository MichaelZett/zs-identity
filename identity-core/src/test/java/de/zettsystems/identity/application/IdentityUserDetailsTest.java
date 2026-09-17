package de.zettsystems.identity.application;

import de.zettsystems.identity.values.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The active scope: the half of the role check that is not in the database.
 */
class IdentityUserDetailsTest {

    private static final Scope CLUB_17 = Scope.of("club", "17");
    private static final Scope CLUB_4 = Scope.of("club", "4");

    private static IdentityUserDetails anna() {
        List<GrantedAuthority> granted = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN@club:17"),
                new SimpleGrantedAuthority("season:read@club:17"),
                new SimpleGrantedAuthority("ROLE_MEMBER@club:4"));
        return new IdentityUserDetails(7L, "anna@example.com", "Anna", "hash", true, false, granted);
    }

    @Test
    void withoutAnActiveScopeOnlyTheGlobalRolesCountUnqualified() {
        assertThat(names(anna()))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN@club:17", "season:read@club:17",
                        "ROLE_MEMBER@club:4");
        assertThat(anna().activeScope()).isEmpty();
    }

    /**
     * The whole point: in club 17, {@code @RolesAllowed("ADMIN")} means "admin
     * of club 17", without the application having to write the scope into every
     * check.
     */
    @Test
    void theActiveScopeMakesItsOwnRolesCountUnqualified() {
        IdentityUserDetails inClub17 = anna().withActiveScope(CLUB_17);

        assertThat(names(inClub17))
                .contains("ROLE_ADMIN", "season:read")
                .as("the qualified form remains in place as well")
                .contains("ROLE_ADMIN@club:17", "ROLE_MEMBER@club:4", "ROLE_USER");
    }

    @Test
    void rolesOfOtherScopesStayQualified() {
        IdentityUserDetails inClub17 = anna().withActiveScope(CLUB_17);

        assertThat(names(inClub17))
                .as("being a member of club 4 must mean nothing in club 17")
                .doesNotContain("ROLE_MEMBER");
    }

    @Test
    void switchingBackRemovesTheUnqualifiedRolesAgain() {
        IdentityUserDetails afterSwitch = anna().withActiveScope(CLUB_17).withActiveScope(CLUB_4);

        assertThat(names(afterSwitch)).contains("ROLE_MEMBER").doesNotContain("ROLE_ADMIN");
        assertThat(names(afterSwitch.withActiveScope(null))).doesNotContain("ROLE_MEMBER", "ROLE_ADMIN");
    }

    @Test
    void anActiveScopeWithoutAnyRoleAddsNothing() {
        IdentityUserDetails inStrangeClub = anna().withActiveScope(Scope.of("club", "999"));

        assertThat(names(inStrangeClub)).containsExactlyInAnyOrderElementsOf(names(anna()));
    }

    /** The principal lives in the session, so the assignments have to survive the switch. */
    @Test
    void theGrantedAuthoritiesThemselvesNeverChange() {
        IdentityUserDetails switched = anna().withActiveScope(CLUB_17);

        assertThat(switched.grantedAuthorities()).containsExactlyElementsOf(anna().grantedAuthorities());
        assertThat(switched.userId()).isEqualTo(7L);
        assertThat(switched.getUsername()).isEqualTo("anna@example.com");
    }

    private static List<String> names(IdentityUserDetails user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
