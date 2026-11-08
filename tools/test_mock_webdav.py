import http.client
import threading
import unittest
from http.server import ThreadingHTTPServer

from mock_webdav import Handler

class MockWebDavTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def call(self, method, path, headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)
        try:
            conn.request(method, path, headers=headers or {})
            res = conn.getresponse()
            return res.status, dict(res.getheaders()), res.read()
        finally:
            conn.close()

    def test_multistatus(self):
        self.assertEqual(207, self.call("PROPFIND", "/AllMusic", {"Depth": "1"})[0])

    def test_no_infinity(self):
        self.assertEqual(403, self.call("PROPFIND", "/AllMusic", {"Depth": "infinity"})[0])

    def test_partial_range(self):
        status, headers, data = self.call("GET", "/AllMusic/test.wav", {"Range": "bytes=0-0"})
        self.assertEqual(206, status)
        self.assertEqual(b"R", data)
        self.assertEqual("bytes", headers["Accept-Ranges"])

    def test_error_codes(self):
        for code in (401, 403, 404, 429, 500, 503):
            self.assertEqual(code, self.call("PROPFIND", f"/errors/{code}")[0])

if __name__ == "__main__":
    unittest.main()
