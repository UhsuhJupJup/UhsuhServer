package uhsuhjupjup.backend.learningnote.ui;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.context.request.async.DeferredResult;
import reactor.core.Disposable;
import uhsuhjupjup.backend.common.auth.LoginMember;
import uhsuhjupjup.backend.learningnote.application.NoteGraphService;
import uhsuhjupjup.backend.learningnote.application.NoteRecommendationService;
import uhsuhjupjup.backend.learningnote.application.NoteService;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteGraphResponse;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteRecommendationResponse;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteRequest;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteResponse;
import uhsuhjupjup.backend.member.domain.Member;

import java.time.Duration;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notes")
public class NoteController implements NoteControllerApi {

    private final NoteService noteService;
    private final NoteRecommendationService noteRecommendationService;
    private final NoteGraphService noteGraphService;
    private final Duration recommendationTimeout;

    public NoteController(NoteService noteService,
                          NoteRecommendationService noteRecommendationService,
                          NoteGraphService noteGraphService,
                          @Value("${note.recommendation.timeout:PT15S}") Duration recommendationTimeout) {
        this.noteService = noteService;
        this.noteRecommendationService = noteRecommendationService;
        this.noteGraphService = noteGraphService;
        this.recommendationTimeout = recommendationTimeout;
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@LoginMember Member member, @Valid @RequestBody NoteRequest request) {
        return NoteResponse.from(noteService.create(member, request.title(), request.content()));
    }

    @Override
    @GetMapping
    public List<NoteResponse> list(@LoginMember Member member) {
        return noteService.findSummaries(member.getId()).stream()
                .map(NoteResponse::from)
                .toList();
    }

    @Override
    @GetMapping("/{id}")
    public NoteResponse get(@LoginMember Member member, @PathVariable Long id) {
        return NoteResponse.from(noteService.getDetail(id, member.getId()));
    }

    @Override
    @PutMapping("/{id}")
    public NoteResponse update(@LoginMember Member member, @PathVariable Long id,
                               @Valid @RequestBody NoteRequest request) {
        return NoteResponse.from(noteService.update(id, member.getId(), request.title(), request.content()));
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginMember Member member, @PathVariable Long id) {
        noteService.delete(id, member.getId());
    }

    @Override
    @GetMapping("/{id}/recommendations")
    public DeferredResult<NoteRecommendationResponse> recommendations(@LoginMember Member member,
                                                                      @PathVariable Long id) {
        DeferredResult<NoteRecommendationResponse> result = new DeferredResult<>(recommendationTimeout.toMillis());
        Disposable aiCall = noteRecommendationService.recommendAsync(id, member.getId())
                .map(NoteRecommendationResponse::from)
                .subscribe(result::setResult, result::setErrorResult);
        result.onTimeout(() -> {
            aiCall.dispose();
            log.warn("노트 추천 시간 초과, AI 호출 취소 후 문자열 기반 추천 noteId={}", id);
            result.setResult(NoteRecommendationResponse.from(
                    noteRecommendationService.recommendWithoutAi(id, member.getId())));
        });
        result.onError(error -> aiCall.dispose());
        return result;
    }

    @Override
    @GetMapping("/{id}/graph")
    public NoteGraphResponse graph(@LoginMember Member member, @PathVariable Long id) {
        return NoteGraphResponse.from(noteGraphService.graph(id, member.getId()));
    }
}
