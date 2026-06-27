package uhseojupjup.backend.keyword.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.keyword.infra.KeywordRepository;
import uhseojupjup.backend.support.KeywordFixture;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KeywordServiceTest {

    @Mock
    private KeywordRepository keywordRepository;

    @InjectMocks
    private KeywordService keywordService;

    @Test
    void search_withBlankQuery_returnsAll() {
        List<Keyword> all = List.of(KeywordFixture.keyword(1L, "Kafka"), KeywordFixture.keyword(2L, "Redis"));
        given(keywordRepository.findAllByOrderByNameAsc()).willReturn(all);

        assertThat(keywordService.search("  ")).isEqualTo(all);
    }

    @Test
    void search_withQuery_returnsFiltered() {
        List<Keyword> filtered = List.of(KeywordFixture.keyword(1L, "Kafka"));
        given(keywordRepository.findByNameContainingIgnoreCaseOrderByNameAsc("ka")).willReturn(filtered);

        assertThat(keywordService.search("ka")).isEqualTo(filtered);
    }
}
