package de.zettsystems.identity.values;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * What an external provider says about the person signing in, reduced to
 * what the building block uses (since 1.2.0).
 *
 * <p>Free of OAuth2 types on purpose: the rules about accounts live in the
 * core and can be tested without a provider. Reading a provider's answer into
 * this shape is the job of the security configurer.
 *
 * @param registrationId the client registration the sign-in came through
 * @param subject        the person's stable id at the provider (OIDC
 *                       {@code sub}, GitHub's numeric user id)
 * @param email          the address the provider reports; {@code null} when
 *                       it reports none
 * @param emailVerified  whether the provider vouches for the address. Only
 *                       then does the address count for anything.
 * @param givenName      first name, if the provider knows one
 * @param familyName     last name, if the provider knows one
 * @param fullName       the name as a whole ({@code name}), if the provider
 *                       knows one
 * @param locale         the language the provider reports; {@code null} when
 *                       none
 */
public record ExternalIdentityClaims(String registrationId,
                                     String subject,
                                     @Nullable String email,
                                     boolean emailVerified,
                                     @Nullable String givenName,
                                     @Nullable String familyName,
                                     @Nullable String fullName,
                                     @Nullable Locale locale) {

    /** The columns are this wide; a provider may know a longer name, and the sign-in must not fail on it. */
    private static final int NAME_PART_MAX_LENGTH = 128;
    private static final int DISPLAY_NAME_MAX_LENGTH = 260;

    public ExternalIdentityClaims {
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(subject, "subject");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        email = blankToNull(email);
        givenName = blankToNull(givenName);
        familyName = blankToNull(familyName);
        fullName = blankToNull(fullName);
    }

    /** The address, but only if the provider vouches for it. */
    public @Nullable String verifiedEmail() {
        return emailVerified ? email : null;
    }

    /**
     * The name for a new account, in the shape the application asks for:
     * first and last name where it keeps real names and the provider has
     * both, otherwise a display name from whatever the provider knows -- the
     * full name, the parts, or the part of the address before the {@code @}.
     */
    public AccountName accountName(NameMode nameMode) {
        if (nameMode == NameMode.FULL_NAME && givenName != null && familyName != null) {
            return AccountName.of(cut(givenName, NAME_PART_MAX_LENGTH), cut(familyName, NAME_PART_MAX_LENGTH));
        }
        if (fullName != null) {
            return display(fullName);
        }
        if (givenName != null || familyName != null) {
            return display(((givenName != null ? givenName : "") + " "
                    + (familyName != null ? familyName : "")).strip());
        }
        if (email != null) {
            int at = email.indexOf('@');
            return display(at > 0 ? email.substring(0, at) : email);
        }
        return display(subject);
    }

    private static AccountName display(String name) {
        return AccountName.display(cut(name, DISPLAY_NAME_MAX_LENGTH));
    }

    private static String cut(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength).strip();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
