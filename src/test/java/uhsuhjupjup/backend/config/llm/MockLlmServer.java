package uhsuhjupjup.backend.config.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
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
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MockLlmServer {

    private static final Duration STALL = Duration.ofSeconds(5);
    private static final String API_KEY = "test-key";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicInteger received = new AtomicInteger();
    private final List<ReceivedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile List<HttpHandler> responses = List.of(exchange -> json(exchange, 500, "{}", Map.of()));

    public MockLlmServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            long receivedAtNanos = System.nanoTime();
            int order = received.incrementAndGet();
            Headers headers = new Headers(exchange.getRequestHeaders());
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new ReceivedRequest(receivedAtNanos, headers, body));
            List<HttpHandler> script = responses;
            script.get(Math.min(order, script.size()) - 1).handle(exchange);
        });
        server.setExecutor(executor);
        server.start();
    }

    public AnthropicClient anthropicClient(LlmCallLimits limits) {
        return LlmClients.withLimits(AnthropicOkHttpClient.builder().baseUrl(baseUrl()).apiKey(API_KEY), limits)
                .build();
    }

    public void respondInOrder(HttpHandler... handlers) {
        this.responses = List.of(handlers);
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getAddress().getHostAddress() + ":" + server.getAddress().getPort();
    }

    public int requestCount() {
        return requests.size();
    }

    public String requestBody(int request) {
        return requests.get(request).body();
    }

    String header(int request, String name) {
        return requests.get(request).headers().getFirst(name);
    }

    Duration gapBetweenRequests(int earlier, int later) {
        return Duration.ofNanos(requests.get(later).receivedAtNanos() - requests.get(earlier).receivedAtNanos());
    }

    public Duration sinceFirstRequest() {
        return Duration.ofNanos(System.nanoTime() - requests.get(0).receivedAtNanos());
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    public static HttpHandler status(int status, String body) {
        return status(status, body, Map.of());
    }

    public static HttpHandler status(int status, String body, Map<String, String> headers) {
        return exchange -> json(exchange, status, body, headers);
    }

    public static HttpHandler stall() {
        return exchange -> {
            try {
                Thread.sleep(STALL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        };
    }

    public static HttpHandler trickle(int status, String body, Duration interval, Duration total) {
        return exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (Duration sent = Duration.ZERO; sent.compareTo(total) < 0; sent = sent.plus(interval)) {
                    out.write(' ');
                    out.flush();
                    Thread.sleep(interval);
                }
                out.write(body.getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    private static void json(HttpExchange exchange, int status, String body, Map<String, String> headers)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        headers.forEach(exchange.getResponseHeaders()::set);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private record ReceivedRequest(long receivedAtNanos, Headers headers, String body) {
    }
}
