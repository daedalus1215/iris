import contextlib
import logging
import secrets

from fastapi import (
    APIRouter,
    Depends,
    FastAPI,
    Header,
    HTTPException,
    Request,
    WebSocket,
    WebSocketDisconnect,
)
from fastapi.responses import JSONResponse, Response
from fastapi.staticfiles import StaticFiles
from pyatv import exceptions
from pydantic import BaseModel, Field, ValidationError

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


class TouchEvent(BaseModel):
    phase: str
    x: int = Field(ge=0, le=1000)
    y: int = Field(ge=0, le=1000)


def status_for(exc: Exception) -> int | None:
    """The HTTP status ERROR_STATUS gives this exception, following its class hierarchy."""
    return next((ERROR_STATUS[c] for c in type(exc).__mro__ if c in ERROR_STATUS), None)


def create_app(settings: Settings | None = None, manager: DeviceManager | None = None) -> FastAPI:
    settings = settings or Settings()
    manager = manager or DeviceManager(settings)

    @contextlib.asynccontextmanager
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

    @api.websocket("/devices/{device_id}/touch")
    async def touch(websocket: WebSocket, device_id: str) -> None:
        """A finger on the touchpad, as a stream of {phase, x, y}; x and y run 0 to 1000.

        A phase is press, move or release. Errors come back as {detail, status}, and the
        socket stays open, so the next touch can try again.
        """
        await websocket.accept()
        held: TouchEvent | None = None  # last position while a finger is down
        try:
            while True:
                message = await websocket.receive_text()
                try:
                    event = TouchEvent.model_validate_json(message)
                    await manager.touch(device_id, event.phase, event.x, event.y)
                    held = event if event.phase != "release" else None
                except ValidationError:
                    await websocket.send_json({"detail": "bad touch event", "status": 400})
                except tuple(ERROR_STATUS) as e:
                    status = status_for(e)
                    if status >= 500:
                        log.warning("touch on %s -> %d: %r", device_id, status, e)
                    detail = str(e) or type(e).__name__
                    await websocket.send_json({"detail": detail, "status": status})
        except WebSocketDisconnect:
            # Don't leave a finger resting on the Apple TV's touchpad.
            if held is not None:
                with contextlib.suppress(Exception):
                    await manager.touch(device_id, "release", held.x, held.y)

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
