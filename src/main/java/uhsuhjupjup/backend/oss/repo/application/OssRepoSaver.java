package uhsuhjupjup.backend.oss.repo.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uhsuhjupjup.backend.oss.repo.domain.OssRepo;
import uhsuhjupjup.backend.oss.repo.infra.OssRepoRepository;

@Component
@RequiredArgsConstructor
public class OssRepoSaver {

    private final OssRepoRepository ossRepoRepository;

    @Transactional
    public OssRepo save(OssRepo repo) {
        return ossRepoRepository.save(repo);
    }
}
