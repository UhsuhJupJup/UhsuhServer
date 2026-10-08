package uhsuhjupjup.backend.oss.issue.application.dto;

import java.util.List;

public record OssIssuePageResult(OssIssueLanguage language, List<OssIssueResult> items, OssIssueCursor nextCursor) {
}
