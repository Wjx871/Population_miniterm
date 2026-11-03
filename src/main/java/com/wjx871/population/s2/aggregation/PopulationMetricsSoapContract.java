package com.wjx871.population.s2.aggregation;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;

@WebService(name = "PopulationMetricsPortType", targetNamespace = "urn:com:wjx871:population:s2")
@SOAPBinding(parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public interface PopulationMetricsSoapContract {
    @WebMethod(operationName = "aggregate")
    PopulationMetricsResult aggregate(PopulationMetricsRequest request)
            throws InvalidPopulationMetricsException;
}
