package uhsuhjupjup.backend.oss.repo.application;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.repo.application.dto.OssCategoryResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OssRepoCategoryServiceTest {

    private static final long REPO_ID = 10L;

    @Mock
    private OssRepoRepository ossRepoRepository;

    @Mock
    private OssCategoryRepository ossCategoryRepository;

    @Mock
    private OssRepoCategoryRepository ossRepoCategoryRepository;

    @InjectMocks
    private OssRepoCategoryService ossRepoCategoryService;

    @Captor
    private ArgumentCaptor<Iterable<OssRepoCategory>> removed;

    @Captor
    private ArgumentCaptor<Iterable<OssRepoCategory>> added;

    private final OssCategory aiMl = category(1L, "ai-ml", "AI와 머신러닝", "AI & Machine Learning");
    private final OssCategory backend = category(4L, "backend", "백엔드와 API", "Backend & APIs");
    private final OssCategory devtools = category(8L, "devtools", "개발 도구", "Developer Tools");
    private final OssRepo repo = repo();

    @Test
    void replaceCategories_missingRepo_throwsNotFoundWithoutLookingAtCategories() {
        given(ossRepoRepository.findForUpdateById(REPO_ID)).willReturn(Optional.empty());

        assertFailsWith(ErrorCode.OSS_REPO_NOT_FOUND,
                () -> ossRepoCategoryService.replaceCategories(REPO_ID, List.of("ai-ml")));

        then(ossCategoryRepository).shouldHaveNoInteractions();
        then(ossRepoCategoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void replaceCategories_unknownCode_throwsCategoryNotFoundWithoutWriting() {
        given(ossRepoRepository.findForUpdateById(REPO_ID)).willReturn(Optional.of(repo));
        given(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("ai-ml", "robotics")))
                .willReturn(List.of(aiMl));

        assertFailsWith(ErrorCode.OSS_CATEGORY_NOT_FOUND,
                () -> ossRepoCategoryService.replaceCategories(REPO_ID, List.of("ai-ml", "robotics")));

        then(ossRepoCategoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void replaceCategories_codeMatchedOnlyIgnoringCase_isTreatedAsUnknown() {
        given(ossRepoRepository.findForUpdateById(REPO_ID)).willReturn(Optional.of(repo));
        given(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("AI-ML"))).willReturn(List.of(aiMl));

        assertFailsWith(ErrorCode.OSS_CATEGORY_NOT_FOUND,
                () -> ossRepoCategoryService.replaceCategories(REPO_ID, List.of("AI-ML")));

        then(ossRepoCategoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void replaceCategories_threeCategories_isRejectedByDomainRuleWithoutWriting() {
        List<String> codes = List.of("ai-ml", "backend", "devtools");
        given(ossRepoRepository.findForUpdateById(REPO_ID)).willReturn(Optional.of(repo));
        given(ossCategoryRepository.findAllByCodeInOrderByIdAsc(codes)).willReturn(List.of(aiMl, backend, devtools));

        assertFailsWith(ErrorCode.VALIDATION_ERROR, () -> ossRepoCategoryService.replaceCategories(REPO_ID, codes));

        then(ossRepoCategoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void replaceCategories_otherCategories_removesDroppedAndAddsOnlyNewOnes() {
        List<OssRepoCategory> current = repo.linkCategories(List.of(aiMl, backend));
        givenRepoLinkedTo(current);
        given(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("devtools", "backend")))
                .willReturn(List.of(backend, devtools));

        OssRepoResult result = ossRepoCategoryService.replaceCategories(REPO_ID, List.of("devtools", "backend"));

        assertThat(result).isEqualTo(new OssRepoResult(REPO_ID, 1296269L, "octocat/Hello-World", null, "Java", 80,
                OssRepoStatus.ACTIVE, List.of(
                        new OssCategoryResult("backend", "백엔드와 API", "Backend & APIs"),
                        new OssCategoryResult("devtools", "개발 도구", "Developer Tools"))));
        then(ossRepoCategoryRepository).should().deleteAll(removed.capture());
        then(ossRepoCategoryRepository).should().saveAll(added.capture());
        assertThat(removed.getValue()).containsExactly(current.get(0));
        assertThat(added.getValue()).singleElement().satisfies(link -> {
            assertThat(link.getRepo()).isSameAs(repo);
            assertThat(link.getCategory()).isSameAs(devtools);
        });
    }

    @Test
    void replaceCategories_noCodes_removesAllCategories() {
        List<OssRepoCategory> current = repo.linkCategories(List.of(aiMl, backend));
        givenRepoLinkedTo(current);
        given(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of())).willReturn(List.of());

        OssRepoResult result = ossRepoCategoryService.replaceCategories(REPO_ID, List.of());

        assertThat(result.categories()).isEmpty();
        then(ossRepoCategoryRepository).should().deleteAll(removed.capture());
        then(ossRepoCategoryRepository).should().saveAll(added.capture());
        assertThat(removed.getValue()).containsExactlyElementsOf(current);
        assertThat(added.getValue()).isEmpty();
    }

    @Test
    void replaceCategories_sameCategoriesAgain_removesAndAddsNothing() {
        givenRepoLinkedTo(repo.linkCategories(List.of(aiMl, backend)));
        given(ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of("backend", "ai-ml")))
                .willReturn(List.of(aiMl, backend));

        OssRepoResult result = ossRepoCategoryService.replaceCategories(REPO_ID, List.of("backend", "ai-ml"));

        assertThat(result.categories()).extracting(OssCategoryResult::code).containsExactly("ai-ml", "backend");
        then(ossRepoCategoryRepository).should().deleteAll(removed.capture());
        then(ossRepoCategoryRepository).should().saveAll(added.capture());
        assertThat(removed.getValue()).isEmpty();
        assertThat(added.getValue()).isEmpty();
    }

    private void givenRepoLinkedTo(List<OssRepoCategory> current) {
        given(ossRepoRepository.findForUpdateById(REPO_ID)).willReturn(Optional.of(repo));
        given(ossRepoCategoryRepository.findAllByRepoId(REPO_ID)).willReturn(current);
    }

    private static void assertFailsWith(ErrorCode expected, ThrowingCallable replace) {
        assertThatThrownBy(replace)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    private static OssRepo repo() {
        OssRepo repo = OssRepo.create(1296269L, "octocat/Hello-World", null, "Java", 80);
        ReflectionTestUtils.setField(repo, "id", REPO_ID);
        return repo;
    }

    private static OssCategory category(long id, String code, String nameKo, String nameEn) {
        OssCategory category = OssCategory.create(code, nameKo, nameEn);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
