# Deploying the fabric-analysis backend

`server.py` is a **reference/mock** server: plain Python + Pillow (the only
pip dependency, needed to decode uploads), deterministic mock verdicts
labelled `"engine": "MOCK"`.
It exists so the app's CLOUD engine can be exercised end-to-end without a
production backend. Replacing it with a real model service later requires
no app changes — only the URL in Settings changes.

## Option A — plain Python (fastest)

```bash
pip install -r tools/mock_server/requirements.txt   # Pillow
python3 tools/mock_server/server.py --port 8080
# smoke test:
curl http://localhost:8080/health
curl -s -X POST http://localhost:8080/v1/analyze \
     -F "image=@some_fabric.jpg;type=image/jpeg"
```

## Option B — Docker

```bash
docker build -t ntoma-mock-server tools/mock_server
docker run --rm -p 8080:8080 ntoma-mock-server
```

## Option C — systemd (a real VPS)

```ini
# /etc/systemd/system/ntoma-mock.service
[Unit]
Description=Ntoma Studio mock analysis server
After=network.target

[Service]
ExecStart=/usr/bin/python3 /opt/ntoma/server.py --port 8080
Restart=always
DynamicUser=yes

[Install]
WantedBy=multi-user.target
```

Put it behind nginx/caddy with TLS — the app only accepts `https://` URLs in
production use (plain `http://` is for LAN testing).

## Option D — fly.io (managed, free tier)

```bash
cd tools/mock_server
fly launch --no-deploy   # accept the generated app name or use fly.toml's
fly deploy
# app URL: https://<app>.fly.dev  -> put that in Settings -> Cloud
```

`fly.toml` in this directory is preconfigured (Dockerfile build, TLS forced).

## Pointing the app at it

1. Open **Settings → Analysis engine → Cloud** in the app.
2. Enter the base URL, e.g. `https://fabrics.example.com` (no trailing path —
   the app appends `/v1/analyze`).
3. Analyze a fabric: the results badge now reads **CLOUD** instead of
   ON-DEVICE DEMO. Clearing the URL reverts to on-device.

The client lives in `app/src/main/java/com/ntoma/studio/data/remote/CloudAnalysisClient.kt`
(Retrofit, multipart JPEG upload). The mock's response schema
(`{category, confidence, colors[], pattern, engine}`) is the contract the real
backend must honour.
