package de.zettsystems.identity.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The refresher depends on three surrounding conditions: the session, the
 * transaction and the servlet environment. The tests recreate each of them
 * individually instead of starting an application.
 */
class AuthenticationRefresherTest {

    private static final String EMAIL = "anna@example.com";

    private final RecordingContextRepository contextRepository = new RecordingContextRepository();

    /** Returns the account with a <em>new</em> role, which is exactly the case at hand. */
    private final UserDetailsService userDetailsService = username ->
            User.withUsername(username).password("irrelevant").authorities("ROLE_USER", "ROLE_GROUP_LEADER").build();

    private final AuthenticationRefresher testee =
            new AuthenticationRefresher(userDetailsService, contextRepository);

    @AfterEach
    void clearThreadLocals() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static void signIn(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                email, "credentials", List.of()));
        SecurityContextHolder.setContext(context);
    }

    private static List<String> currentAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? List.of() : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    void withoutARunningTransactionTheSessionIsRefreshedStraightAway() {
        signIn(EMAIL);

        testee.refreshAfterCommit(EMAIL);

        assertThat(currentAuthorities()).contains("ROLE_GROUP_LEADER");
    }

    /**
     * Only after the commit: if the session were refreshed inside the
     * transaction and that were then rolled back, the person would carry on
     * with rights that never existed in the database.
     */
    @Test
    void insideATransactionTheRefreshWaitsForTheCommit() {
        signIn(EMAIL);
        TransactionSynchronizationManager.initSynchronization();

        testee.refreshAfterCommit(EMAIL);

        assertThat(currentAuthorities())
                .as("not committed yet")
                .doesNotContain("ROLE_GROUP_LEADER");

        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        assertThat(synchronizations).hasSize(1);
        synchronizations.getFirst().afterCommit();

        assertThat(currentAuthorities()).contains("ROLE_GROUP_LEADER");
    }

    @Test
    void aManagedAccountWithoutAddressIsIgnored() {
        signIn(EMAIL);

        testee.refreshAfterCommit(null);

        assertThat(currentAuthorities()).isEmpty();
    }

    @Test
    void aChangeToSomebodyElseLeavesTheSessionAlone() {
        signIn(EMAIL);

        testee.refreshAfterCommit("bert@example.com");

        assertThat(currentAuthorities()).isEmpty();
    }

    @Test
    void withoutAnybodySignedInNothingHappens() {
        testee.refreshAfterCommit(EMAIL);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** The address is not case-sensitive, so it is the same account. */
    @Test
    void theComparisonIgnoresUpperAndLowerCase() {
        signIn("Anna@Example.com");

        testee.refreshAfterCommit(EMAIL);

        assertThat(currentAuthorities()).contains("ROLE_GROUP_LEADER");
    }

    /**
     * Since Spring Security 6 the framework no longer writes the context back
     * into the session by itself; without saving it explicitly the refresh
     * would last exactly one request.
     */
    @Test
    void inAServletRequestTheRefreshedContextIsStoredInTheSession() {
        signIn(EMAIL);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest(), new MockHttpServletResponse()));

        testee.refreshAfterCommit(EMAIL);

        assertThat(contextRepository.saved).hasSize(1);
        assertThat(contextRepository.saved.getFirst().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_GROUP_LEADER");
    }

    /** Without a response object there is no session to write into. */
    @Test
    void withoutAResponseNothingIsStored() {
        signIn(EMAIL);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        testee.refreshAfterCommit(EMAIL);

        assertThat(currentAuthorities()).contains("ROLE_GROUP_LEADER");
        assertThat(contextRepository.saved).isEmpty();
    }

    @Test
    void outsideAServletEnvironmentTheThreadContextIsEnough() {
        signIn(EMAIL);

        testee.refreshAfterCommit(EMAIL);

        assertThat(currentAuthorities()).contains("ROLE_GROUP_LEADER");
        assertThat(contextRepository.saved).isEmpty();
    }

    /**
     * An account deleted in the meantime must not make the business action that
     * just succeeded look like a failure after the fact.
     */
    @Test
    void aVanishedAccountLeavesTheOldSessionStanding() {
        signIn(EMAIL);
        AuthenticationRefresher refresher = new AuthenticationRefresher(username -> {
            throw new UsernameNotFoundException("gone");
        }, contextRepository);

        refresher.refreshAfterCommit(EMAIL);

        assertThat(currentAuthorities()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    /** Without a test double the repository is created when saving; that route has to hold too. */
    @Test
    void theProductionConstructorWorksWithoutARepository() {
        signIn(EMAIL);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest(), new MockHttpServletResponse()));

        new AuthenticationRefresher(userDetailsService).refreshAfterCommit(EMAIL);

        assertThat(currentAuthorities()).contains("ROLE_GROUP_LEADER");
    }

    private static final class RecordingContextRepository implements SecurityContextRepository {

        private final List<SecurityContext> saved = new ArrayList<>();

        @Override
        @SuppressWarnings("deprecation")
        public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
            return SecurityContextHolder.createEmptyContext();
        }

        @Override
        public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
            saved.add(context);
        }

        @Override
        public boolean containsContext(HttpServletRequest request) {
            return !saved.isEmpty();
        }
    }
}
