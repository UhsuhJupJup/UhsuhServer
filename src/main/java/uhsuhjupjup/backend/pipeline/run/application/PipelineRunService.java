package uhsuhjupjup.backend.pipeline.run.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.pipeline.run.domain.PipelineRun;
import uhsuhjupjup.backend.pipeline.run.infra.PipelineRunRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PipelineRunService {

    private final PipelineRunRepository pipelineRunRepository;

    public Page<PipelineRun> runs(int page, int size) {
        return pipelineRunRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size));
    }
}
