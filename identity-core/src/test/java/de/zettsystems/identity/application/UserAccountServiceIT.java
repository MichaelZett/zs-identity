package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The route along which an administrator creates accounts and grants rights. */
class UserAccountServiceIT extends AbstractIdentityIntegrationTest {

    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private TransactionTemplate transactions;

    @BeforeEach
    void clearAccounts() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void anAccountCreatedByAnAdministrationCanBeUsedImmediately() {
        UserAccountDto created = userAccountService.createAccount(
                "verwaltet@example.com", "ein-langes-passwort", "Ver", "Waltet", true);

        assertThat(created.enabled()).isTrue();
        assertThat(created.emailVerified()).isTrue();
        assertThat(created.roleCodes()).containsExactly("USER");
        assertThat(userDetailsService.loadUserByUsername("verwaltet@example.com").isEnabled()).isTrue();
    }

    /** The route an application puts behind its account settings. */
    @Test
    void theLanguageOfAnAccountCanBeSetAndTakenBack() {
        UserAccountDto created = userAccountService.createAccount(
                "sprache@example.com", "ein-langes-passwort", "Sarah", "Sprache", true);

        assertThat(userAccountService.changeLocale(created.id(), Locale.ENGLISH).locale())
                .isEqualTo(Locale.ENGLISH);
        assertThat(userAccountService.findById(created.id()).orElseThrow().locale())
                .as("the choice has to survive the call, not merely appear in the return value")
                .isEqualTo(Locale.ENGLISH);

        assertThat(userAccountService.changeLocale(created.id(), null).locale())
                .as("without a choice zs.identity.locale applies again")
                .isNull();
    }

