package com.wjx871.population.s2.rmi;

import com.wjx871.population.s2.aggregation.PopulationMetricsClient;
import com.wjx871.population.s2.trace.S2TraceContext;

import java.lang.reflect.Proxy;

public final class PopulationMetricsClientFactory {

    private PopulationMetricsClientFactory() {
    }

    public static PopulationMetricsClient create(int registryPort, int objectPort) {
        return (PopulationMetricsClient) Proxy.newProxyInstance(
                PopulationMetricsClient.class.getClassLoader(),
                new Class<?>[]{PopulationMetricsClient.class},
                new RmiInvocationHandler(registryPort, objectPort));
    }

    public static String instanceId() {
        return S2TraceContext.instanceId();
    }
}
