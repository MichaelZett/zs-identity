/**
 * Vaadin-Oberfläche des Auth-Bausteins: Login, Registrierung, Bestätigung und
 * Passwort-Reset.
 *
 * <p>Damit Vaadin die {@code @Route}-Klassen aus dieser Bibliothek findet, muss
 * die einbindende Anwendung dieses Paket in {@code vaadin.allowed-packages}
 * aufführen. Fehlt der Eintrag, sind die Views schlicht nicht erreichbar — ohne
 * Fehlermeldung.
 *
 * <p>Alle Routen hier tragen {@code autoLayout = false}. Eine einbindende
 * Anwendung setzt üblicherweise ein {@code @Layout} mit Kopfzeile, Navigation
 * und Abmelde-Knopf um alle ihre Ansichten; dieser Rahmen würde sonst auch die
 * Anmeldeseite umschließen. Der Baustein sagt damit nur "kein Anwendungslayout"
 * und muss die Anwendung dafür nicht kennen.
 */
@NullMarked
package de.zettsystems.identity.ui;

import org.jspecify.annotations.NullMarked;
