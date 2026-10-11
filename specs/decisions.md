# Decisions

Newest at the bottom. A status is **Accepted** (agreed with you), **Proposed** (my recommendation, not yet confirmed) or **Superseded**.

### D1: Specs live in `specs/` as Markdown

- **Date:** 2026-10-04
- **Status:** Accepted
- **Why:** They're versioned with the code (D7), Claude reads them at the start of each session, and they diff cleanly.

### D2: Replace the NestJS backend with Python (FastAPI + pyatv)

- **Date:** 2026-10-04
- **Status:** Accepted
- **Why:** pyatv is the mature implementation of Apple's Companion and AirPlay remote protocols, and using it as a library keeps the connection open, so presses are fast. The NestJS code was a thin wrapper around the `atvremote` command and had a shell-injection hole.
- **Consequences:**
  - One language and one process in the backend.
  - The API moves from `/apple-tv/*` to `/api/*`, and devices are keyed by id instead of MAC (M1). The frontend needs small changes to match.
  - The NestJS code stays in `iris-backend`'s git history.

### D3: Deploy with Docker Compose on a home-lab Docker host

- **Date:** 2026-10-04
- **Status:** Accepted
- **Why:** You run a Compose host. The same Compose file and image work on this PC and in the home lab.

### D4: The Android app is a native Kotlin client of the backend

- **Date:** 2026-10-04
- **Status:** Accepted
- **Why:** It reuses the backend's pairing and connection handling, and it's a moderate amount of work.
- **Deferred:** a standalone mode, where the phone talks to the Apple TV directly. That would mean porting pyatv's discovery, pairing and encrypted-session code to Kotlin, which is several days or more.

### D5: One container image serves both the API and the frontend

- **Date:** 2026-10-04
- **Status:** Superseded by D16 (2026-10-04). The home lab's apps all ship a backend and a frontend image, with Traefik sending `/api` to the backend on the same host name, and its `build-push.sh` builds one image per folder. Iris follows that: `iris-backend` and `iris-web`. It's still same-origin, so there's still no CORS setup.
- **Why:** The page and the API share an origin, so there's no CORS setup and no hardcoded API URL in the frontend. It's also one thing to deploy and version.
- **Alternative:** separate frontend (nginx) and backend containers. That adds routing between them for no gain at this size.

### D6: A device's id is pyatv's identifier, and any of its identifiers finds it

- **Date:** 2026-10-04
- **Status:** Accepted
- **Why:** Bedroom's MAC is locally administered, which suggests a private, randomized Wi-Fi address that can change.
- **Finding:** pyatv's main identifier for Bedroom turned out to be that same value (its AirPlay device id). So the API uses it as the id, but looks a device up by any identifier it advertises, including the Companion UUID. If the MAC changes, a client holding the old id gets a 404 and has to rescan; the pairing itself is unaffected.

### D7: One monorepo, `daedalus1215/iris`

- **Date:** 2026-10-04
- **Status:** Accepted
- **Decision:** the `iris` repo holds everything, one folder per app: `backend/`, `web/` and `android/`, plus `specs/` and, later, `deploy/`.
- **Why:**
  - The backend image needs the web build, both clients follow the backend's API, and the specs cover all three.
  - One PR can change the API and both clients together.
  - The Docker build context is simply the repo root.
- **How:** `backend/` and `web/` were imported with their full history, rewritten with `git filter-repo --to-subdirectory-filter` so `git log` and `git blame` work on the new paths. The Android app was moved with `git mv`.
- **Consequences:** `iris-backend` and `iris-frontend` get archived on GitHub once the monorepo reaches `main`. CI uses path filters per folder.
- **Replaces:** the earlier options of an `iris-root` repo, or Docker files in `iris-backend`.

### D8: The container uses host networking

- **Date:** 2026-10-04
- **Status:** Superseded by D16 (2026-10-04). Plain bridge networking works: a container on `docker-dev`'s edge network scanned and listed both Apple TVs by address. That only works because `IRIS_SCAN_HOSTS` lists them, since a multicast scan can't get out of a container. Host networking would have bypassed Traefik.
- **Why:** Multicast scanning (mDNS) and any connections the Apple TV opens back to the server work without extra setup. The cost is that ports come from `IRIS_PORT` instead of port mappings.
- **Revisit:** bridge networking with `IRIS_SCAN_HOSTS` set may also work; test it in M2 if host networking is a problem.
- **Note:** the backend now scans Bedroom directly by IP (`IRIS_SCAN_HOSTS`) on this PC, which works.

