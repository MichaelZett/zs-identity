package de.zettsystems.identity.values;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountDtoTest {

    private static final Instant CREATED = Instant.parse("2026-09-01T10:00:00Z");

    private static UserAccountDto accountWith(String email, AccountName name, Set<String> roles) {
        return new UserAccountDto(1L, email, name, true, true, CREATED, roles);
    }

    /** Applications using real names should get by without a case distinction. */
    @Test
    void aMissingFirstOrLastNameBecomesAnEmptyString() {
        UserAccountDto account = accountWith("anna@example.com", AccountName.display("Sternenflotte"), Set.of());

        assertThat(account.displayName()).isEqualTo("Sternenflotte");
        assertThat(account.firstName()).isEmpty();
        assertThat(account.lastName()).isEmpty();
    }

    @Test
    void aFullNameIsHandedOutAsItIs() {
        UserAccountDto account = accountWith("anna@example.com", AccountName.of("Anna", "Beispiel"), Set.of());

        assertThat(account.firstName()).isEqualTo("Anna");
        assertThat(account.lastName()).isEqualTo("Beispiel");
    }

    /** Managed accounts have no address and cannot sign in. */
    @Test
    void anAccountWithoutAnAddressIsManaged() {
        assertThat(accountWith(null, AccountName.display("Gastkonto"), Set.of()).managed()).isTrue();
        assertThat(accountWith("anna@example.com", AccountName.display("Anna"), Set.of()).managed()).isFalse();
    }

    @Test
    void rolesAreAnsweredByCode() {
        UserAccountDto account = accountWith("anna@example.com", AccountName.display("Anna"), Set.of("USER"));

        assertThat(account.hasRole("USER")).isTrue();
        assertThat(account.hasRole("ADMIN")).isFalse();
    }

    /** The set of roles is copied: a DTO is a snapshot, not a window. */
    @Test
    void theRoleSetIsCopiedAndUnmodifiable() {
        Set<String> roles = new HashSet<>(Set.of("USER"));
        UserAccountDto account = accountWith("anna@example.com", AccountName.display("Anna"), roles);

        roles.add("ADMIN");

        Set<String> handedOut = account.roleCodes();
        assertThat(handedOut).containsExactly("USER");
        assertThatThrownBy(() -> handedOut.add("ADMIN"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theMandatoryFieldsAreChecked() {
        AccountName name = AccountName.display("Anna");
        Set<String> roles = Set.of();

        assertThatThrownBy(() -> new UserAccountDto(null, "anna@example.com", name, true, true, CREATED, roles))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
        assertThatThrownBy(() -> new UserAccountDto(1L, "anna@example.com", name, true, true, null, roles))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdAt");
    }
}
