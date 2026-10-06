# M2: Home-lab deploy (dev + prod)

**Status:** In progress: dev and prod live (`fc51edc`) and reachable from the phone; prod is paired with both Apple TVs, dev only with Living Room
**Depends on:** M1 backend
**Outcome:** Iris runs on the home lab's Docker hosts as a dev and a prod environment, at `iris.dev.lan` and `iris.lan`. It's deployed the same way as the lab's other apps.

## How it fits the home lab

The home-lab repo (`~/Projects/home-lab`, private) already runs its apps as Compose stacks on two Docker VMs. Iris follows that repo's runbooks rather than bringing its own setup (D16). This replaces this spec's first design: one image, host networking, and a `deploy/` folder in this repo.

| | dev | prod |
|---|---|---|
| Host | `docker-dev` | `docker-prod2` |
| Address | `iris.dev.lan` | `iris.lan` |
| Env file (home-lab) | `compose/env/dev.env` | `compose/env/prod2.env` |
| Name on the Apple TVs | Iris (dev) | Iris (prod) |

- **Images:**
  - The home lab's `compose/build-push.sh` builds every top-level folder that has a Dockerfile, running on `docker-dev`, and pushes `registry.lan:5000/iris-backend:<sha>` and `iris-web:<sha>`.
  - Dev builds; prod promotes the same tag without rebuilding.
- **Stack:** `compose/apps/iris.yml` runs `backend` (`/api`, port 8080) and `web` (port 80) behind Traefik on the same host name. Ordinary bridge networking works (D8 superseded).
- **Config:** `IRIS_TAG`, `IRIS_HOSTS`, `IRIS_ENV` and `IRIS_SCAN_HOSTS` in the env files. There are no secrets.
- **State:** pairing credentials in each host's `iris_data` volume.
  - prod2 is backed up nightly along with its VM.
  - dev isn't backed up; if its volume is lost, pair it again.
- **Names:** `compose/shared/dns/lan.hosts`, deployed with `./compose/deploy-shared.sh dns`.
- **Deploy:**
  1. `./compose/ship.sh iris dev --repo ~/Projects/iris-root/iris`.
  2. Merge the PR with a merge commit, so the image tag stays a commit on `main`.
  3. `./compose/ship.sh iris prod2`.
- **Same TVs:** dev and prod control the same two Apple TVs. There's no dev TV.

### Reaching it from the phone

Phones can't resolve `.lan` names, and the router is staying as it is. So the `web` container is also published on port 8080 of each host, outside Traefik. Its nginx forwards `/api` to the backend, so that one port serves the whole remote (D17, home-lab D37).

| | Phone (browser or Android app) | Laptop |
|---|---|---|
| prod | `http://<prod-host-ip>:8080` | `iris.lan` |
| dev | `http://<dev-host-ip>:8080`, or `http://<dev-host-ip>` | `iris.dev.lan` |

## Tasks

### Iris repo

- [x] Dockerfiles:
  - backend: python:3.13-slim with a uv venv, non-root, `/data` volume
  - web: the Quasar build on nginx
- [x] Checked that a container on `docker-dev`'s edge network can scan and list both Apple TVs
- [x] Kept home-network details out of the public repo (D15)

### Home lab

- [x] Wired Iris in (home-lab commit `0084d36`, not pushed yet):
  - `compose/apps/iris.yml`
  - dev and prod2 env entries
  - `lan.hosts` names
  - INVENTORY section for the Apple TVs and their MACs
  - README status row
- [x] Images built and pushed at `ed7b9bd`
- [x] Deploy to dev with `./compose/ship.sh iris dev` (the age key is now on this Mac)
- [ ] Deploy the DNS names (`deploy-shared.sh dns`). This Mac has no `shared-docker` context and doesn't know the registry host's SSH key.
- [x] Dev reachable from the phone without DNS: Iris holds dev's bare-IP slot in `dev.env`
- [ ] Pair dev with both Apple TVs. Living Room is done (both protocols, from the app); Bedroom isn't yet.
- [x] Merged into `main` (PR #2), rebuilt dev from `main` (`07fd521`), promoted to prod2. Both run the same image digest.
- [x] Pair prod with both Apple TVs (one Companion PIN each). Both report paired over Companion and AirPlay (checked 2026-10-05).
- [x] Phones reach Iris without `.lan` names: port 8080 on each host (D17). Verified on dev.
- [x] Promoted to prod2 (`c68517c`, merged in PR #3 with its commit IDs intact, so no rebuild). Prod answers on `:8080`.
- [ ] DHCP reservations for both Apple TVs (MACs in the home-lab INVENTORY)
- [x] Point the Iris (prod) app at `http://<prod-host-ip>:8080`, then pair both Apple TVs

## Acceptance criteria

- [ ] `iris.dev.lan` and `iris.lan` serve the web remote, and both list Bedroom and Living Room.
- [ ] Each environment is paired with both Apple TVs and controls them.
- [ ] prod2 runs the exact image tag that dev tested (promoted, not rebuilt).
- [ ] Prod comes back on its own after a host reboot and keeps its pairings.
- [ ] The phone reaches prod from both the browser and the Android app (the direct port).
- [ ] Rolling back prod takes one command: set `IRIS_TAG` back and ship again.

## Open questions

- Auth token: for now it's LAN only, with no token. Revisit if anyone who shouldn't be pressing buttons on the TVs can reach the LAN.
