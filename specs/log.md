# Log

Newest first. Add one entry per working session.

## 2026-10-05 (smoother swipes)

**Done**
- You reported swipes as slightly janky, with a swipe right sometimes snapping back left. The cause was D18's known limit: touch times taken on arrival, plus the finger sliding back as it lifts (D20).
- On branch `smooth-swipes`, one commit per change:
  - Backend: touch events take the finger's time `t`, and the backend sends pyatv's Companion touch event with that time. 41 tests (3 new, including one that checks the pyatv internals); no failures in 10 single-core runs.
  - Android and web send each touch's own time.
  - Android and web ignore a backward slide at lift-off. Checked on the web in headless Chromium against the mock backend: a swipe right whose finger slid 6 px back as it lifted released where the drag last was, and one that pushed on kept the extra.
- You tried the APK against this PC's backend: swipes right were better, but almost every swipe left was still bad.
  - With the backend logging each touch, 21 swipes showed no backward steps, and phone timing was steady at 16–17 ms.
  - The difference: over their last third, left swipes curved 12–30° downward (a thumb's arc), while right swipes stayed within 5°.
  - Fix (D20): a swipe that sets out within 30° of an axis stays on it. This is `SwipeRail` in Android core (4 tests, 53 in total), plus the same rule on the web. Replayed in headless Chromium: swipe #7's arcing path reached the backend perfectly level, a wobbly upward swipe stayed in its column, and a diagonal stayed free.
- This branch also carries the `log-keyboard-shipped` commit, so that PR can be closed.

**Next**
- Merge, then ship to dev and prod2. The timing fix needs the new backend; the lift-off fix is in the apps alone.
- You tried again: a swipe left still snapped right as you lifted. In 102 logged swipes, no left swipe had a single rightward step, so the snap came from the TV. Many left swipes had run off the touchpad's left edge (pinned at 0). You then noticed that swipes starting on the right side were fine, and ones starting left of the middle weren't.
  - Fix (D21): every drag starts in the middle of the TV's touchpad and moves with the finger. Android and web, one commit each. Replayed on the web: a left swipe from near the pad's left edge and a right swipe from near its right edge both started at 500 and moved evenly.
  - A scripted five-swipe test was prepared but not run. It sends paced touches through the backend's touch socket, 16 ms apart, with `t`: left across the middle (700→400), the same then resting 300 ms, left into the edge (400→0) then resting, right then resting (300→600), and left from the centre (500→200).
- You: install the new APK and try swiping both ways on the TV, starting anywhere on the pad. If left still snaps, run the scripted test next; if it's right, look at how far a swipe moves.

**Blockers**
- None.

## 2026-10-05 (keyboard shipped)

**Done**
- You merged the keyboard PR (#7). `main` (`2651f83`) has the same content CI tested on `keyboard` (`70fbb1a`).
- Shipped `2651f83` to dev with `./compose/ship.sh iris dev`, then promoted the same image to prod2. Both are healthy.
- Checked both through nginx on `:8080`, the phones' path, without typing or touching anything. On each paired TV, the keyboard socket reports the text field (none open), and the touch socket answers.
  - Prod: Bedroom and Living Room are both paired.
  - Dev: Living Room only.
- Home-lab commit `deploy(iris): 2651f83 to dev+prod2`, not pushed.

**Next**
- You: push the home-lab commit.
- You: try a search on each TV with the phone's keyboard, in the app (dev or prod) and the web remote. Real focus events haven't been seen yet.
- Pair Bedroom on dev.

**Blockers**
- None.

## 2026-10-05 (keyboard)

**Done**
- You merged the touchpad PR (#6, from `touchpad-a`) and pulled `main`. Dev runs `36961ce`, which isn't on `main` by ID, but `main`'s `b56ea5e` has identical content.
- Typing with the phone's keyboard (D19), on branch `keyboard`, one commit per step:
  - Backend: a fix first. pyatv holds listeners weakly, and the connection-lost listener was being collected at once, so lost connections went unnoticed until a command retried. The test fake now holds listeners weakly too, which reproduced it.
  - Backend: the keyboard WebSocket. 38 tests (7 new), ruff clean, and no failures in 20 single-core runs. Checked against both real TVs without typing anything: the socket connects to pyatv's keyboard and reports no text field open.
  - Android: the keyboard client and the controller in core, with 10 new tests (49 in total). The dialog, a header button and a keyboard icon (Material Symbols, like the others) are in the app.
  - Web: the keyboard helper and the dialog. Driven in headless Chromium against a mock backend: the dialog opens when the TV focuses a field, starts from its text, sends each change, and closing it leaves a "Type on the TV" button until the field loses focus.

**Next**
- Push `keyboard`, so CI builds the APK (the app module only builds there, D10).
- You: try a search on each TV, from the app and the web remote. The real TVs' focus events haven't been seen yet; only the starting state has.
- Merge with a merge commit, then ship to dev and prod2.

**Blockers**
- None.

## 2026-10-04 (touchpad)

**Done**
- Touch spike: pyatv's touch gestures work on tvOS 26.6. Both TVs report Swipe, Action and Click as available. On Living Room, a streamed drag moved the scrub bar's marker during playback (the video only jumps on a click), so the Apple TV treats the events as a finger on its touchpad.
- The touchpad (D18), on branch `touchpad`:
  - Backend: the WebSocket `/api/devices/{id}/touch`, passed to pyatv's `touch.action`. 31 tests (7 new), ruff clean. Checked against the running server without touching a TV: bad events get errors back.
  - Android: the touchpad fills the middle of the screen. A tap selects, a long press holds select, and the arrow buttons are a setting. The screen no longer scrolls, and the app is portrait only. Core: 39 tests (11 new). The app module only builds in CI (D10).
  - Web: the same touchpad, with a switch for the arrow buttons. Driven in headless Chromium, emulating a phone, against a mock backend: a tap sends `select`, a drag streams a press, moves and a release, a long press sends `select` with `hold`, and the page doesn't scroll.
  - nginx passes WebSocket upgrades through to the backend, and the dev server proxies them.
  - CI: two runs failed on a flaky new core test. MockWebServer wouldn't shut down around a half-closed socket, about half the time on one CPU core. The test server now finishes the closing handshake: 0 failures in 30 single-core runs. CI also reports failed tests and compile errors as annotations, which show without signing in to GitHub.
- The phone's volume buttons already control the TV in the app; I wrongly suggested that as new.

- Shipped `36961ce` (branch `touchpad`) to dev with `./compose/ship.sh iris dev`. Both containers are healthy. The touch socket answers through nginx on `:8080` (the phones' path) and through Traefik on dev's bare IP; checked with bad events only, so no TV was touched. The home-lab change (`IRIS_TAG` in `dev.env`) isn't committed yet.

**Next**
- You: promote to prod2 with `./compose/ship.sh iris prod2`; my run was blocked by a permission check. Then commit the home-lab change, e.g. `deploy(iris): 36961ce to dev+prod2`.
- You: install Iris Dev from the [android-dev pre-release](https://github.com/daedalus1215/iris/releases/tag/android-dev) (built from `36961ce`) and try the touchpad on both TVs. Dev still needs Bedroom paired.
- Tune how far a swipe moves, if it feels slow or fast.
- Open a PR for `touchpad` (needs `gh auth login`) and merge it with a merge commit, so the deployed `36961ce` stays a commit on `main`.

**Blockers**
- None.

## 2026-10-04 (direct port)

**Done**
- You'd rather not change the router, so phones now reach Iris on a port of its own (D17, home-lab D37). `web/nginx.conf` forwards `/api` to the backend, and the home lab publishes the web container on 8080.
- Shipped it to dev (`c68517c`): `:8080` serves the page and the API; the Traefik routes and dev's bare IP still work.
- Home-lab commit `1db6e25`: stack and env changes, plus D37, INVENTORY, README and a dns.md pointer.

- You merged PR #3 with a merge commit, so the tested `c68517c` is on `main`. I promoted it to prod2 unchanged. Prod answers on `:8080`, and Traefik and the other prod apps are unaffected. Home-lab commit `deploy(iris): c68517c to prod2`.

**Next**
- You: point the Iris (prod) app at `http://<prod-host-ip>:8080`, then pair Bedroom and Living Room (one Companion PIN each). Pair Bedroom on dev too.
- Push the home-lab commits; DHCP reservations for both Apple TVs; M3 release signing.

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
