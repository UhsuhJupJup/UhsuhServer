package uhseojupjup.backend.topic.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhseojupjup.backend.topic.domain.Topic;
import uhseojupjup.backend.topic.infra.TopicRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;

    public List<Topic> findAll() {
        return topicRepository.findAllByOrderByIdAsc();
    }
}
