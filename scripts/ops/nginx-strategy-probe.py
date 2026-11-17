"""Probe Nginx from distinct loopback addresses inside its test container namespace."""
from concurrent.futures import ThreadPoolExecutor
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from queue import Queue
from socket import create_connection
from threading import Event, Thread
from time import monotonic, sleep


INSTANCES = {"backend-1", "backend-2"}
INSTANCES_BY_PORT = {18184: "backend-1", 18185: "backend-2"}
HOLD_STARTED = Queue()
RELEASE_HOLD = Event()


def request_instance(port, path="/api/health", source="127.0.0.2"):
    connection = HTTPConnection("127.0.0.1", port, timeout=15, source_address=(source, 0))
    try:
        connection.request("GET", path)
        response = connection.getresponse()
        instance = response.getheader("X-Instance-Id")
        body = response.read()
        assert response.status == 200 and instance in INSTANCES, (port, response.status, instance)
        if path == "/api/health":
            assert json.loads(body)["data"]["database"] == "UP", instance
        return instance
    finally:
        connection.close()


class ProbeUpstream(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def do_GET(self):
        instance = INSTANCES_BY_PORT[self.server.server_port]
        if self.path == "/api/hold":
            HOLD_STARTED.put(instance)
            if not RELEASE_HOLD.wait(timeout=12):
                self.send_error(504)
                return
        self.send_response(200)
        self.send_header("X-Instance-Id", instance)
        self.send_header("Content-Length", "0")
        self.end_headers()


def check_least_conn():
    live_routes = [request_instance(18181) for _ in range(8)]
    assert set(live_routes) == INSTANCES, live_routes
    upstreams = [ThreadingHTTPServer(("127.0.0.1", port), ProbeUpstream) for port in INSTANCES_BY_PORT]
    for upstream in upstreams:
        Thread(target=upstream.serve_forever, daemon=True).start()
    try:
        with ThreadPoolExecutor(max_workers=1) as requests:
            hold = requests.submit(request_instance, 18183, "/api/hold")
            try:
                busy = HOLD_STARTED.get(timeout=5)
                probes = [request_instance(18183, "/api/probe") for _ in range(6)]
                assert not hold.done(), "The original upstream connection must still be active"
                assert set(probes) == INSTANCES - {busy}, (busy, probes)
            finally:
                RELEASE_HOLD.set()
            assert hold.result() == busy
        return {"live_routes": live_routes, "held_instance": busy, "routes_while_held": probes}
    finally:
        for upstream in upstreams:
            upstream.shutdown()
            upstream.server_close()


def check_ip_hash():
    # Nginx IPv4 ip_hash groups addresses by their first three octets.
    sources = ("127.0.0.2", "127.0.0.3", "127.0.1.2")

    def routes(source):
        return [request_instance(18182, source=source) for _ in range(6)]

    with ThreadPoolExecutor(max_workers=len(sources)) as clients:
        routes_by_source = dict(zip(sources, clients.map(routes, sources)))
    assert all(len(set(routes)) == 1 for routes in routes_by_source.values()), routes_by_source
    assert routes_by_source[sources[0]] == routes_by_source[sources[1]], routes_by_source
    assert {routes[0] for routes in routes_by_source.values()} == INSTANCES, routes_by_source
    return routes_by_source


def main():
    # Reload is asynchronous. Wait until the strategy listeners are available.
    deadline = monotonic() + 5
    for port in range(18180, 18184):
        while True:
            try:
                with create_connection(("127.0.0.1", port), timeout=1):
                    break
            except OSError:
                if monotonic() >= deadline:
                    raise
                sleep(0.1)
    round_robin = [request_instance(18180) for _ in range(12)]
    assert set(round_robin) == INSTANCES, round_robin
    assert all(left != right for left, right in zip(round_robin, round_robin[1:])), round_robin
    print(json.dumps({
        "round_robin": round_robin,
        "least_conn": check_least_conn(),
        "ip_hash": check_ip_hash(),
    }))


if __name__ == "__main__":
    main()
