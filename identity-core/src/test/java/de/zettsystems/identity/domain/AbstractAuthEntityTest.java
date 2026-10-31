package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.AccountName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Identity by database id. The LAZY proxy has its own test against a real session. */
class AbstractAuthEntityTest {

    private static UserAccount account(Long id) {
        UserAccount account = new UserAccount("anna@example.com", "hash", AccountName.of("Anna", "Beispiel"),
                Instant.parse("2026-09-01T10:00:00Z"));
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    @Test
    void theSameIdInTheSameTypeIsTheSameEntity() {
        assertThat(account(1L)).isEqualTo(account(1L)).hasSameHashCodeAs(account(1L));
    }

    @Test
    void aDifferentIdIsADifferentEntity() {
        assertThat(account(1L)).isNotEqualTo(account(2L));
    }

    @Test
    void twoUnsavedEntitiesAreDifferentAndOneIsItself() {
        UserAccount unsaved = account(null);

        assertThat(unsaved).isEqualTo(unsaved).isNotEqualTo(account(null));
    }

    @Test
    void theSameIdInAnotherTypeIsADifferentEntity() {
        Role role = new Role("USER", "role.user");
        ReflectionTestUtils.setField(role, "id", 1L);

        assertThat(account(1L)).isNotEqualTo(role);
    }

    @Test
    void somethingThatIsNoEntityIsNeverEqual() {
        assertThat(account(1L)).isNotEqualTo("anna@example.com");
    }
}
