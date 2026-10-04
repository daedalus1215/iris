# Log

Newest first. Add one entry per working session.

## 2026-10-04 (evening)

**Done**
- Committed M1 as three commits on `backend` and pushed it.
- Started M3 on branch `android` (made from `backend`):
  - Found that this Mac can't build Android apps: Google's `aapt2` is x86-only on Linux, and community ARM builds won't load on the 16 KB-page kernel. APKs build in GitHub Actions instead (D10).
  - Rebuilt the Android project on AGP 9.4.1 / Gradle 9.8 / Kotlin 2.4.20 / Compose. Current AndroidX requires AGP 9 (D11).
  - Wrote the app: a `core` Kotlin module (API client, hold-to-repeat, remote state, with tests), a remote screen, a settings dialog, phone volume keys controlling the TV, and dev/prod flavors.
- The first CI run passed (core tests, both APKs). The APKs are on the [android-dev pre-release](https://github.com/daedalus1215/iris/releases/tag/android-dev).

- First install on the phone: "no connection". The phone's requests never reached the backend. Cause: Android 17 blocks apps targeting API 37 from reaching the local network unless they hold the new `ACCESS_LOCAL_NETWORK` permission. The app now asks for it.

**Next**
- You: reinstall Iris Dev from the release page, allow "Nearby devices", and try it against this PC's backend.
- You: run `gh auth login`, so PRs can be opened from here, and so CI logs can be read if a build fails.
- Open PRs: `backend` into `main` (M1), then `android`.

**Blockers**
- None.

## 2026-10-04 (afternoon)

**Done**
- M1 spike:
  - Paired this PC with Bedroom over Companion and AirPlay, as "Iris (local)".
  - Measured presses: about 7 ms over one open connection, vs 1.5–3.6 s with a new `atvremote` process per press.
  - Recorded which features each protocol provides (see M1, "Spike results").
- New Python backend (FastAPI + pyatv) in `backend/`, replacing NestJS: 24 tests, ruff clean. Tested live against Bedroom.
- Web remote switched to the new API:
  - Pairing dialog, hold-to-repeat without a backlog, Home and volume buttons, a single play/pause button, the d-pad fix, and an environment badge.
- `make run` at the repo root serves everything at `http://172.16.0.102:8080`.
- All of this is on branch `backend`, uncommitted.
- Phone test: you opened the firewall and controlled Bedroom from the phone over Wi-Fi. Every button works (hold-to-repeat, play/pause, previous/next, volume, power), and it's very snappy.

**Next**
- Commit, open a PR, and merge into `main`. That closes M1, except for pairing through the web UI, which gets used in M2.
- Then M2 (Docker) or M3 (Android).

**Blockers**
- None.

## 2026-10-04

**Done**
- Reviewed the three repos:
  - `iris-backend` runs `atvremote` once per button press, with a shell-injection hole.
  - `iris-frontend` is a working Vue/Quasar web remote, but pairing from it doesn't work.
  - `iris` is an empty Android template.
- Scanned the network with pyatv 0.18.0 (through `uvx`). Found Bedroom: Apple TV 4K, tvOS 26.6, 172.16.0.242. Companion and AirPlay both require pairing; nothing is paired yet.
- Checked this PC: Docker is installed but not running, and there's no Android tooling.
- Decided (D2–D4): a Python backend, a Docker Compose home lab, and a native Android client.
- Wrote the specs: the README and the M1, M2 and M3 milestone files.
- Turned `iris` into a monorepo (D7) on branch `monorepo`:
  - The Android app moved to `android/`.
  - `iris-backend` and `iris-frontend` were imported into `backend/` and `web/` with their full history. Both folders are byte-identical to their source repos.
  - The specs and CLAUDE.md moved in. Nothing is pushed yet.

**Next**
- You: review the `monorepo` branch, then push it and merge it into `main`. After that, archive `iris-backend` and `iris-frontend` on GitHub.
- M1 spike: pair this PC with Bedroom (you'll need to be at the TV for the PINs), try the commands, and measure latency.
- Answer the open questions in the README.

**Blockers**
- None.
