package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.ActiveScopeService;
import de.zettsystems.identity.application.IdentityMailSender;
import de.zettsystems.identity.application.InvitationService;
import de.zettsystems.identity.application.PasswordResetService;
import de.zettsystems.identity.application.RegistrationService;
import de.zettsystems.identity.application.RoleCatalog;
import de.zettsystems.identity.application.UserAccountService;
import de.zettsystems.identity.domain.AuthTokenRepository;
import de.zettsystems.identity.domain.Role;
import de.zettsystems.identity.domain.RoleRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.PostgresTestImage;
import de.zettsystems.identity.testsupport.RecordingMailSender;
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
 * Answers the question "can the building block really be embedded through
 * auto-configuration alone?" with a proof rather than a claim.
 *
 * <p>The application below is as minimal as an outside project can be:
 * {@code @SpringBootApplication} in a <strong>different package tree</strong>,
 * a {@code RoleCatalog} bean, a mail stand-in for testing. No
 * {@code @EntityScan}, no {@code @EnableJpaRepositories}, no {@code @Import},
 * and -- the point where it used to fail -- no entry in
 * {@code spring.flyway.locations}.
 *
 * <p>If this test breaks, embedding into the next project has turned into
 * manual work.
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

    /** As little as an embedding application has to bring along. */
    @SpringBootApplication
    static class BareMinimumApplication {

        @Bean
        RoleCatalog applicationRoles() {
            return () -> Set.of(RoleDefinition.of("GROUP_ADMIN", "role.groupAdmin"),
                    new RoleDefinition("MEMBER", "role.member", Set.of("season:read")));
        }

        @Bean
        IdentityMailSender testMailSender() {
            return new RecordingMailSender();
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
        assertThat(context.getBean(ActiveScopeService.class)).isNotNull();
        assertThat(context.getBean(IdentityProperties.class)).isNotNull();
    }

    @Test
    void repositoriesAreFoundAlthoughTheyLiveOutsideTheApplicationPackage() {
        // Without IdentityPackageRegistrar this would be the end: Spring Boot
        // looks for repositories only below the @SpringBootApplication class.
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
                        "de.zettsystems.identity.domain.RoleAssignment",
                        "de.zettsystems.identity.domain.AuthToken");
    }

    @Test
    void theModuleBringsItsOwnDatabaseTables() {
        // The actual proof for IdentityFlywayAutoConfiguration: the application
        // never touched spring.flyway.locations.
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
                .extracting(Role::getCode)
                .contains("SYSTEM_ADMIN", "USER", "GROUP_ADMIN", "MEMBER");
    }

    @Test
    void theApplicationCanReplaceIndividualBeans() {
        // The application above provides an IdentityMailSender bean of its own.
        // It has to have displaced the default of the building block -- exactly
        // what did not work while the beans came from a component scan.
        assertThat(context.getBean(IdentityMailSender.class))
                .isInstanceOf(RecordingMailSender.class);
        assertThat(context.getBeanNamesForType(IdentityMailSender.class))
                .as("no second bean next to it")
                .hasSize(1);
    }

    @Test
    void aFullRegistrationRunsThroughInThisBareSetup() {
        RegistrationService registration = context.getBean(RegistrationService.class);
        var mails = (RecordingMailSender)
                context.getBean(IdentityMailSender.class);
        mails.clear();

        var created = registration.register("blank@example.com", "ein-langes-passwort", "Blanko", "Test");
        var confirmed = registration.confirmEmail(mails.tokenFromLastMailTo("blank@example.com"));

        assertThat(created.enabled()).isFalse();
        assertThat(confirmed.enabled()).isTrue();
    }
}
