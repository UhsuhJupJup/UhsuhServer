package uhsuhjupjup.backend.notification.application.dto;

import java.util.List;

public record DigestArticleView(String blogName, String dateText, String title, String url, List<String> keywords) {
}
