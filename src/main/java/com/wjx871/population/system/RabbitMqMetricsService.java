package com.wjx871.population.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

/** Read-only management API adapter. Never requests message contents or returns credentials. */
@Service
public class RabbitMqMetricsService {
    private final RabbitMqProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;
    public RabbitMqMetricsService(RabbitMqProperties properties, ObjectMapper json) {
        this.properties = properties; this.json = json;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.getTimeoutMs())).build();
    }
    public Map<String, Object> snapshot() {
        if (!properties.isEnabled()) return Map.of("enabled", false, "status", "DISABLED");
        try {
            String base = properties.getManagementUrl().replaceAll("/+$", "");
            String vhost = URLEncoder.encode(properties.getVirtualHost(), StandardCharsets.UTF_8).replace("+", "%20");
            String url = base + "/api/queues/" + vhost + "?page=1&page_size=100&pagination=true&sort=name";
            String credentials = Base64.getEncoder().encodeToString((properties.getUsername() + ":" + properties.getPassword()).getBytes(StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Authorization", "Basic " + credentials).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return down(response.statusCode() == 401 || response.statusCode() == 403 ? "MANAGEMENT_AUTH_FAILED" : "MANAGEMENT_UNAVAILABLE");
            JsonNode root = json.readTree(response.body());
            JsonNode items = root.isArray() ? root : root.path("items");
            if (!items.isArray()) return down("INVALID_MANAGEMENT_RESPONSE");
            List<Map<String, Object>> queues = new ArrayList<>();
            long ready = 0, unacked = 0, consumers = 0;
            for (JsonNode queue : items) {
                if (queues.size() == 100) break;
                long r = queue.path("messages_ready").asLong(), u = queue.path("messages_unacknowledged").asLong(), c = queue.path("consumers").asLong();
                ready += r; unacked += u; consumers += c;
                queues.add(Map.of("name", queue.path("name").asText(), "messagesReady", r,
                        "messagesUnacknowledged", u, "consumers", c, "state", queue.path("state").asText()));
            }
            long total = root.isArray() ? root.size() : root.path("filtered_count").asLong(root.path("total_count").asLong(items.size()));
            var result = new LinkedHashMap<String, Object>();
            result.put("enabled", true); result.put("status", "UP"); result.put("queueCount", total);
            result.put("sampledQueues", queues.size()); result.put("complete", total <= queues.size());
            result.put("aggregationScope", total <= queues.size() ? "virtual_host" : "first_100_queues");
            result.put("messagesReady", ready); result.put("messagesUnacknowledged", unacked); result.put("consumers", consumers);
            result.put("queues", queues);
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return down("MANAGEMENT_INTERRUPTED");
        } catch (Exception ex) { return down("MANAGEMENT_UNAVAILABLE"); }
    }
    private Map<String, Object> down(String reason) { return Map.of("enabled", true, "status", "DOWN", "reason", reason); }
}
