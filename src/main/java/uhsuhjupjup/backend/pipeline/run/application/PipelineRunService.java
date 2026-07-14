package uhsuhjupjup.backend.pipeline.run.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import uhsuhjupjup.backend.pipeline.run.domain.PipelineRun;
import uhsuhjupjup.backend.pipeline.run.infra.PipelineRunRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PipelineRunService {

    private final PipelineRunRepository pipelineRunRepository;

    public List<PipelineRun> recentRuns(int limit) {
        return pipelineRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit));
    }
}
