package uhsuhjupjup.backend.oss.repo.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.oss.repo.application.dto.OssCategoryResult;
import uhsuhjupjup.backend.oss.repo.infra.OssCategoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OssCategoryService {

    private final OssCategoryRepository ossCategoryRepository;

    public List<OssCategoryResult> findAll() {
        return ossCategoryRepository.findAllByOrderByIdAsc().stream()
                .map(OssCategoryResult::from)
                .toList();
    }
}
