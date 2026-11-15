import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 独立 Java 17 HTTP 探针，不修改 Spring Boot，不执行任何业务写入。 */
class JavaAiServiceSmoke {
    public static void main(String[] args) throws Exception {
        String key = System.getenv("YIXUN_AI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Set YIXUN_AI_API_KEY; the key will not be printed");
        }
        String base = System.getenv().getOrDefault("YIXUN_AI_BASE_URL", "http://127.0.0.1:8091");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        check(client, base, "/health", null, null, 200, "\"status\":\"UP\"");
        check(client, base, "/internal/policy-query", key,
              "{\"question\":\"户籍迁入需要什么材料？\",\"topK\":4}", 200, "\"knowledgeVersion\"");
        check(client, base, "/internal/policy-query", null,
              "{\"question\":\"迁入材料\"}", 401, "UNAUTHORIZED");
        check(client, base, "/internal/document-analysis", key,
              "{\"taskId\":\"JAVA-SMOKE\",\"businessType\":\"MIGRATION_IN\",\"materialIds\":[1]}",
              503, "MODEL_NOT_CONFIGURED");
        System.out.println("PASS: Java 17 client -> Python AI service (4 HTTP cases)");
    }

    private static void check(HttpClient client, String base, String path, String key,
                              String body, int expected, String marker) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        if (key != null) builder.header("X-AI-Service-Key", key);
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != expected || !response.body().contains(marker)) {
            throw new IllegalStateException("Failed " + path + ": HTTP " + response.statusCode());
        }
        System.out.println(path + " HTTP " + response.statusCode());
    }
}
