package uhsuhjupjup.backend.pipeline.collection.application.dto;

public record CollectionResult(int total, int succeeded, int transientFailed, int permanentFailed, int newArticles) {
}
