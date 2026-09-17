package de.zettsystems.identity.application;

import de.zettsystems.identity.values.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Der aktive Bereich — die Hälfte der Rollenprüfung, die nicht in der
 * Datenbank steht.
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
     * Der Sinn der Sache: In Verein 17 bedeutet {@code @RolesAllowed("ADMIN")}
     * „Admin von Verein 17" — ohne dass die Anwendung den Bereich in jede
     * Prüfung hineinschreiben muss.
     */
    @Test
    void theActiveScopeMakesItsOwnRolesCountUnqualified() {
        IdentityUserDetails inClub17 = anna().withActiveScope(CLUB_17);

        assertThat(names(inClub17))
                .contains("ROLE_ADMIN", "season:read")
                .as("die qualifizierte Gestalt bleibt zusätzlich bestehen")
                .contains("ROLE_ADMIN@club:17", "ROLE_MEMBER@club:4", "ROLE_USER");
    }

    @Test
    void rolesOfOtherScopesStayQualified() {
        IdentityUserDetails inClub17 = anna().withActiveScope(CLUB_17);

        assertThat(names(inClub17))
                .as("Mitglied in Verein 4 zu sein, darf in Verein 17 nichts bedeuten")
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

    /** Der Prinzipal liegt in der Sitzung — die Zuweisungen müssen den Wechsel überleben. */
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
