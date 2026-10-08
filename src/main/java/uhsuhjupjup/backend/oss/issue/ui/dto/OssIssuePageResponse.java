package uhsuhjupjup.backend.oss.issue.ui.dto;

import uhsuhjupjup.backend.oss.issue.application.dto.OssIssuePageResult;

import java.util.List;

public record OssIssuePageResponse(String lang, List<OssIssueResponse> items, String nextCursor) {

    public static OssIssuePageResponse from(OssIssuePageResult result) {
        return new OssIssuePageResponse(result.language().code(),
                result.items().stream().map(OssIssueResponse::from).toList(),
                result.nextCursor() == null ? null : result.nextCursor().encode());
    }
}
