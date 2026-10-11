# M2: Home-lab deploy (dev + prod)

**Status:** Not started
**Depends on:** M1 backend
**Outcome:** The same container image runs on this PC and on the home-lab Docker host, with dev and prod side by side. Moving between machines changes only an env file.

## Design

### Image

One image, `iris`, built in two stages:

1. A Node 20+ stage builds `web/` (`quasar build`, output in `dist/spa`).
2. A Python 3.13-slim stage installs the backend with uv, copies the frontend build into `IRIS_STATIC_DIR`, and runs uvicorn as a non-root user.

The Dockerfile sits at the repo root, so the build context includes both `backend/` and `web/` (D7).

### Compose

A single parameterized file, `deploy/compose.yml`:

- `network_mode: host` (D8). Scanning the network works, and the Apple TV is reachable as it would be from a program running directly on the host. Because of this, the port comes from `IRIS_PORT` instead of a port mapping.
- Volume `data:/data` with `IRIS_DATA_DIR=/data` for pairing credentials. Each environment gets its own volume.
- `restart: unless-stopped`, a healthcheck on `/api/health`, and json-file logs with rotation.
- The image tag comes from `IRIS_IMAGE_TAG`.

### Environments

Each environment is its own Compose project:

| Env | Where | Compose project | Port | Image tag | Data volume |
|---|---|---|---|---|---|
| local | this PC | `iris-local` | 8080 | built locally | `iris-local_data` |
| dev | home lab | `iris-dev` | 8081 | `dev` (latest `main`) | `iris-dev_data` |
| prod | home lab | `iris-prod` | 8080 | a pinned version, e.g. `1.0.0` | `iris-prod_data` |

- Each environment has an env file in `deploy/env/` (`local.env`, `dev.env`, `prod.env`) that sets `IRIS_ENV`, `IRIS_PORT`, `IRIS_IMAGE_TAG` and `IRIS_SCAN_HOSTS`.
- Secrets such as `IRIS_AUTH_TOKEN` go in an untracked file or the host's environment.
- The underlying command is `docker compose -p iris-prod --env-file deploy/env/prod.env -f deploy/compose.yml up -d`. A Makefile wraps it: `make up ENV=prod`.

### Release flow

This assumes GitHub Container Registry, which is still an open question.

- **Dev:** a merge to `main` that touches `backend/` or `web/` makes CI build `ghcr.io/daedalus1215/iris:dev`, plus a git-sha tag. Running `make up ENV=dev` on the home lab pulls it.
- **Prod:** tagging a release (`v1.0.0`) makes CI build `:1.0.0`. Set `IRIS_IMAGE_TAG` in `prod.env`, then run `make up ENV=prod`.
- **Rollback:** set the previous tag and run `make up ENV=prod` again.

### Networking requirements

- **Home-lab host to Apple TV.** The simplest setup is the same subnet as the Apple TV (172.16.0.0/24) with no firewall between them. Across VLANs, allow the host to reach the Apple TV on all TCP ports (AirPlay negotiates extra ports beyond 7000) and on UDP 5353 for scanning. Also set `IRIS_SCAN_HOSTS`, because multicast scans won't cross VLANs.
- **Phone to host.** On ports 8080 and 8081 over home Wi-Fi, and through Tailscale when away (the host joins the tailnet).
- **Optional:** reverse-proxy hostnames such as `iris.<domain>` and `iris-dev.<domain>` with HTTPS. HTTPS also lets the web remote install as a home-screen app (PWA).

## Tasks

### Local (this PC)

- [ ] Enable Docker here. You run `sudo systemctl enable --now docker`, then either `sudo usermod -aG docker $USER` and log in again, or set up rootless Docker. Note that the `docker` group is effectively root access.
- [ ] Multi-stage Dockerfile and `.dockerignore`, running as a non-root user
- [ ] `deploy/compose.yml`, the env files and the Makefile
- [ ] Run `make up ENV=local`, pair, and test from the phone. It should behave the same as the native M1 run.

### CI and registry

- [ ] Decide on the registry: GHCR, or building on the host
- [ ] GitHub Actions workflow that builds and pushes on `main` and on tags, filtered to changes in `backend/`, `web/` and `deploy/`

### Home lab

- [ ] Collect host details: IP, OS, Docker version, subnet
- [ ] Check that the host can reach the Apple TV: `atvremote -s 172.16.0.242 scan`, from the host and from a container
- [ ] DHCP reservation for the Apple TV, or confirm its address is stable
- [ ] Deploy dev, pair, and test from the phone
- [ ] Deploy prod, pair, and test from the phone
- [ ] Put the host on Tailscale, if you want remote access
- [ ] Runbook in `deploy/README.md`: deploy, update, roll back, re-pair, logs, back up the data volume

## Acceptance criteria

- [ ] There is one image; local, dev and prod differ only in their env file.
- [ ] Dev and prod run side by side on the home-lab host without sharing ports, data or pairings.
- [ ] Prod comes back on its own after a host reboot or an Apple TV restart.
- [ ] The phone controls Bedroom through prod within M1's latency target.
- [ ] Updating or rolling back prod takes one command.

## Open questions

- Home-lab host details, and whether it's on the Apple TV's subnet.
- Registry: GHCR via Actions, or build on the host?
- Reverse proxy and local DNS: which ones, if any?
- Auth token, or LAN plus Tailscale only?
- Dev updates: automatic (e.g. Watchtower), or a manual `make up ENV=dev`?
