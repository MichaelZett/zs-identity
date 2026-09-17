package de.zettsystems.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Stores a {@link Locale} as a BCP 47 language tag ({@code de-DE}).
 *
 * <p>Deliberately a converter of its own instead of Hibernate's built-in
 * mapping: that one writes the older underscore form ({@code de_DE}), and the
 * value leaves this building block -- through {@code UserAccountDto} and thus
 * into code this block knows nothing about. A language tag per RFC 5646 is
 * what HTTP, HTML and JavaScript use anyway.
 *
 * <p><strong>Not</strong> {@code autoApply}: otherwise it would also reach
 * {@code Locale} fields of the embedding application, and this building block
 * has no business deciding how those are mapped. It is attached to the single
 * field instead.
 */
@Converter
public class LocaleAttributeConverter implements AttributeConverter<Locale, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable Locale attribute) {
        return attribute == null ? null : attribute.toLanguageTag();
    }

    @Override
    public @Nullable Locale convertToEntityAttribute(@Nullable String dbData) {
        // Locale#forLanguageTag never throws; for nonsense it returns Locale.ROOT.
        // An empty language tag is not a language, so the application's default
        // applies again.
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        Locale locale = Locale.forLanguageTag(dbData);
        return "und".equals(locale.toLanguageTag()) ? null : locale;
    }
}
