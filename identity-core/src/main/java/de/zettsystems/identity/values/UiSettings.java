package de.zettsystems.identity.values;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Einstellungen der mitgelieferten Ansichten, Präfix {@code zs.identity.ui}.
 *
 * <p>Die Voreinstellungen sind so gewählt, dass eine Anwendung hier nichts
 * setzen muss: eine schmale, zentrierte Formularspalte, die vom Telefon bis
 * zum Rechner trägt. Wer mehr will, stylt über die festen CSS-Klassen der
 * Ansichten ({@code identity-view}, {@code identity-view__form} und je Ansicht
 * eine eigene) — dafür braucht es hier keine Einstellung.
 *
 * <p>Liegt trotz des Zwecks in {@code identity-core}: Der Baustein bindet alle
 * Einstellungen an einen Record unter {@code zs.identity}, und die Gegenprobe —
 * ein zweiter {@code @ConfigurationProperties}-Record im Vaadin-Modul — hätte
 * dort eine eigene Auto-Konfiguration nötig gemacht. Vaadin kommt hier nicht
 * vor; der Kern bleibt frei davon.
 *
 * @param maxWidth            Breite, ab der die Formularspalte nicht weiter
 *                            mitwächst — eine CSS-Länge, also mit Einheit.
 *                            Zeilen über etwa 60 Zeichen liest niemand gern,
 *                            und ein Formular über die ganze Bildschirmbreite
 *                            wirkt auf dem Rechner verloren.
 * @param classNames          zusätzliche CSS-Klassen an jeder Ansicht des
 *                            Bausteins. Der Andockpunkt für das Theme einer
 *                            Anwendung: Sie hängt hier ihre eigene Klasse ein
 *                            (etwa {@code my-app-card}) und stylt darüber,
 *                            ohne dass der Baustein ihr Theme kennen muss.
 * @param notificationDuration wie lange die Hinweise der Ansichten stehen
 *                            bleiben. {@link Duration#ZERO} lässt sie stehen,
 *                            bis jemand sie wegklickt — sinnvoll bei
 *                            Anwendungen mit langen Texten oder
 *                            Vorlesewerkzeugen.
 */
public record UiSettings(@DefaultValue("28rem") String maxWidth,
                         @DefaultValue({}) List<String> classNames,
                         @DefaultValue("5s") Duration notificationDuration) {

    public UiSettings {
        if (maxWidth.isBlank()) {
            throw new IllegalArgumentException("zs.identity.ui.max-width must not be blank");
        }
        if (notificationDuration.isNegative()) {
            throw new IllegalArgumentException("zs.identity.ui.notification-duration must not be negative");
        }
        classNames = List.copyOf(classNames);
    }

    /** Voreinstellungen für Tests, die den Record von Hand bauen. */
    public static UiSettings defaults() {
        return new UiSettings("28rem", List.of(), Duration.ofSeconds(5));
    }
}
