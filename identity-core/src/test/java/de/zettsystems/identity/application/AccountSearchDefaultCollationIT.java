package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.values.AccountQuery;
import de.zettsystems.identity.values.IdentityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the search does when the application sets nothing: the collation is
 * {@code C}, so the order is that of the bytes and {@code Ä} comes behind
 * {@code Z}. This is what the building block promises; an application with
 * German names sets {@code zs.identity.search.collation} (see
 * {@link AccountSearchIT}).
 */
class AccountSearchDefaultCollationIT extends AbstractIdentityIntegrationTest {

    @Autowired
    private UserAccountService accounts;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private IdentityProperties properties;

    @BeforeEach
    void clearAccounts() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void theCollationIsCUnlessTheApplicationChoosesOne() {
        assertThat(properties.search().collation()).isEqualTo("C");
    }

    @Test
    void underCUmlautsSortBehindTheAsciiLetters() {
        accounts.createManagedAccount("Anna", "Ärztin");
        accounts.createManagedAccount("Zoe", "Zander");

        List<String> names = accounts.search(AccountQuery.all(), 0, 10).items().stream()
                .map(account -> account.name().displayName()).toList();

        assertThat(names).containsExactly("Zoe Zander", "Anna Ärztin");
    }

    @Test
    void underCCaseIsFoldedForAsciiAndTheTextFoldsTheSameWay() {
        accounts.createManagedAccount("Anna", "Ärztin");

        // The search text goes through the same collation as the column, so a
        // capital umlaut in both still matches.
        assertThat(accounts.count(AccountQuery.all().withText("ÄRZT"))).isEqualTo(1);
        assertThat(accounts.count(AccountQuery.all().withText("ANNA"))).isEqualTo(1);
    }
}
