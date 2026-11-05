package de.zettsystems.identity.application;

import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.values.ScopedRole;
import de.zettsystems.identity.values.UserAccountDto;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An application that switches on Hibernate's second-level and query cache gets accounts with their roles from
 * the cache, and every change through the building block shows at once. The rest of the suite runs without a
 * cache.
 */
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=true",
        "spring.jpa.properties.hibernate.cache.use_query_cache=true",
        "spring.jpa.properties.hibernate.cache.region.factory_class=jcache",
        "spring.jpa.properties.hibernate.javax.cache.provider="
                + "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider",
        "spring.jpa.properties.hibernate.javax.cache.missing_cache_strategy=create",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
class SecondLevelCacheIT extends AbstractIdentityIntegrationTest {

    private static final String PASSWORD = "ein-langes-passwort";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private UserAccountService accounts;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long account;

    /** Other test classes change accounts through their own context, past this cache. */
    @BeforeEach
    void createAccount() {
        entityManagerFactory.unwrap(SessionFactory.class).getCache().evictAllRegions();
        account = newAccount();
        accounts.grantRole(account, "SYSTEM_ADMIN");
    }

    @Test
    void anAccountLookedUpAgainNeedsNoStatement() {
        UserAccountDto first = accounts.findById(account).orElseThrow();

        UserAccountDto again = once(() -> accounts.findById(account).orElseThrow());

        assertThat(again).isEqualTo(first);
        assertThat(codes(again)).contains("SYSTEM_ADMIN");
    }

    @Test
    void severalAccountsLookedUpAgainNeedNoStatement() {
        long other = newAccount();
        List<UserAccountDto> first = accounts.findAllById(List.of(account, other));

        List<UserAccountDto> again = once(() -> accounts.findAllById(List.of(account, other)));

        assertThat(again).containsExactlyInAnyOrderElementsOf(first).hasSize(2);
    }

    @Test
    void aGrantedOrRevokedRoleShowsAtOnce() {
        accounts.findById(account).orElseThrow();

        accounts.grantRole(account, "MEMBER");
        assertThat(codes(accounts.findById(account).orElseThrow())).contains("MEMBER");

        accounts.revokeRole(account, "MEMBER");
        assertThat(codes(accounts.findById(account).orElseThrow())).doesNotContain("MEMBER");
    }

    @Test
    void aDeletedAccountIsGoneAtOnce() {
        accounts.findById(account).orElseThrow();
        accounts.findAllById(List.of(account));

        accounts.deleteAccount(account);

        assertThat(accounts.findById(account)).isEmpty();
        assertThat(accounts.findAllById(List.of(account))).isEmpty();
    }

    /** Runs the lookup and expects it to run no statement at all. */
    private <T> T once(Supplier<T> lookup) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        T result = lookup.get();
        assertThat(statistics.getPrepareStatementCount()).as("statements").isZero();
        assertThat(statistics.getQueryCacheHitCount()).as("query cache hits").isPositive();
        return result;
    }

    private long newAccount() {
        int n = COUNTER.incrementAndGet();
        return accounts.createAccount("cache" + n + "@example.com", PASSWORD, "Cache", "Konto " + n, true).id();
    }

    private static List<String> codes(UserAccountDto account) {
        return account.roleAssignments().stream().map(ScopedRole::roleCode).toList();
    }
}
