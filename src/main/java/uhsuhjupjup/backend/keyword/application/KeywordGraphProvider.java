package uhsuhjupjup.backend.keyword.application;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import uhsuhjupjup.backend.config.CacheConfig;
import uhsuhjupjup.backend.keyword.application.dto.KeywordGraphResult;

@Component
@RequiredArgsConstructor
public class KeywordGraphProvider {

    private final KeywordService keywordService;

    @Cacheable(cacheNames = CacheConfig.KEYWORD_GRAPH, key = "#keywordId", sync = true)
    public KeywordGraphResult keywordGraph(Long keywordId) {
        return keywordService.getGraph(keywordId);
    }
}
