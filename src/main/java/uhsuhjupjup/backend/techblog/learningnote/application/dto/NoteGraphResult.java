package uhsuhjupjup.backend.techblog.learningnote.application.dto;

import java.util.List;

public record NoteGraphResult(List<GraphNode> nodes, List<GraphEdge> edges) {
}
