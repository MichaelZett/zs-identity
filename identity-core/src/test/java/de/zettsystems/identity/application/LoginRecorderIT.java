package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** A phone waking up sends several requests at once, and each one signs in again. */
class LoginRecorderIT extends AbstractIdentityIntegrationTest {

    private static final int REQUESTS = 6;

    @Autowired
    private UserAccountService accounts;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private UserAccountRepository userRepository;

    @Test
    void parallelSignInsOfOneAccountAllGetThrough() throws Exception {
        String email = "parallel-login@example.com";
        long account = accounts.createAccount(email, "ein-langes-passwort", "Parallel", "Anmeldung", true).id();
        CyclicBarrier start = new CyclicBarrier(REQUESTS);
        List<Future<?>> requests = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
            for (int i = 0; i < REQUESTS; i++) {
                requests.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    events.publishEvent(new AuthenticationSuccessEvent(
                            UsernamePasswordAuthenticationToken.authenticated(email, null, List.of())));
                    return null;
                }));
            }
            for (Future<?> request : requests) {
                request.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(userRepository.findById(account).orElseThrow().getLastLoginAt()).isNotNull();
    }
}
