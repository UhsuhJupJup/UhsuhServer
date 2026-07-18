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
        Context context = new Context();
        context.setVariable("subEmail", member.getEmail());
        context.setVariable("digestDate", digestDate);
        context.setVariable("digestCount", articles.size());
        context.setVariable("articles", articles);
        context.setVariable("unsubscribeUrl", unsubscribeUrl(member));
        return templateEngine.process("mail/digest", context);
    }

    public String unsubscribeUrl(Member member) {
        return unsubscribeBaseUrl + "?token=" + member.getUnsubscribeToken();
    }
}
