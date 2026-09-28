package uhsuhjupjup.backend.oss.repo.domain;

import org.junit.jupiter.api.Test;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssRepoTest {

    private static final OssCategory AI_ML = OssCategory.create("ai-ml", "AI와 머신러닝", "AI & Machine Learning");
    private static final OssCategory BACKEND = OssCategory.create("backend", "백엔드와 API", "Backend & APIs");
    private static final OssCategory DEVTOOLS = OssCategory.create("devtools", "개발 도구", "Developer Tools");

    private final OssRepo repo = OssRepo.create(1296269L, "octocat/Hello-World", null, "Java", 80);

    @Test
    void linkCategories_twoCategories_linksEachToTheRepo() {
        List<OssRepoCategory> links = repo.linkCategories(List.of(AI_ML, BACKEND));

        assertThat(links).extracting(OssRepoCategory::getCategory).containsExactly(AI_ML, BACKEND);
        assertThat(links).allSatisfy(link -> assertThat(link.getRepo()).isSameAs(repo));
    }

    @Test
    void linkCategories_oneCategory_linksIt() {
        assertThat(repo.linkCategories(List.of(DEVTOOLS)))
                .extracting(OssRepoCategory::getCategory)
                .containsExactly(DEVTOOLS);
    }

    @Test
    void linkCategories_noCategory_linksNothing() {
        assertThat(repo.linkCategories(List.of())).isEmpty();
    }

    @Test
    void linkCategories_threeCategories_isRejected() {
        assertThatThrownBy(() -> repo.linkCategories(List.of(AI_ML, BACKEND, DEVTOOLS)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void linkCategories_sameCodeTwice_countsOnceAndKeepsTheFirst() {
        OssCategory sameCodeAsAiMl = OssCategory.create("ai-ml", "AI와 머신러닝", "AI & Machine Learning");

        List<OssRepoCategory> links = repo.linkCategories(List.of(AI_ML, sameCodeAsAiMl, BACKEND, AI_ML));

        assertThat(links).extracting(OssRepoCategory::getCategory).containsExactly(AI_ML, BACKEND);
    }
}
