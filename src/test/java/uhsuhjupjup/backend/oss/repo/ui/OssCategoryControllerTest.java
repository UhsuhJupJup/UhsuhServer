package uhsuhjupjup.backend.oss.repo.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uhsuhjupjup.backend.oss.repo.application.OssCategoryService;
import uhsuhjupjup.backend.oss.repo.application.dto.OssCategoryResult;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OssCategoryControllerTest {

    @Mock
    private OssCategoryService ossCategoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OssCategoryController(ossCategoryService)).build();
    }

    @Test
    void list_returnsCodeAndBilingualNamesWithoutId() throws Exception {
        given(ossCategoryService.findAll()).willReturn(List.of(
                new OssCategoryResult("ai-ml", "AI와 머신러닝", "AI & Machine Learning"),
                new OssCategoryResult("backend", "백엔드와 API", "Backend & APIs")));

        mockMvc.perform(get("/api/oss/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("ai-ml"))
                .andExpect(jsonPath("$[0].nameKo").value("AI와 머신러닝"))
                .andExpect(jsonPath("$[0].nameEn").value("AI & Machine Learning"))
                .andExpect(jsonPath("$[1].code").value("backend"))
                .andExpect(jsonPath("$[1].nameKo").value("백엔드와 API"))
                .andExpect(jsonPath("$[1].nameEn").value("Backend & APIs"))
                .andExpect(jsonPath("$[*].id").doesNotExist());
    }
}
