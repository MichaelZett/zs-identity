package de.zettsystems.identity.testsupport;

import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.RoleCatalog;
import de.zettsystems.identity.values.RoleDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import java.time.Clock;
import java.util.Set;

/**
 * A minimal application embedding the building block, the way an outside
 * project would.
 *
 * <p>This is also the proof of the pudding: this class lives in a different
 * package tree from {@code de.zettsystems.identity}. If Spring finds the
 * entities and repositories of the building block anyway, the package registrar
 * of the auto-configuration works.
 */
@SpringBootApplication
public class IdentityTestApplication {

    /** Stands for the domain roles a real application brings along. */
    @Bean
    RoleCatalog testRoleCatalog() {
        return () -> Set.of(
                RoleDefinition.of("GROUP_ADMIN", "role.groupAdmin"),
                new RoleDefinition("MEMBER", "role.member", Set.of("season:read")));
    }

    /** Collects mails in memory instead of sending them. */
    @Bean
    IdentityMailSender recordingMailSender() {
        return new RecordingMailSender();
    }

    /**
     * What an application with "keep me signed in" brings along. It is here so
     * that every integration test runs with the RememberMeTokenCleaner in
     * place -- proof that it gets in nobody's way.
     */
    @Bean
    PersistentTokenRepository recordingTokenRepository() {
        return new RecordingTokenRepository();
    }

    /** A fixed time, so that tests can control when tokens expire. */
    @Bean
    Clock testClock() {
        return new MutableTestClock();
    }
}
