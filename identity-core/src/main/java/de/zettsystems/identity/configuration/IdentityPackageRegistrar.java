package de.zettsystems.identity.configuration;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registers {@code de.zettsystems.identity} as an additional
 * auto-configuration package.
 *
 * <p>Why that is needed: Spring Boot looks for entities and repositories only
 * below the class carrying {@code @SpringBootApplication}. This building block
 * lives in a package tree of its own, so its entities and repositories would
 * be invisible.
 *
 * <p>Why not {@code @EntityScan} / {@code @EnableJpaRepositories}: both
 * <strong>replace</strong> the default instead of extending it, which would
 * cost the application its own entities. {@link
 * AutoConfigurationPackages#register} appends to the existing list instead and
 * affects both at once.
 */
public class IdentityPackageRegistrar implements ImportBeanDefinitionRegistrar {

    static final String IDENTITY_PACKAGE = "de.zettsystems.identity";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        AutoConfigurationPackages.register(registry, IDENTITY_PACKAGE);
    }
}
