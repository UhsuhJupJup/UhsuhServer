package uhsuhjupjup.backend.learningnote.application.dto;

import java.util.List;

public record NoteAnalysis(boolean fallback, List<Long> keywordIds, List<String> keywordNames) {

    public static NoteAnalysis saved() {
        return new NoteAnalysis(false, List.of(), List.of());
    }

    public static NoteAnalysis fallback(List<Long> keywordIds, List<String> keywordNames) {
        return new NoteAnalysis(true, keywordIds, keywordNames);
    }
}
