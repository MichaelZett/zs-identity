package de.zettsystems.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One-time token for email verification and password reset.
 *
 * <p>Only the <strong>hash</strong> of the token is stored. The plain text is
 * seen by the recipient of the mail and by nobody else; whoever reads the
 * database cannot reconstruct a valid token from it.
 */
@Entity
@Table(name = "auth_token")
@Getter
public class AuthToken extends AbstractAuthEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_token_seq")
    @SequenceGenerator(name = "auth_token_seq", sequenceName = "auth_token_seq", allocationSize = 20)
    private @Nullable Long id;

    // LAZY: see UserAccount#roles -- the EAGER default produces N+1 queries.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @SuppressWarnings("NullAway.Init") // populated by Hibernate through reflection
    private UserAccount user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    @SuppressWarnings("NullAway.Init")
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @SuppressWarnings("NullAway.Init")
    private AuthTokenType type;

    @Column(name = "expires_at", nullable = false)
    @SuppressWarnings("NullAway.Init")
    private Instant expiresAt;

    @Column(name = "used_at")
    private @Nullable Instant usedAt;

    protected AuthToken() {
        // for JPA
    }

    public AuthToken(UserAccount user, String tokenHash, AuthTokenType type, Instant expiresAt) {
        this.user = Objects.requireNonNull(user, "user");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.type = Objects.requireNonNull(type, "type");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    /** Voids the token. A second visit to the same link then leads nowhere. */
    public void markUsed(Instant at) {
        this.usedAt = Objects.requireNonNull(at, "at");
    }
}
