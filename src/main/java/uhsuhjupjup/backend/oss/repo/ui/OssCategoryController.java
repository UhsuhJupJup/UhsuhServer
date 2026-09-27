package uhsuhjupjup.backend.oss.repo.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.oss.repo.application.OssCategoryService;
import uhsuhjupjup.backend.oss.repo.ui.dto.OssCategoryResponse;

import java.util.List;

@RestController
@RequestMapping("/api/oss/categories")
@RequiredArgsConstructor
public class OssCategoryController implements OssCategoryControllerApi {

    private final OssCategoryService ossCategoryService;

    @Override
    @GetMapping
    public List<OssCategoryResponse> list() {
        return ossCategoryService.findAll().stream()
                .map(OssCategoryResponse::from)
                .toList();
    }
}
