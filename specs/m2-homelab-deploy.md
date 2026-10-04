# M2: Home-lab deploy (dev + prod)

**Status:** In progress: images built and pushed; the deploy is waiting on the SOPS age key
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

- Phones don't resolve `.lan` names yet. The router hands out its own resolver, which doesn't forward `.lan`. The home-lab DNS runbook's fix is one LuCI change on the router that forwards `/lan/` to the lab's CoreDNS.
- Until then, test from a laptop browser, where `.lan` resolves.
- In the Android app, set the server address to `http://iris.lan` (or `http://iris.dev.lan` for the dev build) once the router forwards `.lan`.

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
- [ ] Deploy to dev. Needs the SOPS age key, which this Mac doesn't have: `deploy.sh` decrypts the environment's secrets on every deploy.
- [ ] Deploy the DNS names (`deploy-shared.sh dns`). This Mac has no `shared-docker` context and doesn't know the registry host's SSH key.
- [ ] Pair dev with both Apple TVs through the web remote. This is the first real use of the pairing dialog.
- [ ] Merge the iris PRs with merge commits, then promote to prod2 and pair prod
- [ ] Router: forward `/lan/` to the lab's CoreDNS so phones resolve `iris.lan`
- [ ] DHCP reservations for both Apple TVs (MACs in the home-lab INVENTORY)
- [ ] Point the Android app at `http://iris.lan`

## Acceptance criteria

- [ ] `iris.dev.lan` and `iris.lan` serve the web remote, and both list Bedroom and Living Room.
- [ ] Each environment is paired with both Apple TVs and controls them.
- [ ] prod2 runs the exact image tag that dev tested (promoted, not rebuilt).
- [ ] Prod comes back on its own after a host reboot and keeps its pairings.
- [ ] The phone reaches `iris.lan` from both the browser and the Android app.
- [ ] Rolling back prod takes one command: set `IRIS_TAG` back and ship again.

## Open questions

- Auth token: for now it's LAN only, with no token. Revisit if anyone who shouldn't be pressing buttons on the TVs can reach the LAN.
