package uhsuhjupjup.backend.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import uhsuhjupjup.backend.learningnote.application.dto.GraphEdge;
import uhsuhjupjup.backend.learningnote.application.dto.GraphNode;
import uhsuhjupjup.backend.learningnote.application.dto.NoteGraphResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheValueSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void noteGraphResult_typedSerializer_roundTrip() {
        Jackson2JsonRedisSerializer<NoteGraphResult> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, NoteGraphResult.class);

        NoteGraphResult original = new NoteGraphResult(
                List.of(new GraphNode("kw:1", "keyword", "Spring", false, null, 42L),
                        new GraphNode("kw:2", "keyword", "JPA", false, 3, 7L)),
                List.of(new GraphEdge("kw:1", "kw:2", 5L)));

        NoteGraphResult back = serializer.deserialize(serializer.serialize(original));

        assertThat(back).isEqualTo(original);
        assertThat(back.nodes().get(0).weight()).isInstanceOf(Long.class);
    }

    @Test
    void listOfLong_typedSerializer_roundTrip() {
        JavaType listOfLong = mapper.getTypeFactory().constructCollectionType(List.class, Long.class);
        Jackson2JsonRedisSerializer<List<Long>> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, listOfLong);

        List<Long> original = List.of(1L, 2L, 9_999_999_999L);

        List<Long> back = serializer.deserialize(serializer.serialize(original));

        assertThat(back).isEqualTo(original);
        assertThat(back).allSatisfy(e -> assertThat((Object) e).isInstanceOf(Long.class));
    }
}
