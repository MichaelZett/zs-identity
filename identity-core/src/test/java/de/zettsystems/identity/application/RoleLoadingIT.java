package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.values.Scope;
import de.zettsystems.identity.values.UserAccountDto;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.LongFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loading one account must not fetch its roles one by one (reported from
 * {@code orgaapp}: 12-21 {@code select ... from auth_role where id=?} per page,
 * because {@code findById} had no entity graph). Every path that turns an
 * account into a DTO needs the same number of statements for one role as for
 * four.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class RoleLoadingIT extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "ein-langes-passwort";
    private static final Scope CLUB_17 = Scope.of("club", "17");

    @Autowired
    private UserAccountService accounts;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private long few;
    private long many;

    @BeforeEach
    void createAccounts() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        few = accounts.createAccount("wenig@example.com", PASSWORD, "Wenig", "Rollen", true).id();
        many = accounts.createAccount("viel@example.com", PASSWORD, "Viel", "Rollen", true).id();
        accounts.grantRole(many, "GROUP_ADMIN", CLUB_17);
        accounts.grantRole(many, "MEMBER");
        accounts.grantRole(many, "SYSTEM_ADMIN");
        assertThat(accounts.findById(few).orElseThrow().roleAssignments()).hasSize(1);
        assertThat(accounts.findById(many).orElseThrow().roleAssignments()).hasSize(4);
    }

    @Test
    void findByIdLoadsTheAccountWithItsRolesInOneStatement() {
        assertThat(statements(id -> accounts.findById(id).orElseThrow())).containsExactly(1L, 1L);
    }

    @Test
    void rolesOfAndScopesOfDoNotDependOnTheNumberOfRoles() {
        assertSameForFewAndMany(id -> accounts.rolesOf(id, CLUB_17));
        assertSameForFewAndMany(id -> accounts.scopesOf(id, "club"));
    }

    /**
     * Counted as lazy fetches, not statements: a grant inserts a row, and
     * which insert has to fetch the next block of the sequence depends on the
     * order the tests run in.
     */
    @Test
    void changingAnAccountFetchesNothingLazily() {
        assertNoLazyFetch(id -> accounts.grantRole(id, "GROUP_ADMIN", Scope.of("club", "4")));
        assertNoLazyFetch(id -> accounts.revokeRole(id, "GROUP_ADMIN", Scope.of("club", "4")));
    }

    private void assertNoLazyFetch(LongFunction<?> work) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        for (long id : new long[]{few, many}) {
            statistics.clear();
            work.apply(id);
            assertThat(statistics.getEntityFetchCount() + statistics.getCollectionFetchCount())
                    .as("lazy fetches for account %d", id).isZero();
        }
    }

    /**
     * The safety net for the paths without an entity graph, such as the
     * account behind a token or a passkey handle, and an application's own
     * access: the roles come in one batch, not one by one.
     */
    @Test
    void anAccountLoadedWithoutTheGraphFetchesItsRolesInOneBatch() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertSameForFewAndMany(id -> transaction.execute(status ->
                UserAccountMapper.toDto(userRepository.findById(id).orElseThrow())));
    }

    /** The same for the sign-in view, which also reads the authorities of every role. */
    @Test
    void userDetailsWithoutTheGraphFetchRolesAndAuthoritiesInOneBatchEach() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertSameForFewAndMany(id -> transaction.execute(status ->
                IdentityUserDetailsService.toUserDetails(userRepository.findById(id).orElseThrow()).orElseThrow()));
    }

    private void assertSameForFewAndMany(LongFunction<?> work) {
        long[] counts = statements(work);
        assertThat(counts[1]).as("statements for four roles vs. one").isEqualTo(counts[0]);
    }

    private long[] statements(LongFunction<?> work) {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        long[] counts = new long[2];
        long[] ids = {few, many};
        for (int i = 0; i < ids.length; i++) {
            sessionFactory.getStatistics().clear();
            work.apply(ids[i]);
            counts[i] = sessionFactory.getStatistics().getPrepareStatementCount();
        }
        return counts;
    }
}
