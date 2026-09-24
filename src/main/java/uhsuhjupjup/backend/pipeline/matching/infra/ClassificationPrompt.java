package uhsuhjupjup.backend.pipeline.matching.infra;

import uhsuhjupjup.backend.pipeline.matching.domain.MatchCatalog;
import uhsuhjupjup.backend.pipeline.matching.domain.MatchTarget;

final class ClassificationPrompt {

    private ClassificationPrompt() {
    }

    static String system(MatchCatalog catalog) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("너는 기술 블로그 글을 아래 구독 키워드로 분류한다.\n");
        prompt.append("이 글이 실제로 주제로 다루는 키워드만 고른다. 스쳐 언급되거나 무관하면 고르지 않는다.\n");
        prompt.append("이 글의 핵심을 가장 잘 나타내는 순서로, 최대 5개까지만 고른다.\n");
        prompt.append("해당하는 키워드가 하나도 없으면 빈 목록을 반환한다. 목록에 없는 키워드는 만들어내지 않는다.\n\n");
        prompt.append("키워드 목록:\n");
        for (MatchTarget target : catalog.targets()) {
            prompt.append("- ").append(target.lowerName());
            if (!target.lowerAliases().isEmpty()) {
                prompt.append(" (별칭: ").append(String.join(", ", target.lowerAliases())).append(")");
            }
            prompt.append("\n");
        }
        return prompt.toString();
    }

    static String user(String title, String body) {
        String safeBody = (body == null || body.isBlank()) ? "(본문 없음)" : body;
        return "제목: " + title + "\n\n본문: " + safeBody;
    }
}