### D9: FastAPI rather than plain aiohttp

- **Date:** 2026-10-04
- **Status:** Proposed
- **Why:** FastAPI validates requests and generates OpenAPI docs, which help when building the Android client. aiohttp would avoid extra dependencies, since pyatv already depends on it.

### D10: Android APKs are built by GitHub Actions, not on this Mac

- **Date:** 2026-10-04
- **Status:** Accepted
- **Why:** This Mac (Apple Silicon running Linux, with a 16 KB-page kernel) can't run `aapt2`, the Android resource compiler that every app build needs:
  - Google publishes `aapt2` for Linux only as an x86-64 binary.
  - The community ARM builds are aligned for 4 KB pages, so a 16 KB-page kernel refuses to load them.
- **Consequences:**
  - The app's logic lives in `android/core`, a plain Kotlin build that compiles and tests here without the Android SDK (`./gradlew -p core test`).
  - The `app` module only compiles in CI.
  - Every push that touches `android/` publishes both APKs to the rolling `android-dev` pre-release, which the phone installs from.
- **Revisit:** if the home-lab host (x86) becomes a faster place to build.

### D11: AGP 9 with compileSdk 37

- **Date:** 2026-10-04
- **Status:** Accepted
- **Why:** The current AndroidX core (1.19) and Compose (1.12) libraries require AGP 9.1+ and compileSdk 37. Staying on AGP 8 would mean pinning year-old libraries.
- **Consequences:** AGP 9 compiles Kotlin itself, so there's no `kotlin-android` plugin in the app module. That plugin is declared only to pin the Kotlin version.
- **Consequences:** Targeting API 37 means Android 17's local network protection applies. The app must hold the `ACCESS_LOCAL_NETWORK` runtime permission ("Nearby devices") to reach the server on the LAN; without it, connections just time out. The app asks on first launch, and shows an "Allow" banner if denied.

### D12: Application id `io.github.daedalus1215.iris`

- **Date:** 2026-10-04
- **Status:** Proposed
- **Why:** It replaces the template's `com.example.iris` with a reverse-domain id based on the GitHub account. The dev flavor adds `.dev`, so both apps can be installed side by side.
- **Note:** changing it after installing means uninstalling and reinstalling, so decide before relying on it.

### D13: The app allows plain HTTP

- **Date:** 2026-10-04
- **Status:** Proposed
- **Why:** The server runs on the home network over HTTP, at an address set in the app.
- **Revisit:** once M2 puts HTTPS in front of the server through a reverse proxy.

### D14: The debug signing key is committed

- **Date:** 2026-10-04
- **Status:** Proposed
- **Why:** Each CI build has to install over the previous one on the phone, which requires the same signing key every time. Debug keys aren't secrets; Android's own is the same well-known password everywhere.
- **Consequences:** the release key (M3) must stay out of git, in a CI secret.

### D15: No home-network details in this repo

- **Date:** 2026-10-04
- **Status:** Accepted
- **Why:** The repo, its CI logs and its APKs are public. Private addresses can't be reached from the internet, so publishing them isn't dangerous. But there's no reason to publish a map of the home network either: addresses, device names and IDs, the tailnet address.
- **How:**
  - Specs use placeholders such as `<bedroom-ip>`. The real values live in `LOCAL.md` (git-ignored) and the home-lab `INVENTORY.md`.
  - The app has no built-in server address. It asks on first launch, or takes one from your own `~/.gradle/gradle.properties`.
  - Test fixtures use documentation addresses (`192.0.2.x`).
- **Not done:** older commits still contain the values. Rewriting public history and force-pushing isn't worth it for private addresses.

### D16: Deploy through the home-lab repo

- **Date:** 2026-10-04
- **Status:** Accepted
- **Decision:** Iris is deployed as an app in the home-lab repo, like its other apps:
  - `compose/apps/iris.yml`
  - per-environment env files
  - `build-push.sh` and `ship.sh`, with dev on `docker-dev` and prod on `docker-prod2`
  - Traefik and `.lan` names

  This repo only provides the Dockerfiles.
