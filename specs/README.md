# Iris specs

Iris turns an Android phone into a remote for the Apple TV. This folder is the source of truth for the effort across sessions: what we're building, what's done, and what's next.

## Goal

1. **Working remote.** Control the Apple TV from the phone, with presses that feel instant.
2. **Home lab.** Run it 24/7 on the home-lab Docker host, as separate dev and prod environments.
3. **Android app.** A native app that talks to the home-lab backend.

These can happen in any order once M1's backend exists. Until then, everything runs on this PC, built so it can move to the home lab by changing only an env file.

## Status

| ID | Milestone | Status | Depends on | Spec |
|---|---|---|---|---|
| M1 | Working remote, running on this PC | Not started | — | [m1-working-remote.md](m1-working-remote.md) |
| M2 | Home-lab deploy (dev + prod) | Not started | M1 backend | [m2-homelab-deploy.md](m2-homelab-deploy.md) |
| M3 | Android app (native client) | Not started | M1 API | [m3-android-app.md](m3-android-app.md) |

Status values: Not started · In progress · Blocked (say on what) · Done.

## Target architecture

```
 Phone ─┬─ browser: web remote (M1)
        └─ Android app (M3)
              │  HTTP over home Wi-Fi, or Tailscale when away
              ▼
 ┌─────────── iris container (home-lab Docker host) ────────────┐
 │ FastAPI                                                      │
 │   /api/*  → device registry → pyatv connection (kept open) ──┼──► Apple TV "Bedroom"
 │   /*      → built web/ files                                 │    172.16.0.242
 │ /data     → pairing credentials (volume)                     │    Companion :49153, AirPlay :7000
 └──────────────────────────────────────────────────────────────┘
   prod :8080 · dev :8081 (home lab)    local :8080 (this PC)
```

## Layout

Everything lives in the `iris` monorepo (`daedalus1215/iris`, D7):

| Folder | What | Plan |
|---|---|---|
| `backend/` | API and Apple TV control | Rewrite in Python (FastAPI + pyatv) in M1. The imported NestJS code stays in git history. |
| `web/` | Web remote (Vue/Quasar) | Switch to the new `/api` and add pairing UI. The backend serves its built files. |
| `android/` | Android app (Kotlin/Compose) | Native client of the backend (M3). Currently an empty Android Studio template. |
| `specs/` | These specs | — |
| `deploy/` | Compose and env files | Added in M2. |

`backend/` and `web/` were imported from `iris-backend` and `iris-frontend` with their full history. PR numbers in commits from before the import refer to those repos. Each change goes on a feature branch and merges into `main` through a PR.

## Known facts

| What | Details (as of 2026-10-04) |
|---|---|
| Apple TV | "Bedroom": Apple TV 4K, tvOS 26.6, `172.16.0.242`. Companion (TCP 49153) and AirPlay (TCP 7000) both require pairing; nothing is paired yet. It was in deep sleep during the scan. |
| This PC | `172.16.0.102` on Wi-Fi, the same /24 as the Apple TV. Tailscale `100.93.232.17`. Node 26, Python 3.14, uv. |
| Docker here | 29.8 and Compose 5.5 are installed, but the service is inactive and the user isn't in the `docker` group. |
| Android tooling here | None: no JDK, Android SDK or adb. |
| pyatv | 0.18.0 runs with `uvx --from pyatv atvremote …`, no install needed. |

## Open questions

- [ ] Home-lab host: IP or hostname, OS, Docker version. Is it on the Apple TV's subnet (172.16.0.0/24)?
- [ ] Image registry: GitHub Container Registry via Actions, or build on the home-lab host?
- [ ] Is there a reverse proxy or local DNS in the home lab (for names like `iris.<domain>`)?
- [ ] Auth: is LAN plus Tailscale enough, or should the API also require a token?
- [ ] Can the Apple TV get a DHCP reservation, and does its address stay stable? (See M1 risks.)
- [ ] Android applicationId to replace `com.example.iris`.

## Working with these specs

- **Start of a session:** read this file and the newest entry in [log.md](log.md).
- **During:** tick task boxes in the milestone file, and record decisions in [decisions.md](decisions.md).
- **End of a session:** add a dated entry to [log.md](log.md) (done, next, blockers) and update the status table above.
