package de.zettsystems.identity.configuration;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Meldet {@code de.zettsystems.identity} als zusätzliches Auto-Konfigurations-Paket an.
 *
 * <p>Warum das nötig ist: Spring Boot sucht Entities und Repositories nur
 * unterhalb der Klasse mit {@code @SpringBootApplication}. Dieser Baustein liegt
 * in einem eigenen Paketbaum, seine Entities und Repositories wären also
 * unsichtbar.
 *
 * <p>Warum nicht {@code @EntityScan} / {@code @EnableJpaRepositories}: Beide
 * <strong>ersetzen</strong> die Voreinstellung, statt sie zu erweitern — die
 * Anwendung würde damit ihre eigenen Entities verlieren. {@link
 * AutoConfigurationPackages#register} hängt dagegen an die bestehende Liste an
 * und wirkt auf beides zugleich.
 */
public class IdentityPackageRegistrar implements ImportBeanDefinitionRegistrar {

    static final String IDENTITY_PACKAGE = "de.zettsystems.identity";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        AutoConfigurationPackages.register(registry, IDENTITY_PACKAGE);
    }
}
