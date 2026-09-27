package uhsuhjupjup.backend.techblog.learningnote.application.dto;

public record GraphNode(String id, String type, String label, Boolean inNote, Integer rank, Long weight) {
}
