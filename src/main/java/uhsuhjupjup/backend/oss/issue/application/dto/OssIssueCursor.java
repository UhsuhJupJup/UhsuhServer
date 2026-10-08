package uhsuhjupjup.backend.oss.issue.application.dto;

import uhsuhjupjup.backend.oss.issue.domain.OssIssue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Base64;

public record OssIssueCursor(LocalDateTime githubCreatedAt, long id) {

    private static final String SEPARATOR = ":";
    private static final int PARTS = 2;
    private static final int MIN_YEAR = 1000;
    private static final int MAX_YEAR = 9999;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final String INVALID = "커서 형식이 올바르지 않습니다.";

    public OssIssueCursor {
        if (githubCreatedAt == null || id < 1 || githubCreatedAt.getNano() != 0
                || githubCreatedAt.getYear() < MIN_YEAR || githubCreatedAt.getYear() > MAX_YEAR) {
            throw new IllegalArgumentException(INVALID);
        }
    }

    public static OssIssueCursor after(OssIssue issue) {
        return new OssIssueCursor(issue.getGithubCreatedAt(), issue.getId());
    }

    public static OssIssueCursor decode(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        String[] parts = payload.split(SEPARATOR, PARTS);
        if (parts.length != PARTS) {
            throw new IllegalArgumentException(INVALID);
        }
        return new OssIssueCursor(parseTime(parts[1]), Long.parseLong(parts[0]));
    }

    public String encode() {
        String payload = id + SEPARATOR + TIME_FORMAT.format(githubCreatedAt);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDateTime parseTime(String text) {
        try {
            return LocalDateTime.parse(text, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(INVALID);
        }
    }
}
