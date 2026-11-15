package com.wjx871.population.system;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

@Tag("unit")
class RabbitMqMetricsServiceTest {
    private HttpServer server;
    private RabbitMqProperties properties;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>("{\"items\":[{\"name\":\"queue-a\",\"messages_ready\":3,\"messages_unacknowledged\":2,\"consumers\":1,\"state\":\"running\"}],\"filtered_count\":1}");
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/queues/", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestURI().getRawPath()).endsWith("%2F");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("Basic ");
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        properties = new RabbitMqProperties();
        properties.setEnabled(true);
        properties.setUsername("fixture"); properties.setPassword("PRIVATE_PASSWORD");
        properties.setManagementUrl("http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void exposesQueueCountersWithoutCredentialsOrConnectionDetails() {
        var result = new RabbitMqMetricsService(properties, new ObjectMapper()).snapshot();
        assertThat(result).containsEntry("status", "UP").containsEntry("messagesReady", 3L)
                .containsEntry("messagesUnacknowledged", 2L).containsEntry("consumers", 1L).containsEntry("complete", true);
        assertThat(result.toString()).doesNotContain("PRIVATE_PASSWORD", "127.0.0.1", "Basic ");
    }
    @Test void failedManagementRequestReturnsAControlledStatusWithoutBody() {
        status.set(503); body.set("PRIVATE_PROVIDER_DETAIL");
        var result = new RabbitMqMetricsService(properties, new ObjectMapper()).snapshot();
        assertThat(result).containsEntry("status", "DOWN");
        assertThat(result.toString()).doesNotContain("PRIVATE_PROVIDER_DETAIL", "PRIVATE_PASSWORD");
    }
    @Test void largeQueueSetsAreExplicitlyMarkedAsPartial() {
        body.set("{\"items\":[],\"filtered_count\":300}");
        var result = new RabbitMqMetricsService(properties, new ObjectMapper()).snapshot();
        assertThat(result).containsEntry("complete", false).containsEntry("aggregationScope", "first_100_queues");
    }
    @Test void disabledMonitoringDoesNotContactBroker() {
        properties.setEnabled(false);
        assertThat(new RabbitMqMetricsService(properties, new ObjectMapper()).snapshot()).containsEntry("status", "DISABLED");
        assertThat(calls.get()).isZero();
    }
}
