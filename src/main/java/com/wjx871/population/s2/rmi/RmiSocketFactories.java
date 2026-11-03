package com.wjx871.population.s2.rmi;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class RmiSocketFactories {

    private static final ConcurrentLinkedQueue<String> OBSERVED_ENDPOINTS = new ConcurrentLinkedQueue<>();

    private RmiSocketFactories() {
    }

    public static void resetObservedEndpoints() {
        OBSERVED_ENDPOINTS.clear();
    }

    public static List<String> observedEndpoints() {
        return List.copyOf(OBSERVED_ENDPOINTS);
    }

    public static final class LoopbackServerSocketFactory implements RMIServerSocketFactory, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
            return new ServerSocket(port, 50, InetAddress.getByName(RmiPorts.LOOPBACK_HOST));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof LoopbackServerSocketFactory;
        }

        @Override
        public int hashCode() {
            return LoopbackServerSocketFactory.class.hashCode();
        }
    }

    public static final class TimeoutClientSocketFactory implements RMIClientSocketFactory, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final int connectTimeoutMillis;
        private final int readTimeoutMillis;
        private final int expectedPort;

        public TimeoutClientSocketFactory(int connectTimeoutMillis, int readTimeoutMillis, int expectedPort) {
            this.connectTimeoutMillis = connectTimeoutMillis;
            this.readTimeoutMillis = readTimeoutMillis;
            this.expectedPort = expectedPort;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            OBSERVED_ENDPOINTS.add(host + ":" + port);
            if (!RmiPorts.LOOPBACK_HOST.equals(host) || port != expectedPort) {
                throw new IOException("RMI endpoint does not match the configured loopback address and port");
            }
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(InetAddress.getByName(host), port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            return socket;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TimeoutClientSocketFactory that)) {
                return false;
            }
            return connectTimeoutMillis == that.connectTimeoutMillis
                    && readTimeoutMillis == that.readTimeoutMillis
                    && expectedPort == that.expectedPort;
        }

        @Override
        public int hashCode() {
            return Objects.hash(connectTimeoutMillis, readTimeoutMillis, expectedPort);
        }
    }
}
