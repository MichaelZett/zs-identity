package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.values.Scope;
import de.zettsystems.identity.values.ScopedRole;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roles with a scope, from granting them through to the authorities of the
 * sign-in: the route a multi-tenant application takes.
 */
class ScopedRolesIT extends AbstractIdentityIntegrationTest {

    private static final Scope CLUB_17 = Scope.of("club", "17");
    private static final Scope CLUB_4 = Scope.of("club", "4");

    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private UserDetailsService userDetailsService;

    @BeforeEach
    void clearAccounts() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private UserAccountDto anna() {
        return userAccountService.createAccount("anna@example.com", "ein-langes-passwort", "Anna", "Beispiel", true);
    }

    @Test
    void theSameRoleCanBeHeldInSeveralScopesAtOnce() {
        UserAccountDto created = anna();

        userAccountService.grantRole(created.id(), "GROUP_ADMIN", CLUB_17);
        UserAccountDto withBoth = userAccountService.grantRole(created.id(), "GROUP_ADMIN", CLUB_4);

        assertThat(withBoth.roleAssignments())
                .contains(ScopedRole.of("GROUP_ADMIN", CLUB_17), ScopedRole.of("GROUP_ADMIN", CLUB_4));
        assertThat(withBoth.scopesOf("club")).containsExactlyInAnyOrder(CLUB_17, CLUB_4);
    }

    /** The question this is all about: "may Anna do this <em>here</em>?" */
    @Test
    void aScopedRoleCountsOnlyInItsOwnScope() {
        UserAccountDto created = anna();

        UserAccountDto granted = userAccountService.grantRole(created.id(), "GROUP_ADMIN", CLUB_17);

        assertThat(granted.hasRole("GROUP_ADMIN", CLUB_17)).isTrue();
        assertThat(granted.hasRole("GROUP_ADMIN", CLUB_4)).isFalse();
        assertThat(granted.hasRole("GROUP_ADMIN"))
                .as("she is not admin everywhere, only in club 17")
                .isFalse();
    }

    @Test
    void aGlobalRoleCountsEverywhere() {
        UserAccountDto created = anna();

        assertThat(created.roleCodes())
                .as("the default role stays global")
                .containsExactly("USER");
        assertThat(created.hasRole("USER", CLUB_17)).isTrue();
        assertThat(created.rolesIn(CLUB_17)).contains("USER");
    }

    @Test
    void revokingInOneScopeLeavesTheOtherAndTheGlobalRoleAlone() {
        UserAccountDto created = anna();
        userAccountService.grantRole(created.id(), "GROUP_ADMIN", CLUB_17);
        userAccountService.grantRole(created.id(), "GROUP_ADMIN", CLUB_4);
        userAccountService.grantRole(created.id(), "GROUP_ADMIN");

        UserAccountDto afterRevoke = userAccountService.revokeRole(created.id(), "GROUP_ADMIN", CLUB_17);

        assertThat(afterRevoke.hasRole("GROUP_ADMIN", CLUB_17))
                .as("granted globally it still applies everywhere")
                .isTrue();
        assertThat(afterRevoke.roleAssignments())
                .doesNotContain(ScopedRole.of("GROUP_ADMIN", CLUB_17))
                .contains(ScopedRole.of("GROUP_ADMIN", CLUB_4), ScopedRole.global("GROUP_ADMIN"));
    }

    @Test
    void theSameGrantTwiceChangesNothing() {
        UserAccountDto created = anna();

        userAccountService.grantRole(created.id(), "GROUP_ADMIN", CLUB_17);
        UserAccountDto again = userAccountService.grantRole(created.id(), "GROUP_ADMIN", CLUB_17);

        assertThat(again.roleAssignments()).hasSize(2); // USER global + GROUP_ADMIN in club:17
    }

    @Test
    void theQuestionsAnApplicationAsksAreAnsweredWithoutTheDto() {
        UserAccountDto created = anna();
        userAccountService.grantRole(created.id(), "GROUP_ADMIN", CLUB_17);
        userAccountService.grantRole(created.id(), "MEMBER", CLUB_4);

        assertThat(userAccountService.rolesOf(created.id(), CLUB_17))
                .containsExactlyInAnyOrder("GROUP_ADMIN", "USER");
        assertThat(userAccountService.scopesOf(created.id(), "club"))
                .containsExactlyInAnyOrder(CLUB_17, CLUB_4);
        assertThat(userAccountService.rolesOf(-1L, CLUB_17))
                .as("an unknown account may do nothing here")
                .isEmpty();
    }

    /**
     * What ends up in the session: scoped roles in qualified form, plus the
     * fine-grained permissions of the role carrying the same suffix.
     */
    @Test
    void theSignInCarriesTheScopeInTheAuthorities() {
        UserAccountDto created = anna();
        userAccountService.grantRole(created.id(), "MEMBER", CLUB_17);

        var authorities = userDetailsService.loadUserByUsername("anna@example.com").getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities)
                .contains("ROLE_USER", "ROLE_MEMBER@club:17", "season:read@club:17")
                .doesNotContain("ROLE_MEMBER", "season:read");
    }

    /** The assignments have to survive a restart, since they live in the database. */
    @Test
    void scopedAssignmentsSurviveALoadFromTheDatabase() {
        UserAccountDto created = anna();
        userAccountService.grantRole(created.id(), "GROUP_ADMIN", CLUB_17);

        UserAccountDto reloaded = userAccountService.findById(created.id()).orElseThrow();

        assertThat(reloaded.roleAssignments()).contains(ScopedRole.of("GROUP_ADMIN", CLUB_17));
    }
}
