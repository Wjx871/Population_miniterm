package com.wjx871.population.s2.rmi;

import com.wjx871.population.s2.aggregation.PopulationMetricsClient;
import com.wjx871.population.s2.aggregation.PopulationMetricsRequest;
import com.wjx871.population.s2.aggregation.PopulationRemoteCallException;
import org.junit.jupiter.api.Test;

import java.io.StreamCorruptedException;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.rmi.RemoteException;
import java.rmi.UnmarshalException;

import static org.assertj.core.api.Assertions.assertThat;

class RmiInvocationHandlerClassificationTest {

    @Test
    void onlyKnownTransportFailuresAreUnavailableAndTimeoutKeepsPrecedence() throws Exception {
        RmiInvocationHandler handler = new RmiInvocationHandler(1199, 1200);
        Method method = PopulationMetricsClient.class.getMethod("aggregate", PopulationMetricsRequest.class);

        assertThat(classify(handler, method, new RemoteException("remote execution failed")).getErrorCode())
                .isEqualTo("REMOTE_FAILURE");
        assertThat(classify(handler, method,
                new UnmarshalException("unexpected protocol data", new StreamCorruptedException()))
                .getErrorCode()).isEqualTo("REMOTE_FAILURE");
        assertThat(classify(handler, method,
                new UnmarshalException("connection closed", new SocketException("connection reset")))
                .getErrorCode()).isEqualTo("REMOTE_UNAVAILABLE");
        assertThat(classify(handler, method,
                new RemoteException("read timed out", new SocketTimeoutException()))
                .getErrorCode()).isEqualTo("REMOTE_TIMEOUT");
    }

    private static PopulationRemoteCallException classify(RmiInvocationHandler handler, Method method,
                                                          Throwable failure) {
        return handler.classify(PopulationMetricsClient.class.getName(), method,
                "remote_method_invocation", System.nanoTime(), failure);
    }
}
