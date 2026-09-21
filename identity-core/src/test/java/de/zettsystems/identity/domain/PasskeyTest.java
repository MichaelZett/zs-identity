package de.zettsystems.identity.domain;

import de.zettsystems.identity.values.AccountName;
import de.zettsystems.identity.values.PasskeyDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasskeyTest {

    private static final Instant CREATED = Instant.parse("2026-09-21T10:00:00Z");

    private static UserAccount account() {
        return new UserAccount("anna@example.com", "hash", AccountName.of("Anna", "Beispiel"), CREATED);
    }

    private static PasskeyCredential credential(Set<String> transports) {
        return new PasskeyCredential("Y3JlZA", "public-key", "cHVibGlj", true, true, false, transports,
                "YXR0", "Y2xpZW50");
    }

    @Test
    void theCredentialComesBackAsItWentIn() {
        Passkey passkey = new Passkey(account(), credential(Set.of("internal", "hybrid")), 3, "iPhone", CREATED);

        assertThat(passkey.credential()).isEqualTo(credential(Set.of("internal", "hybrid")));
        assertThat(passkey.getSignatureCount()).isEqualTo(3);
        assertThat(passkey.getLabel()).isEqualTo("iPhone");
        assertThat(passkey.getLastUsedAt()).as("never used yet").isNull();
    }

    @Test
    void noTransportsAreStoredAsNothingAndReadAsEmpty() {
        Passkey passkey = new Passkey(account(), credential(Set.of()), 0, "Laptop", CREATED);

        assertThat(passkey.getTransports()).isNull();
        assertThat(passkey.transportSet()).isEmpty();
    }

    @Test
    void aSignInMovesTheCounterAndTheTimeOfUse() {
        Passkey passkey = new Passkey(account(), credential(Set.of("internal")), 3, "iPhone", CREATED);
        Instant used = CREATED.plusSeconds(3600);

        passkey.recordUse(4, used);

        assertThat(passkey.getSignatureCount()).isEqualTo(4);
        assertThat(passkey.getLastUsedAt()).isEqualTo(used);
    }

    /** A label is for a list; nobody reads a longer one, and the column has a limit. */
    @Test
    void aLongLabelIsCutAndAPaddedOneTrimmed() {
        String tooLong = "x".repeat(PasskeyDto.LABEL_MAX_LENGTH + 20);

        Passkey cut = new Passkey(account(), credential(Set.of()), 0, tooLong, CREATED);
        Passkey trimmed = new Passkey(account(), credential(Set.of()), 0, "  iPhone  ", CREATED);

        assertThat(cut.getLabel()).hasSize(PasskeyDto.LABEL_MAX_LENGTH);
        assertThat(trimmed.getLabel()).isEqualTo("iPhone");
    }

    @Test
    void theUserHandleIsAssignedOnceAndNeverReplaced() {
        UserAccount account = account();
        assertThat(account.getPasskeyUserHandle()).isNull();

        account.assignPasskeyUserHandle("aGFuZGxl");
        account.assignPasskeyUserHandle("aGFuZGxl");
        assertThat(account.getPasskeyUserHandle()).isEqualTo("aGFuZGxl");

        assertThatThrownBy(() -> account.assignPasskeyUserHandle("YW5kZXJl"))
                .as("the registered passkeys point at the old handle")
                .isInstanceOf(IllegalStateException.class);

        account.withdrawPasskeyUserHandle();
        assertThat(account.getPasskeyUserHandle()).isNull();
    }
}
