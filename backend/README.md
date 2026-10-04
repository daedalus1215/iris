# Iris backend

HTTP API that controls Apple TVs through [pyatv](https://pyatv.dev). It keeps one connection open per Apple TV, so a button press takes a few milliseconds. It also serves the built web remote from `../web`.

## Setup

Requires [uv](https://docs.astral.sh/uv/). It installs Python 3.13 and the dependencies itself.

```sh
uv sync
cp .env.example .env   # then adjust; see the comments in the file
```

## Run

```sh
uv run iris-backend
```

From the repo root, `make run` builds the web remote first and serves both at `http://<this machine>:8080`.

API docs: `http://<this machine>:8080/api/docs`. The API is described in `../specs/m1-working-remote.md`.

## Pairing

Pair once per Apple TV, through the web remote ("Pair this Apple TV") or the API. Credentials are saved in `$IRIS_DATA_DIR/pyatv.conf`; keep that directory out of git.

## Test

```sh
uv run pytest
uv run ruff check . && uv run ruff format --check .
```

The tests use a fake Apple TV (`tests/fakes.py`) and need no network.
