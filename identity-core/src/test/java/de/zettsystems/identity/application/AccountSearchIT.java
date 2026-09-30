package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.AccountPage;
import de.zettsystems.identity.values.AccountQuery;
import de.zettsystems.identity.values.Scope;
import de.zettsystems.identity.values.UserAccountDto;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The paged account search against a real PostgreSQL, with the German
 * collation an application sets (the building block itself defaults to
 * {@code C}, see {@link AccountSearchDefaultCollationIT}).
 */
@TestPropertySource(properties = {
        "zs.identity.search.collation=de-DE-x-icu",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
class AccountSearchIT extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "ein-langes-passwort";

    @Autowired
    private UserAccountService accounts;
    @Autowired
    private InvitationService invitations;
    @Autowired
    private RegistrationService registrations;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void clearAccounts() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private List<String> displayNames(AccountPage page) {
        return page.items().stream().map(account -> account.name().displayName()).toList();
    }

    @Test
    void aPageHoldsTheAccountsInOrderAndTheTotalCountsAllOfThem() {
        for (String last : List.of("Eins", "Zwei", "Drei", "Vier", "Fuenf")) {
            accounts.createAccount(last.toLowerCase() + "@example.com", PASSWORD, "Person", last, true);
        }

        AccountPage first = accounts.search(AccountQuery.all(), 0, 2);
        AccountPage second = accounts.search(AccountQuery.all(), 2, 2);
        AccountPage last = accounts.search(AccountQuery.all(), 4, 2);
        AccountPage beyond = accounts.search(AccountQuery.all(), 10, 2);

        assertThat(displayNames(first)).containsExactly("Person Drei", "Person Eins");
        assertThat(displayNames(second)).containsExactly("Person Fuenf", "Person Vier");
        assertThat(displayNames(last)).containsExactly("Person Zwei");
        assertThat(first.total()).isEqualTo(5);
        assertThat(second.total()).isEqualTo(5);
        assertThat(last.total()).isEqualTo(5);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(5);
        assertThat(first.hasMore(0)).isTrue();
        assertThat(last.hasMore(4)).isFalse();
        assertThat(accounts.count(AccountQuery.all())).isEqualTo(5);
    }

    @Test
    void anEmptyDatabaseHasNoPage() {
        AccountPage page = accounts.search(AccountQuery.all(), 0, 25);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void namesSortByLastNameFirstNameAndIdWithUmlautsAndMissingPartsHandled() {
        accounts.createAccount("zander@example.com", PASSWORD, "Zoe", "Zander", true);
        accounts.createAccount("aerztin@example.com", PASSWORD, "Anna", "Ärztin", true);
        accounts.createAccount("meier-b@example.com", PASSWORD, "Berta", "meier", true);
        accounts.createAccount("meier-a@example.com", PASSWORD, "Anton", "Meier", true);
        // Without name parts, sorted by its display name "Kunz", not at the end.
        accounts.createManagedAccount(AccountName.display("Kunz"));

        AccountPage page = accounts.search(AccountQuery.all(), 0, 10);

        // "Ärztin" before "Zander" is what the German collation makes of it;
        // under "C" it would come last.
        assertThat(displayNames(page)).containsExactly(
                "Anna Ärztin", "Kunz", "Anton Meier", "Berta meier", "Zoe Zander");
    }

    @Test
    void theSameNameOrderIsBrokenByTheId() {
        UserAccountDto first = accounts.createManagedAccount("Max", "Muster");
        UserAccountDto second = accounts.createManagedAccount("Max", "Muster");

        AccountPage page = accounts.search(AccountQuery.all(), 0, 10);

        assertThat(page.items()).extracting(UserAccountDto::id)
                .containsExactly(first.id(), second.id());
    }

    @Test
    void textFindsPartsOfNamesAndAddressesIgnoringCase() {
        accounts.createAccount("anna@example.com", PASSWORD, "Anna", "Ärztin", true);
        accounts.createAccount("bert@firma.example", PASSWORD, "Bert", "Beispiel", true);

        assertThat(displayNames(accounts.search(AccountQuery.all().withText("ÄRZT"), 0, 10)))
                .containsExactly("Anna Ärztin");
        assertThat(displayNames(accounts.search(AccountQuery.all().withText("bert b"), 0, 10)))
                .containsExactly("Bert Beispiel");
        assertThat(displayNames(accounts.search(AccountQuery.all().withText("FIRMA.EX"), 0, 10)))
                .containsExactly("Bert Beispiel");
        assertThat(accounts.search(AccountQuery.all().withText("   "), 0, 10).total()).isEqualTo(2);
    }

    @Test
    void percentAndUnderscoreInTheTextAreTakenLiterally() {
        accounts.createManagedAccount("Hundert%", "Prozent");
        accounts.createManagedAccount("Unter_strich", "Test");
        accounts.createManagedAccount("Gewöhnlich", "Name");

        assertThat(displayNames(accounts.search(AccountQuery.all().withText("%"), 0, 10)))
                .containsExactly("Hundert% Prozent");
        assertThat(displayNames(accounts.search(AccountQuery.all().withText("_"), 0, 10)))
                .containsExactly("Unter_strich Test");
        assertThat(accounts.search(AccountQuery.all().withText("t_e"), 0, 10).items()).isEmpty();
        assertThat(accounts.count(AccountQuery.all().withText("!"))).isZero();
    }

    @Test
    void theStateFiltersTellManagedInvitedAwaitingAndSettledApart() {
        UserAccountDto settled = accounts.createAccount("fertig@example.com", PASSWORD, "Fritz", "Fertig", true);
        UserAccountDto managed = accounts.createManagedAccount("Manuela", "Verwaltet");
        UserAccountDto invited = invitations.inviteNewAccount("eingeladen@example.com", "Ida", "Eingeladen");
        UserAccountDto awaiting = registrations.register("wartet@example.com", PASSWORD, "Willi", "Wartet");

        assertThat(ids(AccountQuery.all().withManaged(true))).containsExactly(managed.id());
        assertThat(ids(AccountQuery.all().withManaged(false)))
                .containsExactlyInAnyOrder(settled.id(), invited.id(), awaiting.id());
        assertThat(ids(AccountQuery.all().withInvitationOpen(true))).containsExactly(invited.id());
        assertThat(ids(AccountQuery.all().withInvitationOpen(false)))
                .containsExactlyInAnyOrder(settled.id(), managed.id(), awaiting.id());
        assertThat(ids(AccountQuery.all().withAwaitingConfirmation(true))).containsExactly(awaiting.id());
        assertThat(ids(AccountQuery.all().withAwaitingConfirmation(false)))
                .containsExactlyInAnyOrder(settled.id(), managed.id(), invited.id());
    }

    @Test
    void theRoleFilterCountsGlobalRolesOnly() {
        UserAccountDto global = accounts.createAccount("global@example.com", PASSWORD, "Gerd", "Global", true);
        UserAccountDto scoped = accounts.createAccount("scoped@example.com", PASSWORD, "Sina", "Scoped", true);
        accounts.createAccount("plain@example.com", PASSWORD, "Paul", "Plain", true);
        accounts.grantRole(global.id(), "GROUP_ADMIN");
        accounts.grantRole(scoped.id(), "GROUP_ADMIN", new Scope("club", "17"));

        assertThat(ids(AccountQuery.all().withRoleCode("GROUP_ADMIN"))).containsExactly(global.id());
        assertThat(accounts.count(AccountQuery.all().withRoleCode("GROUP_ADMIN"))).isEqualTo(1);
        assertThat(accounts.count(AccountQuery.all().withRoleCode("USER"))).isEqualTo(3);
        assertThat(accounts.count(AccountQuery.all().withRoleCode("GIBT_ES_NICHT"))).isZero();
    }

    @Test
    void idFiltersNarrowDownAndAnEmptyOneMeansNobodyNotEverybody() {
        UserAccountDto anna = accounts.createAccount("anna@example.com", PASSWORD, "Anna", "Eins", true);
        UserAccountDto bert = accounts.createAccount("bert@example.com", PASSWORD, "Bert", "Zwei", true);
        UserAccountDto carl = accounts.createAccount("carl@example.com", PASSWORD, "Carl", "Drei", true);

        assertThat(ids(AccountQuery.all().withUserIds(Set.of(anna.id(), carl.id()))))
                .containsExactlyInAnyOrder(anna.id(), carl.id());
        assertThat(ids(AccountQuery.all().withExcludeUserIds(Set.of(anna.id()))))
                .containsExactlyInAnyOrder(bert.id(), carl.id());
        assertThat(ids(AccountQuery.all().withUserIds(Set.of(anna.id(), bert.id()))
                .withExcludeUserIds(Set.of(bert.id())))).containsExactly(anna.id());

        AccountQuery nobody = AccountQuery.all().withUserIds(Set.of());
        assertThat(accounts.search(nobody, 0, 10).items()).isEmpty();
        assertThat(accounts.search(nobody, 0, 10).total()).isZero();
        assertThat(accounts.count(nobody)).isZero();
        // Nothing to exclude is no restriction; null for userIds is none either.
        assertThat(accounts.count(AccountQuery.all().withExcludeUserIds(Set.of()).withUserIds(null))).isEqualTo(3);
    }

    @Test
    void filtersCombineWithAnd() {
        UserAccountDto anna = accounts.createAccount("anna@example.com", PASSWORD, "Anna", "Beispiel", true);
        UserAccountDto bert = accounts.createAccount("bert@example.com", PASSWORD, "Bert", "Beispiel", true);
        accounts.createManagedAccount("Anna", "Verwaltet");
        accounts.grantRole(anna.id(), "GROUP_ADMIN");
        accounts.grantRole(bert.id(), "GROUP_ADMIN");

        AccountQuery query = AccountQuery.all().withText("anna").withManaged(false).withRoleCode("GROUP_ADMIN")
                .withExcludeUserIds(Set.of(bert.id()));

        assertThat(ids(query)).containsExactly(anna.id());
        assertThat(accounts.count(query)).isEqualTo(1);
    }

    @Test
    void thePageCarriesTheRolesOfItsAccounts() {
        UserAccountDto admin = accounts.createAccount("admin@example.com", PASSWORD, "Ada", "Admin", true);
        accounts.grantRole(admin.id(), "GROUP_ADMIN");

        UserAccountDto found = accounts.search(AccountQuery.all(), 0, 10).items().getFirst();

        assertThat(found.roleCodes()).containsExactlyInAnyOrder("USER", "GROUP_ADMIN");
    }

    @Test
    void aPageNeedsTheSameStatementsForFewAndForManyAccounts() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        createAccounts(5);
        long forFive = statementsForAPage(sessionFactory);

        createAccounts(45);
        long forFifty = statementsForAPage(sessionFactory);

        assertThat(forFifty).isEqualTo(forFive);
    }

    @Test
    void anOffsetBelowZeroOrALimitBelowOneIsRefused() {
        assertThatThrownBy(() -> accounts.search(AccountQuery.all(), -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accounts.search(AccountQuery.all(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private long statementsForAPage(SessionFactory sessionFactory) {
        sessionFactory.getStatistics().clear();
        AccountPage page = accounts.search(AccountQuery.all(), 0, 5);
        assertThat(page.items()).hasSize(5);
        return sessionFactory.getStatistics().getPrepareStatementCount();
    }

    private int created;

    private void createAccounts(int count) {
        for (int i = 0; i < count; i++) {
            created++;
            accounts.createAccount("massen" + created + "@example.com", PASSWORD, "Massen", "Konto" + created, true);
        }
    }

    private List<Long> ids(AccountQuery query) {
        return accounts.search(query, 0, 50).items().stream().map(UserAccountDto::id).toList();
    }
}
