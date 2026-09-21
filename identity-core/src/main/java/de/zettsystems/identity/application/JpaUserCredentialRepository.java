package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.Passkey;
import de.zettsystems.identity.domain.PasskeyCredential;
import de.zettsystems.identity.domain.PasskeyRepository;
import de.zettsystems.identity.domain.UserAccount;
import de.zettsystems.identity.domain.UserAccountRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Spring Security's view of the passkeys, backed by the {@code auth_passkey}
 * table instead of Spring's own {@code user_credentials}.
 *
 * <p>Own tables rather than {@code JdbcUserCredentialRepository}, because the
 * passkey has to hang off the account: a foreign key with cascade, the
 * account id for the application, one schema and one migration run. Spring's
 * tables know only the opaque user handle.
 *
 * <p>Spring calls {@link #save} twice in a passkey's life: once with the new
 * record after registration, and on every sign-in with the same record
 * carrying a new signature counter and time of use. The two are told apart
 * by whether the credential id is known.
 */
class JpaUserCredentialRepository implements UserCredentialRepository {

    private final PasskeyRepository passkeys;
    private final UserAccountRepository users;
    private final Clock clock;

    JpaUserCredentialRepository(PasskeyRepository passkeys, UserAccountRepository users, Clock clock) {
        this.passkeys = passkeys;
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void delete(Bytes credentialId) {
        passkeys.deleteByCredentialId(credentialId.toBase64UrlString());
    }

    @Override
    @Transactional
    public void save(CredentialRecord record) {
        String credentialId = record.getCredentialId().toBase64UrlString();
        Optional<Passkey> existing = passkeys.findByCredentialId(credentialId);
        if (existing.isPresent()) {
            existing.get().recordUse(record.getSignatureCount(), clock.instant());
            return;
        }
        String handle = record.getUserEntityUserId().toBase64UrlString();
        UserAccount user = users.findByPasskeyUserHandle(handle)
                .orElseThrow(() -> new IllegalStateException("No account for the passkey user handle"));
        passkeys.save(new Passkey(user, toCredential(record), record.getSignatureCount(), record.getLabel(),
                clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable CredentialRecord findByCredentialId(Bytes credentialId) {
        return passkeys.findByCredentialId(credentialId.toBase64UrlString())
                .map(JpaUserCredentialRepository::toRecord)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return passkeys.findAllByUserPasskeyUserHandleOrderByCreatedAtAsc(userId.toBase64UrlString()).stream()
                .map(JpaUserCredentialRepository::toRecord)
                .toList();
    }

    /**
     * Has to run inside the transaction that loaded the passkey: the user
     * handle sits on the lazily loaded account.
     */
    static CredentialRecord toRecord(Passkey passkey) {
        PasskeyCredential credential = passkey.credential();
        String handle = Objects.requireNonNull(passkey.getUser().getPasskeyUserHandle(),
                "an account with a passkey has a user handle");
        return ImmutableCredentialRecord.builder()
                .credentialId(Bytes.fromBase64(credential.credentialId()))
                .credentialType(PublicKeyCredentialType.valueOf(credential.credentialType()))
                .userEntityUserId(Bytes.fromBase64(handle))
                .publicKey(ImmutablePublicKeyCose.fromBase64(credential.publicKeyCose()))
                .signatureCount(passkey.getSignatureCount())
                .uvInitialized(credential.uvInitialized())
                .backupEligible(credential.backupEligible())
                .backupState(credential.backupState())
                .transports(credential.transports().stream()
                        .map(AuthenticatorTransport::valueOf)
                        .collect(Collectors.toUnmodifiableSet()))
                .attestationObject(fromBase64(credential.attestationObject()))
                .attestationClientDataJSON(fromBase64(credential.attestationClientDataJson()))
                .label(passkey.getLabel())
                .created(passkey.getCreatedAt())
                .lastUsed(passkey.getLastUsedAt() != null ? passkey.getLastUsedAt() : passkey.getCreatedAt())
                .build();
    }

    static PasskeyCredential toCredential(CredentialRecord record) {
        Set<String> transports = new HashSet<>();
        for (AuthenticatorTransport transport : record.getTransports()) {
            transports.add(transport.getValue());
        }
        PublicKeyCredentialType type = record.getCredentialType();
        return new PasskeyCredential(
                record.getCredentialId().toBase64UrlString(),
                type != null ? type.getValue() : PublicKeyCredentialType.PUBLIC_KEY.getValue(),
                toBase64(record.getPublicKey().getBytes()),
                record.isUvInitialized(),
                record.isBackupEligible(),
                record.isBackupState(),
                transports,
                toBase64(record.getAttestationObject()),
                toBase64(record.getAttestationClientDataJSON()));
    }

    private static @Nullable Bytes fromBase64(@Nullable String base64Url) {
        return base64Url != null ? Bytes.fromBase64(base64Url) : null;
    }

    private static @Nullable String toBase64(@Nullable Bytes bytes) {
        return bytes != null ? bytes.toBase64UrlString() : null;
    }

    private static String toBase64(byte[] bytes) {
        return new Bytes(bytes).toBase64UrlString();
    }
}
