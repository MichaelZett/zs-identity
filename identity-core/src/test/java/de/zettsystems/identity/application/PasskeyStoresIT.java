package de.zettsystems.identity.application;

import de.zettsystems.identity.domain.PasskeyRepository;
import de.zettsystems.identity.domain.UserAccountRepository;
import de.zettsystems.identity.testsupport.AbstractIdentityIntegrationTest;
import de.zettsystems.identity.testsupport.MutableTestClock;
import de.zettsystems.identity.values.IdentityMessageKeys;
import de.zettsystems.identity.values.PasskeyDto;
import de.zettsystems.identity.values.UserAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The passkey stores the way Spring Security's WebAuthn filters drive them,
 * against real PostgreSQL: the user handle on the account, the credential
 * round trip, the update on sign-in, and what the {@link PasskeyService}
 * shows an application of it.
 */
class PasskeyStoresIT extends AbstractIdentityIntegrationTest {

    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private PasskeyService passkeyService;
    @Autowired
    private UserCredentialRepository credentials;
    @Autowired
    private PublicKeyCredentialUserEntityRepository userEntities;
    @Autowired
    private PasskeyRepository passkeyRepository;
    @Autowired
    private UserAccountRepository userRepository;
    @Autowired
    private Clock clock;

    private UserAccountDto anna;

    @BeforeEach
    void anAccountWithoutPasskeys() {
        passkeyRepository.deleteAll();
        userRepository.deleteAll();
        anna = userAccountService.createAccount("anna@example.com", "ein-langes-passwort", "Anna", "Beispiel", true);
    }

    /** Spring asks by name first and saves a fresh handle only when nothing came back. */
    @Test
    void theUserHandleIsCreatedOnceAndFoundBothWays() {
        assertThat(userEntities.findByUsername("anna@example.com")).as("no passkey yet, no handle").isNull();

        PublicKeyCredentialUserEntity fresh = userEntity("anna@example.com", Bytes.random());
        userEntities.save(fresh);

        PublicKeyCredentialUserEntity byName = userEntities.findByUsername("Anna@Example.com");
        assertThat(byName).isNotNull();
        assertThat(byName.getId()).isEqualTo(fresh.getId());
        assertThat(byName.getName()).isEqualTo("anna@example.com");
        assertThat(byName.getDisplayName()).isEqualTo("Anna Beispiel");
        assertThat(userEntities.findById(fresh.getId())).isEqualTo(byName);
        assertThat(userEntities.findById(Bytes.random())).isNull();
    }

    @Test
    void aCredentialSurvivesTheRoundTripThroughTheTable() {
        Bytes handle = handleFor(anna);
        CredentialRecord saved = credentialRecord(handle, Bytes.random(), "iPhone");

        credentials.save(saved);

        CredentialRecord found = credentials.findByCredentialId(saved.getCredentialId());
        assertThat(found).isNotNull();
        assertThat(found.getCredentialId()).isEqualTo(saved.getCredentialId());
        assertThat(found.getUserEntityUserId()).isEqualTo(handle);
        assertThat(found.getPublicKey().getBytes()).isEqualTo(saved.getPublicKey().getBytes());
        assertThat(found.getSignatureCount()).isEqualTo(7);
        assertThat(found.isUvInitialized()).isTrue();
        assertThat(found.isBackupEligible()).isTrue();
        assertThat(found.isBackupState()).isFalse();
        assertThat(found.getTransports()).containsExactlyInAnyOrder(AuthenticatorTransport.INTERNAL,
                AuthenticatorTransport.HYBRID);
        assertThat(found.getAttestationObject()).isEqualTo(saved.getAttestationObject());
        assertThat(found.getAttestationClientDataJSON()).isEqualTo(saved.getAttestationClientDataJSON());
        assertThat(found.getLabel()).isEqualTo("iPhone");
        assertThat(found.getCreated()).isEqualTo(clock.instant());
        assertThat(credentials.findByUserId(handle)).extracting(CredentialRecord::getCredentialId)
                .containsExactly(saved.getCredentialId());
        assertThat(credentials.findByCredentialId(Bytes.random())).isNull();
    }

