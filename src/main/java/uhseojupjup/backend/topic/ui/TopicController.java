package uhseojupjup.backend.topic.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhseojupjup.backend.topic.application.TopicService;
import uhseojupjup.backend.topic.ui.dto.TopicResponse;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping
    public List<TopicResponse> list() {
        return topicService.findAll().stream()
                .map(TopicResponse::from)
                .toList();
    }
}
