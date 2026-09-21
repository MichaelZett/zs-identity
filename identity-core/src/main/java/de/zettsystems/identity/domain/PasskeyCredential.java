package de.zettsystems.identity.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * What the authenticator handed over when a passkey was created, in the shape
 * the building block stores it: every binary part as a base64url string.
 *
 * <p>Strings rather than byte arrays on purpose. The values travel through
 * JSON as base64url anyway, a record must not carry arrays (they break its
 * equality), and a text column needs no defensive copies.
 *
 * @param credentialId             the id the authenticator chose for the
 *                                 passkey; unique across all accounts
 * @param credentialType           {@code public-key}, kept for the record
 * @param publicKeyCose            the public key in COSE encoding
 * @param uvInitialized            whether the authenticator verified the
 *                                 person (Face ID, PIN) when creating the key
 * @param backupEligible           whether the passkey may be synced between
 *                                 devices
 * @param backupState              whether it currently is
 * @param transports               how the authenticator can be reached
 *                                 ({@code internal}, {@code hybrid}, ...)
 * @param attestationObject        the attestation as sent by the browser;
 *                                 needed again for every sign-in
 * @param attestationClientDataJson the client data of the creation
 */
public record PasskeyCredential(String credentialId,
                                String credentialType,
                                String publicKeyCose,
                                boolean uvInitialized,
                                boolean backupEligible,
                                boolean backupState,
                                Set<String> transports,
                                @Nullable String attestationObject,
                                @Nullable String attestationClientDataJson) {

    public PasskeyCredential {
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(credentialType, "credentialType");
        Objects.requireNonNull(publicKeyCose, "publicKeyCose");
        Objects.requireNonNull(transports, "transports");
        transports = Set.copyOf(transports);
    }
}
