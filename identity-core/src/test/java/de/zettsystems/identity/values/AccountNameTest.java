package de.zettsystems.identity.values;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountNameTest {

    @Test
    void aFullNameBecomesTheDisplayName() {
        AccountName name = AccountName.of("Anna", "Beispiel");

        assertThat(name.displayName()).isEqualTo("Anna Beispiel");
        assertThat(name.firstName()).isEqualTo("Anna");
        assertThat(name.lastName()).isEqualTo("Beispiel");
        assertThat(name.hasFullName()).isTrue();
    }

    @Test
    void aDisplayNameStandsAlone() {
        AccountName name = AccountName.display("Sternenflotte");

        assertThat(name.displayName()).isEqualTo("Sternenflotte");
        assertThat(name.firstName()).isNull();
        assertThat(name.lastName()).isNull();
        assertThat(name.hasFullName()).isFalse();
    }

    @Test
    void surroundingSpacesAreDropped() {
        AccountName name = AccountName.of("  Anna  ", "  Beispiel  ");

        assertThat(name.displayName()).isEqualTo("Anna Beispiel");
        assertThat(name.firstName()).isEqualTo("Anna");
    }

    /** Blank input becomes {@code null}; otherwise an empty string would sit in the database. */
    @Test
    void blankPartsBecomeNull() {
        AccountName name = new AccountName("Anna", "   ", "");

        assertThat(name.firstName()).isNull();
        assertThat(name.lastName()).isNull();
        assertThat(name.hasFullName()).isFalse();
    }

    @Test
    void oneMissingHalfIsNoFullName() {
        assertThat(new AccountName("Anna", "Anna", null).hasFullName()).isFalse();
        assertThat(new AccountName("Beispiel", null, "Beispiel").hasFullName()).isFalse();
    }

    @Test
    void aBlankDisplayNameIsRefused() {
        assertThatThrownBy(() -> new AccountName("   ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayName");
    }

    @Test
    void aBlankFirstOrLastNameIsRefused() {
        assertThatThrownBy(() -> AccountName.of("  ", "Beispiel"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firstName");
        assertThatThrownBy(() -> AccountName.of("Anna", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastName");
    }

    @Test
    void aMissingDisplayNameIsRefused() {
        assertThatThrownBy(() -> new AccountName(null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
