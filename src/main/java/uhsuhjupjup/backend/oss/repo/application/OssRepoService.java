package uhsuhjupjup.backend.oss.repo.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoCursor;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoPageResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoSort;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoStatus;
import uhsuhjupjup.backend.oss.repo.infra.OssCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OssRepoService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String LIKE_ESCAPE = "!";

    private final OssRepoRepository ossRepoRepository;
    private final OssCategoryRepository ossCategoryRepository;
    private final OssRepoCategoryRepository ossRepoCategoryRepository;

    public OssRepoPageResult explore(String category, String language, String q, OssRepoSort sort,
                                     OssRepoCursor cursor, Integer size) {
        OssRepoSort order = sort == null ? OssRepoSort.STARS : sort;
        if (cursor != null && cursor.sort() != order) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Long categoryId = findCategoryId(trimToNull(category));
        int pageSize = clampSize(size);
        List<OssRepo> found = findPage(order, categoryId, trimToNull(language), containsPattern(trimToNull(q)),
                cursor, Limit.of(pageSize + 1));
        if (found.size() <= pageSize) {
            return new OssRepoPageResult(withCategories(found), null);
        }
        List<OssRepo> page = found.subList(0, pageSize);
        return new OssRepoPageResult(withCategories(page), OssRepoCursor.after(order, page.getLast()));
    }

    private Long findCategoryId(String code) {
        if (code == null) {
            return null;
        }
        return ossCategoryRepository.findAllByCodeInOrderByIdAsc(List.of(code)).stream()
                .filter(category -> category.getCode().equals(code))
                .map(OssCategory::getId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_CATEGORY_NOT_FOUND));
    }

    private List<OssRepo> findPage(OssRepoSort sort, Long categoryId, String language, String pattern,
                                   OssRepoCursor after, Limit limit) {
        Long afterId = after == null ? null : after.id();
        return switch (sort) {
            case STARS -> ossRepoRepository.findPageSortedByStars(OssRepoStatus.ACTIVE, categoryId, language,
                    pattern, after == null ? null : after.stars(), afterId, limit);
            case NAME -> ossRepoRepository.findPageSortedByName(OssRepoStatus.ACTIVE, categoryId, language,
                    pattern, after == null ? null : after.key(), afterId, limit);
        };
    }

    private List<OssRepoResult> withCategories(List<OssRepo> repos) {
        if (repos.isEmpty()) {
            return List.of();
        }
        Map<Long, List<OssCategory>> categoriesByRepoId = ossRepoCategoryRepository
                .findWithCategoryByRepoIdIn(repos.stream().map(OssRepo::getId).toList()).stream()
                .collect(Collectors.groupingBy(link -> link.getRepo().getId(),
                        Collectors.mapping(OssRepoCategory::getCategory, Collectors.toList())));
        return repos.stream()
                .map(repo -> OssRepoResult.of(repo, categoriesByRepoId.getOrDefault(repo.getId(), List.of())))
                .toList();
    }

    private static int clampSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private static String containsPattern(String text) {
        if (text == null) {
            return null;
        }
        String escaped = text.replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }
}
