"""Internal cloud-only HTTP fixture for proving proxy retries and non-replayed writes."""
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading

records = {method: [] for method in ("GET", "POST", "PUT", "DELETE", "PATCH")}
lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def handle_request(self):
        if self.command == "GET" and self.path == "/counts":
            with lock:
                payload = json.dumps(records).encode()
            status = 200
        else:
            body = self.rfile.read(int(self.headers.get("Content-Length", "0"))).decode()
            with lock:
                records[self.command].append(body)
            status = 200 if self.command == "GET" and os.getenv("FIXTURE_MODE") == "healthy" else 503
            payload = b'{"fixture":true}'
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = handle_request


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
