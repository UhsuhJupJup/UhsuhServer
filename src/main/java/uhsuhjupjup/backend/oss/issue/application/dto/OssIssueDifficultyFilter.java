package uhsuhjupjup.backend.oss.issue.application.dto;

import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public record OssIssueDifficultyFilter(Set<OssIssueDifficulty> difficulties) {

    private static final String SEPARATOR = ",";
    private static final int KEEP_EMPTY_CODES = -1;

    public OssIssueDifficultyFilter {
        if (difficulties == null || difficulties.isEmpty()) {
            throw new IllegalArgumentException("난이도를 하나 이상 골라야 합니다.");
        }
        difficulties = Collections.unmodifiableSet(EnumSet.copyOf(difficulties));
    }

    public static OssIssueDifficultyFilter all() {
        return new OssIssueDifficultyFilter(EnumSet.allOf(OssIssueDifficulty.class));
    }

    public static OssIssueDifficultyFilter fromCodes(String codes) {
        return new OssIssueDifficultyFilter(Arrays.stream(codes.split(SEPARATOR, KEEP_EMPTY_CODES))
                .map(OssIssueDifficultyCode::fromCode)
                .map(OssIssueDifficultyCode::difficulty)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(OssIssueDifficulty.class))));
    }
}
