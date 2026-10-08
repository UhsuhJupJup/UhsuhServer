package uhsuhjupjup.backend.oss.pipeline.grading.application;

import uhsuhjupjup.backend.oss.pipeline.grading.application.dto.IssueGradingResult;

import java.util.List;

public interface IssueGrader {

    String ISSUE_ID_LOG_KEY = "ossIssueId";

    IssueGradingResult grade(String title, String body, List<String> labels);
}
