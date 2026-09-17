package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityBeans;
import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Wires up the identity building block as soon as it is on the classpath.
 *
 * <p>An embedding application has to do nothing for that beyond taking the
 * dependency, providing a {@code RoleCatalog} bean and, where needed, setting
 * values under {@code zs.identity}. Entities, repositories and Flyway
 * migrations are found by the building block itself; see
 * {@link IdentityPackageRegistrar} and {@link IdentityFlywayAutoConfiguration}.
 *
 * <p><strong>Deliberately without {@code @ComponentScan}.</strong> It would be
 * convenient here, but it has two catches that break exactly what this
 * building block is built for: scanned beans carry no
 * {@code @ConditionalOnMissingBean} and can therefore not be replaced by the
 * application; and a scan started from an auto-configuration reaches into the
 * application's package tree as soon as the packages overlap. Every bean is
 * therefore declared explicitly in {@link IdentityBeans}, each one
 * individually replaceable.
 */
@AutoConfiguration
@ConditionalOnClass(JpaRepository.class)
@EnableConfigurationProperties(IdentityProperties.class)
@Import({IdentityPackageRegistrar.class, IdentityBeans.class})
public class IdentityAutoConfiguration {
}
