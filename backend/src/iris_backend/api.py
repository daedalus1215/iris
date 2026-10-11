import logging
import secrets
from contextlib import asynccontextmanager

from fastapi import APIRouter, Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse, Response
from fastapi.staticfiles import StaticFiles
from pyatv import exceptions
from pydantic import BaseModel, Field

from . import __version__
from .devices import (
    Device,
    DeviceManager,
    DeviceNotFound,
    InvalidRequest,
    NotPaired,
    PairingFailed,
    PairingSessionNotFound,
)
from .settings import Settings

log = logging.getLogger(__name__)

# Exception -> HTTP status. Lookup follows the exception's class hierarchy, so
# TimeoutError (a subclass of OSError) gets 504 rather than 503.
ERROR_STATUS: dict[type[Exception], int] = {
    InvalidRequest: 400,
    PairingFailed: 400,
    PairingSessionNotFound: 400,
    DeviceNotFound: 404,
    NotPaired: 409,
    exceptions.NoCredentialsError: 409,
    exceptions.AuthenticationError: 409,
    exceptions.InvalidCredentialsError: 409,
    exceptions.NotSupportedError: 409,
    exceptions.ConnectionFailedError: 503,
    exceptions.ConnectionLostError: 503,
    OSError: 503,
    TimeoutError: 504,
    exceptions.OperationTimeoutError: 504,
}


class CommandBody(BaseModel):
    action: str | None = None


class PinBody(BaseModel):
    session: str
    pin: str = Field(pattern=r"^\d{4}$")


def create_app(settings: Settings | None = None, manager: DeviceManager | None = None) -> FastAPI:
    settings = settings or Settings()
    manager = manager or DeviceManager(settings)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        await manager.start()
        yield
        await manager.stop()

    app = FastAPI(
        title="Iris",
        version=__version__,
        lifespan=lifespan,
        docs_url="/api/docs",
        openapi_url="/api/openapi.json",
        redoc_url=None,
    )

    def require_token(authorization: str | None = Header(default=None)) -> None:
        if settings.auth_token and not secrets.compare_digest(
            authorization or "", f"Bearer {settings.auth_token}"
        ):
            raise HTTPException(401, "missing or wrong token")

    public = APIRouter(prefix="/api")
    api = APIRouter(prefix="/api", dependencies=[Depends(require_token)])

    @public.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok", "env": settings.env, "version": __version__}

    @api.get("/devices")
    async def list_devices() -> list[Device]:
        return await manager.list_devices()

    @api.post("/devices/scan")
    async def scan() -> list[Device]:
        return await manager.scan()

    @api.post("/devices/{device_id}/pairing/{protocol}")
    async def start_pairing(device_id: str, protocol: str) -> dict[str, str]:
        return {"session": await manager.start_pairing(device_id, protocol)}

    @api.post("/devices/{device_id}/pairing/{protocol}/pin")
    async def finish_pairing(device_id: str, protocol: str, body: PinBody) -> dict[str, bool]:
        await manager.finish_pairing(device_id, protocol, body.session, body.pin)
        return {"paired": True}

    @api.post("/devices/{device_id}/commands/{command}", status_code=204)
    async def send_command(
        device_id: str, command: str, body: CommandBody | None = None
    ) -> Response:
        await manager.send(device_id, command, body.action if body else None)
        return Response(status_code=204)

    def error_handler(status: int):
        async def handle(request: Request, exc: Exception) -> JSONResponse:
            if status >= 500:
                log.warning("%s %s -> %d: %r", request.method, request.url.path, status, exc)
            return JSONResponse({"detail": str(exc) or type(exc).__name__}, status_code=status)

        return handle

    for exc_type, status in ERROR_STATUS.items():
        app.add_exception_handler(exc_type, error_handler(status))

    app.include_router(public)
    app.include_router(api)

    if settings.static_dir:
        if settings.static_dir.is_dir():
            app.mount("/", StaticFiles(directory=settings.static_dir, html=True), name="web")
        else:
            log.warning(
                "IRIS_STATIC_DIR %s doesn't exist; serving the API only", settings.static_dir
            )

    return app
