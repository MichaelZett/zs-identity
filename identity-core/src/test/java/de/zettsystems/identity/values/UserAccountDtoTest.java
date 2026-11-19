package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountDtoTest {

    private static final Instant CREATED = Instant.parse("2026-09-01T10:00:00Z");
    private static final Scope CLUB_7 = Scope.of("club", "7");
    private static final Scope CLUB_8 = Scope.of("club", "8");

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

    /** The shapes before 0.10.0 know no invitations; an account with an address counts as registered. */
    @Test
    void theOlderShapesDeriveClaimedFromTheAddress() {
        AccountName name = AccountName.of("Anna", "Beispiel");

        assertThat(accountWith("anna@example.com", name, Set.of()).claimed()).isTrue();
        assertThat(accountWith(null, name, Set.of()).claimed()).isFalse();
    }

    /** The shapes before 1.8.0 know no sign-ins; such an account has never signed in. */
    @Test
    void theOlderShapesHaveNoLastSignIn() {
        UserAccountDto account = new UserAccountDto(1L, "anna@example.com", AccountName.display("Anna"), true, true,
                CREATED, Set.of(), false, null, true, null, true);

        assertThat(account.lastLoginAt()).isNull();
        assertThat(account.hasPassword()).isTrue();
        assertThat(accountWith("anna@example.com", AccountName.display("Anna"), Set.of()).lastLoginAt()).isNull();
    }

    private static UserAccountDto accountWithAssignments(Set<ScopedRole> assignments, @Nullable Instant lockedUntil,
                                                         @Nullable Locale locale) {
        return new UserAccountDto(1L, "anna@example.com", AccountName.display("Anna"), true, true, CREATED,
                assignments, false, locale, true, lockedUntil, true);
    }

    /** Only the global roles are the role codes; a role in a scope applies there alone. */
    @Test
    void roleCodesLeaveOutTheScopedRoles() {
        UserAccountDto account = accountWithAssignments(
                Set.of(ScopedRole.global("USER"), ScopedRole.of("GROUP_ADMIN", CLUB_7)), null, null);

        assertThat(account.roleCodes()).containsExactly("USER");
        assertThat(account.hasRole("GROUP_ADMIN")).isFalse();
    }

    @Test
    void aScopedRoleAppliesInItsScopeAndAGlobalOneEverywhere() {
        UserAccountDto account = accountWithAssignments(
                Set.of(ScopedRole.global("USER"), ScopedRole.of("GROUP_ADMIN", CLUB_7)), null, null);

        assertThat(account.hasRole("GROUP_ADMIN", CLUB_7)).isTrue();
        assertThat(account.hasRole("GROUP_ADMIN", CLUB_8)).isFalse();
        assertThat(account.hasRole("USER", CLUB_8)).isTrue();
        assertThat(account.hasRole("MEMBER", CLUB_7)).isFalse();
        assertThat(account.rolesIn(CLUB_7)).containsExactlyInAnyOrder("USER", "GROUP_ADMIN");
        assertThat(account.rolesIn(CLUB_8)).containsExactly("USER");
    }

    @Test
    void scopesOfAKindListOnlyThatKind() {
        Scope tournament = Scope.of("tournament", "3");
        UserAccountDto account = accountWithAssignments(Set.of(ScopedRole.global("USER"),
                ScopedRole.of("MEMBER", CLUB_7), ScopedRole.of("REFEREE", tournament)), null, null);

        assertThat(account.scopesOf("club")).containsExactly(CLUB_7);
        assertThat(account.scopesOf("tournament")).containsExactly(tournament);
        assertThat(account.scopesOf("game")).isEmpty();
    }

    /** The lock holds until its end, exclusive; without an end there is none. */
    @Test
    void aLockHoldsUntilItsEnd() {
        Instant until = CREATED.plusSeconds(600);
        UserAccountDto locked = accountWithAssignments(Set.of(), until, null);

        assertThat(locked.lockedAt(until.minusSeconds(1))).isTrue();
        assertThat(locked.lockedAt(until)).isFalse();
        assertThat(accountWithAssignments(Set.of(), null, null).lockedAt(until)).isFalse();
    }

    @Test
    void theChosenLanguageWinsOverTheFallback() {
        assertThat(accountWithAssignments(Set.of(), null, Locale.ENGLISH).localeOr(Locale.GERMAN))
                .isEqualTo(Locale.ENGLISH);
        assertThat(accountWithAssignments(Set.of(), null, null).localeOr(Locale.GERMAN)).isEqualTo(Locale.GERMAN);
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
