package uhsuhjupjup.backend.learningnote.ui;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.LoginMember;
import uhsuhjupjup.backend.learningnote.application.NoteRecommendationService;
import uhsuhjupjup.backend.learningnote.application.NoteService;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteRequest;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteResponse;
import uhsuhjupjup.backend.learningnote.ui.dto.RecommendedArticleResponse;
import uhsuhjupjup.backend.member.domain.Member;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final NoteRecommendationService noteRecommendationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@LoginMember Member member, @Valid @RequestBody NoteRequest request) {
        return NoteResponse.from(noteService.create(member, request.title(), request.content()));
    }

    @GetMapping
    public List<NoteResponse> list(@LoginMember Member member) {
        return noteService.findByMember(member.getId()).stream()
                .map(NoteResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public NoteResponse get(@LoginMember Member member, @PathVariable Long id) {
        return NoteResponse.from(noteService.get(id, member.getId()));
    }

    @PutMapping("/{id}")
    public NoteResponse update(@LoginMember Member member, @PathVariable Long id,
                               @Valid @RequestBody NoteRequest request) {
        return NoteResponse.from(noteService.update(id, member.getId(), request.title(), request.content()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginMember Member member, @PathVariable Long id) {
        noteService.delete(id, member.getId());
    }

    @GetMapping("/{id}/recommendations")
    public List<RecommendedArticleResponse> recommendations(@LoginMember Member member, @PathVariable Long id) {
        return noteRecommendationService.recommend(id, member.getId()).stream()
                .map(RecommendedArticleResponse::from)
                .toList();
    }
}
