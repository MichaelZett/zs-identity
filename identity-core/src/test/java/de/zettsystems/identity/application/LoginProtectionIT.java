package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.MutableTestClock;
import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.testsupport.RecordingMailSender;
import de.zettsystems.identity.values.AccountTemporarilyLocked;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.RoleDefinition;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.WebAttributes;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * The protection against guessing through a real form login: the filter
 * chain an application writes, and nothing of the building block configured
 * by hand -- the provider has to get into the sign-in on its own.
 */
@SpringBootTest(classes = LoginProtectionIT.FormLoginApplication.class)
class LoginProtectionIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestImage.resolve())
            .withDatabaseName("identity")
            .withUsername("app")
            .withPassword("app")
            .withReuse(true);

    private static final String EMAIL = "anna@example.com";
    private static final String PASSWORD = "ein-langes-passwort";
    private static final String WRONG = "falsches-passwort";

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * No component scan: this package holds the building block's own
     * configuration, and a scan would pick it up as the application's --
     * ahead of the beans below that are meant to displace parts of it.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class FormLoginApplication {

        @Bean
        RoleCatalog applicationRoles() {
            return () -> Set.of(RoleDefinition.of("MEMBER", "role.member"));
        }

        @Bean
        IdentityMailSender testMailSender() {
            return new RecordingMailSender();
        }

        @Bean
        Clock testClock() {
            return new MutableTestClock();
        }

        /** Records the waits instead of sitting through them; displaces the building block's throttle. */
        @Bean
        LoginThrottle recordingThrottle(IdentityProperties properties, Clock clock, Waits waits) {
            return new LoginThrottle(properties.loginProtection(), clock, waits.list::add);
        }

        @Bean
        Waits waits() {
            return new Waits();
        }

        @Bean
        LockEvents lockEvents() {
            return new LockEvents();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) {
            http.formLogin(login -> login.loginPage("/login").permitAll())
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated());
            return http.build();
        }
    }

    static class Waits {

        final List<Duration> list = Collections.synchronizedList(new ArrayList<>());
    }

    static class LockEvents {

        final List<AccountTemporarilyLocked> received = Collections.synchronizedList(new ArrayList<>());

        @EventListener
        void on(AccountTemporarilyLocked event) {
            received.add(event);
        }
    }

    @Autowired
    private WebApplicationContext webContext;
    @Autowired
    private FilterChainProxy filterChain;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private IdentityMailSender mailSender;
    @Autowired
    private Clock clock;
    @Autowired
    private Waits recordedWaits;
    @Autowired
    private LockEvents lockEvents;

    private final Map<MockHttpServletResponse, MockHttpServletRequest> requests = new IdentityHashMap<>();
    private RecordingMailSender mails;
    private MutableTestClock testClock;
    private List<Duration> waits;
    private UserAccountDto anna;

    @BeforeEach
    void anAccount() {
        userRepository.deleteAll();
        mails = (RecordingMailSender) mailSender;
        mails.clear();
        testClock = (MutableTestClock) clock;
        // Each test on a fresh stretch of time: the throttle's memory lives
        // as long as the context, and forgets after the lock duration.
        testClock.advanceBy(Duration.ofDays(1));
        waits = recordedWaits.list;
        waits.clear();
        lockEvents.received.clear();
        anna = userAccountService.createAccount(EMAIL, PASSWORD, "Anna", "Beispiel", true);
    }

    @Test
    void theRightPasswordGetsIn() throws Exception {
        assertThat(signIn(EMAIL, PASSWORD).getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void threeWrongPasswordsLockTheAccountAndThenEvenTheRightOneIsRefused() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertRefused(signIn(EMAIL, WRONG));
        }

        assertRefused(signIn(EMAIL, PASSWORD));

        UserAccountDto locked = userAccountService.findById(anna.id()).orElseThrow();
        assertThat(locked.lockedAt(clock.instant())).isTrue();
        assertThat(locked.lockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
        assertThat(locked.enabled()).as("a lock is not a disabled account").isTrue();
        assertThat(lockEvents.received).singleElement().satisfies(event -> {
            assertThat(event.userId()).isEqualTo(anna.id());
            assertThat(event.clientAddress()).isEqualTo("127.0.0.1");
        });
        assertThat(awaitMailTo(EMAIL).kind()).isEqualTo(RecordingMailSender.Kind.ACCOUNT_TEMPORARILY_LOCKED);
        assertThat(awaitMailTo(EMAIL).url()).isEqualTo("http://localhost:8080/password/forgot");
    }

    @Test
    void onceTheLockRunsOutTheRightPasswordGetsInAgain() throws Exception {
        lockAnna();

        testClock.advanceBy(Duration.ofMinutes(15).plusSeconds(1));

        assertThat(signIn(EMAIL, PASSWORD).getRedirectedUrl()).isEqualTo("/");
        UserAccount account = userRepository.findById(anna.id()).orElseThrow();
        assertThat(account.getFailedLoginCount()).as("a successful sign-in forgets").isZero();
        assertThat(account.getLockedUntil()).isNull();
    }

    @Test
    void forgotPasswordLiftsTheLock() throws Exception {
        lockAnna();
        mails.clear();

        passwordResetService.requestReset(EMAIL);
        passwordResetService.resetPassword(mails.tokenFromLastMailTo(EMAIL), "ein-ganz-neues-passwort");

        assertThat(signIn(EMAIL, "ein-ganz-neues-passwort").getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void anAdministratorCanLiftTheLock() throws Exception {
        lockAnna();

        UserAccountDto unlocked = userAccountService.unlock(anna.id());

        assertThat(unlocked.lockedUntil()).isNull();
        assertThat(signIn(EMAIL, PASSWORD).getRedirectedUrl()).isEqualTo("/");
    }

    /** Nothing to guess at a passkey, and it proves possession: the lock does not stand in its way. */
    @Test
    void aPasskeySignInForgetsTheFailuresAndLiftsTheLock() throws Exception {
        lockAnna();
        UserDetails user = userDetailsService.loadUserByUsername(EMAIL);

        events.publishEvent(new AuthenticationSuccessEvent(new PasskeyAuthentication(user, user.getAuthorities())));

        assertThat(userAccountService.findById(anna.id()).orElseThrow().lockedUntil()).isNull();
        assertThat(signIn(EMAIL, PASSWORD).getRedirectedUrl()).isEqualTo("/");
    }

    /**
     * Unknown address, locked account, wrong password: the same answer, down
     * to the exception in the session -- whoever is guessing learns neither
     * that the address has an account nor that the password was right.
     */
    @Test
    void unknownLockedAndWrongLookTheSame() throws Exception {
        userAccountService.createAccount("bert@example.com", PASSWORD, "Bert", "Beispiel", true);
        lockAnna();

        MockHttpServletResponse unknown = signIn("nobody@example.com", PASSWORD, "198.51.100.1");
        MockHttpServletResponse wrong = signIn("bert@example.com", WRONG, "198.51.100.2");
        MockHttpServletResponse locked = signIn(EMAIL, PASSWORD, "198.51.100.3");

        assertThat(List.of(unknown, wrong, locked)).allSatisfy(this::assertRefused);
        assertThat(lastException(unknown).getMessage())
                .isEqualTo(lastException(wrong).getMessage())
                .isEqualTo(lastException(locked).getMessage());
        assertThat(lastException(unknown).getClass())
                .isEqualTo(lastException(wrong).getClass())
                .isEqualTo(lastException(locked).getClass());
    }

    /** The wait is kept by what was typed, so it cannot tell a known address from an unknown one. */
    @Test
    void anUnknownAddressWaitsAsLongAsAKnownOne() throws Exception {
        signIn(EMAIL, WRONG, "198.51.100.1");
        signIn("nobody@example.com", WRONG, "198.51.100.2");
        waits.clear();

        signIn(EMAIL, WRONG, "198.51.100.3");
        signIn("nobody@example.com", WRONG, "198.51.100.4");

        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    void theWaitComesBeforeThePasswordIsChecked() throws Exception {
        signIn(EMAIL, WRONG);
        signIn(EMAIL, WRONG);

        assertThat(waits).as("nothing before the first failure, then doubling").containsExactly(Duration.ofSeconds(1));
        signIn(EMAIL, PASSWORD);
        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    private void lockAnna() throws Exception {
        for (int i = 0; i < 3; i++) {
            signIn(EMAIL, WRONG);
        }
        assertThat(userAccountService.findById(anna.id()).orElseThrow().lockedAt(clock.instant())).isTrue();
    }

    private MockHttpServletResponse signIn(String username, String password) throws Exception {
        return signIn(username, password, "127.0.0.1");
    }

    private MockHttpServletResponse signIn(String username, String password, String clientAddress)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(webContext.getServletContext(), "POST", "/login");
        request.setRemoteAddr(clientAddress);
        request.setParameter("username", username);
        request.setParameter("password", password);
        csrf().postProcessRequest(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filterChain.doFilter(request, response, new MockFilterChain());
        } finally {
            SecurityContextHolder.clearContext();
        }
        requests.put(response, request);
        return response;
    }

    private AuthenticationException lastException(MockHttpServletResponse response) {
        MockHttpServletRequest request = requests.get(response);
        Object exception = request.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        assertThat(exception).isInstanceOf(AuthenticationException.class);
        return (AuthenticationException) exception;
    }

    private void assertRefused(MockHttpServletResponse response) {
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
    }

    /** The notice goes out from a thread of its own, so the test waits for it a little. */
    private RecordingMailSender.SentMail awaitMailTo(String email) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            var mail = mails.lastMailTo(email);
            if (mail.isPresent()) {
                return mail.get();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No mail to " + email);
    }
}
