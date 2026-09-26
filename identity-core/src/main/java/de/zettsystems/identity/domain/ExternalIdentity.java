package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.IdentitySchema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * An identity at an external provider (Google, GitHub, a company's Keycloak)
 * linked to an account, since V1_8.
 *
 * <p>The sign-in looks up by {@link #registrationId} and {@link #subject}
 * alone. The address the provider reports is kept for display, never for
 * finding the account again: addresses change, and some providers let people
 * change theirs to one they do not own.
 *
 * <p>Created and removed through the account ({@link UserAccount#linkExternalIdentity},
 * {@link UserAccount#unlinkExternalIdentity}), which owns its identities the
 * way it owns its role assignments.
 */
@Entity
@Table(name = "auth_external_identity", schema = IdentitySchema.NAME)
@Getter
public class ExternalIdentity extends AbstractAuthEntity {

    /** Wide enough for any registration id an application would write into its configuration. */
    public static final int REGISTRATION_ID_MAX_LENGTH = 64;
    /** OIDC allows up to 255 ASCII characters for a subject. */
    public static final int SUBJECT_MAX_LENGTH = 255;
    private static final int EMAIL_MAX_LENGTH = 320;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_external_identity_seq")
    @SequenceGenerator(name = "auth_external_identity_seq", sequenceName = "auth_external_identity_seq",
            schema = IdentitySchema.NAME, allocationSize = 20)
    private @Nullable Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @SuppressWarnings("NullAway.Init") // populated by Hibernate through reflection
    private UserAccount user;

    @Column(name = "registration_id", nullable = false, length = REGISTRATION_ID_MAX_LENGTH)
    @SuppressWarnings("NullAway.Init")
    private String registrationId;

    @Column(nullable = false, length = SUBJECT_MAX_LENGTH)
    @SuppressWarnings("NullAway.Init")
    private String subject;

    /** What the provider reported at the last sign-in; for display only. */
    @Column(length = EMAIL_MAX_LENGTH)
    private @Nullable String email;

    @Column(name = "created_at", nullable = false)
    @SuppressWarnings("NullAway.Init")
    private Instant createdAt;

    @Column(name = "last_used_at")
    private @Nullable Instant lastUsedAt;

    protected ExternalIdentity() {
        // for JPA
    }

    ExternalIdentity(UserAccount user, String registrationId, String subject, @Nullable String email,
                     Instant createdAt) {
        this.user = Objects.requireNonNull(user, "user");
        this.registrationId = requireLength(registrationId, "registrationId", REGISTRATION_ID_MAX_LENGTH);
        this.subject = requireLength(subject, "subject", SUBJECT_MAX_LENGTH);
        this.email = normalize(email);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Whether this is the identity a provider just named. */
    public boolean matches(String otherRegistrationId, String otherSubject) {
        return registrationId.equals(otherRegistrationId) && subject.equals(otherSubject);
    }

    /** A successful sign-in: the address the provider reports now, and when. */
    public void recordUse(@Nullable String reportedEmail, Instant at) {
        this.email = normalize(reportedEmail);
        this.lastUsedAt = Objects.requireNonNull(at, "at");
    }

    /** Lower case like the account's own address; cut to the column, since it is for display only. */
    private static @Nullable String normalize(@Nullable String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = UserAccount.normalizeEmail(email);
        return normalized.length() <= EMAIL_MAX_LENGTH ? normalized : normalized.substring(0, EMAIL_MAX_LENGTH);
    }

    private static String requireLength(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "%s must be 1 to %d characters, was %d".formatted(field, maxLength, value.length()));
        }
        return value;
    }
}
