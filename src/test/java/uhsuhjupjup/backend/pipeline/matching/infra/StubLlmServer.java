package uhsuhjupjup.backend.pipeline.matching.infra;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

class StubLlmServer implements AutoCloseable {

    record Reply(int status, String body, Duration delay) {
    }

    record Received(String path, Map<String, List<String>> headers, String body) {
    }

    private final HttpServer server;
    private final Map<String, List<Reply>> replies = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();

    StubLlmServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void reply(String path, Reply... sequence) {
        replies.put(path, List.of(sequence));
    }

    int count(String path) {
        return counts.getOrDefault(path, new AtomicInteger()).get();
    }

    List<Received> received() {
        return received;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received.add(new Received(path, Map.copyOf(exchange.getRequestHeaders()), body));
        int index = counts.computeIfAbsent(path, key -> new AtomicInteger()).getAndIncrement();
        List<Reply> sequence = replies.getOrDefault(path, List.of(new Reply(404, "{}", Duration.ZERO)));
        Reply reply = sequence.get(Math.min(index, sequence.size() - 1));
        try {
            Thread.sleep(reply.delay().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        try {
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException ignored) {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
