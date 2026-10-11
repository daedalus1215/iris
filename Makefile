.PHONY: run web dev-backend dev-web test

# Build the web remote and serve it, with the API, at http://<this machine>:8080
run: web
	cd backend && uv run iris-backend

web:
	cd web && { test -d node_modules || npm ci; } && npm run build

# For development, run these two in separate terminals.
# The web dev server hot-reloads and forwards /api to the backend on :8080.
dev-backend:
	cd backend && uv run iris-backend

dev-web:
	cd web && { test -d node_modules || npm ci; } && npm run dev

test:
	cd backend && uv run pytest -q && uv run ruff check .
	cd web && npm run lint
