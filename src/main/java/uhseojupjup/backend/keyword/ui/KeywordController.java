package uhseojupjup.backend.keyword.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhseojupjup.backend.keyword.application.KeywordService;
import uhseojupjup.backend.keyword.ui.dto.KeywordResponse;

import java.util.List;

@RestController
@RequestMapping("/api/keywords")
@RequiredArgsConstructor
public class KeywordController {

    private final KeywordService keywordService;

    @GetMapping
    public List<KeywordResponse> list(@RequestParam(required = false) String q) {
        return keywordService.search(q).stream()
                .map(KeywordResponse::from)
                .toList();
    }
}