    @Test
    void changingTheLanguageOfAnUnknownAccountFails() {
        assertThatThrownBy(() -> userAccountService.changeLocale(-1L, Locale.ENGLISH))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.ACCOUNT_NOT_FOUND);
    }

    @Test
    void severalAccountsAreLoadedInOneGo() {
        UserAccountDto a = userAccountService.createAccount("a@example.com", "ein-langes-passwort", "A", "Aa", true);
        UserAccountDto b = userAccountService.createAccount("b@example.com", "ein-langes-passwort", "B", "Bb", true);

        assertThat(userAccountService.findAllById(List.of(a.id(), b.id(), -1L)))
                .extracting(UserAccountDto::id)
                .containsExactlyInAnyOrder(a.id(), b.id());
        assertThat(userAccountService.findAllById(List.of())).isEmpty();
    }

    /** A deleted account is gone, role included; a second delete reports "not found". */
    @Test
    void anAccountCanBeDeletedForGood() {
        UserAccountDto created = userAccountService.createAccount(
                "doppelt@example.com", "ein-langes-passwort", "Dop", "Pelt", true);
        userAccountService.grantRole(created.id(), "SYSTEM_ADMIN");

        userAccountService.deleteAccount(created.id());

        Long deletedId = created.id();
        assertThat(userAccountService.findById(deletedId)).isEmpty();
        assertThat(userAccountService.findByEmail("doppelt@example.com")).isEmpty();
        assertThatThrownBy(() -> userAccountService.deleteAccount(deletedId))
                .isInstanceOf(IdentityException.class);
    }

    /** An initial password from an administrator: the flag stays until the password is new. */
    @Test
    void aRequiredPasswordChangeIsClearedByChangingThePassword() {
        UserAccountDto created = userAccountService.createAccount(
                "start@example.com", "ein-start-passwort", "Start", "Konto", true);
        assertThat(created.mustChangePassword()).isFalse();

        UserAccountDto flagged = userAccountService.requirePasswordChange(created.id());

        assertThat(flagged.mustChangePassword()).isTrue();
        assertThat(userAccountService.findById(created.id()).orElseThrow().mustChangePassword()).isTrue();
        assertThat(((IdentityUserDetails) userDetailsService.loadUserByUsername("start@example.com"))
                .mustChangePassword()).isTrue();

        userAccountService.changePassword(created.id(), "ein-neues-langes-passwort");

        assertThat(userAccountService.findById(created.id()).orElseThrow().mustChangePassword()).isFalse();
        assertThat(((IdentityUserDetails) userDetailsService.loadUserByUsername("start@example.com"))
                .mustChangePassword()).isFalse();
    }

    @Test
    void anAccountCreatedWithoutVerificationStaysLocked() {
        UserAccountDto created = userAccountService.createAccount(
                "gesperrt@example.com", "ein-langes-passwort", "Ge", "Sperrt", false);

        assertThat(created.enabled()).isFalse();
    }

    @Test
    void aManagedAccountExistsWithoutCredentialsAndCannotSignIn() {
        UserAccountDto created = userAccountService.createManagedAccount("Paul", "Platzhalter");

        assertThat(created.managed()).isTrue();
        assertThat(created.email()).isNull();
        assertThat(created.enabled()).isTrue();
        assertThat(created.displayName()).isEqualTo("Paul Platzhalter");
        assertThat(created.roleCodes()).containsExactly("USER");
        // Without an email address there is no sign-in name, so the account
        // cannot be reached through any sign-in path.
        assertThat(userAccountService.findAll())
                .filteredOn(UserAccountDto::managed)
                .hasSize(1);
    }

    @Test
    void anAccountWithAFreelyChosenNameCarriesNoFullNameAndSignsInWithItsId() {
        UserAccountDto created = userAccountService.createAccount(
                "spieler@example.com", "ein-langes-passwort", AccountName.display("  Nova  "), true);

        assertThat(created.displayName()).isEqualTo("Nova");
        assertThat(created.name().hasFullName()).isFalse();
        assertThat(created.firstName()).as("empty instead of null, so real-name apps need no case distinction")
                .isEmpty();
        assertThat(created.lastName()).isEmpty();

        assertThat(userDetailsService.loadUserByUsername("spieler@example.com"))
                .isInstanceOfSatisfying(IdentityUserDetails.class, principal -> {
                    assertThat(principal.userId()).isEqualTo(created.id());
                    assertThat(principal.displayName()).isEqualTo("Nova");
                });
    }

    @Test
    void renamingSwitchesBetweenBothNameShapes() {
        Long id = userAccountService.createAccount(
                "name@example.com", "ein-langes-passwort", "Vor", "Nach", true).id();

        UserAccountDto renamed = userAccountService.rename(id, AccountName.display("Kurz"));

        assertThat(renamed.displayName()).isEqualTo("Kurz");
        assertThat(renamed.firstName()).isEmpty();
        assertThat(userAccountService.rename(id, AccountName.of("Anna", "Neu")).displayName())
                .isEqualTo("Anna Neu");
    }

    @Test
    void rolesCanBeGrantedAndRevoked() {
        Long id = userAccountService.createAccount(
                "rollen@example.com", "ein-langes-passwort", "Rol", "Len", true).id();

        assertThat(userAccountService.grantRole(id, "GROUP_ADMIN").roleCodes())
                .containsExactlyInAnyOrder("USER", "GROUP_ADMIN");
        assertThat(userAccountService.revokeRole(id, "GROUP_ADMIN").roleCodes())
                .containsExactly("USER");
    }

    /**
     * The case an embedding application produces: it loads the account and then
     * grants -- inside the same transaction -- a role it already has. The
     * existing assignment is loaded by then and its role is a LAZY proxy;
     * whoever fails to recognise it creates a second assignment and runs into
     * ux_auth_user_role at commit time.
     */
    @Test
    void grantingAgainAfterTheAccountWasLoadedChangesNothing() {
        Long id = userAccountService.createAccount(
                "nochmal@example.com", "ein-langes-passwort", "Noch", "Mal", true).id();
        userAccountService.grantRole(id, "GROUP_ADMIN");

        transactions.executeWithoutResult(status -> {
            userAccountService.findById(id).orElseThrow();
            userAccountService.grantRole(id, "GROUP_ADMIN");
        });

        assertThat(userAccountService.findById(id).orElseThrow().roleAssignments())
                .as("no second assignment of the same role")
                .hasSize(2);
    }

    @Test
    void grantingRolesShowsUpInTheSecurityAuthorities() {
        Long id = userAccountService.createAccount(
                "rechte@example.com", "ein-langes-passwort", "Rech", "Te", true).id();
        userAccountService.grantRole(id, "MEMBER");

        var details = userDetailsService.loadUserByUsername("rechte@example.com");

        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .as("the role as ROLE_, the permission attached to it verbatim")
                .contains("ROLE_MEMBER", "season:read");
    }

    @Test
    void anUnknownRoleIsRefusedWithATellingMessage() {
        Long id = userAccountService.createAccount(
                "unbekannt@example.com", "ein-langes-passwort", "Un", "Bekannt", true).id();

        assertThatThrownBy(() -> userAccountService.grantRole(id, "GIBT_ES_NICHT"))
                .isInstanceOf(IdentityException.class)
                .hasMessageContaining("RoleCatalog");
    }

    @Test
    void disablingAnAccountBlocksSigningIn() {
        Long id = userAccountService.createAccount(
                "abschalten@example.com", "ein-langes-passwort", "Ab", "Schalten", true).id();

        userAccountService.setEnabled(id, false);

        assertThat(userDetailsService.loadUserByUsername("abschalten@example.com").isEnabled()).isFalse();

        userAccountService.setEnabled(id, true);
        assertThat(userDetailsService.loadUserByUsername("abschalten@example.com").isEnabled()).isTrue();
    }

    @Test
    void changingThePasswordTakesEffectForSigningIn() {
        Long id = userAccountService.createAccount(
                "wechsel@example.com", "ein-langes-passwort", "Wech", "Sel", true).id();
        String oldHash = userDetailsService.loadUserByUsername("wechsel@example.com").getPassword();

        userAccountService.changePassword(id, "ein-ganz-neues-passwort");

        assertThat(userDetailsService.loadUserByUsername("wechsel@example.com").getPassword())
                .isNotEqualTo(oldHash);
    }

    @Test
    void aTooShortPasswordIsRefused() {
        Long id = userAccountService.createAccount(
                "kurz@example.com", "ein-langes-passwort", "Kur", "Z", true).id();

        assertThatThrownBy(() -> userAccountService.changePassword(id, "kurz"))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.PASSWORD_TOO_SHORT);
    }

    @Test
    void operationsOnAnUnknownAccountFail() {
        assertThatThrownBy(() -> userAccountService.setEnabled(999_999L, true))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.ACCOUNT_NOT_FOUND);
    }

    @Test
    void theSameAddressCannotBeCreatedTwice() {
        userAccountService.createAccount("doppelt@example.com", "ein-langes-passwort", "Dop", "Pelt", true);

        assertThatThrownBy(() -> userAccountService.createAccount(
                "DOPPELT@example.com", "ein-langes-passwort", "Dop", "Pelt", true))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void lookupsWorkByIdAndByAddressAndListAlphabetically() {
        Long id = userAccountService.createAccount(
                "zweite@example.com", "ein-langes-passwort", "Berta", "Zweite", true).id();
        userAccountService.createAccount("erste@example.com", "ein-langes-passwort", "Anna", "Erste", true);

        assertThat(userAccountService.findById(id)).isPresent();
        assertThat(userAccountService.findByEmail("ZWEITE@example.com"))
                .as("the lookup has to match regardless of case")
                .isPresent();
        assertThat(userAccountService.findAll())
                .extracting(UserAccountDto::lastName)
                .containsExactly("Erste", "Zweite");
    }

    @Test
    void signingInWithAnUnknownAddressIsRejected() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("niemand@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
