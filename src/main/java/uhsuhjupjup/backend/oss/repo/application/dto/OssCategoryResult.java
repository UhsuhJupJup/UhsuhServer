package uhsuhjupjup.backend.oss.repo.application.dto;

import uhsuhjupjup.backend.oss.repo.domain.OssCategory;

public record OssCategoryResult(String code, String nameKo, String nameEn) {

    public static OssCategoryResult from(OssCategory category) {
        return new OssCategoryResult(category.getCode(), category.getNameKo(), category.getNameEn());
    }
}
