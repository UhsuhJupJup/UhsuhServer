package uhsuhjupjup.backend.oss.repo.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.common.exception.BusinessException;
import uhsuhjupjup.backend.common.exception.ErrorCode;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoResult;
import uhsuhjupjup.backend.oss.repo.domain.OssCategory;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.domain.OssRepoCategory;
import uhsuhjupjup.backend.oss.repo.infra.OssCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoCategoryRepository;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OssRepoCategoryService {

    private final OssRepoRepository ossRepoRepository;
    private final OssCategoryRepository ossCategoryRepository;
    private final OssRepoCategoryRepository ossRepoCategoryRepository;

    @Transactional
    public OssRepoResult replaceCategories(Long repoId, List<String> categoryCodes) {
        OssRepo repo = ossRepoRepository.findForUpdateById(repoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_REPO_NOT_FOUND));
        List<OssCategory> categories = findCategories(categoryCodes);
        List<OssRepoCategory> wanted = repo.linkCategories(categories);
        List<OssRepoCategory> current = ossRepoCategoryRepository.findAllByRepoId(repoId);
        ossRepoCategoryRepository.deleteAll(notIn(current, wanted));
        ossRepoCategoryRepository.saveAll(notIn(wanted, current));
        return OssRepoResult.of(repo, categories);
    }

    private List<OssCategory> findCategories(List<String> codes) {
        List<OssCategory> categories = ossCategoryRepository.findAllByCodeInOrderByIdAsc(codes);
        Set<String> foundCodes = categories.stream()
                .map(OssCategory::getCode)
                .collect(Collectors.toSet());
        if (!foundCodes.containsAll(codes)) {
            throw new BusinessException(ErrorCode.OSS_CATEGORY_NOT_FOUND);
        }
        return categories;
    }

    private static List<OssRepoCategory> notIn(List<OssRepoCategory> links, List<OssRepoCategory> others) {
        Set<Long> otherCategoryIds = others.stream()
                .map(link -> link.getCategory().getId())
                .collect(Collectors.toSet());
        return links.stream()
                .filter(link -> !otherCategoryIds.contains(link.getCategory().getId()))
                .toList();
    }
}
