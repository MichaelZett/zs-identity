package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.IdentitySchema;
import de.zettsystems.identity.values.PasskeyDto;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A passkey (WebAuthn credential) registered for an account.
 *
 * <p>The account is the owner: the row hangs off {@code auth_user} with
 * {@code ON DELETE CASCADE}, so deleting an account takes its passkeys with
 * it. The credential id is unique across all accounts, because an
 * authenticator identifies itself by it alone when signing in; only after
 * the signature has been checked does the account come into play.
 *
 * <p>What changes after creation is small: the signature counter and the
 * time of last use, both written on every sign-in ({@link #recordUse}).
 * Everything else is the authenticator's word and stays as it was.
 */
@Entity
@Table(name = "auth_passkey", schema = IdentitySchema.NAME)
@Getter
public class Passkey extends AbstractAuthEntity {

    /** Labels longer than this are cut; the column is that wide, and the views limit their field to it. */
    public static final int LABEL_MAX_LENGTH = PasskeyDto.LABEL_MAX_LENGTH;

    private static final String TRANSPORT_SEPARATOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "auth_passkey_seq")
    @SequenceGenerator(name = "auth_passkey_seq", sequenceName = "auth_passkey_seq", schema = IdentitySchema.NAME, allocationSize = 20)
    private @Nullable Long id;

    // LAZY: the sign-in path needs the credential first and the account only
    // once the signature is good; the list view needs the account not at all.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @SuppressWarnings("NullAway.Init") // populated by Hibernate through reflection
    private UserAccount user;

    @Column(name = "credential_id", nullable = false, unique = true, length = 1024)
    @SuppressWarnings("NullAway.Init")
    private String credentialId;

    @Column(name = "credential_type", nullable = false, length = 32)
    @SuppressWarnings("NullAway.Init")
    private String credentialType;

    @Column(name = "public_key_cose", nullable = false)
    @SuppressWarnings("NullAway.Init")
    private String publicKeyCose;

    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(name = "uv_initialized", nullable = false)
    private boolean uvInitialized;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible;

    @Column(name = "backup_state", nullable = false)
    private boolean backupState;

    /** Comma-separated; there are at most a handful of them and nobody queries by transport. */
    @Column(length = 255)
    private @Nullable String transports;

    @Column(name = "attestation_object")
    private @Nullable String attestationObject;

    @Column(name = "attestation_client_data_json")
    private @Nullable String attestationClientDataJson;

    @Column(nullable = false, length = LABEL_MAX_LENGTH)
    @SuppressWarnings("NullAway.Init")
    private String label;

    @Column(name = "created_at", nullable = false)
    @SuppressWarnings("NullAway.Init")
    private Instant createdAt;

    @Column(name = "last_used_at")
    private @Nullable Instant lastUsedAt;

    protected Passkey() {
        // for JPA
    }

    /**
     * @param signatureCount the counter the authenticator reported at
     *                       creation; every sign-in has to report a higher one
     *                       or the same, otherwise the key was cloned
     */
    public Passkey(UserAccount user, PasskeyCredential credential, long signatureCount, String label,
                   Instant createdAt) {
        this.user = Objects.requireNonNull(user, "user");
        Objects.requireNonNull(credential, "credential");
        this.credentialId = credential.credentialId();
        this.credentialType = credential.credentialType();
        this.publicKeyCose = credential.publicKeyCose();
        this.uvInitialized = credential.uvInitialized();
        this.backupEligible = credential.backupEligible();
        this.backupState = credential.backupState();
        this.transports = credential.transports().isEmpty()
                ? null
                : String.join(TRANSPORT_SEPARATOR, credential.transports());
        this.attestationObject = credential.attestationObject();
        this.attestationClientDataJson = credential.attestationClientDataJson();
        this.signatureCount = signatureCount;
        this.label = shorten(Objects.requireNonNull(label, "label"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** The credential as it was handed over at creation. */
    public PasskeyCredential credential() {
        return new PasskeyCredential(credentialId, credentialType, publicKeyCose, uvInitialized, backupEligible,
                backupState, transportSet(), attestationObject, attestationClientDataJson);
    }

    /** The transports as a set; empty when the authenticator named none. */
    public Set<String> transportSet() {
        if (transports == null || transports.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(transports.split(TRANSPORT_SEPARATOR)));
    }

    /** A successful sign-in: the counter the authenticator reported, and when. */
    public void recordUse(long newSignatureCount, Instant at) {
        this.signatureCount = newSignatureCount;
        this.lastUsedAt = Objects.requireNonNull(at, "at");
    }

    private static String shorten(String text) {
        String trimmed = text.strip();
        return trimmed.length() <= LABEL_MAX_LENGTH ? trimmed : trimmed.substring(0, LABEL_MAX_LENGTH);
    }
}
