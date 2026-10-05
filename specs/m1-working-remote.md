# M1: Working remote on this PC

**Status:** In progress
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
- **connections:** one pyatv connection per device. It opens on first use and is reused afterwards, reconnects when the connection drops, and closes on shutdown. Commands and touch events to one device run one at a time (an `asyncio.Lock`), so held buttons can't build a backlog.
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
| `WS /api/devices/{id}/touch` | A stream of `{phase: "press" \| "move" \| "release", x, y}`, with x and y from 0 to 1000 (D18) | Only errors, as `{detail, status}`; the socket stays open |

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

## Spike results (2026-10-04)

- **Pairing:** done over Companion and AirPlay as "Iris (local)". Credentials are in `~/.local/share/iris/local/pyatv.conf` (mode 600).
- **Device id:** pyatv's identifier for Bedroom is its AirPlay device id, which equals the MAC. Bedroom also advertises a Companion UUID. The backend accepts any of a device's identifiers (D6).
- **What each protocol provides** (pyatv's feature report):

  | Feature | Companion only | AirPlay only | Both |
  |---|---|---|---|
  | Arrows, select, menu, home | yes | yes | yes |
  | Play/pause | yes | unavailable* | unavailable* |
  | Volume up/down | yes | unavailable* | unavailable* |
  | Home hold, top menu, current app | no | yes | yes |
  | Text entry | yes | no | yes |
  | Power on/off, power state | yes | yes | yes |

  *Reported unavailable while nothing was playing. With both protocols paired, pyatv sends these through AirPlay, which reports availability based on what's playing. Retest with something playing.
- **Latency:**

  | Method | Time per press |
  |---|---|
  | A new `atvremote` process per press (the old backend) | 1.5–3.6 s |
  | One open connection (spike script) | median 6.8 ms, p95 19.7 ms |
  | Through the new API, first press (includes connecting) | 157 ms |
  | Through the new API, later presses | about 6 ms |

- **pyatv API:** `remote_control.volume_up/down` are deprecated; the backend uses `audio.volume_up/down`.
- **Companion alone is enough:** pyatv's Companion protocol implements every command Iris sends: arrows, select, menu, home, play/pause, previous/next, skip and volume, plus power. So pairing Companion alone (one PIN per TV) gives a working remote. AirPlay adds things Iris doesn't use, like now-playing details.
- **Confirmed from the phone:** play/pause, previous/next, volume and power off/on all work. Nothing is left to check.
- **Touch (tested later on 2026-10-04):** pyatv's Companion touch gestures work on tvOS 26.6. Both TVs report Swipe, Action and Click as available. On Living Room, a drag streamed as press, moves and release moved the scrub bar's marker during playback; the video only jumps on a click, as with the Siri Remote. This is the basis for the touchpad (D18).

## Tasks

### Spike (needs you at the TV to read PINs)

- [x] Pair this PC with Bedroom over Companion and AirPlay (with a pyatv script rather than `atvremote`, since the PIN comes in mid-flow)
- [x] Try each command. Every button in the web remote works from the phone: arrows, OK, back, home, play/pause, previous/next, volume and power.
- [x] Time a press with a new `atvremote` process vs a persistent pyatv connection
- [x] Check power: `turn_off` and `turn_on` both work from the phone. A wake after a long deep sleep wasn't tested separately.

### Backend

- [x] Scaffold the uv project (FastAPI, uvicorn, pyatv, pytest, ruff) with Python 3.13 pinned
- [x] Settings from env
- [x] Devices: scan and identifiers
- [x] Connections: persistent, reconnecting, one command at a time per device
- [x] Pairing endpoints, with `FileStorage` under `IRIS_DATA_DIR`
- [x] Command endpoint and allowlist
- [x] Health endpoint, optional token auth, serving the static frontend
- [x] Tests against a fake Apple TV driver (no network): 24 tests
- [x] Remove the NestJS code and rewrite the README

### Web

- [x] `/api` base URL and dev-server forwarding
- [x] Device list keyed by id
- [x] Pairing dialog (built, not yet used against the real Apple TV)
- [x] Hold-to-repeat without a backlog: one request in flight, 400 ms delay, then a repeat every 150 ms
- [x] Home button and d-pad layout fix; play and pause merged into one button; volume buttons added
- [x] Environment badge. Pairing state is shown; live connection state isn't yet.
- [x] Touchpad (D18), with a switch to bring back the arrow buttons

### Run and verify

- [x] One command that starts both locally: `make run` at the repo root
- [x] Open this PC's firewall for the phone (ufw blocks incoming connections by default)
- [x] Test from the phone over Wi-Fi at `http://<laptop-ip>:8080`. Works, and presses feel instant.
- [ ] Merge into `main` through a PR

## Acceptance criteria

- [x] From the phone on home Wi-Fi, use every button in the UI. It feels very snappy.
- [ ] Pair through the web UI. The backend's pairing API was used for real on 2026-10-04 to pair Living Room (both protocols, PINs on the TV). The web dialog on top of it is still unused; it gets used when dev and prod pair in M2.
- [x] Once connected, the backend sends a command to the Apple TV in under 150 ms at p95. Measured: p95 19.7 ms in the spike, about 6 ms through the API.
- [x] Holding an arrow moves continuously, and releasing stops within about 0.5 s (no backlog).
- [x] A bad device id, command or protocol gets a 4xx. The backend runs no shell commands at all.
- [x] Restarting the backend keeps pairings.
- [x] No IPs, ports or paths are hardcoded; all come from env.

## Risks

- **tvOS 26.6 vs pyatv 0.18.** A new tvOS release can break pyatv. The spike will catch this; if it happens, check pyatv's issue tracker and pin a working version.
- **Unstable MAC (resolved).** This was a false alarm. The locally administered value is Bedroom's AirPlay device ID, not its network MAC. Both Apple TVs have real hardware MACs, so DHCP reservations will hold. The backend still accepts any of a device's identifiers (D6).
- **Deep sleep.** The first command after the Apple TV has been idle may be slow, or need a wake-up first.
