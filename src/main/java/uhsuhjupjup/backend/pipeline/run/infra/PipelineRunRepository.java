package uhsuhjupjup.backend.pipeline.run.infra;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uhsuhjupjup.backend.pipeline.run.domain.PipelineRun;

import java.util.List;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {

    List<PipelineRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
