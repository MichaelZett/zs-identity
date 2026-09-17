package de.zettsystems.identity.values;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScopeTest {

    @Test
    void aScopeReadsAsTypeAndId() {
        assertThat(Scope.of("club", "17")).hasToString("club:17");
        assertThat(Scope.parse("club:17")).isEqualTo(Scope.of("club", "17"));
    }

    /** Eine Kennung darf selbst Doppelpunkte enthalten — getrennt wird am ersten. */
    @Test
    void parsingSplitsAtTheFirstSeparatorOnly() {
        assertThatThrownBy(() -> Scope.parse("club:a:b"))
                .as("ein Doppelpunkt in der Kennung ist verboten, nicht still erlaubt")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textWithoutASeparatorIsNoScope() {
        assertThatThrownBy(() -> Scope.parse("club"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("club");
    }

    /**
     * Der sicherheitsrelevante Teil: Aus Rolle und Bereich wird
     * {@code ROLE_ADMIN@club:17}. Dürfte eine Kennung diese Zeichen tragen,
     * ließe sich damit eine Berechtigung erfinden, die niemand vergeben hat.
     */
    @ParameterizedTest
    @ValueSource(strings = {"4@club", "a:b", "mit leerzeichen", " ", ""})
    void anIdThatCouldForgeAnAuthorityIsRefused(String id) {
        assertThatThrownBy(() -> Scope.of("club", id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cl@ub", "cl:ub", "c ub", ""})
    void theSameHoldsForTheType(String type) {
        assertThatThrownBy(() -> Scope.of(type, "17"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void surroundingSpaceIsTrimmedAwayRatherThanRefused() {
        assertThat(Scope.of(" club ", " 17 ")).isEqualTo(Scope.of("club", "17"));
    }

    @Test
    void anAuthorityNameCarriesTheScopeAtTheEnd() {
        assertThat(ScopedRole.global("ADMIN").authorityName()).isEqualTo("ROLE_ADMIN");
        assertThat(ScopedRole.of("ADMIN", Scope.of("club", "17")).authorityName())
                .isEqualTo("ROLE_ADMIN@club:17");
        assertThat(ScopedRole.of("ADMIN", Scope.of("club", "17")).qualify("season:read"))
                .as("auch feingranulare Berechtigungen gelten nur in ihrem Bereich")
                .isEqualTo("season:read@club:17");
    }
}
