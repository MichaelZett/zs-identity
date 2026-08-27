package de.zettsystems.identity.testsupport;

import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.RoleCatalog;
import de.zettsystems.identity.values.RoleDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.util.Set;

/**
 * Minimale Anwendung, die den Baustein einbindet — so, wie es ein fremdes
 * Projekt tun würde.
 *
 * <p>Das ist zugleich die Probe aufs Exempel: Diese Klasse liegt in einem
 * anderen Paketbaum als {@code de.zettsystems.identity}. Findet Spring die
 * Entities und Repositories des Bausteins trotzdem, funktioniert der
 * Paket-Registrar der Auto-Konfiguration.
 */
@SpringBootApplication
public class IdentityTestApplication {

    /** Steht für die fachlichen Rollen, die eine echte Anwendung mitbringt. */
    @Bean
    RoleCatalog testRoleCatalog() {
        return () -> Set.of(
                RoleDefinition.of("GROUP_ADMIN", "role.groupAdmin"),
                new RoleDefinition("MEMBER", "role.member", Set.of("season:read")));
    }

    /** Sammelt Mails im Speicher, statt sie zu verschicken. */
    @Bean
    IdentityMailSender recordingMailSender() {
        return new RecordingMailSender();
    }

    /** Feste Zeit, damit Tests den Ablauf von Token steuern können. */
    @Bean
    Clock testClock() {
        return new MutableTestClock();
    }
}
