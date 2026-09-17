package de.zettsystems.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Speichert eine {@link Locale} als BCP-47-Sprachkennzeichen ({@code de-DE}).
 *
 * <p>Bewusst ein eigener Konverter statt Hibernates eingebauter Abbildung: Die
 * schreibt die ältere Gestalt mit Unterstrich ({@code de_DE}), und der Wert
 * verlässt den Baustein — in {@code UserAccountDto} und damit in Code, den
 * dieser Baustein nicht kennt. Ein Sprachkennzeichen nach RFC 5646 ist das,
 * was HTTP, HTML und JavaScript ohnehin verwenden.
 *
 * <p><strong>Nicht</strong> {@code autoApply}: Sonst griffe er auch auf
 * {@code Locale}-Felder der einbindenden Anwendung über — der Baustein hat
 * über deren Abbildung nicht zu entscheiden. Er hängt deshalb einzeln am Feld.
 */
@Converter
public class LocaleAttributeConverter implements AttributeConverter<Locale, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable Locale attribute) {
        return attribute == null ? null : attribute.toLanguageTag();
    }

    @Override
    public @Nullable Locale convertToEntityAttribute(@Nullable String dbData) {
        // Locale#forLanguageTag wirft nie, es liefert bei Unsinn Locale.ROOT.
        // Ein leeres Sprachkennzeichen ist keine Sprache — dann gilt wieder
        // die Voreinstellung der Anwendung.
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        Locale locale = Locale.forLanguageTag(dbData);
        return "und".equals(locale.toLanguageTag()) ? null : locale;
    }
}
