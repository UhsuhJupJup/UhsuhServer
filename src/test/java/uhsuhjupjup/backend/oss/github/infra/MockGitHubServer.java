package uhsuhjupjup.backend.oss.github.infra;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MockGitHubServer {

    private static final Duration STALL = Duration.ofSeconds(5);

    private final String host;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<ReceivedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile HttpHandler handler = exchange -> json(exchange, 500, "{}");

    MockGitHubServer(String host) throws IOException {
        this.host = host;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(host), 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new ReceivedRequest(
                    exchange.getRequestURI().getRawPath(), new Headers(exchange.getRequestHeaders())));
            handler.handle(exchange);
        });
        server.setExecutor(executor);
        server.start();
    }

    void respond(HttpHandler handler) {
        this.handler = handler;
    }

    String url(String path) {
        return "http://" + host + ":" + server.getAddress().getPort() + path;
    }

    List<ReceivedRequest> requests() {
        return requests;
    }

    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static void redirect(HttpExchange exchange, int status, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    static void stall(HttpExchange exchange) {
        try {
            Thread.sleep(STALL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        exchange.close();
    }

    record ReceivedRequest(String rawPath, Headers headers) {

        String header(String name) {
            return headers.getFirst(name);
        }
    }
}
