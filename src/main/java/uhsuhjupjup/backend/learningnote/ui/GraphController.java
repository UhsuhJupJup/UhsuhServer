package uhsuhjupjup.backend.learningnote.ui;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uhsuhjupjup.backend.common.auth.LoginMember;
import uhsuhjupjup.backend.learningnote.application.GlobalGraphService;
import uhsuhjupjup.backend.learningnote.ui.dto.NoteGraphResponse;
import uhsuhjupjup.backend.member.domain.Member;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GlobalGraphService globalGraphService;

    @GetMapping
    public NoteGraphResponse global() {
        return NoteGraphResponse.from(globalGraphService.globalGraph());
    }

    @GetMapping("/mine")
    public NoteGraphResponse mine(@LoginMember Member member) {
        return NoteGraphResponse.from(globalGraphService.graph(member.getId()));
    }
}
