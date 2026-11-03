package com.wjx871.population.s2.rmi;

public final class RmiPorts {
    public static final String LOOPBACK_HOST = "127.0.0.1";
    public static final String BINDING_NAME = "PopulationMetricsService";
    public static final int DEFAULT_REGISTRY_PORT = 1199;
    public static final int DEFAULT_OBJECT_PORT = 1200;
    public static final int CONNECT_TIMEOUT_MILLIS = 1000;
    public static final int READ_TIMEOUT_MILLIS = 2000;

    private RmiPorts() {
    }

    public static int registryPort() {
        return Integer.getInteger("s2.rmi.registry-port", DEFAULT_REGISTRY_PORT);
    }

    public static int objectPort() {
        return Integer.getInteger("s2.rmi.object-port", DEFAULT_OBJECT_PORT);
    }
}
