package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.Impersonation;
import de.zettsystems.identity.values.ImpersonationEnded;
import de.zettsystems.identity.values.ImpersonationStarted;
import de.zettsystems.identity.values.Scope;
import de.zettsystems.identity.values.UserAccountDto;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Acting as a managed account (since 1.5.0): the session switches to the
 * target and back, only managed accounts qualify, the application's policy
 * has the last word, and the account's settings stay locked meanwhile.
 */
@Import(ImpersonationServiceIT.Policy.class)
@RecordApplicationEvents
class ImpersonationServiceIT extends AbstractIdentityIntegrationTest {

    private static final String ADMIN = "admin@example.com";
    private static final String PASSWORD = "ein-langes-passwort";

    /** The application's rule, switchable per test. */
    @TestConfiguration
    static class Policy {
        static final AtomicBoolean ALLOWED = new AtomicBoolean(true);

        @Bean
        ImpersonationPolicy impersonationPolicy() {
            return (actor, target) -> ALLOWED.get();
        }
    }

    @Autowired
    private ImpersonationService impersonation;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private InvitationService invitationService;
    @Autowired
    private ActiveScopeService activeScopeService;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private AuthTokenRepository tokenRepository;
    @Autowired
    private IdentityMailSender mailSender;
    @Autowired
    private ApplicationEventPublisher publisher;
    @Autowired
    private ApplicationEvents events;

    private Long adminId;
    private Authentication adminSession;
    private UserAccountDto managed;

    @BeforeEach
    void signInAsAdministrator() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        ((RecordingMailSender) mailSender).clear();
        Policy.ALLOWED.set(true);

        adminId = userAccountService.createAccount(ADMIN, PASSWORD, AccountName.of("Ada", "Admin"), true).id();
        userAccountService.grantRole(adminId, "GROUP_ADMIN");
        IdentityUserDetails admin = (IdentityUserDetails) userDetailsService.loadUserByUsername(ADMIN);
        adminSession = UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(adminSession);

