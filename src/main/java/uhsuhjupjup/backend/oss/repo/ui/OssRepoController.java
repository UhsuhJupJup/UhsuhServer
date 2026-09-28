package uhsuhjupjup.backend.oss.repo.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.oss.repo.application.OssRepoService;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoCursor;
import uhsuhjupjup.backend.oss.repo.application.dto.OssRepoSort;
import uhsuhjupjup.backend.oss.repo.ui.dto.OssRepoPageResponse;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.util.function.Function;

@RestController
@RequestMapping("/api/oss/repos")
@RequiredArgsConstructor
public class OssRepoController implements OssRepoControllerApi {

    private final OssRepoService ossRepoService;

    @InitBinder
    public void registerParameterEditors(WebDataBinder binder) {
        binder.registerCustomEditor(OssRepoSort.class, parsedBy(OssRepoSort::fromParameter));
        binder.registerCustomEditor(OssRepoCursor.class, parsedBy(OssRepoCursor::decode));
    }

    @Override
    @GetMapping
    public OssRepoPageResponse explore(@RequestParam(required = false) String category,
                                       @RequestParam(required = false) String language,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) OssRepoSort sort,
                                       @RequestParam(required = false) OssRepoCursor cursor,
                                       @RequestParam(required = false) Integer size) {
        return OssRepoPageResponse.from(ossRepoService.explore(category, language, q, sort, cursor, size));
    }

    private static PropertyEditor parsedBy(Function<String, ?> parser) {
        return new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text.isEmpty() ? null : parser.apply(text));
            }
        };
    }
}
