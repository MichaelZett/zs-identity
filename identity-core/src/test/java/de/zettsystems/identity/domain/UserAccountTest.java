package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.Scope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;
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

    /**
     * {@link java.util.Locale#ROOT} ist die Abwesenheit einer Sprache. Ohne
     * diese Regel stünde dafür ein leeres Sprachkennzeichen in der Datenbank —
     * ununterscheidbar von einer echten Wahl.
     */
    @Test
    void theRootLocaleCountsAsNoChoiceAtAll() {
        UserAccount user = newAccount("neu@example.com");

        user.changeLocale(Locale.ENGLISH);
        assertThat(user.getLocale()).isEqualTo(Locale.ENGLISH);

        user.changeLocale(Locale.ROOT);
        assertThat(user.getLocale()).isNull();

        user.changeLocale(Locale.GERMAN);
        user.changeLocale(null);
        assertThat(user.getLocale()).isNull();
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

    /** Dieselbe Rolle in zwei Vereinen ist zweierlei — und beides gleichzeitig möglich. */
    @Test
    void aRoleCanBeHeldInSeveralScopes() {
        UserAccount user = newAccount("a@b.c");
        Role admin = new Role("GROUP_ADMIN", "role.groupAdmin");
        Scope club17 = Scope.of("club", "17");
        Scope club4 = Scope.of("club", "4");

        user.grant(admin, club17);
        user.grant(admin, club4);

        assertThat(user.rolesIn(club17)).containsExactly(admin);
        assertThat(user.rolesIn(club4)).containsExactly(admin);
        assertThat(user.getRoles())
                .as("in keinem Verein Admin zu sein, heißt nicht überall Admin zu sein")
                .isEmpty();
        assertThat(user.scopesOf("club")).containsExactlyInAnyOrder(club17, club4);
        assertThat(user.scopesOf("game")).isEmpty();
    }

    @Test
    void revokingInOneScopeLeavesTheOthersAlone() {
        UserAccount user = newAccount("a@b.c");
        Role admin = new Role("GROUP_ADMIN", "role.groupAdmin");
        Scope club17 = Scope.of("club", "17");
        Scope club4 = Scope.of("club", "4");
        user.grant(admin, club17);
        user.grant(admin, club4);
        user.grant(admin);

        user.revoke(admin, club17);

        assertThat(user.rolesIn(club17)).isEmpty();
        assertThat(user.rolesIn(club4)).containsExactly(admin);
        assertThat(user.getRoles()).containsExactly(admin);
    }

    @Test
    void theSameAssignmentIsNeverStoredTwice() {
        UserAccount user = newAccount("a@b.c");
        Role admin = new Role("GROUP_ADMIN", "role.groupAdmin");
        Scope club17 = Scope.of("club", "17");

        user.grant(admin, club17);
        user.grant(admin, club17);

        assertThat(user.getRoleAssignments()).hasSize(1);
    }

    /**
     * {@code replaceRoles} setzt die globalen Rollen — über die Mandanten
     * einer Person sagt der Aufruf nichts, also darf er sie auch nicht
     * stillschweigend leeren.
     */
    @Test
    void replacingTheGlobalRolesKeepsTheScopedOnes() {
        UserAccount user = newAccount("a@b.c");
        Role member = new Role("MEMBER", "role.member");
        Role admin = new Role("GROUP_ADMIN", "role.groupAdmin");
        Scope club17 = Scope.of("club", "17");
        user.grant(admin, club17);
        user.grant(admin);

        user.replaceRoles(Set.of(member));

        assertThat(user.getRoles()).containsExactly(member);
        assertThat(user.rolesIn(club17)).containsExactly(admin);
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
