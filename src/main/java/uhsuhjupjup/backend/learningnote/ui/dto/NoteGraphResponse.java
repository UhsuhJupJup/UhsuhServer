package uhsuhjupjup.backend.learningnote.ui.dto;

import uhsuhjupjup.backend.learningnote.application.dto.GraphEdge;
import uhsuhjupjup.backend.learningnote.application.dto.GraphNode;
import uhsuhjupjup.backend.learningnote.application.dto.NoteGraphResult;

import java.util.List;

public record NoteGraphResponse(List<GraphNode> nodes, List<GraphEdge> edges) {

    public static NoteGraphResponse from(NoteGraphResult result) {
        return new NoteGraphResponse(result.nodes(), result.edges());
    }
}
