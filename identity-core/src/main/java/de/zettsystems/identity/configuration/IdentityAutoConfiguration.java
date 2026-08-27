package de.zettsystems.identity.configuration;

import de.zettsystems.identity.application.IdentityBeans;
import de.zettsystems.identity.values.IdentityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Verdrahtet den Identity-Baustein, sobald er im Klassenpfad liegt.
 *
 * <p>Eine einbindende Anwendung muss dafür nichts tun außer: die Abhängigkeit
 * aufnehmen, eine {@code RoleCatalog}-Bean bereitstellen und bei Bedarf Werte
 * unter {@code zs.identity} setzen. Entities, Repositories und
 * Flyway-Migrationen findet der Baustein selbst — siehe
 * {@link IdentityPackageRegistrar} und {@link IdentityFlywayAutoConfiguration}.
 *
 * <p><strong>Bewusst ohne {@code @ComponentScan}.</strong> Der wäre hier
 * bequem, hat aber zwei Haken, die genau das kaputt machen, wofür dieser
 * Baustein gebaut ist: gescannte Beans tragen kein
 * {@code @ConditionalOnMissingBean}, sind also von der Anwendung nicht
 * ersetzbar; und ein Scan aus einer Auto-Konfiguration heraus greift in den
 * Paketbaum der Anwendung ein, sobald sich die Pakete überschneiden. Alle Beans
 * stehen deshalb explizit in {@link IdentityBeans} — jede einzeln
 * überschreibbar.
 */
@AutoConfiguration
@ConditionalOnClass(JpaRepository.class)
@EnableConfigurationProperties(IdentityProperties.class)
@Import({IdentityPackageRegistrar.class, IdentityBeans.class})
public class IdentityAutoConfiguration {
}
