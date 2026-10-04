# Log

Newest first. Add one entry per working session.

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
