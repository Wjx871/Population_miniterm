package com.wjx871.population.s2.rmi;

import com.wjx871.population.s2.aggregation.PopulationMetricsRemote;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObjectInvocationHandler;

@Log4j2
public final class RmiStubVerifier {

    private RmiStubVerifier() {
    }

    public static String verify(PopulationMetricsRemote stub, int expectedObjectPort) throws RemoteException {
        if (!Proxy.isProxyClass(stub.getClass())) {
            throw new RemoteException("RMI stub is not a JDK remote proxy");
        }
        InvocationHandler handler = Proxy.getInvocationHandler(stub);
        if (!(handler instanceof RemoteObjectInvocationHandler remoteHandler)) {
            throw new RemoteException("Unexpected RMI stub invocation handler");
        }
        String referenceType = remoteHandler.getRef().getClass().getName();
        log.info("S2 RMI stub verified proxyClass={} remoteRefType={} expectedObjectEndpoint={}:{}; actual socket endpoint is checked by the client socket factory",
                stub.getClass().getName(), referenceType, RmiPorts.LOOPBACK_HOST, expectedObjectPort);
        return "proxyClass=" + stub.getClass().getName() + ",remoteRefType=" + referenceType;
    }
}
