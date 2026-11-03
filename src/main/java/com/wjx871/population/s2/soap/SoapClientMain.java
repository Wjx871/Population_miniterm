package com.wjx871.population.s2.soap;

import com.wjx871.population.s2.aggregation.InvalidPopulationMetricsException;
import com.wjx871.population.s2.aggregation.PopulationMetricsRequest;
import com.wjx871.population.s2.aggregation.PopulationMetricsSoapContract;
import com.wjx871.population.s2.trace.S2TraceContext;
import jakarta.xml.ws.Service;
import lombok.extern.log4j.Log4j2;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import javax.xml.namespace.QName;
import java.net.URL;
import java.util.UUID;

@Log4j2
public final class SoapClientMain {

    public static final String NAMESPACE = "urn:com:wjx871:population:s2";

    private SoapClientMain() {
    }

    public static void main(String[] args) throws Exception {
        String traceId = S2TraceContext.resolveTraceId(System.getProperty("s2.traceId"));
        String wsdl = System.getProperty("s2.soap.wsdl",
                SoapServerMain.endpointAddress() + "?wsdl");
        // JAX-WS loads WSDL before an HTTPConduit exists; bound that URLConnection bootstrap as well.
        System.setProperty("sun.net.client.defaultConnectTimeout", "1000");
        System.setProperty("sun.net.client.defaultReadTimeout", "2000");
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
          try {
            Service service = Service.create(new URL(wsdl), new QName(NAMESPACE, "PopulationMetricsSoapContractService"));
            PopulationMetricsSoapContract port = service.getPort(PopulationMetricsSoapContract.class);
            Client client = ClientProxy.getClient(port);
            if (client.getConduit() instanceof HTTPConduit conduit) {
                HTTPClientPolicy policy = new HTTPClientPolicy();
                policy.setConnectionTimeout(1000);
                policy.setReceiveTimeout(2000);
                policy.setAllowChunking(false);
                conduit.setClient(policy);
            }

            boolean invalid = Boolean.getBoolean("s2.demo.invalid");
            PopulationMetricsRequest request = new PopulationMetricsRequest(
                    "soap-demo-" + UUID.randomUUID().toString().substring(0, 8), traceId,
                    invalid ? -1 : 1200, 21, 30, 400);
            log.info("S2 SOAP client starting dataOrigin=SYNTHETIC_DEMO wsdl={} pid={} instanceId={}",
                    wsdl, ProcessHandle.current().pid(), S2TraceContext.instanceId());
            var result = port.aggregate(request);
            log.info("S2 SOAP client result requestId={} traceId={} residentCount={} floatingRegistrationToResidentRatio={} activeKeyPopulationToResidentRatio={} residentToActiveHouseholdRatio={}",
                    result.getRequestId(), result.getTraceId(), result.getResidentCount(),
                    result.getFloatingRegistrationToResidentRatio(), result.getActiveKeyPopulationToResidentRatio(),
                    result.getResidentToActiveHouseholdRatio());
          } catch (InvalidPopulationMetricsException exception) {
              log.error("S2 SOAP client received SOAP Fault errorCode={}", exception.getFaultInfo().getCode());
              System.exit(3);
          } catch (jakarta.xml.ws.WebServiceException exception) {
              log.error("S2 SOAP client unavailable errorClass={}", exception.getClass().getSimpleName());
              System.exit(4);
          }
        }
    }
}
