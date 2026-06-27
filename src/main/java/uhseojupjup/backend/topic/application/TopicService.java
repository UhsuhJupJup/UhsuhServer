package uhseojupjup.backend.topic.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhseojupjup.backend.common.exception.BusinessException;
import uhseojupjup.backend.common.exception.ErrorCode;
import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.topic.domain.Topic;
import uhseojupjup.backend.topic.domain.TopicKeyword;
import uhseojupjup.backend.topic.infra.TopicKeywordRepository;
import uhseojupjup.backend.topic.infra.TopicRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicKeywordRepository topicKeywordRepository;

    public List<Topic> findAll() {
        return topicRepository.findAllByOrderByIdAsc();
    }

    public TopicDetailResult getDetail(Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOPIC_NOT_FOUND));
        List<Keyword> keywords = topicKeywordRepository.findWithKeywordByTopicId(topicId).stream()
                .map(TopicKeyword::getKeyword)
                .toList();
        return new TopicDetailResult(topic, keywords);
    }
}
