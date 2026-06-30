package uhsuhjupjup.backend.pipeline.run.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.pipeline.run.domain.PipelineRun;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {
}