    /** On every sign-in Spring saves the same record with a new counter; that must not insert a second row. */
    @Test
    void savingAKnownCredentialRecordsTheUseInsteadOfInsertingAgain() {
        Bytes handle = handleFor(anna);
        CredentialRecord saved = credentialRecord(handle, Bytes.random(), "iPhone");
        credentials.save(saved);
        ((MutableTestClock) clock).advanceBy(Duration.ofHours(2));

        credentials.save(ImmutableCredentialRecord.fromCredentialRecord(saved).signatureCount(8).build());

        assertThat(credentials.findByUserId(handle)).hasSize(1);
        CredentialRecord used = credentials.findByCredentialId(saved.getCredentialId());
        assertThat(used).isNotNull();
        assertThat(used.getSignatureCount()).isEqualTo(8);
        assertThat(used.getLastUsed()).isEqualTo(clock.instant());
        assertThat(passkeyService.findAllOf(anna.id())).singleElement()
                .extracting(PasskeyDto::lastUsedAt).isEqualTo(clock.instant());
    }

    @Test
    void whatTheApplicationSeesListCountAndWhoHasOne() {
        UserAccountDto ben = userAccountService.createAccount("ben@example.com", "ein-langes-passwort", "Ben",
                "Beispiel", true);
        Bytes handle = handleFor(anna);
        assertThat(passkeyService.hasPasskey(anna.id())).isFalse();

        credentials.save(credentialRecord(handle, Bytes.random(), "iPhone"));
        ((MutableTestClock) clock).advanceBy(Duration.ofMinutes(1));
        credentials.save(credentialRecord(handle, Bytes.random(), "Laptop"));

        assertThat(passkeyService.countFor(anna.id())).isEqualTo(2);
        assertThat(passkeyService.findAllOf(anna.id())).extracting(PasskeyDto::label)
                .as("oldest first").containsExactly("iPhone", "Laptop");
        assertThat(passkeyService.countFor(ben.id())).isZero();
        assertThat(passkeyService.accountsWithPasskeys(List.of(anna.id(), ben.id(), -1L)))
                .containsExactly(anna.id());
        assertThat(passkeyService.accountsWithPasskeys(List.of())).isEmpty();
    }

    /** A passkey id from somebody else's list is not enough to delete it. */
    @Test
    void deletingChecksTheOwnerAndSpringCanDeleteByCredential() {
        UserAccountDto ben = userAccountService.createAccount("ben@example.com", "ein-langes-passwort", "Ben",
                "Beispiel", true);
        CredentialRecord annas = credentialRecord(handleFor(anna), Bytes.random(), "iPhone");
        credentials.save(annas);
        Long passkeyId = passkeyService.findAllOf(anna.id()).getFirst().id();
        Long benId = ben.id();

        assertThatThrownBy(() -> passkeyService.delete(benId, passkeyId))
                .isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).getMessageKey())
                .isEqualTo(IdentityMessageKeys.PASSKEY_NOT_FOUND);
        assertThat(passkeyService.countFor(anna.id())).isEqualTo(1);

        passkeyService.delete(anna.id(), passkeyId);
        assertThat(passkeyService.countFor(anna.id())).isZero();

        credentials.save(annas);
        credentials.delete(annas.getCredentialId());
        assertThat(credentials.findByCredentialId(annas.getCredentialId())).isNull();
    }

    /** The foreign key does the work; the test proves the schema, not the code. */
    @Test
    void deletingTheAccountTakesItsPasskeysAlong() {
        credentials.save(credentialRecord(handleFor(anna), Bytes.random(), "iPhone"));
        assertThat(passkeyRepository.count()).isEqualTo(1);

        userAccountService.deleteAccount(anna.id());

        assertThat(passkeyRepository.count()).isZero();
    }

    private Bytes handleFor(UserAccountDto account) {
        Bytes handle = Bytes.random();
        userEntities.save(userEntity(account.email(), handle));
        return handle;
    }

    private static PublicKeyCredentialUserEntity userEntity(String email, Bytes handle) {
        return ImmutablePublicKeyCredentialUserEntity.builder().id(handle).name(email).displayName(email).build();
    }

    private static CredentialRecord credentialRecord(Bytes handle, Bytes credentialId, String label) {
        return ImmutableCredentialRecord.builder()
                .userEntityUserId(handle)
                .credentialId(credentialId)
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .publicKey(new ImmutablePublicKeyCose(new byte[] {1, 2, 3, 4}))
                .signatureCount(7)
                .uvInitialized(true)
                .backupEligible(true)
                .backupState(false)
                .transports(Set.of(AuthenticatorTransport.INTERNAL, AuthenticatorTransport.HYBRID))
                .attestationObject(new Bytes(new byte[] {9, 9, 9}))
                .attestationClientDataJSON(new Bytes(new byte[] {8, 8}))
                .label(label)
                .build();
    }
}
