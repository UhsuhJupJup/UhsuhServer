package uhsuhjupjup.backend.oss.issue.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.oss.issue.application.OssIssueService;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueCursor;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueDifficultyFilter;
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.ui.dto.OssIssuePageResponse;

import static uhsuhjupjup.backend.common.web.ParameterEditors.parsedBy;

@RestController
@RequestMapping("/api/oss/repos/{repoId}/issues")
@RequiredArgsConstructor
public class OssRepoIssueController implements OssRepoIssueControllerApi {

    private final OssIssueService ossIssueService;

    @InitBinder
    public void registerParameterEditors(WebDataBinder binder) {
        binder.registerCustomEditor(OssIssueDifficultyFilter.class, parsedBy(OssIssueDifficultyFilter::fromCodes));
        binder.registerCustomEditor(OssIssueLanguage.class, parsedBy(OssIssueLanguage::fromCode));
        binder.registerCustomEditor(OssIssueCursor.class, parsedBy(OssIssueCursor::decode));
    }

    @Override
    @GetMapping
    public OssIssuePageResponse repoIssues(@PathVariable Long repoId,
                                           @RequestParam(required = false) OssIssueDifficultyFilter difficulty,
                                           @RequestParam(required = false) OssIssueLanguage lang,
                                           @RequestParam(required = false) OssIssueCursor cursor,
                                           @RequestParam(required = false) Integer size) {
        return OssIssuePageResponse.from(ossIssueService.getRepoIssues(repoId, difficulty, lang, cursor, size));
    }
}
