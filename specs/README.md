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
| M1 | Working remote, running on this PC | In progress: everything works; merge pending | — | [m1-working-remote.md](m1-working-remote.md) |
| M2 | Home-lab deploy (dev + prod) | In progress: dev and prod live; prod needs pairing and phone DNS | M1 backend | [m2-homelab-deploy.md](m2-homelab-deploy.md) |
| M3 | Android app (native client) | In progress: works on the phone, pairing included | M1 API | [m3-android-app.md](m3-android-app.md) |

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
 │   /*      → built web/ files                                 │    <bedroom-ip>
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
| Apple TV | "Bedroom": Apple TV 4K, tvOS 26.6, `<bedroom-ip>`. Companion (TCP 49153) and AirPlay (TCP 7000) both require pairing. This PC is paired over both as "Iris (local)". |
| Apple TV 2 | "Living Room": Apple TV 4K (gen 2), tvOS 26.6, `<living-room-ip>`. Companion on TCP 55339 (ports differ per device), AirPlay on TCP 7000. Paired over both protocols as "Iris (local)". |
| This PC | ARM (aarch64). `<laptop-ip>` on Wi-Fi, the same LAN as the Apple TVs. Node 26, Python 3.14, uv. The ufw firewall blocks incoming connections except LocalSend. |
| Docker here | 29.8 and Compose 5.5 are installed, but the service is inactive and the user isn't in the `docker` group. |
| Android tooling here | This Mac can't run `aapt2` (ARM with 16 KB pages), so APKs build in GitHub Actions (D10). A JDK is still needed here for the core tests. |
| pyatv | 0.18.0 runs with `uvx --from pyatv atvremote …`, no install needed. |

Real addresses for the `<…-ip>` placeholders are in `LOCAL.md` (git-ignored) and the home-lab `INVENTORY.md`. This repo is public, so they stay out of it (D15).

## Open questions

- [x] Home-lab hosts: `docker-dev` and `docker-prod2`, on the same LAN as the Apple TVs (D16)
- [x] Image registry: the home lab's `registry.lan`, through its `build-push.sh` (D16)
- [x] Reverse proxy and DNS: the home lab's Traefik and `.lan` names. Phones still need a router change (M2).
- [ ] Auth: LAN only with no token for now. Is that enough?
- [ ] Can both Apple TVs get DHCP reservations? `IRIS_SCAN_HOSTS` lists their addresses, so those addresses must not change.
- [x] Android applicationId: `io.github.daedalus1215.iris` (D12)

## Working with these specs

- **Start of a session:** read this file and the newest entry in [log.md](log.md).
- **During:** tick task boxes in the milestone file, and record decisions in [decisions.md](decisions.md).
- **End of a session:** add a dated entry to [log.md](log.md) (done, next, blockers) and update the status table above.
