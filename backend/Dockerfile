# Iris backend: FastAPI + pyatv on port 8080.
# The home lab builds it with compose/build-push.sh as registry.lan:5000/iris-backend:<sha>.
FROM python:3.13-slim AS build
COPY --from=ghcr.io/astral-sh/uv:0.12.23 /uv /usr/local/bin/uv
WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never

# Dependencies first, so a code change doesn't reinstall them.
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project
COPY README.md ./
COPY src ./src
# Non-editable, so the venv holds the app itself and is all the runtime image needs.
RUN uv sync --locked --no-dev --no-editable

FROM python:3.13-slim
RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin iris \
 && mkdir /data \
 && chown iris /data
COPY --from=build --chown=iris /app/.venv /app/.venv
ENV PATH=/app/.venv/bin:$PATH \
    IRIS_HOST=0.0.0.0 \
    IRIS_PORT=8080 \
    IRIS_DATA_DIR=/data
USER iris
WORKDIR /app
EXPOSE 8080
# Pairing credentials live here; mount a volume so they survive redeploys.
VOLUME /data
CMD ["iris-backend"]
