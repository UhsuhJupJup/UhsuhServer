package uhsuhjupjup.backend.support;

import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.keyword.domain.Keyword;

public final class KeywordFixture {

    private KeywordFixture() {
    }

    public static Keyword keyword(Long id, String name) {
        Keyword keyword = Keyword.create(name);
        ReflectionTestUtils.setField(keyword, "id", id);
        return keyword;
    }
}
