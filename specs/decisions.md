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
