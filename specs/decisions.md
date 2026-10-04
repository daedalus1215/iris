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
