package uhsuhjupjup.backend.oss.issue.application.dto;

import uhsuhjupjup.backend.oss.issue.domain.OssIssueDifficulty;

import java.util.Arrays;

public enum OssIssueDifficultyCode {

    EASY("easy", OssIssueDifficulty.EASY),
    MEDIUM("medium", OssIssueDifficulty.MEDIUM),
    HARD("hard", OssIssueDifficulty.HARD);

    private final String code;
    private final OssIssueDifficulty difficulty;

    OssIssueDifficultyCode(String code, OssIssueDifficulty difficulty) {
        this.code = code;
        this.difficulty = difficulty;
    }

    public static OssIssueDifficultyCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 난이도입니다: " + code));
    }

    public OssIssueDifficulty difficulty() {
        return difficulty;
    }
}
