package uhseojupjup.backend.keyword.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uhseojupjup.backend.common.exception.BusinessException;
import uhseojupjup.backend.common.exception.ErrorCode;
import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.keyword.infra.KeywordRepository;
import uhseojupjup.backend.topic.domain.Topic;
import uhseojupjup.backend.topic.domain.TopicKeyword;
import uhseojupjup.backend.topic.infra.TopicKeywordRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordService {

    private final KeywordRepository keywordRepository;
    private final TopicKeywordRepository topicKeywordRepository;

    public List<Keyword> search(String query, Long topicId) {
        if (topicId != null) {
            return searchWithinTopic(topicId, query);
        }
        if (!StringUtils.hasText(query)) {
            return keywordRepository.findAllByOrderByNameAsc();
        }
        return keywordRepository.searchByNameOrAlias(query.trim());
    }

    private List<Keyword> searchWithinTopic(Long topicId, String query) {
        List<Keyword> topicKeywords = topicKeywordRepository.findWithKeywordByTopicId(topicId).stream()
                .map(TopicKeyword::getKeyword)
                .toList();
        if (!StringUtils.hasText(query)) {
            return topicKeywords;
        }
        String normalized = query.trim().toLowerCase();
        return topicKeywords.stream()
                .filter(keyword -> keyword.getName().toLowerCase().contains(normalized))
                .toList();
    }

    public KeywordDetailResult getDetail(Long keywordId) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KEYWORD_NOT_FOUND));
        List<Topic> topics = topicKeywordRepository.findWithTopicByKeywordId(keywordId).stream()
                .map(TopicKeyword::getTopic)
                .toList();
        return new KeywordDetailResult(keyword, topics);
    }
}
