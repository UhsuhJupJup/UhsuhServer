package uhsuhjupjup.backend.matching.domain;

import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.keyword.domain.KeywordAlias;
import uhsuhjupjup.backend.support.KeywordFixture;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MatchCatalogTest {

    @Test
    void from_lowercasesNames_andGroupsAliases() {
        List<Keyword> keywords = List.of(KeywordFixture.keyword(1L, "MySQL"), KeywordFixture.keyword(2L, "React"));
        List<KeywordAlias> aliases = List.of(
                KeywordAlias.create(1L, "db"),
                KeywordAlias.create(2L, "리액트"),
                KeywordAlias.create(2L, "ReactJS"));

        MatchCatalog catalog = MatchCatalog.from(keywords, aliases);

        assertThat(catalog.targets())
                .extracting(MatchTarget::keywordId, MatchTarget::lowerName)
                .containsExactly(tuple(1L, "mysql"), tuple(2L, "react"));
        MatchTarget react = catalog.targets().stream()
                .filter(target -> target.keywordId().equals(2L)).findFirst().orElseThrow();
        assertThat(react.lowerAliases()).containsExactlyInAnyOrder("리액트", "reactjs");
    }

    @Test
    void from_keywordWithoutAliases_hasEmptyAliasSet() {
        MatchCatalog catalog = MatchCatalog.from(List.of(KeywordFixture.keyword(1L, "Rust")), List.of());

        assertThat(catalog.targets().get(0).lowerAliases()).isEmpty();
    }
}
