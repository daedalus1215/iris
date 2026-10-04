# Log

Newest first. Add one entry per working session.

## 2026-10-04 (direct port)

**Done**
- You'd rather not change the router, so phones now reach Iris on a port of its own (D17, home-lab D37). `web/nginx.conf` forwards `/api` to the backend, and the home lab publishes the web container on 8080.
- Shipped it to dev (`c68517c`): `:8080` serves the page and the API; the Traefik routes and dev's bare IP still work.
- Home-lab commit `1db6e25`: stack and env changes, plus D37, INVENTORY, README and a dns.md pointer.

**Next**
- You: merge `m2-prod` (merge commit). Then I rebuild dev from `main`, promote to prod2, and you point the Iris (prod) app at the prod host's `:8080` and pair both Apple TVs.

## 2026-10-04 (prod)

**Done**
- You merged PR #2 into `main`. The merge rewrote the branch's commit IDs, but the content matched the tested branch exactly.
- Rebuilt dev from `main` with `./compose/ship.sh iris dev` (`07fd521`); Living Room's dev pairing survived the redeploy.
- Promoted to prod2 with `./compose/ship.sh iris prod2`. It's healthy, reports `env: prod`, and lists both Apple TVs (unpaired). Dev and prod2 run the same image digest, and the other prod apps were unaffected.
- This Mac now has a `prod2-docker` context, plus an SSH config entry for prod2.
- Home-lab commit `deploy(iris): 07fd521 to dev+prod2`, not pushed.

**Next**
- The phone has to reach `iris.lan`: deploy the DNS names (`deploy-shared.sh dns`, waiting on the fingerprint check), and make the router change for `.lan`.
- Then pair prod from the app: Bedroom and Living Room, one PIN each. Also pair Bedroom on dev.
- Push the home-lab commits; DHCP reservations for both Apple TVs.

## 2026-10-04 (late)

**Done**
- Installed the SOPS age key on this Mac (`~/.config/sops/age/keys.txt`, mode 600). Deployed Iris to dev with `./compose/ship.sh iris dev` (`cb02c43`). Checked through Traefik: the page, the API, and both Apple TVs listed (not paired). Home-lab commit `0b41890`.
- Pairing in the Android app (branch `android-pairing`):
  - Header link button, and **Pair** under "isn't paired yet".
  - Show PIN, then type the PIN on the phone.
  - Companion is enough (one PIN per TV); AirPlay is optional.
  - 6 new core tests, 28 in total, run locally now that a JDK is installed.

- Gave Iris dev's bare-IP slot in the home lab (home-lab commit, `IRIS_HOSTS` in `dev.env`), so the phone reaches dev at `http://<dev-host-ip>` without `.lan` DNS. Prod's bare IP belongs to another app, so prod still waits for the router change.
- The laptop backend stopped with the previous session. It isn't a service; restart it with `make run` when it's needed.

- You tested Iris Dev on the phone against dev: paired Living Room over Companion and AirPlay from the app, and it works.

**Next**
- You: pair Bedroom on dev from the app (one Companion PIN).
- Deploy the DNS names (fingerprint check) and make the router change for `.lan`; then prod.

## 2026-10-04 (night)

**Done**
- Pulled home-network details out of the public repo (D15):
  - The app no longer has a built-in server address, and asks for one on first launch.
  - Specs use placeholders, with real values in the git-ignored `LOCAL.md`.
  - Tests use `192.0.2.x`.
- Started M2 on branch `deploy`, following the home-lab repo's runbooks (D16):
  - Dockerfiles for `backend/` and `web/`.
  - Checked that a container on `docker-dev` can scan both Apple TVs over bridge networking (D8 superseded).
  - Built and pushed `iris-backend` and `iris-web` at `ed7b9bd` with the home lab's `build-push.sh`.
  - Home-lab commit `0084d36`, not pushed: `compose/apps/iris.yml`, dev and prod2 env entries, `lan.hosts` names, INVENTORY (Apple TVs and their MACs), README row.
- Found that the Apple TVs' network MACs are real hardware addresses; the "randomized MAC" worry was about an AirPlay ID.

**Next**
- You:
  - Get the SOPS age key onto this Mac, or run `./compose/ship.sh iris dev --repo …` from a machine that has it.
  - Deploy the DNS names (`./compose/deploy-shared.sh dns`).
  - Then pair dev with both Apple TVs through `http://iris.dev.lan`.
- You: router change so phones resolve `.lan`; DHCP reservations for both Apple TVs.
- Open PRs (`backend`, `android`, `deploy`), merge them with merge commits, then `./compose/ship.sh iris prod2`.

**Blockers**
- The deploy needs the SOPS age key.

## 2026-10-04 (evening)

**Done**
- Committed M1 as three commits on `backend` and pushed it.
- Started M3 on branch `android` (made from `backend`):
  - Found that this Mac can't build Android apps: Google's `aapt2` is x86-only on Linux, and community ARM builds won't load on the 16 KB-page kernel. APKs build in GitHub Actions instead (D10).
  - Rebuilt the Android project on AGP 9.4.1 / Gradle 9.8 / Kotlin 2.4.20 / Compose. Current AndroidX requires AGP 9 (D11).
  - Wrote the app: a `core` Kotlin module (API client, hold-to-repeat, remote state, with tests), a remote screen, a settings dialog, phone volume keys controlling the TV, and dev/prod flavors.
- The first CI run passed (core tests, both APKs). The APKs are on the [android-dev pre-release](https://github.com/daedalus1215/iris/releases/tag/android-dev).

- First install on the phone: "no connection". The phone's requests never reached the backend. Cause: Android 17 blocks apps targeting API 37 from reaching the local network unless they hold the new `ACCESS_LOCAL_NETWORK` permission. The app now asks for it.

- Found the second Apple TV, "Living Room" (`<living-room-ip>`, Apple TV 4K gen 2). A multicast scan found nothing, because ufw drops the Apple TVs' direct replies to it (logged as UFW BLOCK from source port 5353). Added both addresses to `IRIS_SCAN_HOSTS` in the local `backend/.env`; the backend now lists both. Living Room isn't paired yet.
- Paired Living Room over Companion and AirPlay through the backend's pairing API, the first real use of it. Then sent it commands: 250 ms for the first (it connects), 4.7 ms after that.

**Next**
- You: reinstall Iris Dev from the release page, allow "Nearby devices", and try it against this PC's backend.
- Switch between Bedroom and Living Room from the web remote and the app.
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
- `make run` at the repo root serves everything at `http://<laptop-ip>:8080`.
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
- Scanned the network with pyatv 0.18.0 (through `uvx`). Found Bedroom: Apple TV 4K, tvOS 26.6, at `<bedroom-ip>`. Companion and AirPlay both require pairing; nothing is paired yet.
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
