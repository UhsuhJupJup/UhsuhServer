package uhsuhjupjup.backend.pipeline.matching.application.dto;

import java.util.Set;

public record SavedTags(int created, Set<Long> changedKeywordIds) {

    public static final SavedTags NONE = new SavedTags(0, Set.of());
}
