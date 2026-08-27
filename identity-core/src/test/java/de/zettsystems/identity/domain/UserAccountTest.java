package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.AccountName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountTest {

    private static final Instant CREATED = Instant.parse("2026-09-01T10:00:00Z");

    @Test
    void addressesAreLowercasedAndTrimmedOnConstruction() {
        UserAccount user = newAccount("  Anna.Beispiel@Example.COM  ");

        assertThat(user.getEmail()).isEqualTo("anna.beispiel@example.com");
    }

    @Test
    void normalizingUsesRootLocale() {
        // Auf einem System mit türkischem Locale würde toLowerCase() aus "I" ein
        // punktloses "ı" machen — die Adresse wäre dann eine andere.
        assertThat(UserAccount.normalizeEmail("INFO@EXAMPLE.COM")).isEqualTo("info@example.com");
    }

    @Test
    void aNewAccountIsLockedUntilTheAddressIsConfirmed() {
        UserAccount user = newAccount("neu@example.com");

        assertThat(user.isEnabled()).isFalse();
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void confirmingTheAddressUnlocksTheAccount() {
        UserAccount user = newAccount("neu@example.com");

        user.activateAfterEmailVerification();

        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void unlockingWithoutVerificationLeavesTheAddressUnconfirmed() {
        UserAccount user = newAccount("neu@example.com");

        user.activateWithoutVerification();

        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isEmailVerified())
                .as("ohne Bestätigung wissen wir nicht, ob die Adresse dem Konto gehört")
                .isFalse();
    }

    @Test
    void disablingLocksTheAccountAgain() {
        UserAccount user = newAccount("neu@example.com");
        user.activateAfterEmailVerification();

        user.disable();

        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void theDisplayNameJoinsFirstAndLastName() {
        assertThat(newAccount("a@b.c").getDisplayName()).isEqualTo("Anna Beispiel");
    }

    @Test
    void renamingReplacesBothParts() {
        UserAccount user = newAccount("a@b.c");

        user.rename(AccountName.of("Berta", "Muster"));

        assertThat(user.getDisplayName()).isEqualTo("Berta Muster");
    }

    @Test
    void rolesCanBeGrantedRevokedAndReplaced() {
        UserAccount user = newAccount("a@b.c");
        Role member = new Role("MEMBER", "role.member");
        Role admin = new Role("GROUP_ADMIN", "role.groupAdmin");

        user.grant(member);
        assertThat(user.getRoles()).containsExactly(member);

        user.grant(admin);
        assertThat(user.getRoles()).containsExactlyInAnyOrder(member, admin);

        user.revoke(member);
        assertThat(user.getRoles()).containsExactly(admin);

        user.replaceRoles(Set.of(member));
        assertThat(user.getRoles()).containsExactly(member);
    }

    @Test
    void theRoleSetHandedOutCannotBeModifiedFromOutside() {
        UserAccount user = newAccount("a@b.c");
        Set<Role> handedOut = user.getRoles();
        Role intruder = new Role("X", "x");

        assertThatThrownBy(() -> handedOut.add(intruder))
                .as("sonst umgeht ein Aufrufer die Domänenmethoden")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theLastLoginIsInitiallyUnknown() {
        UserAccount user = newAccount("a@b.c");

        assertThat(user.getLastLoginAt()).isNull();

        Instant loggedIn = CREATED.plusSeconds(3600);
        user.recordLogin(loggedIn);
        assertThat(user.getLastLoginAt()).isEqualTo(loggedIn);
    }

    @Test
    void changingThePasswordReplacesTheStoredHash() {
        UserAccount user = newAccount("a@b.c");

        user.changePassword("neuer-hash");

        assertThat(user.getPasswordHash()).isEqualTo("neuer-hash");
    }

    @Test
    void twoUnsavedAccountsAreNeverEqual() {
        // Beide haben id == null. Wären sie gleich, würden sie sich in einem Set
        // gegenseitig verdrängen.
        assertThat(newAccount("a@b.c")).isNotEqualTo(newAccount("a@b.c"));
    }

    private static UserAccount newAccount(String email) {
        return new UserAccount(email, "hash", AccountName.of("Anna", "Beispiel"), CREATED);
    }
}
