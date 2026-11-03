package com.wjx871.population.s2.aggregation;

import jakarta.xml.ws.WebFault;

import java.io.Serial;
import java.io.Serializable;

@WebFault(name = "InvalidPopulationMetrics", targetNamespace = "urn:com:wjx871:population:s2")
public class InvalidPopulationMetricsException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    private final FaultInfo faultInfo;

    public InvalidPopulationMetricsException(String code, String message) {
        super(message);
        this.faultInfo = new FaultInfo(code, message);
    }

    public InvalidPopulationMetricsException(String message, FaultInfo faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }

    public FaultInfo getFaultInfo() {
        return faultInfo;
    }

    public static class FaultInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String code;
        private String message;

        public FaultInfo() {
        }

        public FaultInfo(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
