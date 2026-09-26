package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.LoginProtectionSettings;
import de.zettsystems.identity.values.Scope;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
        // On a system with a Turkish locale, toLowerCase() would turn "I" into a
        // dotless "i", and the address would then be a different one.
        assertThat(UserAccount.normalizeEmail("INFO@EXAMPLE.COM")).isEqualTo("info@example.com");
    }

    /**
     * {@link java.util.Locale#ROOT} is the absence of a language. Without this
     * rule an empty language tag would sit in the database for it,
     * indistinguishable from a real choice.
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
    void anAccountIsClaimedOnceItHasAPassword() {
        UserAccount managed = UserAccount.managed(AccountName.of("Ida", "Beispiel"), CREATED);
        assertThat(managed.isClaimed()).isFalse();

        managed.assignEmail("ida@example.com");
        assertThat(managed.isClaimed()).as("invited, not yet redeemed").isFalse();

        managed.claimWithPassword("hash");
        assertThat(managed.isClaimed()).isTrue();
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
                .as("without confirmation we do not know whether the address belongs to the account")
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

    /** The same role in two clubs is two things, and both are possible at once. */
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
                .as("being admin of no club does not mean being admin everywhere")
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
     * {@code replaceRoles} sets the global roles. The call says nothing about a
     * person's tenants, so it must not silently empty them either.
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
                .as("otherwise a caller bypasses the domain methods")
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
    void theThirdWrongPasswordLocksTheAccountForTheLockDuration() {
        UserAccount user = newAccount("a@b.c");
        LoginProtectionSettings settings = LoginProtectionSettings.defaults();

        assertThat(user.recordFailedLogin(CREATED, settings)).isNull();
        assertThat(user.recordFailedLogin(CREATED.plusSeconds(1), settings)).isNull();
        Instant third = CREATED.plusSeconds(2);

        assertThat(user.recordFailedLogin(third, settings)).isEqualTo(third.plus(Duration.ofMinutes(15)));
        assertThat(user.isLockedAt(third.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLockedAt(third.plus(Duration.ofMinutes(15)))).as("runs out by itself").isFalse();
    }

    /** Otherwise whoever knows the address could keep the owner out for good. */
    @Test
    void nothingIsCountedWhileTheLockIsInForce() {
        UserAccount user = lockedOnce(LoginProtectionSettings.defaults());

        assertThat(user.recordFailedLogin(CREATED.plus(Duration.ofMinutes(5)), LoginProtectionSettings.defaults()))
                .isNull();

        assertThat(user.getFailedLoginCount()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isEqualTo(CREATED.plus(Duration.ofMinutes(15)));
    }

    @Test
    void everyFurtherLockLastsTwiceAsLongUpToTheMaximum() {
        LoginProtectionSettings settings = new LoginProtectionSettings(true, 3, Duration.ofMinutes(15),
                Duration.ofMinutes(45), Duration.ZERO, Duration.ZERO, 1, false);
        UserAccount user = lockedOnce(settings);
        Instant afterFirst = CREATED.plus(Duration.ofMinutes(16));

        user.recordFailedLogin(afterFirst, settings);
        user.recordFailedLogin(afterFirst, settings);
        assertThat(user.recordFailedLogin(afterFirst, settings)).isEqualTo(afterFirst.plus(Duration.ofMinutes(30)));

        Instant afterSecond = afterFirst.plus(Duration.ofMinutes(31));
        user.recordFailedLogin(afterSecond, settings);
        user.recordFailedLogin(afterSecond, settings);
        assertThat(user.recordFailedLogin(afterSecond, settings))
                .as("60 minutes, capped at the maximum of 45")
                .isEqualTo(afterSecond.plus(Duration.ofMinutes(45)));
    }

    @Test
    void failuresOlderThanTheLongestLockAreForgotten() {
        UserAccount user = newAccount("a@b.c");
        LoginProtectionSettings settings = LoginProtectionSettings.defaults();
        user.recordFailedLogin(CREATED, settings);
        user.recordFailedLogin(CREATED, settings);

        Instant nextDay = CREATED.plus(Duration.ofHours(25));
        assertThat(user.recordFailedLogin(nextDay, settings)).as("a fresh start, not the third in a row").isNull();
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
    }

    /** Reset, invitation and change all end here, and each proves the account is in the right hands. */
    @Test
    void aNewPasswordLiftsTheLock() {
        UserAccount user = lockedOnce(LoginProtectionSettings.defaults());

        user.changePassword("neuer-hash");

        assertThat(user.isLockedAt(CREATED)).isFalse();
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLastFailedLoginAt()).isNull();
    }

    private static UserAccount lockedOnce(LoginProtectionSettings settings) {
        UserAccount user = newAccount("a@b.c");
        for (int i = 0; i < settings.maxAttempts(); i++) {
            user.recordFailedLogin(CREATED, settings);
        }
        assertThat(user.isLockedAt(CREATED)).isTrue();
        return user;
    }

    @Test
    void anAccountFromAProviderHasNoPasswordButIsClaimedAndConfirmed() {
        UserAccount user = UserAccount.external(" Ida@Example.com ", AccountName.display("Ida"), CREATED);
        user.linkExternalIdentity("google", "sub-1", "ida@example.com", CREATED);

        assertThat(user.getEmail()).isEqualTo("ida@example.com");
        assertThat(user.hasPassword()).isFalse();
        assertThat(user.isClaimed()).as("a linked identity is a way in").isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void withoutPasswordAndWithoutIdentityAnAccountIsNotClaimed() {
        UserAccount user = UserAccount.external("ida@example.com", AccountName.display("Ida"), CREATED);

        assertThat(user.isClaimed()).isFalse();
    }

    @Test
    void theSameIdentityLinkedTwiceIsOneLink() {
        UserAccount user = newAccount("a@b.c");

        ExternalIdentity first = user.linkExternalIdentity("google", "sub-1", null, CREATED);
        ExternalIdentity second = user.linkExternalIdentity("google", "sub-1", "a@b.c", CREATED);

        assertThat(second).isSameAs(first);
        assertThat(user.getExternalIdentities()).hasSize(1);
    }

    /** One identity per provider, so that unlinking can name it by the provider alone. */
    @Test
    void aSecondIdentityAtTheSameProviderIsRefused() {
        UserAccount user = newAccount("a@b.c");
        user.linkExternalIdentity("google", "sub-1", null, CREATED);

        assertThatThrownBy(() -> user.linkExternalIdentity("google", "sub-2", null, CREATED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unlinkingRemovesTheIdentityOfThatProviderOnly() {
        UserAccount user = newAccount("a@b.c");
        user.linkExternalIdentity("google", "sub-1", null, CREATED);
        user.linkExternalIdentity("github", "42", null, CREATED);

        assertThat(user.unlinkExternalIdentity("google")).isTrue();
        assertThat(user.unlinkExternalIdentity("google")).as("gone already").isFalse();
        assertThat(user.externalIdentity("github")).isPresent();
    }

    @Test
    void anUnconfirmedPasswordCanBeDropped() {
        UserAccount user = newAccount("a@b.c");
        user.requirePasswordChange();

        user.dropUnconfirmedPassword();

        assertThat(user.hasPassword()).isFalse();
        assertThat(user.isMustChangePassword()).isFalse();
    }

    @Test
    void aConfirmedPasswordStays() {
        UserAccount user = newAccount("a@b.c");
        user.activateAfterEmailVerification();

        assertThatThrownBy(user::dropUnconfirmedPassword).isInstanceOf(IllegalStateException.class);
        assertThat(user.hasPassword()).isTrue();
    }

    /** A forced change would lock such an account into a view it cannot leave. */
    @Test
    void anAccountWithoutPasswordCannotBeForcedToChangeIt() {
        UserAccount user = UserAccount.external("ida@example.com", AccountName.display("Ida"), CREATED);

        assertThatThrownBy(user::requirePasswordChange).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void identitiesCheckTheirLength() {
        UserAccount user = newAccount("a@b.c");

        assertThatThrownBy(() -> user.linkExternalIdentity(" ", "sub", null, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
        String tooLong = "x".repeat(256);
        assertThatThrownBy(() -> user.linkExternalIdentity("google", tooLong, null, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anIdentityRemembersItsLastUse() {
        UserAccount user = newAccount("a@b.c");
        ExternalIdentity identity = user.linkExternalIdentity("google", "sub-1", "old@b.c", CREATED);

        identity.recordUse("new@b.c", CREATED.plusSeconds(60));

        assertThat(identity.getEmail()).isEqualTo("new@b.c");
        assertThat(identity.getLastUsedAt()).isEqualTo(CREATED.plusSeconds(60));
        assertThat(identity.matches("google", "sub-1")).isTrue();
        assertThat(identity.matches("github", "sub-1")).isFalse();
    }

    @Test
    void twoUnsavedAccountsAreNeverEqual() {
        // Both have id == null. If they were equal, they would displace each
        // other inside a set.
        assertThat(newAccount("a@b.c")).isNotEqualTo(newAccount("a@b.c"));
    }

    private static UserAccount newAccount(String email) {
        return new UserAccount(email, "hash", AccountName.of("Anna", "Beispiel"), CREATED);
    }
}
