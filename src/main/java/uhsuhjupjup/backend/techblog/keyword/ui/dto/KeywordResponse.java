package uhsuhjupjup.backend.techblog.keyword.ui.dto;

import uhsuhjupjup.backend.techblog.keyword.domain.Keyword;

public record KeywordResponse(Long id, String name) {

    public static KeywordResponse from(Keyword keyword) {
        return new KeywordResponse(keyword.getId(), keyword.getName());
    }
}
