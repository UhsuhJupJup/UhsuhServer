package uhsuhjupjup.backend.topic.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.keyword.domain.Keyword;
import uhsuhjupjup.backend.topic.application.dto.TopicDetailResult;
import uhsuhjupjup.backend.topic.domain.Topic;
import uhsuhjupjup.backend.topic.domain.TopicKeyword;
import uhsuhjupjup.backend.topic.infra.TopicKeywordRepository;
import uhsuhjupjup.backend.topic.infra.TopicRepository;

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
