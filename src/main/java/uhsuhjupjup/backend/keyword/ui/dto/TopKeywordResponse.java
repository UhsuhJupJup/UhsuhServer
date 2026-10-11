package uhsuhjupjup.backend.keyword.ui.dto;

import uhsuhjupjup.backend.article.application.dto.KeywordArticleCount;

public record TopKeywordResponse(Long id, String name, long articleCount) {

    public static TopKeywordResponse from(KeywordArticleCount keyword) {
        return new TopKeywordResponse(keyword.keywordId(), keyword.name(), keyword.articleCount());
    }
}
