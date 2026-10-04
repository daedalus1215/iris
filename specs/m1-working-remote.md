# M1: Working remote on this PC

**Status:** Not started
**Outcome:** From the phone on home Wi-Fi, open the web remote served by this PC, pair with Bedroom, and control it with presses that feel instant.

**In scope:** a Python backend replacing the NestJS one, frontend changes to use it, and running both natively on this PC (no Docker needed).
**Out of scope:** containers and dev/prod (M2), and the Android app (M3).

## Why the current code is replaced

- **Shell injection.** The backend puts the request's `mac` straight into a shell command (`backend/src/apple-tv/domain/apple-tv.service.ts:68` and `:182`), and the server accepts connections from the whole network.
- **Slow presses.** Every press starts a new `atvremote` process that finds the Apple TV and reconnects. Hold-to-repeat sends a request every 500 ms, then every 100 ms, so processes pile up.
- **Pairing doesn't work from the UI.** The frontend sends the placeholder `protocol: 'protocol_name'`, and the backend's pairing process never receives the PIN.

## Backend design (`backend/`)

**Stack:** Python 3.13 (pinned with uv; the image uses the same version), FastAPI + uvicorn, pyatv 0.18, pytest, ruff. Under this PC's Python 3.14, pyatv's `miniaudio` dependency had to be compiled from source, which is why the version is pinned.

### Configuration

All configuration comes from environment variables. Nothing is hardcoded.

| Variable | Default | Purpose |
|---|---|---|
| `IRIS_HOST` | `0.0.0.0` | Address to listen on |
| `IRIS_PORT` | `8080` | HTTP port |
| `IRIS_ENV` | `local` | `local`, `dev` or `prod`; shown in the UI |
| `IRIS_DATA_DIR` | `./data` | Where pairing credentials are stored (a pyatv `FileStorage` file) |
| `IRIS_SCAN_HOSTS` | empty | Comma-separated Apple TV IPs to scan directly. Empty means a multicast scan of the network. |
| `IRIS_STATIC_DIR` | empty | Built frontend to serve at `/`. Empty means the API only. |
| `IRIS_AUTH_TOKEN` | empty | If set, the API requires `Authorization: Bearer <token>` |
| `IRIS_LOG_LEVEL` | `info` | Log level |

### Components

- **settings:** reads the environment (pydantic-settings).
- **devices:** scans with `pyatv.scan(hosts=…, storage=…)`. A device's id is pyatv's `identifier`, not its MAC (D6).
- **connections:** one pyatv connection per device. It opens on first use and is reused afterwards, reconnects when the connection drops, and closes on shutdown. Commands to one device run one at a time (an `asyncio.Lock`), so held buttons can't build a backlog.
- **pairing:** wraps the `pyatv.pair()` handler (`begin` → `pin` → `finish`) for each protocol. Sessions live in memory and expire after 2 minutes. Credentials are saved to `FileStorage` under `IRIS_DATA_DIR`.
- **api:** the routes below.

### API v1

| Method and path | Body | Returns |
|---|---|---|
| `GET /api/health` | — | `{status, env, version}` |
| `GET /api/devices` | — | `[{id, name, address, model, os, paired: {companion, airplay}, connected}]` |
| `POST /api/devices/scan` | — | Same list, freshly scanned |
| `POST /api/devices/{id}/pairing/{protocol}` | — | `{session}`. The PIN appears on the TV. `protocol` is `companion` or `airplay`. |
| `POST /api/devices/{id}/pairing/{protocol}/pin` | `{session, pin}` | `{paired: true}` |
| `POST /api/devices/{id}/commands/{command}` | `{action?: "tap" \| "double_tap" \| "hold"}` | `204` |

**Commands** (an allowlist; anything else gets a 400): `up`, `down`, `left`, `right`, `select`, `menu`, `home`, `play_pause`, `play`, `pause`, `next`, `previous`, `skip_forward`, `skip_backward`, `volume_up`, `volume_down`, `turn_on`, `turn_off`. Whether volume works depends on the TV's HDMI setup; the spike will tell.

