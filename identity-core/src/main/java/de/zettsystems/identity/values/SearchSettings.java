package de.zettsystems.identity.values;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.regex.Pattern;

/**
 * Settings of the account search ({@code UserAccountService#search}), prefix
 * {@code zs.identity.search}.
 *
 * <p>How names sort and how "contains, ignoring case" is decided depends on
 * the <strong>collation</strong> of the database, and that is rarely what an
 * application expects: a PostgreSQL in a container often runs with {@code C}
 * or {@code en_US}, where {@code Ärztin} sorts behind {@code Zander}. The
 * search therefore names its collation itself instead of trusting whatever the
 * database was created with.
 *
 * @param collation the PostgreSQL collation the search sorts and compares
 *                  names with. Defaults to {@code C}, byte order and case
 *                  folding for ASCII only, which every database has; an
 *                  application that keeps German names sets
 *                  {@code de-DE-x-icu} (needs a PostgreSQL built with ICU;
 *                  {@code select collname from pg_collation} lists what is
 *                  there). Blank means "the collation of the database". The
 *                  name is put into the statement, so it may consist of
 *                  letters, digits and {@code _ . @ -} only.
 */
public record SearchSettings(@DefaultValue("C") String collation) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.@-]+");

    public SearchSettings {
        collation = collation == null ? "" : collation.trim();
        if (!collation.isEmpty() && !NAME.matcher(collation).matches()) {
            throw new IllegalArgumentException(
                    "zs.identity.search.collation may contain letters, digits and _ . @ - only, was '%s'"
                            .formatted(collation));
        }
    }

    /** The settings of an application that configures nothing. */
    public static SearchSettings defaults() {
        return new SearchSettings("C");
    }

    /** Whether the search names a collation; otherwise the database decides. */
    public boolean hasCollation() {
        return !collation.isEmpty();
    }
}
