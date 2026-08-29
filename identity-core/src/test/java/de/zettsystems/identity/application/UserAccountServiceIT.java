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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Der Weg, auf dem eine Verwaltung Konten anlegt und Rechte vergibt. */
class UserAccountServiceIT extends AbstractIdentityIntegrationTest {

    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private UserDetailsService userDetailsService;

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

    /** Startpasswort aus einer Verwaltung: Das Flag steht, bis das Passwort neu ist. */
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
        // Ohne E-Mail-Adresse gibt es keinen Anmeldenamen — das Konto ist
        // über keinen Anmeldeweg erreichbar.
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
        assertThat(created.firstName()).as("leer statt null, damit Klarnamen-Apps ohne Fallunterscheidung bleiben")
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

    @Test
    void grantingRolesShowsUpInTheSecurityAuthorities() {
        Long id = userAccountService.createAccount(
                "rechte@example.com", "ein-langes-passwort", "Rech", "Te", true).id();
        userAccountService.grantRole(id, "MEMBER");

        var details = userDetailsService.loadUserByUsername("rechte@example.com");

        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .as("die Rolle als ROLE_, die daran hängende Berechtigung im Klartext")
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
                .as("die Suche muss unabhängig von der Schreibweise treffen")
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
