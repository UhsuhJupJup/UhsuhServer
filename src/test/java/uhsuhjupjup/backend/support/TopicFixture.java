package uhsuhjupjup.backend.support;

import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.topic.domain.Topic;

public final class TopicFixture {

    private TopicFixture() {
    }

    public static Topic topic(Long id, String name) {
        Topic topic = Topic.create(name);
        ReflectionTestUtils.setField(topic, "id", id);
        return topic;
    }
}
