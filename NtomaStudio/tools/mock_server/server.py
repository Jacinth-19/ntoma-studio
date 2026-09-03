#!/usr/bin/env python3
"""Reference implementation of the Ntoma cloud analysis contract — a MOCK, not a product.

Contract (mirrors CloudAnalysisClient.kt):
    POST {base}/v1/analyze    multipart form, part name "image" (jpeg)
    -> 200 JSON {"category": "...", "confidence": 0.0, "pattern": "...", "texture": "..."}
    -> 400 on missing/undecodable image, 501 on unknown paths.

Purpose: lets anyone exercise the app's cloud path end-to-end (Settings -> cloud base URL)
without a real CV backend. The "analysis" here is an honest placeholder: average-colour
heuristics only, confidence capped at 0.5, and every response says engine=MOCK. The app
itself caps cloud confidence at 0.8 and labels on-device fallback honestly.

To use with a physical device on the same Wi-Fi:
    python3 tools/mock_server/server.py --port 8080
    then set the cloud base URL in the app to http://<this-machine-ip>:8080
For an emulator: adb reverse tcp:8080 tcp:8080 and use http://127.0.0.1:8080

Stdlib only. No training, no claims.
"""
import argparse
import json
from http.server import BaseHTTPRequestHandler, HTTPServer

ENGINE = "MOCK"


def classify_from_avg_rgb(r: float, g: float, b: float):
    """Toy classifier: average colour only. Deliberately weak and honest about it."""
    mx, mn = max(r, g, b), min(r, g, b)
    sat = 0 if mx == 0 else (mx - mn) / mx
    if sat < 0.12:
        return "COTTON_PLAIN", "SOLID"
    if r > g > b and sat > 0.3:
        return "WAX", "GEOMETRIC"
    if b > r and b > g:
        return "DENIM", "SOLID"
    if r > 120 and g > 90 and b < 80:
        return "KENTE_PRINT", "STRIPED"
    return "ANKARA", "ORGANIC"


def avg_rgb(jpeg: bytes):
    try:
        from PIL import Image
        import io
        im = Image.open(io.BytesIO(jpeg)).convert("RGB").resize((16, 16))
        px = list(im.getdata())
        n = len(px)
        return (sum(p[0] for p in px) / n, sum(p[1] for p in px) / n, sum(p[2] for p in px) / n)
    except Exception:
        return None


def extract_image(body: bytes, content_type: str):
    if "boundary=" not in content_type:
        return None
    boundary = content_type.split("boundary=")[-1].strip().encode()
    for part in body.split(b"--" + boundary):
        if b'name="image"' in part:
            head, _, payload = part.partition(b"\r\n\r\n")
            return payload.rstrip(b"\r\n--")
    return None


class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, payload: dict):
        data = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if self.path.rstrip("/") != "/v1/analyze":
            return self._send(501, {"error": "unknown path; contract is POST /v1/analyze"})
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        image = extract_image(body, self.headers.get("Content-Type", ""))
        if not image:
            return self._send(400, {"error": 'multipart part "image" is required'})
        rgb = avg_rgb(image)
        if rgb is None:
            return self._send(400, {"error": "image could not be decoded (Pillow needed for the mock classifier)"})
        category, pattern = classify_from_avg_rgb(*rgb)
        self._send(200, {
            "category": category,
            "confidence": 0.5,   # mock confidence, always middling and capped below the app's 0.8
            "pattern": pattern,
            "texture": "WOVEN",
            "engine": ENGINE,
        })

    def do_GET(self):
        if self.path.rstrip("/") == "/health":
            return self._send(200, {"status": "ok", "engine": ENGINE})
        self._send(501, {"error": "this mock serves POST /v1/analyze and GET /health only"})

    def log_message(self, fmt, *args):
        print(f"[mock] {self.address_string()} {fmt % args}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8080)
    args = ap.parse_args()
    print(f"MOCK analysis server (not a real backend) on http://{args.host}:{args.port}")
    HTTPServer((args.host, args.port), Handler).serve_forever()
