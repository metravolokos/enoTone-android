#!/usr/bin/env python3
"""Local HTTP-only mock (CI/test use only), never used by production app."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote

MULTISTATUS = b'''<?xml version="1.0" encoding="UTF-8"?>
<d:multistatus xmlns:d="DAV:">
 <d:response><d:href>/AllMusic/</d:href><d:propstat><d:prop>
 <d:resourcetype><d:collection/></d:resourcetype><d:getetag>"fake-etag"</d:getetag>
 </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
</d:multistatus>'''
TEST_FILE = b"RIFFtest-audio-fixture"  # HTTP fixture; not a playable WAV.

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass  # No request/credential logging.

    def respond(self, code, body=b"", extra=None):
        self.send_response(code)
        for key, value in (extra or {}).items():
            self.send_header(key, value)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if body:
            self.wfile.write(body)

    def do_PROPFIND(self):
        p = unquote(self.path)
        if p.startswith("/errors/"):
            return self.respond(int(p.rsplit("/", 1)[-1]))
        if self.headers.get("Depth") == "infinity":
            return self.respond(403)
        if p.rstrip("/") in ("", "/AllMusic"):
            return self.respond(207, MULTISTATUS, {"Content-Type": "application/xml", "ETag": '"fake-etag"'})
        return self.respond(404)

    def do_GET(self):
        p = unquote(self.path)
        if p.startswith("/errors/"):
            return self.respond(int(p.rsplit("/", 1)[-1]))
        if p == "/AllMusic/test.wav":
            if self.headers.get("Range") == "bytes=0-0":
                return self.respond(206, TEST_FILE[:1], {
                    "Accept-Ranges": "bytes", "Content-Range": f"bytes 0-0/{len(TEST_FILE)}",
                })
            return self.respond(200, TEST_FILE, {"ETag": '"fake-etag"'})
        return self.respond(404)

def serve(host="127.0.0.1", port=8765):
    server = ThreadingHTTPServer((host, port), Handler)
    try:
        server.serve_forever()
    finally:
        server.server_close()

if __name__ == "__main__":
    serve()
