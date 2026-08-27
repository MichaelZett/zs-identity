package de.zettsystems.identity.application;

import de.zettsystems.identity.values.RoleDefinition;

import java.util.Set;

/**
 * Liefert die Rollen, die eine Anwendung braucht.
 *
 * <p>Das ist der Grund, warum dieser Baustein wiederverwendbar ist: Welche
 * Rollen es gibt, weiß nur die Anwendung. Sie stellt eine Bean bereit, der
 * Baustein spiegelt den Katalog beim Start idempotent in die Datenbank. Ein fest
 * verdrahtetes Enum im Baustein wäre in der zweiten Anwendung sofort falsch.
 *
 * <p>Der Baustein bringt seine eigenen Basisrollen über
 * {@code BuiltinRoleCatalog} mit; die Kataloge aller Beans werden vereinigt.
 * Beispiel aus der Anwendung:
 *
 * <pre>
 * &#64;Component
 * class HallenplanungRoleCatalog implements RoleCatalog {
 *     &#64;Override
 *     public Set&lt;RoleDefinition&gt; roles() {
 *         return Set.of(RoleDefinition.of("GROUP_ADMIN", "role.groupAdmin"),
 *                       RoleDefinition.of("MEMBER", "role.member"));
 *     }
 * }
 * </pre>
 *
 * <p>Rollen, die aus keinem Katalog mehr kommen, bleiben in der Datenbank
 * stehen. Automatisches Löschen würde bei einem Tippfehler im Katalog
 * schlagartig allen Betroffenen die Rechte entziehen.
 */
@FunctionalInterface
public interface RoleCatalog {

    Set<RoleDefinition> roles();
}
