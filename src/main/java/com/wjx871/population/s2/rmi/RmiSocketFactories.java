package com.wjx871.population.s2.rmi;

import lombok.extern.log4j.Log4j2;

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

@Log4j2
public final class RmiSocketFactories {

    private RmiSocketFactories() {
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
            if (!RmiPorts.LOOPBACK_HOST.equals(host) || port != expectedPort) {
                throw new IOException("RMI endpoint does not match the configured loopback address and port");
            }
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(InetAddress.getByName(host), port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            log.info("S2 RMI socket connected host={} port={} connectTimeoutMillis={} readTimeoutMillis={}",
                    host, port, connectTimeoutMillis, readTimeoutMillis);
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
