package uhsuhjupjup.backend.keyword.ui.dto;

import uhsuhjupjup.backend.article.application.dto.KeywordRelation;

public record RelatedKeywordResponse(Long id, String name, long sharedArticleCount) {

    public static RelatedKeywordResponse from(KeywordRelation relation) {
        return new RelatedKeywordResponse(relation.keywordId(), relation.name(), relation.sharedArticleCount());
    }
}
