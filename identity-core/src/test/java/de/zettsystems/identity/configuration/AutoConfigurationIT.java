package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.InvitationService;
import de.zettsystems.identity.application.PasswordResetService;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.application.RoleCatalog;
import de.zettsystems.identity.application.UserAccountService;
import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.values.IdentityProperties;
import de.zettsystems.identity.values.RoleDefinition;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Beantwortet die Frage "lässt sich der Baustein wirklich allein per
 * Auto-Konfiguration einbauen?" mit einem Beweis statt mit einer Behauptung.
 *
 * <p>Die Anwendung unten ist so minimal, wie ein fremdes Projekt nur sein kann:
 * {@code @SpringBootApplication} in einem <strong>anderen Paketbaum</strong>,
 * eine {@code RoleCatalog}-Bean, ein Mail-Ersatz fürs Testen. Kein
 * {@code @EntityScan}, kein {@code @EnableJpaRepositories}, kein
 * {@code @Import}, und — der Punkt, an dem es zuvor scheiterte — kein Eintrag
 * in {@code spring.flyway.locations}.
 *
 * <p>Bricht dieser Test, ist der Einbau in das nächste Projekt Handarbeit
 * geworden.
 */
@SpringBootTest(classes = AutoConfigurationIT.BareMinimumApplication.class)
class AutoConfigurationIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestImage.resolve())
            .withDatabaseName("identity")
            .withUsername("identity")
            .withPassword("identity")
            .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** So wenig, wie eine einbindende Anwendung mitbringen muss. */
    @SpringBootApplication
    static class BareMinimumApplication {

        @Bean
        RoleCatalog applicationRoles() {
            return () -> Set.of(RoleDefinition.of("GROUP_ADMIN", "role.groupAdmin"),
                    new RoleDefinition("MEMBER", "role.member", Set.of("season:read")));
        }

        @Bean
        IdentityMailSender testMailSender() {
            return new de.zettsystems.identity.testsupport.RecordingMailSender();
        }
    }

    @Autowired
    private ApplicationContext context;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void theServicesAreAvailableWithoutAnyWiringByTheApplication() {
        assertThat(context.getBean(RegistrationService.class)).isNotNull();
        assertThat(context.getBean(PasswordResetService.class)).isNotNull();
        assertThat(context.getBean(InvitationService.class)).isNotNull();
        assertThat(context.getBean(UserAccountService.class)).isNotNull();
        assertThat(context.getBean(UserDetailsService.class)).isNotNull();
        assertThat(context.getBean(IdentityProperties.class)).isNotNull();
    }

    @Test
    void repositoriesAreFoundAlthoughTheyLiveOutsideTheApplicationPackage() {
        // Ohne IdentityPackageRegistrar wäre hier Schluss: Spring Boot sucht
        // Repositories nur unterhalb der @SpringBootApplication-Klasse.
        assertThat(context.getBean(UserAccountRepository.class)).isNotNull();
        assertThat(context.getBean(RoleRepository.class)).isNotNull();
        assertThat(context.getBean(AuthTokenRepository.class)).isNotNull();
    }

    @Test
    void entitiesAreRegisteredWithThePersistenceUnit() {
        assertThat(entityManager.getMetamodel().getEntities())
                .extracting(type -> type.getJavaType().getName())
                .contains("de.zettsystems.identity.domain.UserAccount",
                        "de.zettsystems.identity.domain.Role",
                        "de.zettsystems.identity.domain.AuthToken");
    }

    @Test
    void theModuleBringsItsOwnDatabaseTables() {
        // Der eigentliche Beweis für IdentityFlywayAutoConfiguration: Die
        // Anwendung hat spring.flyway.locations nie angefasst.
        Integer tables = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema = 'public'
                   and table_name in ('auth_user', 'auth_role', 'auth_token',
                                      'auth_user_role', 'auth_role_authority')
                """, Integer.class);

        assertThat(tables).isEqualTo(5);
    }

    @Test
    void theRoleCatalogOfTheApplicationEndsUpInTheDatabase() {
        RoleRepository roles = context.getBean(RoleRepository.class);

        assertThat(roles.findAllByOrderByCodeAsc())
                .extracting(de.zettsystems.identity.domain.Role::getCode)
                .contains("SYSTEM_ADMIN", "USER", "GROUP_ADMIN", "MEMBER");
    }

    @Test
    void theApplicationCanReplaceIndividualBeans() {
        // Die Anwendung oben stellt eine eigene IdentityMailSender-Bean bereit.
        // Sie muss die Voreinstellung des Bausteins verdrängt haben — genau das
        // funktionierte nicht, solange die Beans per Komponentensuche kamen.
        assertThat(context.getBean(IdentityMailSender.class))
                .isInstanceOf(de.zettsystems.identity.testsupport.RecordingMailSender.class);
        assertThat(context.getBeanNamesForType(IdentityMailSender.class))
                .as("keine zweite Bean daneben")
                .hasSize(1);
    }

    @Test
    void aFullRegistrationRunsThroughInThisBareSetup() {
        RegistrationService registration = context.getBean(RegistrationService.class);
        var mails = (de.zettsystems.identity.testsupport.RecordingMailSender)
                context.getBean(IdentityMailSender.class);
        mails.clear();

        var created = registration.register("blank@example.com", "ein-langes-passwort", "Blanko", "Test");
        var confirmed = registration.confirmEmail(mails.tokenFromLastMailTo("blank@example.com"));

        assertThat(created.enabled()).isFalse();
        assertThat(confirmed.enabled()).isTrue();
    }
}
