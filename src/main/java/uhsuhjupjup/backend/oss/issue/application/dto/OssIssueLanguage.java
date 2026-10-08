package uhsuhjupjup.backend.oss.issue.application.dto;

import java.util.Arrays;

public enum OssIssueLanguage {

    KO("ko"),
    EN("en");

    private final String code;

    OssIssueLanguage(String code) {
        this.code = code;
    }

    public static OssIssueLanguage fromCode(String code) {
        return Arrays.stream(values())
                .filter(language -> language.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 언어입니다: " + code));
    }

    public String code() {
        return code;
    }

    public String choose(String korean, String english) {
        return switch (this) {
            case KO -> korean;
            case EN -> english;
        };
    }
}
