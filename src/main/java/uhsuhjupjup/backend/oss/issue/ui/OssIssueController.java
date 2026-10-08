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
import uhsuhjupjup.backend.oss.issue.application.dto.OssIssueLanguage;
import uhsuhjupjup.backend.oss.issue.ui.dto.OssIssueDetailResponse;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.util.function.Function;

@RestController
@RequestMapping("/api/oss/issues")
@RequiredArgsConstructor
public class OssIssueController implements OssIssueControllerApi {

    private final OssIssueService ossIssueService;

    @InitBinder
    public void registerParameterEditors(WebDataBinder binder) {
        binder.registerCustomEditor(OssIssueLanguage.class, parsedBy(OssIssueLanguage::fromCode));
    }

    @Override
    @GetMapping("/{issueId}")
    public OssIssueDetailResponse detail(@PathVariable Long issueId,
                                         @RequestParam(required = false) OssIssueLanguage lang) {
        return OssIssueDetailResponse.from(ossIssueService.getDetail(issueId, lang));
    }

    private static PropertyEditor parsedBy(Function<String, ?> parser) {
        return new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text.isEmpty() ? null : parser.apply(text));
            }
        };
    }
}
