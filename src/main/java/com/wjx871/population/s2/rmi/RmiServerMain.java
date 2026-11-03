package com.wjx871.population.s2.rmi;

import com.wjx871.population.s2.aggregation.PopulationMetricsCalculator;
import com.wjx871.population.s2.aggregation.PopulationMetricsRemote;
import com.wjx871.population.s2.trace.S2TraceContext;
import lombok.extern.log4j.Log4j2;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;

@Log4j2
public final class RmiServerMain {

    private RmiServerMain() {
    }

    public static void main(String[] args) throws Exception {
        int registryPort = RmiPorts.registryPort();
        int objectPort = RmiPorts.objectPort();
        if (registryPort < 1 || registryPort > 65535 || objectPort < 1 || objectPort > 65535
                || registryPort == objectPort) {
            throw new IllegalArgumentException("Registry and remote-object ports must be distinct values in 1..65535");
        }

        System.setProperty("java.rmi.server.hostname", RmiPorts.LOOPBACK_HOST);
        System.setProperty("s2.instanceId", S2TraceContext.instanceId());

        RmiSocketFactories.LoopbackServerSocketFactory serverSocketFactory =
                new RmiSocketFactories.LoopbackServerSocketFactory();
        Registry registry = LocateRegistry.createRegistry(registryPort,
                new RmiSocketFactories.TimeoutClientSocketFactory(
                        RmiPorts.CONNECT_TIMEOUT_MILLIS, RmiPorts.READ_TIMEOUT_MILLIS, registryPort),
                serverSocketFactory);
        long configuredDelay = Long.getLong("s2.rmi.demo-delay-millis", 0L);
        PopulationMetricsRemote implementation = new PopulationMetricsRemoteImpl(
                new PopulationMetricsCalculator(), configuredDelay);
        PopulationMetricsRemote stub = (PopulationMetricsRemote) UnicastRemoteObject.exportObject(
                implementation,
                objectPort,
                new RmiSocketFactories.TimeoutClientSocketFactory(
                        RmiPorts.CONNECT_TIMEOUT_MILLIS, RmiPorts.READ_TIMEOUT_MILLIS, objectPort),
                serverSocketFactory);
        registry.rebind(RmiPorts.BINDING_NAME, stub);

        log.info("S2 RMI ready advertisedHost={} registryPort={} objectPort={} binding={} pid={} instanceId={} demoDelayMillis={}",
                RmiPorts.LOOPBACK_HOST, registryPort, objectPort, RmiPorts.BINDING_NAME,
                ProcessHandle.current().pid(), S2TraceContext.instanceId(), configuredDelay);

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                registry.unbind(RmiPorts.BINDING_NAME);
            } catch (Exception ignored) {
                // Registry may already be stopping.
            }
            try {
                UnicastRemoteObject.unexportObject(implementation, true);
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (Exception ignored) {
                // Shutdown is best-effort after the process has stopped accepting calls.
            }
            stopped.countDown();
        }, "s2-rmi-shutdown"));
        stopped.await();
    }
}
