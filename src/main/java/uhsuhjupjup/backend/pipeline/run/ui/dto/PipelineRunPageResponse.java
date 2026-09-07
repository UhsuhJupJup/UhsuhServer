package uhsuhjupjup.backend.pipeline.run.ui.dto;

import org.springframework.data.domain.Page;
import uhsuhjupjup.backend.pipeline.run.domain.PipelineRun;

import java.util.List;

public record PipelineRunPageResponse(
        List<PipelineRunResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static PipelineRunPageResponse from(Page<PipelineRun> runs) {
        List<PipelineRunResponse> content = runs.getContent().stream()
                .map(PipelineRunResponse::from)
                .toList();
        return new PipelineRunPageResponse(
                content,
                runs.getNumber(),
                runs.getSize(),
                runs.getTotalElements(),
                runs.getTotalPages());
    }
}
