package uhsuhjupjup.backend.techblog.learningnote.ui.dto;

import uhsuhjupjup.backend.techblog.learningnote.application.dto.GraphEdge;
import uhsuhjupjup.backend.techblog.learningnote.application.dto.GraphNode;
import uhsuhjupjup.backend.techblog.learningnote.application.dto.NoteGraphResult;

import java.util.List;

public record NoteGraphResponse(List<GraphNode> nodes, List<GraphEdge> edges) {

    public static NoteGraphResponse from(NoteGraphResult result) {
        return new NoteGraphResponse(result.nodes(), result.edges());
    }
}
