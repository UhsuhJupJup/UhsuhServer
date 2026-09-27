package uhsuhjupjup.backend.oss.repo.ui.dto;

import uhsuhjupjup.backend.oss.repo.application.dto.OssCategoryResult;

public record OssCategoryResponse(String code, String nameKo, String nameEn) {

    public static OssCategoryResponse from(OssCategoryResult result) {
        return new OssCategoryResponse(result.code(), result.nameKo(), result.nameEn());
    }
}
