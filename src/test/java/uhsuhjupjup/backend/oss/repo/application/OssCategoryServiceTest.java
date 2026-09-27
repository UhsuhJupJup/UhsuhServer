package uhsuhjupjup.backend.oss.repo.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uhsuhjupjup.backend.oss.repo.application.dto.OssCategoryResult;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.oss.repo.infra.OssCategoryRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OssCategoryServiceTest {

    @Mock
    private OssCategoryRepository ossCategoryRepository;

    @InjectMocks
    private OssCategoryService ossCategoryService;

    @Test
    void findAll_returnsCodeAndBilingualNamesInIdOrder() {
        given(ossCategoryRepository.findAllByOrderByIdAsc()).willReturn(List.of(
                OssCategory.create("ai-ml", "AI와 머신러닝", "AI & Machine Learning"),
                OssCategory.create("backend", "백엔드와 API", "Backend & APIs")));

        assertThat(ossCategoryService.findAll()).containsExactly(
                new OssCategoryResult("ai-ml", "AI와 머신러닝", "AI & Machine Learning"),
                new OssCategoryResult("backend", "백엔드와 API", "Backend & APIs"));
    }
}