- **Why:** The home lab already has a registry, a reverse proxy, DNS, backups and a promotion flow (build once in dev, promote the same tag to prod). A separate setup for one app would duplicate all of that and drift from it.
- **Consequences:**
  - M2's own Compose files, Makefile targets and GHCR plan are dropped.
  - Real addresses live in the home-lab repo, which is private, rather than here (D15).
  - Deploys need the home lab's SOPS age key on the machine running them.

### D17: Phones reach Iris on a port of its own, not through `.lan` names

- **Date:** 2026-10-04
- **Status:** Accepted
- **Decision:** On each home-lab host, the `web` container is published on port 8080, outside Traefik. Its nginx forwards `/api` to the backend, using the network alias `iris-backend`. Phones use `http://<host-ip>:8080`.
- **Why:** Phones can't resolve `.lan`, and the router is staying as it is. Prod's bare IP belongs to another app. A port of Iris's own needs no phone or router setup, and doesn't touch the other apps' Traefik.
- **Details:** the rejected options are recorded in home-lab D37. The port gives up nothing Traefik currently adds; Iris uses no TLS or auth from Traefik.

### D18: A touchpad that passes real touches to the Apple TV

- **Date:** 2026-10-04
- **Status:** Accepted
- **Decision:** The remote's main control is a touchpad, like the Siri Remote's touch surface and Apple's iPhone remote:
  - A drag streams touch events (press, move, release) over a WebSocket, `/api/devices/{id}/touch`. The backend passes each one to pyatv's Companion `touch.action`, and tvOS does the rest.
  - A tap sends `select`, and a long press sends `select` with `hold`. Neither sends touch events.
  - The arrow buttons stay available as a setting, in the app and the web remote.
- **Why:**
  - Real touches get tvOS's own behavior: glide, flick to scroll, and scrubbing during playback. Turning swipes into arrow presses can't do that.
  - A WebSocket keeps the events in order and cheap at 60 a second. Separate HTTP requests could arrive out of order.
  - It works: on 2026-10-04 both TVs reported the touch features as available, and a streamed drag moved the scrub bar on Living Room (M1, "Spike results").
- **Details:**
  - The pad stands for the remote's whole surface, 0–1000 on each axis, so where a drag starts matters, as on the remote.
  - Moves go out at most every 16 ms. A touch only becomes a drag once it moves past touch slop, so a tap never sends touches, and a tap near an edge can't read as an arrow.
  - Touch events share the per-device lock and reconnect logic with commands. If the socket drops mid-drag, the backend lifts the finger.
  - The server only sends errors back, as `{detail, status}`, and keeps the socket open. The next touch reopens a dropped socket.
  - The Android screen no longer scrolls, so the app is portrait only.
- **Known limit:** pyatv timestamps each event when the backend sends it, not when the finger moved, so Wi-Fi jitter could make flicks uneven. If that shows up, send the phone's timestamps through, which needs pyatv's lower-level API.

### D19: Type on the TV with the phone's keyboard, opened by the TV

- **Date:** 2026-10-05
- **Status:** Accepted
- **Decision:**
  - The backend watches each Apple TV's text field through pyatv's Companion keyboard, and serves it on a WebSocket, `/api/devices/{id}/keyboard`.
  - The server sends `{focused, text}` when the socket opens and whenever a text field gains or loses focus. The client sends `{text}` to replace what's typed in the field.
  - While the app or web page is open, it keeps that socket open for the selected TV. When a text field gets focus, a dialog opens with the phone's keyboard, starting from the field's text.
  - Each change sends the whole text, so backspace, autocorrect and paste all work, and the TV's search results follow along as you type.
  - Closing the dialog leaves a keyboard button until the field loses focus.
- **Why:**
  - Typing a search letter by letter on the TV's on-screen keyboard is slow.
  - The TV announces focus changes itself (pyatv's keyboard listener), so the phone can offer its keyboard at the right moment, as Apple's iPhone remote does, with no polling.
  - A socket gives those events instantly, and keeps typed text in order.
- **Details:**
  - The socket closes when the backend loses the connection to the Apple TV. Clients reopen it after 1 s, doubling the wait up to 30 s, until a state arrives.
  - Errors before the first state aren't shown, since they're about watching itself and the retry covers them.
  - pyatv holds listeners weakly, so the backend keeps each connection's listener alive itself. Before this, the connection-lost listener was collected straight away, and a lost connection only came to light on the next command's retry.
