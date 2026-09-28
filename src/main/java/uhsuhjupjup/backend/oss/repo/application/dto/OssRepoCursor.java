package uhsuhjupjup.backend.oss.repo.application.dto;

import uhsuhjupjup.backend.oss.repo.domain.OssRepo;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record OssRepoCursor(OssRepoSort sort, String key, long id) {

    private static final String SEPARATOR = ":";
    private static final int PARTS = 3;
    private static final String INVALID = "커서 형식이 올바르지 않습니다.";

    public OssRepoCursor {
        if (sort == null || key == null || key.isEmpty() || id < 1) {
            throw new IllegalArgumentException(INVALID);
        }
        if (sort == OssRepoSort.STARS && Integer.parseInt(key) < 0) {
            throw new IllegalArgumentException(INVALID);
        }
    }

    public static OssRepoCursor after(OssRepoSort sort, OssRepo repo) {
        String key = switch (sort) {
            case STARS -> Integer.toString(repo.getStars());
            case NAME -> repo.getFullNameKey();
        };
        return new OssRepoCursor(sort, key, repo.getId());
    }

    public static OssRepoCursor decode(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        String[] parts = payload.split(SEPARATOR, PARTS);
        if (parts.length != PARTS) {
            throw new IllegalArgumentException(INVALID);
        }
        return new OssRepoCursor(OssRepoSort.valueOf(parts[0]), parts[2], Long.parseLong(parts[1]));
    }

    public String encode() {
        String payload = sort.name() + SEPARATOR + id + SEPARATOR + key;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public int stars() {
        return Integer.parseInt(key);
    }
}
