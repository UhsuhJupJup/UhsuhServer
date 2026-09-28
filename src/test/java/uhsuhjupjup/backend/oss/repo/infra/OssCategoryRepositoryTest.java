package uhsuhjupjup.backend.oss.repo.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.support.MySqlDataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@MySqlDataJpaTest
class OssCategoryRepositoryTest {

    @Autowired
    private OssCategoryRepository ossCategoryRepository;

    @Test
    void findAllByOrderByIdAsc_returnsSeedInIdOrder() {
        assertThat(ossCategoryRepository.findAllByOrderByIdAsc())
                .extracting(OssCategory::getCode, OssCategory::getNameKo, OssCategory::getNameEn)
                .containsExactly(
                        tuple("ai-ml", "AI와 머신러닝", "AI & Machine Learning"),
                        tuple("ai-agents", "AI 에이전트와 LLM 도구", "AI Agents & LLM Tools"),
                        tuple("web-frontend", "웹 프론트엔드", "Web Frontend"),
                        tuple("backend", "백엔드와 API", "Backend & APIs"),
                        tuple("mobile", "모바일", "Mobile"),
                        tuple("desktop", "데스크톱 앱", "Desktop Apps"),
                        tuple("data", "데이터와 데이터베이스", "Data & Databases"),
                        tuple("devtools", "개발 도구", "Developer Tools"),
                        tuple("infra", "인프라와 클라우드", "Infrastructure & Cloud"),
                        tuple("security", "보안", "Security"),
                        tuple("languages", "언어와 런타임", "Languages & Runtimes"),
                        tuple("docs", "문서와 학습 자료", "Docs & Learning"),
                        tuple("games-media", "게임과 미디어", "Games & Media"));
    }

    @Test
    void findAllByCodeInOrderByIdAsc_returnsFoundCategoriesInIdOrder() {
        assertThat(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("backend", "nothing", "ai-ml")))
                .extracting(OssCategory::getCode)
                .containsExactly("ai-ml", "backend");
    }

    @Test
    void findAllByCodeInOrderByIdAsc_noCodes_returnsNothing() {
        assertThat(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of())).isEmpty();
    }

    @Test
    void findAllByCodeInOrderByIdAsc_matchesCodeIgnoringCaseByColumnCollation() {
        assertThat(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("AI-ML")))
                .extracting(OssCategory::getCode)
                .containsExactly("ai-ml");
    }

    @Test
    void duplicateCode_violatesUniqueConstraint() {
        ossCategoryRepository.saveAndFlush(OssCategory.create("robotics", "로보틱스", "Robotics"));

        assertThatThrownBy(() -> ossCategoryRepository.saveAndFlush(
                OssCategory.create("robotics", "로보틱스", "Robotics")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditing_setsCreatedAndUpdatedAt() {
        OssCategory saved = ossCategoryRepository.saveAndFlush(
                OssCategory.create("robotics", "로보틱스", "Robotics"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
