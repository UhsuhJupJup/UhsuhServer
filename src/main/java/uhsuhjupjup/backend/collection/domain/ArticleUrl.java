package uhsuhjupjup.backend.collection.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ArticleUrl {

    private static final Set<String> TRACKING_KEYS =
            Set.of("fbclid", "gclid", "gclsrc", "dclid", "msclkid", "igshid", "mc_eid");

    private ArticleUrl() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        URI uri = parse(trimmed);
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return trimmed;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = normalizePort(scheme, uri.getPort());
        String query = stripTracking(uri.getRawQuery());

        StringBuilder normalized = new StringBuilder(scheme).append("://").append(host);
        if (port != -1) {
            normalized.append(':').append(port);
        }
        if (uri.getRawPath() != null) {
            normalized.append(uri.getRawPath());
        }
        if (query != null) {
            normalized.append('?').append(query);
        }
        return normalized.toString();
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static int normalizePort(String scheme, int port) {
        if (port == -1) {
            return -1;
        }
        if (scheme.equals("http") && port == 80) {
            return -1;
        }
        if (scheme.equals("https") && port == 443) {
            return -1;
        }
        return port;
    }

    private static String stripTracking(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        String kept = Arrays.stream(rawQuery.split("&"))
                .filter(param -> !isTracking(param))
                .collect(Collectors.joining("&"));
        return kept.isBlank() ? null : kept;
    }

    private static boolean isTracking(String param) {
        int eq = param.indexOf('=');
        String key = (eq >= 0 ? param.substring(0, eq) : param).toLowerCase(Locale.ROOT);
        return key.startsWith("utm_") || TRACKING_KEYS.contains(key);
    }
}
