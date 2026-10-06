package uhsuhjupjup.backend.oss.pipeline.sync.application.dto;

import uhsuhjupjup.backend.oss.pipeline.sync.domain.OssIssuePrefilter.ExclusionReason;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record OssIssueSyncResult(
        boolean notModified,
        IncompleteReason incompleteReason,
        int received,
        Map<ExclusionReason, Integer> excluded,
        int created,
        int bodyChanged,
        int bodyUnchanged) {

    public enum IncompleteReason {
        PAGE_LIMIT,
        PAGE_UNAVAILABLE
    }

    public OssIssueSyncResult {
        Map<ExclusionReason, Integer> everyReason = new EnumMap<>(ExclusionReason.class);
        for (ExclusionReason reason : ExclusionReason.values()) {
            everyReason.put(reason, excluded.getOrDefault(reason, 0));
        }
        excluded = Collections.unmodifiableMap(everyReason);
    }

    public static OssIssueSyncResult unchanged() {
        return new OssIssueSyncResult(true, null, 0, Map.of(), 0, 0, 0);
    }

    public boolean complete() {
        return incompleteReason == null;
    }
}
