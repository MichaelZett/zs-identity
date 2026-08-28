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
 * Der Auffrischer hängt an drei Rahmenbedingungen — Sitzung, Transaktion und
 * Servlet-Umgebung. Die Tests bauen jede davon einzeln nach, statt eine
 * Anwendung zu starten.
 */
class AuthenticationRefresherTest {

    private static final String EMAIL = "anna@example.com";

    private final RecordingContextRepository contextRepository = new RecordingContextRepository();

    /** Liefert das Konto mit einer <em>neuen</em> Rolle — genau der Fall, um den es geht. */
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
     * Erst nach dem Commit: Würde die Sitzung innerhalb der Transaktion
     * aufgefrischt und diese danach zurückgerollt, liefe die Person mit
     * Rechten weiter, die es in der Datenbank nie gab.
     */
    @Test
    void insideATransactionTheRefreshWaitsForTheCommit() {
        signIn(EMAIL);
        TransactionSynchronizationManager.initSynchronization();

        testee.refreshAfterCommit(EMAIL);

        assertThat(currentAuthorities())
                .as("noch nicht festgeschrieben")
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

    /** Die Adresse ist nicht schreibweisenabhängig — das Konto ist dasselbe. */
    @Test
    void theComparisonIgnoresUpperAndLowerCase() {
        signIn("Anna@Example.com");

        testee.refreshAfterCommit(EMAIL);

        assertThat(currentAuthorities()).contains("ROLE_GROUP_LEADER");
    }

    /**
     * Seit Spring Security 6 schreibt der Rahmen den Kontext nicht mehr von
     * selbst in die Sitzung zurück; ohne das ausdrückliche Speichern hielte die
     * Auffrischung genau einen Aufruf lang.
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

    /** Ohne Antwortobjekt gibt es keine Sitzung, in die sich schreiben ließe. */
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
     * Ein zwischenzeitlich gelöschtes Konto darf die gerade erfolgreiche
     * Fachaktion nicht nachträglich als Fehler erscheinen lassen.
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

    /** Ohne Testdoppel entsteht das Repository erst beim Speichern — auch dieser Weg muss tragen. */
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
