package uhsuhjupjup.backend.archive.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.archive.application.SentArticleService;
import uhsuhjupjup.backend.archive.ui.dto.SentArticlesResponse;
import uhsuhjupjup.backend.common.auth.LoginMember;
import uhsuhjupjup.backend.member.domain.Member;

@RestController
@RequestMapping("/api/me/notifications")
@RequiredArgsConstructor
public class SentArticleController {

    private final SentArticleService sentArticleService;

    @GetMapping
    public SentArticlesResponse mySentArticles(@LoginMember Member member) {
        return SentArticlesResponse.from(sentArticleService.getSentArticles(member.getId()));
    }
}
