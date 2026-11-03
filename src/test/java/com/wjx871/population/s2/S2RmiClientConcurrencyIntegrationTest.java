package com.wjx871.population.s2;

import com.wjx871.population.s2.aggregation.PopulationMetricsClient;
import com.wjx871.population.s2.aggregation.PopulationMetricsRequest;
import com.wjx871.population.s2.aggregation.PopulationMetricsResult;
import com.wjx871.population.s2.aggregation.PopulationRemoteCallException;
import com.wjx871.population.s2.rmi.PopulationMetricsClientFactory;
import com.wjx871.population.s2.rmi.RmiPorts;
import com.wjx871.population.s2.rmi.RmiServerMain;
import com.wjx871.population.s2.rmi.RmiSocketFactories.TimeoutClientSocketFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
class S2RmiClientConcurrencyIntegrationTest {

    private Process serverProcess;

    @AfterEach
    void stopOnlyTheChildRmiProcess() throws Exception {
        if (serverProcess == null || !serverProcess.isAlive()) {
            return;
        }
        serverProcess.destroy();
        if (!serverProcess.waitFor(5, TimeUnit.SECONDS)) {
            serverProcess.destroyForcibly();
            serverProcess.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void oneProxySupportsRepeatedAndConcurrentCallsAcrossRealJvmBoundary() throws Exception {
        int registryPort = freePort();
        int objectPort = freePortOtherThan(registryPort);
        Path serverLog = Path.of("target", "s2-rmi-concurrency-" + UUID.randomUUID() + ".log").toAbsolutePath();
        serverProcess = startServer(registryPort, objectPort, 0, serverLog);

        verifyConfiguredEndpoints(registryPort, objectPort);
        PopulationMetricsClient client = PopulationMetricsClientFactory.create(registryPort, objectPort);
        assertThat(java.lang.reflect.Proxy.isProxyClass(client.getClass())).isTrue();

        for (int call = 0; call < 12; call++) {
            PopulationMetricsRequest request = request("serial-" + call, "trace-serial-" + call,
                    1000 + call, 100 + call, 200 + call, 300 + call);
            assertEcho(request, client.aggregate(request));
        }

        int workerCount = 8;
        int callsPerWorker = 12;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int worker = 0; worker < workerCount; worker++) {
                int workerId = worker;
                calls.add(executor.submit(() -> {
                    for (int call = 0; call < callsPerWorker; call++) {
                        String suffix = workerId + "-" + call;
                        PopulationMetricsRequest request = request("parallel-" + suffix, "trace-parallel-" + suffix,
                                2000 + workerId * 100 + call,
                                400 + workerId * 100 + call,
                                500 + workerId * 100 + call,
                                600 + workerId * 100 + call);
                        assertEcho(request, client.aggregate(request));
                    }
                    return null;
                }));
            }
            for (Future<?> call : calls) {
                call.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        serverProcess.destroy();
        assertThat(serverProcess.waitFor(5, TimeUnit.SECONDS)).isTrue();
        PopulationMetricsRequest unavailableRequest = request("stopped-service", "trace-stopped", 1, 1, 1, 1);
        try {
            client.aggregate(unavailableRequest);
            fail("Expected a controlled error after stopping the independent RMI server.");
        } catch (PopulationRemoteCallException exception) {
            assertThat(exception.getErrorCode()).isEqualTo("REMOTE_UNAVAILABLE");
        }
    }

    @Test
    void timeoutClientSocketFactoryRejectsUnexpectedHostAndPort() {
        TimeoutClientSocketFactory registryFactory = new TimeoutClientSocketFactory(1000, 2000, 1199);
        assertThrows(IOException.class, () -> registryFactory.createSocket("192.0.2.10", 1199));
        assertThrows(IOException.class, () -> registryFactory.createSocket(RmiPorts.LOOPBACK_HOST, 1200));
    }

    @Test
    void delayedIndependentRmiServerStillProducesRemoteTimeout() throws Exception {
        int registryPort = freePort();
        int objectPort = freePortOtherThan(registryPort);
        Path serverLog = Path.of("target", "s2-rmi-timeout-" + UUID.randomUUID() + ".log").toAbsolutePath();
        serverProcess = startServer(registryPort, objectPort, 3500, serverLog);
        PopulationMetricsClient client = PopulationMetricsClientFactory.create(registryPort, objectPort);
        PopulationMetricsRequest request = request("delayed-service", "trace-delayed", 1000, 10, 20, 30);

        long started = System.nanoTime();
        try {
            client.aggregate(request);
            fail("Expected the client read timeout for a delayed independent RMI server.");
        } catch (PopulationRemoteCallException exception) {
            assertThat(exception.getErrorCode()).isEqualTo("REMOTE_TIMEOUT");
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        assertThat(elapsed).isBetween(Duration.ofMillis(1500), Duration.ofSeconds(5));
    }

    private void verifyConfiguredEndpoints(int registryPort, int objectPort) throws Exception {
        TimeoutClientSocketFactory registryFactory =
                new TimeoutClientSocketFactory(1000, 2000, registryPort);
        TimeoutClientSocketFactory objectFactory =
                new TimeoutClientSocketFactory(1000, 2000, objectPort);
        try (Socket registrySocket = registryFactory.createSocket(RmiPorts.LOOPBACK_HOST, registryPort);
             Socket objectSocket = objectFactory.createSocket(RmiPorts.LOOPBACK_HOST, objectPort)) {
            assertThat(registrySocket.getInetAddress().getHostAddress()).isEqualTo(RmiPorts.LOOPBACK_HOST);
            assertThat(registrySocket.getPort()).isEqualTo(registryPort);
            assertThat(objectSocket.getInetAddress().getHostAddress()).isEqualTo(RmiPorts.LOOPBACK_HOST);
            assertThat(objectSocket.getPort()).isEqualTo(objectPort);
        }
    }

    private Process startServer(int registryPort, int objectPort, long delayMillis, Path logFile) throws Exception {
        Files.createDirectories(logFile.getParent());
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path logConfig = Path.of("src", "main", "resources", "log4j2-spring.xml").toAbsolutePath();
        Process process = new ProcessBuilder(java,
                "-Dlog4j.configurationFile=" + logConfig,
                "-DLOG_DIR=" + logFile.getParent(),
                "-Ds2.rmi.registry-port=" + registryPort,
                "-Ds2.rmi.object-port=" + objectPort,
                "-Ds2.rmi.demo-delay-millis=" + delayMillis,
                "-cp", classPath, RmiServerMain.class.getName())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
        serverProcess = process;
        assertThat(process.pid()).isNotEqualTo(ProcessHandle.current().pid());

        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException("RMI server process exited before readiness: "
                        + Files.readString(logFile, StandardCharsets.UTF_8));
            }
            if (Files.exists(logFile)
                    && Files.readString(logFile, StandardCharsets.UTF_8).contains("S2 RMI ready")) {
                return process;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Timed out waiting for the independent RMI server process.");
    }

    private static PopulationMetricsRequest request(String requestId, String traceId, long resident,
                                                     long floating, long keyPopulation, long household) {
        return new PopulationMetricsRequest(requestId, traceId, resident, floating, keyPopulation, household);
    }

    private static void assertEcho(PopulationMetricsRequest request, PopulationMetricsResult result) {
        assertThat(result.getRequestId()).isEqualTo(request.getRequestId());
        assertThat(result.getTraceId()).isEqualTo(request.getTraceId());
        assertThat(result.getResidentCount()).isEqualTo(request.getResidentCount());
        assertThat(result.getFloatingRegistrationCount()).isEqualTo(request.getFloatingRegistrationCount());
        assertThat(result.getActiveKeyPopulationCount()).isEqualTo(request.getActiveKeyPopulationCount());
        assertThat(result.getActiveHouseholdCount()).isEqualTo(request.getActiveHouseholdCount());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static int freePortOtherThan(int excluded) throws IOException {
        int port;
        do {
            port = freePort();
        } while (port == excluded);
        return port;
    }
}
