package de.zettsystems.identity.values;

import java.io.Serializable;
import java.util.Objects;

/**
 * Der Geltungsbereich einer Rollenzuweisung: „Admin <strong>von Verein
 * 17</strong>" statt nur „Admin".
 *
 * <p>Der Baustein <strong>deutet</strong> ihn nie. Er speichert ihn, gibt ihn
 * zurück und vergleicht ihn auf Gleichheit — was ein {@code "club"} ist, weiß
 * allein die Anwendung. Damit bleibt die erste Regel des Bausteins unangetastet:
 * kein Wissen über die Fachlichkeit derer, die ihn einbinden.
 *
 * <p>Zweiteilig und nicht ein einzelner Schlüssel, weil Anwendungen mehrere
 * Arten nebeneinander führen (ein Portal etwa Spiele <em>und</em> Vereine).
 * Erst dadurch lässt sich „alle Vereine dieser Person" beantworten, ohne
 * Zeichenketten zu zerlegen.
 *
 * <p><strong>Keine Zuweisung ohne Bereich:</strong> Eine <em>globale</em> Rolle
 * wird nicht durch einen besonderen {@code Scope} dargestellt, sondern durch
 * dessen Abwesenheit ({@code null}). Ein „leerer Bereich" als Wert wäre ein
 * zweiter Weg, dasselbe zu sagen.
 *
 * <p>{@link Serializable}, weil der aktive Bereich im Security-Prinzipal und
 * damit in der HTTP-Session liegt.
 *
 * @param type Art des Bereichs, etwa {@code club} oder {@code game}
 * @param id   Kennung innerhalb der Art, etwa {@code 17}
 */
public record Scope(String type, String id) implements Serializable {

    /**
     * Trennt Art und Kennung in der Textgestalt und im Authority-Namen
     * ({@code ROLE_ADMIN@club:17}).
     */
    public static final char SEPARATOR = ':';

    /** Trennt Rolle und Bereich im Authority-Namen. */
    public static final char AUTHORITY_SEPARATOR = '@';

    public Scope {
        type = requireUsable(type, "type");
        id = requireUsable(id, "id");
    }

    public static Scope of(String type, String id) {
        return new Scope(type, id);
    }

    /** {@code club:17} — die Gestalt, die auch im Authority-Namen steht. */
    @Override
    public String toString() {
        return type + SEPARATOR + id;
    }

    /**
     * Liest die Textgestalt zurück.
     *
     * @throws IllegalArgumentException wenn kein {@value #SEPARATOR} enthalten ist
     */
    public static Scope parse(String text) {
        Objects.requireNonNull(text, "text");
        int separator = text.indexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("Not a scope: " + text);
        }
        return new Scope(text.substring(0, separator), text.substring(separator + 1));
    }

    /**
     * Prüft, was später in einem Authority-Namen landet.
     *
     * <p><strong>Eine Sicherheitsfrage, keine Schönheitsfrage:</strong> Aus
     * Rolle und Bereich wird {@code ROLE_ADMIN@club:17}. Dürfte eine Kennung
     * selbst ein {@code @} oder ein {@value #SEPARATOR} enthalten, ließe sich
     * damit eine Berechtigung erfinden, die niemand vergeben hat — etwa die
     * Kennung {@code 4@club} für einen fremden Verein. Leerzeichen sind aus
     * demselben Grund draußen: Ausdrücke wie {@code hasAuthority(..)} werden
     * an ihnen zerlegt.
     */
    private static String requireUsable(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Scope " + field + " must not be blank");
        }
        if (normalized.indexOf(SEPARATOR) >= 0 || normalized.indexOf(AUTHORITY_SEPARATOR) >= 0
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "Scope " + field + " must not contain '" + SEPARATOR + "', '" + AUTHORITY_SEPARATOR
                            + "' or whitespace, was: " + value);
        }
        return normalized;
    }
}
