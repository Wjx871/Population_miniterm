package com.wjx871.population.s2.rmi;

import com.wjx871.population.s2.aggregation.InvalidPopulationMetricsException;
import com.wjx871.population.s2.aggregation.PopulationMetricsClient;
import com.wjx871.population.s2.aggregation.PopulationMetricsRequest;
import com.wjx871.population.s2.aggregation.PopulationRemoteCallException;
import com.wjx871.population.s2.trace.S2TraceContext;
import lombok.extern.log4j.Log4j2;

import java.util.UUID;

@Log4j2
public final class RmiClientMain {

    private RmiClientMain() {
    }

    public static void main(String[] args) throws Exception {
        String traceId = S2TraceContext.resolveTraceId(System.getProperty("s2.traceId"));
        boolean invalid = Boolean.getBoolean("s2.demo.invalid");
        PopulationMetricsRequest request = new PopulationMetricsRequest(
                "rmi-demo-" + UUID.randomUUID().toString().substring(0, 8), traceId,
                invalid ? -1 : 1200, 21, 30, 400);
        PopulationMetricsClient client = PopulationMetricsClientFactory.create(
                RmiPorts.registryPort(), RmiPorts.objectPort());
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
            log.info("S2 RMI client starting dataOrigin=SYNTHETIC_DEMO proxyClass={} isJdkProxy={} registryHost={} registryPort={} expectedObjectPort={} pid={} instanceId={}",
                    client.getClass().getName(), java.lang.reflect.Proxy.isProxyClass(client.getClass()),
                    RmiPorts.LOOPBACK_HOST, RmiPorts.registryPort(), RmiPorts.objectPort(),
                    ProcessHandle.current().pid(), S2TraceContext.instanceId());
            try {
                var result = client.aggregate(request);
                log.info("S2 RMI client result requestId={} traceId={} residentCount={} floatingRegistrationToResidentRatio={} activeKeyPopulationToResidentRatio={} residentToActiveHouseholdRatio={}",
                        result.getRequestId(), result.getTraceId(), result.getResidentCount(),
                        result.getFloatingRegistrationToResidentRatio(), result.getActiveKeyPopulationToResidentRatio(),
                        result.getResidentToActiveHouseholdRatio());
            } catch (PopulationRemoteCallException exception) {
                log.error("S2 RMI client failed errorCode={}", exception.getErrorCode());
                System.exit(2);
            } catch (InvalidPopulationMetricsException exception) {
                log.error("S2 RMI client rejected errorCode={}", exception.getFaultInfo().getCode());
                System.exit(3);
            }
        }
    }
}
