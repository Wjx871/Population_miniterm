package com.wjx871.population.s2.soap;

import com.wjx871.population.s2.aggregation.PopulationMetricsSoapContract;
import com.wjx871.population.s2.trace.S2TraceContext;
import lombok.extern.log4j.Log4j2;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;

import java.util.concurrent.CountDownLatch;

@Log4j2
public final class SoapServerMain {

    public static final String LOOPBACK_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8091;
    public static final String SERVICE_PATH = "/services/statistics";

    private SoapServerMain() {
    }

    public static int port() {
        return Integer.getInteger("s2.soap.port", DEFAULT_PORT);
    }

    public static String endpointAddress() {
        return "http://" + LOOPBACK_HOST + ":" + port() + SERVICE_PATH;
    }

    public static void main(String[] args) throws Exception {
        int port = port();
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("SOAP port must be in 1..65535");
        }
        System.setProperty("s2.instanceId", S2TraceContext.instanceId());

        JaxWsServerFactoryBean factory = new JaxWsServerFactoryBean();
        factory.setServiceClass(PopulationMetricsSoapContract.class);
        factory.setServiceBean(new PopulationMetricsSoapServiceImpl());
        factory.setAddress(endpointAddress());
        Server server = factory.create();
        log.info("S2 SOAP ready endpoint={} wsdl={} pid={} instanceId={}", endpointAddress(),
                endpointAddress() + "?wsdl", ProcessHandle.current().pid(), S2TraceContext.instanceId());

        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
                server.destroy();
            } finally {
                stopped.countDown();
            }
        }, "s2-soap-shutdown"));
        stopped.await();
    }
}
