package uhsuhjupjup.backend.oss.contributor.domain;

import java.util.Arrays;

public enum OssContributorLanguage {

    KO("ko"),
    EN("en");

    private final String code;

    OssContributorLanguage(String code) {
        this.code = code;
    }

    public static OssContributorLanguage fromCode(String code) {
        return Arrays.stream(values())
                .filter(language -> language.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 언어입니다: " + code));
    }

    public String code() {
        return code;
    }
}
