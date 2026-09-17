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

    /** An identifier may contain colons itself; the split happens at the first one. */
    @Test
    void parsingSplitsAtTheFirstSeparatorOnly() {
        assertThatThrownBy(() -> Scope.parse("club:a:b"))
                .as("a colon in the identifier is forbidden, not silently allowed")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textWithoutASeparatorIsNoScope() {
        assertThatThrownBy(() -> Scope.parse("club"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("club");
    }

    /**
     * The security-relevant part: role and scope are combined into
     * {@code ROLE_ADMIN@club:17}. If an identifier were allowed to carry these
     * characters, one could invent a permission nobody granted.
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
                .as("fine-grained permissions apply only in their scope too")
                .isEqualTo("season:read@club:17");
    }
}
