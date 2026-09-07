package uhsuhjupjup.backend.pipeline.run.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uhsuhjupjup.backend.pipeline.run.infra.PipelineRunRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PipelineRunServiceTest {

    @Mock
    private PipelineRunRepository pipelineRunRepository;

    @InjectMocks
    private PipelineRunService pipelineRunService;

    @Test
    void runs는_요청한_페이지와_크기로_최근순_조회한다() {
        given(pipelineRunRepository.findAllByOrderByStartedAtDesc(any(Pageable.class))).willReturn(Page.empty());

        pipelineRunService.runs(2, 15);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(pipelineRunRepository).findAllByOrderByStartedAtDesc(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(15);
    }
}
