package uhsuhjupjup.backend.keyword.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uhsuhjupjup.backend.article.application.dto.KeywordNeighborhoodResult;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.config.CacheConfig;
import uhsuhjupjup.backend.keyword.application.dto.KeywordGraphResult;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = {CacheConfig.class, KeywordGraphProvider.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1"})
@ImportAutoConfiguration(RedisAutoConfiguration.class)
class KeywordGraphProviderRedisDownTest {

    @Autowired
    private KeywordGraphProvider provider;

    @MockitoBean
    private KeywordService keywordService;

    @Test
    void Redis가_안_되면_DB에서_계산해_응답하고_다음_요청은_로컬_캐시에서_응답한다() {
        KeywordGraphResult graph = new KeywordGraphResult(3L, "MySQL", new KeywordNeighborhoodResult(0, List.of(), List.of()));
        given(keywordService.getGraph(3L)).willReturn(graph);

        long started = System.nanoTime();
        assertThatCode(() -> {
            assertThat(provider.keywordGraph(3L)).isEqualTo(graph);
            assertThat(provider.keywordGraph(3L)).isEqualTo(graph);
        }).doesNotThrowAnyException();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        verify(keywordService, times(1)).getGraph(3L);
    }

    @Test
    void Redis가_안_되어도_없는_키워드는_404_오류를_그대로_던진다() {
        given(keywordService.getGraph(99L)).willThrow(new BusinessException(ErrorCode.KEYWORD_NOT_FOUND));

        assertThatThrownBy(() -> provider.keywordGraph(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.KEYWORD_NOT_FOUND));
    }
}
