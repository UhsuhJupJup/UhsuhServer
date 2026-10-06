package uhsuhjupjup.backend.oss.pipeline.grading.application.dto;

import uhsuhjupjup.backend.oss.pipeline.grading.domain.OssIssueVerdict;

public record IssueGradingResult(OssIssueVerdict verdict, String model) {

    @Override
    public String toString() {
        return "IssueGradingResult[model=" + model + ", verdict=" + verdict + "]";
    }
}
