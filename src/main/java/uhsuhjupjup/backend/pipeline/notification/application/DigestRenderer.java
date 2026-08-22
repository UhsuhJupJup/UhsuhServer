package uhsuhjupjup.backend.pipeline.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import uhsuhjupjup.backend.member.domain.Member;
import uhsuhjupjup.backend.pipeline.notification.application.dto.DigestArticleView;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DigestRenderer {

    private final SpringTemplateEngine templateEngine;

    @Value("${mail.unsubscribe-base-url:http://localhost:8080/api/unsubscribe}")
    private String unsubscribeBaseUrl;

    public String render(Member member, List<DigestArticleView> articles, String digestDate) {
        return render(member.getEmail(), articles, digestDate, unsubscribeUrl(member));
    }

    public String render(String subEmail, List<DigestArticleView> articles, String digestDate, String unsubscribeUrl) {
        Context context = new Context();
        context.setVariable("subEmail", subEmail);
        context.setVariable("digestDate", digestDate);
        context.setVariable("digestCount", articles.size());
        context.setVariable("articles", articles);
        context.setVariable("unsubscribeUrl", unsubscribeUrl);
        return templateEngine.process("mail/digest", context);
    }

    public String unsubscribeUrl(Member member) {
        return unsubscribeUrl(member.getUnsubscribeToken());
    }

    public String unsubscribeUrl(String unsubscribeToken) {
        return unsubscribeBaseUrl + "?token=" + unsubscribeToken;
    }
}
