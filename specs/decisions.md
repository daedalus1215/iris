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
- **Status:** Proposed
- **Why:** The page and the API share an origin, so there's no CORS setup and no hardcoded API URL in the frontend. It's also one thing to deploy and version.
- **Alternative:** separate frontend (nginx) and backend containers. That adds routing between them for no gain at this size.

### D6: A device's id is pyatv's identifier, and any of its identifiers finds it

- **Date:** 2026-10-04
- **Status:** Accepted
- **Why:** Bedroom's MAC (`B6:B8:78:10:43:D0`) is locally administered, which suggests a private, randomized Wi-Fi address that can change.
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
- **Status:** Proposed
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
- **Why:** This Mac (Apple M1 Pro running Arch, with a 16 KB-page kernel) can't run `aapt2`, the Android resource compiler that every app build needs:
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
