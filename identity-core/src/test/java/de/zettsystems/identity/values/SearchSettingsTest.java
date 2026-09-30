package de.zettsystems.identity.values;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchSettingsTest {

    @Test
    void theDefaultIsTheCCollation() {
        assertThat(SearchSettings.defaults().collation()).isEqualTo("C");
    }

    @Test
    void aGermanIcuCollationIsAccepted() {
        SearchSettings settings = new SearchSettings("de-DE-x-icu");

        assertThat(settings.hasCollation()).isTrue();
        assertThat(settings.collation()).isEqualTo("de-DE-x-icu");
    }

    @Test
    void blankLeavesItToTheDatabase() {
        assertThat(new SearchSettings("  ").hasCollation()).isFalse();
        assertThat(new SearchSettings(null).hasCollation()).isFalse();
    }

    @Test
    void aNameThatCouldBreakOutOfTheStatementIsRefused() {
        assertThatThrownBy(() -> new SearchSettings("C\"; drop table x; --"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zs.identity.search.collation");
        assertThatThrownBy(() -> new SearchSettings("de DE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theOldShapeOfTheSettingsKeepsTheDefaultCollation() {
        IdentityProperties properties = IdentityProperties.defaults();

        assertThat(properties.search()).isEqualTo(SearchSettings.defaults());
        assertThat(properties.withSearch(new SearchSettings("de-DE-x-icu")).search().collation())
                .isEqualTo("de-DE-x-icu");
        assertThat(properties.withSearch(new SearchSettings("de-DE-x-icu")).withLocale(Locale.ENGLISH)
                .search().collation()).isEqualTo("de-DE-x-icu");
    }

    @Test
    void anAccountQueryNormalisesBlanksAndCopiesTheIds() {
        AccountQuery query = new AccountQuery("  ", null, null, null, " ", Set.of(), Set.of());

        assertThat(query.text()).isNull();
        assertThat(query.roleCode()).isNull();
        // Empty userIds stays empty (nobody); empty excludeUserIds is no restriction.
        assertThat(query.userIds()).isEmpty();
        assertThat(query.excludeUserIds()).isNull();
        assertThat(AccountQuery.all().withText(" anna ").text()).isEqualTo("anna");
    }

    @Test
    void theAccountQueryOfOneDotThreeStillBuildsAndKnowsNothingOfPending() {
        AccountQuery old = new AccountQuery("anna", true, null, null, "ADMIN", Set.of(1L), Set.of(2L));

        assertThat(old.pending()).isNull();
        assertThat(old.withPending(true))
                .isEqualTo(new AccountQuery("anna", true, null, null, "ADMIN", Set.of(1L), Set.of(2L), true));
        // Every other with-method carries it along.
        AccountQuery changed = old.withPending(true).withText("bert").withManaged(null).withInvitationOpen(false)
                .withAwaitingConfirmation(false).withRoleCode(null).withUserIds(null).withExcludeUserIds(null);
        assertThat(changed).isEqualTo(new AccountQuery("bert", null, false, false, null, null, null, true));
    }
}