**Errors:**

| Code | When |
|---|---|
| 400 | Unknown command or protocol, or bad input |
| 401 | Missing or wrong token |
| 404 | Unknown device |
| 409 | Device not paired |
| 503 | Apple TV unreachable |
| 504 | Apple TV timed out |

## Web changes (`web/`)

- Point `api.ts` at the relative path `/api`, and have the Quasar dev server forward `/api` to `http://localhost:8080`. This drops `VITE_API_URL`, so the phone never needs a hardcoded IP.
- Key devices by `id` instead of MAC, and migrate the localStorage keys.
- Add a pairing dialog for unpaired devices: for each protocol, start pairing, then enter the PIN shown on the TV.
- Hold-to-repeat: keep at most one request in flight and send the next when the previous finishes, instead of the 100 ms turbo. Use `action: "hold"` where pyatv supports it.
- Add a Home button. Move ↓ below OK; it currently sits in the same row as ← OK →.
- Show an environment badge (hidden in prod) and the connection and pairing state reported by the backend.
- Nice to have: a web app manifest for a home-screen icon.

## Tasks

### Spike (needs you at the TV to read PINs)

- [ ] Pair this PC with Bedroom over Companion and AirPlay using `atvremote`
- [ ] Try each allowlisted command; note which protocol it needs and whether volume works
- [ ] Time a press with a new `atvremote` process vs a persistent pyatv connection (short script)
- [ ] Check deep sleep: does connecting wake Bedroom, and does `turn_on` work?

### Backend

- [ ] Scaffold the uv project (FastAPI, uvicorn, pyatv, pytest, ruff) with Python 3.13 pinned
- [ ] Settings from env
- [ ] Devices: scan and identifiers
- [ ] Connections: persistent, reconnecting, one command at a time per device
- [ ] Pairing endpoints, with `FileStorage` under `IRIS_DATA_DIR`
- [ ] Command endpoint and allowlist
- [ ] Health endpoint, optional token auth, serving the static frontend
- [ ] Tests against a fake Apple TV driver (no network)
- [ ] Remove the NestJS code and rewrite the README

### Web

- [ ] `/api` base URL and dev-server forwarding
- [ ] Device list keyed by id
- [ ] Pairing dialog
- [ ] Hold-to-repeat without a backlog
- [ ] Home button and d-pad layout fix
- [ ] Environment badge and connection state

### Run and verify

- [ ] One command that starts both locally (a Makefile at the repo root)
- [ ] Test from the phone over Wi-Fi at `http://172.16.0.102:<port>`
- [ ] Merge into `main` through a PR

## Acceptance criteria

- [ ] From the phone on home Wi-Fi, pair Bedroom and use every button in the UI.
- [ ] Once connected, the backend sends a command to the Apple TV in under 150 ms at p95, measured from its logs. Confirm or adjust this target after the spike.
- [ ] Holding an arrow moves continuously, and releasing stops within about 0.5 s (no backlog).
- [ ] A bad device id, command or protocol gets a 4xx. The backend runs no shell commands at all.
- [ ] Restarting the backend keeps pairings.
- [ ] No IPs, ports or paths are hardcoded; all come from env.

## Risks

- **tvOS 26.6 vs pyatv 0.18.** A new tvOS release can break pyatv. The spike will catch this; if it happens, check pyatv's issue tracker and pin a working version.
- **Unstable MAC.** Bedroom's MAC (`B6:B8:…`) is locally administered, which suggests a private, randomized Wi-Fi address. If it changes, MAC-based ids and DHCP reservations break. Use pyatv's identifier, and check whether the Apple TV's address stays stable.
- **Deep sleep.** The first command after the Apple TV has been idle may be slow, or need a wake-up first.
