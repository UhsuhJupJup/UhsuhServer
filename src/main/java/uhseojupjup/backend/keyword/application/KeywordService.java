package uhseojupjup.backend.keyword.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import uhseojupjup.backend.keyword.domain.Keyword;
import uhseojupjup.backend.keyword.infra.KeywordRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordService {

    private final KeywordRepository keywordRepository;

    public List<Keyword> search(String query) {
        if (!StringUtils.hasText(query)) {
            return keywordRepository.findAllByOrderByNameAsc();
        }
        return keywordRepository.findByNameContainingIgnoreCaseOrderByNameAsc(query.trim());
    }
}