        managed = userAccountService.createManagedAccount(AccountName.of("Kai", "Kind"));
        userAccountService.grantRole(managed.id(), "MEMBER", Scope.of("club", "7"));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aManagedAccountCanBeActedAs() {
        Impersonation started = impersonation.start(managed.id());

        Authentication session = SecurityContextHolder.getContext().getAuthentication();
        assertThat(session.getPrincipal()).isInstanceOf(ImpersonatedUser.class);
        ImpersonatedUser principal = (ImpersonatedUser) session.getPrincipal();
        assertThat(principal.userId()).isEqualTo(managed.id());
        assertThat(principal.getUsername()).as("no address to sign in with").isEqualTo("managed:" + managed.id());
        assertThat(session.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .as("the target's roles, not the administrator's")
                .contains("ROLE_USER", "ROLE_MEMBER@club:7")
                .doesNotContain("ROLE_GROUP_ADMIN");
        assertThat(started).isEqualTo(new Impersonation(adminId, managed.id(), "Kai Kind"));
        assertThat(impersonation.current()).contains(started);
        assertThat(impersonation.currentImpersonator()).contains(adminId);
        assertThat(events.stream(ImpersonationStarted.class))
                .containsExactly(new ImpersonationStarted(adminId, managed.id()));
    }

    @Test
    void stoppingGivesTheSessionBackToTheAdministrator() {
        impersonation.start(managed.id());

        assertThat(impersonation.stop()).isTrue();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(adminSession);
        assertThat(impersonation.current()).isEmpty();
        assertThat(impersonation.stop()).as("nothing left to stop").isFalse();
        assertThat(events.stream(ImpersonationEnded.class))
                .containsExactly(new ImpersonationEnded(adminId, managed.id()));
    }

    @Test
    void anAccountSomebodyCanSignInWithIsNeverActedAs() {
        Long registered = userAccountService.createAccount(
                "echt@example.com", PASSWORD, AccountName.of("Echt", "Person"), true).id();

        assertNotAllowed(() -> impersonation.start(registered));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(adminSession);
    }

    @Test
    void anAccountWithAnInvitationIsNoLongerManaged() {
        invitationService.inviteToClaim(managed.id(), "kai@example.com");

        assertNotAllowed(() -> impersonation.start(managed.id()));
    }

    @Test
    void notOneselfAndNotFromWithinAnImpersonation() {
        UserAccountDto second = userAccountService.createManagedAccount(AccountName.of("Zweites", "Kind"));

        assertNotAllowed(() -> impersonation.start(adminId));
        impersonation.start(managed.id());
        assertNotAllowed(() -> impersonation.start(second.id()));
    }

    @Test
    void thePolicyHasTheLastWord() {
        Policy.ALLOWED.set(false);

        assertNotAllowed(() -> impersonation.start(managed.id()));
        assertThat(events.stream(ImpersonationStarted.class)).isEmpty();
    }

    @Test
    void withoutAPolicyNobodyMayActAsAnybody() {
        ImpersonationService withoutPolicy = new ImpersonationServiceImpl(userRepository, null, publisher);

        assertNotAllowed(() -> withoutPolicy.start(managed.id()));
    }

    @Test
    void redeemingTheInvitationEndsTheImpersonationAtTheNextCheck() {
        impersonation.start(managed.id());
        SecurityContext impersonated = SecurityContextHolder.getContext();

        // The administrator invites from another tab; the impersonation goes
        // on as long as nobody can sign in with the account.
        inSessionOf(adminSession, () -> invitationService.inviteToClaim(managed.id(), "kai@example.com"));
        assertThat(impersonation.verify()).isTrue();

        // The person redeems the invitation in their own browser.
        String token = ((RecordingMailSender) mailSender).tokenFromLastMailTo("kai@example.com");
        inSessionOf(null, () -> invitationService.claim(token, PASSWORD));
        SecurityContextHolder.setContext(impersonated);

        assertThat(impersonation.verify()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(adminSession);
        assertThat(events.stream(ImpersonationEnded.class))
                .containsExactly(new ImpersonationEnded(adminId, managed.id()));
    }

    @Test
    void aPolicyThatChangesItsMindEndsTheImpersonationAtTheNextCheck() {
        impersonation.start(managed.id());
        Policy.ALLOWED.set(false);

        assertThat(impersonation.verify()).isFalse();
        assertThat(impersonation.current()).isEmpty();
    }

    @Test
    void theSettingsOfTheAccountAreLockedMeanwhile() {
        impersonation.start(managed.id());
        Long target = managed.id();

        assertRestricted(() -> userAccountService.changePassword(target, "ein-neues-passwort"));
        assertRestricted(() -> userAccountService.deleteAccount(target));
        assertRestricted(() -> invitationService.inviteToClaim(target, "kai@example.com"));
        assertRestricted(() -> userAccountService.requirePasswordChange(target));
        assertThat(userRepository.findById(target).orElseThrow().hasPassword()).isFalse();
    }

    @Test
    void switchingTheActiveScopeKeepsTheImpersonation() {
        impersonation.start(managed.id());

        activeScopeService.switchTo(Scope.of("club", "7"));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_MEMBER");
        assertThat(impersonation.currentImpersonator())
                .as("the way back must not get lost on the way")
                .contains(adminId);
        impersonation.stop();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(adminSession);
    }

    private static void inSessionOf(@Nullable Authentication authentication, Runnable action) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext other = SecurityContextHolder.createEmptyContext();
        other.setAuthentication(authentication);
        SecurityContextHolder.setContext(other);
        try {
            action.run();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private static void assertNotAllowed(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.IMPERSONATION_NOT_ALLOWED);
    }

    private static void assertRestricted(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.IMPERSONATION_RESTRICTED);
    }
}
