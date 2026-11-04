package com.wjx871.population.s2.rmi;

import com.wjx871.population.s2.aggregation.InvalidPopulationMetricsException;
import com.wjx871.population.s2.aggregation.PopulationMetricsClient;
import com.wjx871.population.s2.aggregation.PopulationMetricsRemote;
import com.wjx871.population.s2.aggregation.PopulationMetricsRequest;
import com.wjx871.population.s2.aggregation.PopulationRemoteCallException;
import com.wjx871.population.s2.trace.S2TraceContext;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.rmi.ConnectIOException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.TimeUnit;

@Log4j2
public class RmiInvocationHandler implements InvocationHandler {

    private final int registryPort;
    private final int objectPort;

    public RmiInvocationHandler(int registryPort, int objectPort) {
        this.registryPort = registryPort;
        this.objectPort = objectPort;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "PopulationMetricsClientProxy[127.0.0.1:" + registryPort + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == (args == null ? null : args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        PopulationMetricsRequest request = args != null && args.length > 0
                && args[0] instanceof PopulationMetricsRequest value ? value : null;
        String traceId = request == null ? null : request.getTraceId();
        long started = System.nanoTime();
        String interfaceName = PopulationMetricsClient.class.getName();
        String failureStage = "registry_lookup";
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {

            log.info("S2 proxy resolving interface={} method={} registryHost={} registryPort={} expectedObjectPort={}",
                    interfaceName, method.getName(), RmiPorts.LOOPBACK_HOST, registryPort, objectPort);
            Registry registry = LocateRegistry.getRegistry(RmiPorts.LOOPBACK_HOST, registryPort,
                    new RmiSocketFactories.TimeoutClientSocketFactory(
                            RmiPorts.CONNECT_TIMEOUT_MILLIS, RmiPorts.READ_TIMEOUT_MILLIS, registryPort));
            PopulationMetricsRemote remote = (PopulationMetricsRemote) registry.lookup(RmiPorts.BINDING_NAME);
            String stubReference = RmiStubVerifier.verify(remote, objectPort);
            Method remoteMethod = PopulationMetricsRemote.class.getMethod(method.getName(), method.getParameterTypes());
            log.info("S2 proxy before interface={} method={} registryPort={} stubDescriptor={}",
                    interfaceName, method.getName(), registryPort, stubReference);
            failureStage = "remote_method_invocation";
            Object result = remoteMethod.invoke(remote, args);
            log.info("S2 proxy after interface={} method={} elapsedMs={} outcome=SUCCESS",
                    interfaceName, method.getName(), elapsedMillis(started));
            return result;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof InvalidPopulationMetricsException) {
                log.warn("S2 proxy after interface={} method={} traceId={} elapsedMs={} outcome=REJECTED errorCode=INVALID_REQUEST",
                        interfaceName, method.getName(), traceId, elapsedMillis(started));
                throw cause;
            }
            if (cause instanceof RemoteException remoteException) {
                throw classify(interfaceName, method, failureStage, started, remoteException);
            }
            log.error("S2 proxy after interface={} method={} traceId={} elapsedMs={} outcome=FAILED errorClass={}",
                    interfaceName, method.getName(), traceId, elapsedMillis(started), cause.getClass().getSimpleName());
            throw cause;
        } catch (NotBoundException | RemoteException exception) {
            throw classify(interfaceName, method, failureStage, started, exception);
        }
    }

    PopulationRemoteCallException classify(String interfaceName, Method method, String failureStage,
                                           long started, Throwable exception) {
        String code = contains(exception, SocketTimeoutException.class)
                ? "REMOTE_TIMEOUT"
                : isUnavailableTransport(exception) ? "REMOTE_UNAVAILABLE" : "REMOTE_FAILURE";
        log.warn("S2 proxy after interface={} method={} failureStage={} elapsedMs={} outcome=FAILED errorCode={} errorTypes={}",
                interfaceName, method.getName(), failureStage, elapsedMillis(started), code,
                safeExceptionTypes(exception));
        return new PopulationRemoteCallException(code, exception);
    }

    private boolean isUnavailableTransport(Throwable throwable) {
        return contains(throwable, ConnectException.class)
                || contains(throwable, ConnectIOException.class)
                || contains(throwable, NoSuchObjectException.class)
                || contains(throwable, NotBoundException.class)
                || contains(throwable, EOFException.class)
                || contains(throwable, SocketException.class);
    }

    private boolean contains(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private String safeExceptionTypes(Throwable throwable) {
        StringBuilder types = new StringBuilder();
        for (Throwable current = throwable; current != null && types.length() < 512; current = current.getCause()) {
            if (!types.isEmpty()) {
                types.append(" -> ");
            }
            types.append(current.getClass().getName());
        }
        return types.toString();
    }

    private long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
