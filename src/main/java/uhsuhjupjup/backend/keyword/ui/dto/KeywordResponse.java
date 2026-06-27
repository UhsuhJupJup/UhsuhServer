package uhsuhjupjup.backend.keyword.ui.dto;

import uhsuhjupjup.backend.keyword.domain.Keyword;

public record KeywordResponse(Long id, String name) {

    public static KeywordResponse from(Keyword keyword) {
        return new KeywordResponse(keyword.getId(), keyword.getName());
    }
}
