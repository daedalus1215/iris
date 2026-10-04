# Iris

A monorepo for an Apple TV remote you control from an Android phone:

- `backend/`: the API and Apple TV control. It's moving from NestJS to Python (FastAPI + pyatv).
- `web/`: the Vue/Quasar web remote, served by the backend.
- `android/`: the Android app (Kotlin/Compose).

Specs live in `specs/`. At the start of a session, read `specs/README.md` and the newest entry in `specs/log.md`. At the end, add a log entry, tick the finished tasks, and update the status table.

## Conventions

- Work on a feature branch and merge into `main` through a PR.
- Each folder keeps its own tooling; run its commands from inside that folder.
- The Apple TV is "Bedroom" at `172.16.0.242`. Pairing needs the user at the TV to read the PIN.
- Until the backend's uv project exists, run pyatv with `uvx --from pyatv atvremote …`.
