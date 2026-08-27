/**
 * Wiederverwendbarer Auth-Baustein: Benutzerkonto, Registrierung, Anmeldung,
 * Passwort-Reset, Rollen und Rechte.
 *
 * <p>Dieses Paket kennt keine Anwendung, die es einbindet. Nach außen sichtbar
 * sind ausschließlich Service-Schnittstellen und unveränderliche Records; die
 * JPA-Entities verlassen das Modul nicht. Anwendungen verknüpfen ihre
 * Fachobjekte über die reine {@code userId}.
 */
@NullMarked
package de.zettsystems.identity;

import org.jspecify.annotations.NullMarked;
