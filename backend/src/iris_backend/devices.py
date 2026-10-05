"""Apple TV discovery, pairing, and connections that stay open between commands."""

import asyncio
import logging
import time
import uuid
from collections.abc import Awaitable, Callable
from dataclasses import dataclass

import pyatv
from pyatv import exceptions
from pyatv.const import KeyboardFocusState, OperatingSystem, Protocol
from pyatv.interface import (
    AppleTV,
    BaseConfig,
    DeviceListener,
    KeyboardListener,
    PairingHandler,
    Storage,
)
from pyatv.storage.file_storage import FileStorage

from .commands import ACTIONS, COMMANDS, TOUCH_PHASES
from .settings import Settings

log = logging.getLogger(__name__)

PAIRING_PROTOCOLS = {"companion": Protocol.Companion, "airplay": Protocol.AirPlay}
PAIRING_TTL = 120.0
SCAN_TIMEOUT = 5
CONNECT_TIMEOUT = 15.0

# Errors that mean the connection is gone, so reconnecting may help.
CONNECTION_ERRORS = (exceptions.ConnectionLostError, exceptions.ConnectionFailedError, OSError)

OS_NAMES = {OperatingSystem.TvOS: "tvOS", OperatingSystem.MacOS: "macOS"}


class DeviceNotFound(Exception):
    pass


class NotPaired(Exception):
    pass


class InvalidRequest(Exception):
    pass


class PairingSessionNotFound(Exception):
    pass


class PairingFailed(Exception):
    pass


@dataclass
class KeyboardState:
    """The Apple TV's text field: whether one has focus, and what's typed in it."""

    focused: bool
    text: str | None = None


# Gets the keyboard's new state, or None once the connection to the Apple TV is gone.
KeyboardWatcher = Callable[[KeyboardState | None], None]


@dataclass
class Device:
    id: str
    name: str
    address: str
    model: str
    os: str
    paired: dict[str, bool]
    connected: bool


class PyatvClient:
    """The pyatv calls DeviceManager makes; tests swap in a fake."""

    def storage(self, path: str) -> Storage:
        return FileStorage(path, asyncio.get_running_loop())

    async def scan(self, storage: Storage, hosts: list[str]) -> list[BaseConfig]:
        return await pyatv.scan(
            asyncio.get_running_loop(), timeout=SCAN_TIMEOUT, hosts=hosts or None, storage=storage
        )

    async def connect(self, config: BaseConfig, storage: Storage) -> AppleTV:
        return await pyatv.connect(config, asyncio.get_running_loop(), storage=storage)

    async def pair(
        self, config: BaseConfig, protocol: Protocol, storage: Storage, name: str
    ) -> PairingHandler:
        return await pyatv.pair(
            config, protocol, asyncio.get_running_loop(), storage=storage, name=name
        )


@dataclass
class _PairingSession:
    device_id: str
    protocol: str
    handler: PairingHandler
    expires: float


class _ConnectionListener(DeviceListener, KeyboardListener):
    def __init__(self, manager: "DeviceManager", device_id: str, atv: AppleTV) -> None:
        self._manager = manager
        self._device_id = device_id
        self._atv = atv

    def connection_lost(self, exception: Exception) -> None:
        log.warning("lost connection to %s: %s", self._device_id, exception)
        self._manager._forget(self._device_id, self._atv)

    def connection_closed(self) -> None:
        self._manager._forget(self._device_id, self._atv)

    def focusstate_update(
        self, old_state: KeyboardFocusState, new_state: KeyboardFocusState
    ) -> None:
        self._manager._keyboard_focus_changed(self._device_id)


class DeviceManager:
    def __init__(self, settings: Settings, client: PyatvClient | None = None) -> None:
        self._settings = settings
        self._client = client or PyatvClient()
        self._storage: Storage | None = None
        self._configs: dict[str, BaseConfig] = {}
        self._connections: dict[str, AppleTV] = {}
        # pyatv holds listeners weakly, so they're kept here for as long as their connection.
        self._listeners: dict[str, _ConnectionListener] = {}
        self._locks: dict[str, asyncio.Lock] = {}
        self._pairings: dict[str, _PairingSession] = {}
        self._keyboard_watchers: dict[str, set[KeyboardWatcher]] = {}
        self._tasks: set[asyncio.Task] = set()

    async def start(self) -> None:
        self._settings.data_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        self._storage = self._client.storage(str(self._settings.storage_file))
        await self._storage.load()

    async def stop(self) -> None:
        sessions, self._pairings = list(self._pairings.values()), {}
        for session in sessions:
            await session.handler.close()
        connections, self._connections = list(self._connections.values()), {}
        for atv in connections:
            await self._close(atv)

    # Devices

    async def scan(self) -> list[Device]:
        configs = await self._client.scan(self._storage, self._settings.scan_hosts)
        self._configs = {config.identifier: config for config in configs if config.identifier}
        log.info("scan found %d device(s)", len(self._configs))
        return self.devices()

    async def list_devices(self) -> list[Device]:
        if not self._configs:
            return await self.scan()
        return self.devices()

    def devices(self) -> list[Device]:
        return [self._describe(config) for config in self._configs.values()]

    def _describe(self, config: BaseConfig) -> Device:
        info = config.device_info
        os_name = OS_NAMES.get(info.operating_system, info.operating_system.name)
        return Device(
            id=config.identifier,
            name=config.name,
            address=str(config.address),
            model=info.model_str,
            os=f"{os_name} {info.version}" if info.version else os_name,
            paired={name: _has_credentials(config, p) for name, p in PAIRING_PROTOCOLS.items()},
            connected=config.identifier in self._connections,
        )

    def _lookup(self, device_id: str) -> BaseConfig | None:
        """Find a device by its main identifier or any other identifier it advertises."""
        for config in self._configs.values():
            if device_id == config.identifier or device_id in config.all_identifiers:
                return config
        return None

    async def _config(self, device_id: str) -> BaseConfig:
        config = self._lookup(device_id)
        if config is None:
            await self.scan()
            config = self._lookup(device_id)
        if config is None:
            raise DeviceNotFound(f"no Apple TV with id '{device_id}'")
        return config

    # Commands

    async def send(self, device_id: str, name: str, action: str | None = None) -> None:
        command = COMMANDS.get(name)
        if command is None:
            raise InvalidRequest(f"unknown command '{name}'")
        if action is not None and action not in ACTIONS:
            raise InvalidRequest(f"unknown action '{action}'")
        if action is not None and not command.takes_action:
            raise InvalidRequest(f"'{name}' doesn't take an action")
        args = (ACTIONS[action],) if action else ()

        config = await self._config(device_id)
        started = time.perf_counter()
        await self._call(
            config, lambda atv: getattr(getattr(atv, command.interface), command.method)(*args)
        )
        elapsed_ms = (time.perf_counter() - started) * 1000
        log.info(
            "%s %s%s %.1f ms",
            config.identifier,
            name,
            f" ({action})" if action else "",
            elapsed_ms,
        )

    async def touch(self, device_id: str, phase: str, x: int, y: int) -> None:
        """One step of a finger on the Siri Remote's touch surface; x and y run 0 to 1000."""
        mode = TOUCH_PHASES.get(phase)
        if mode is None:
            raise InvalidRequest(f"unknown touch phase '{phase}'")
        config = await self._config(device_id)
        await self._call(config, lambda atv: atv.touch.action(x, y, mode))
        log.debug("%s touch %s %d,%d", config.identifier, phase, x, y)

    async def _call[T](self, config: BaseConfig, call: Callable[[AppleTV], Awaitable[T]]) -> T:
        """Run one pyatv call on the device's open connection, reconnecting once if it dropped.

        Calls to one device run one at a time, so held buttons can't build a backlog.
        """
        device_id = config.identifier
        async with self._locks.setdefault(device_id, asyncio.Lock()):
            for attempt in (1, 2):
                atv = await self._connection(config)
                try:
                    return await asyncio.wait_for(call(atv), self._settings.command_timeout)
                except TimeoutError:
                    self._drop(device_id)
                    raise
                except CONNECTION_ERRORS as e:
                    self._drop(device_id)
                    if attempt == 2:
                        raise
                    log.info("connection to %s failed (%s); reconnecting", device_id, e)

    async def _connection(self, config: BaseConfig) -> AppleTV:
        device_id = config.identifier
        if atv := self._connections.get(device_id):
            return atv
        if not any(_has_credentials(config, p) for p in PAIRING_PROTOCOLS.values()):
            raise NotPaired(f"'{config.name}' isn't paired yet")
        started = time.perf_counter()
        atv = await asyncio.wait_for(self._client.connect(config, self._storage), CONNECT_TIMEOUT)
        listener = _ConnectionListener(self, device_id, atv)
        atv.listener = listener
        atv.keyboard.listener = listener
        self._connections[device_id] = atv
        self._listeners[device_id] = listener
        log.info("connected to %s in %.0f ms", device_id, (time.perf_counter() - started) * 1000)
        return atv

    def _forget(self, device_id: str, atv: AppleTV) -> None:
        """Drop a connection, unless it has already been replaced by a newer one."""
        if self._connections.get(device_id) is atv:
            del self._connections[device_id]
            self._listeners.pop(device_id, None)
            self._connection_gone(device_id)

    def _drop(self, device_id: str) -> None:
        self._listeners.pop(device_id, None)
        if atv := self._connections.pop(device_id, None):
            asyncio.get_running_loop().create_task(self._close(atv))
            self._connection_gone(device_id)

    @staticmethod
    async def _close(atv: AppleTV) -> None:
        await asyncio.gather(*atv.close(), return_exceptions=True)

    # Keyboard

    async def watch_keyboard(self, device_id: str, watcher: KeyboardWatcher) -> Callable[[], None]:
        """Give [watcher] the text field's state now, then whenever focus moves to or from one.

        It gets None once the connection to the Apple TV is lost, and nothing after that.
        Returns a function that stops watching.
        """
        config = await self._config(device_id)
        watchers = self._keyboard_watchers.setdefault(config.identifier, set())
        # Watch first, so a change while the state is being read isn't missed.
        watchers.add(watcher)
        try:
            watcher(await self._call(config, _keyboard_state))
        except BaseException:
            watchers.discard(watcher)
            raise
        return lambda: watchers.discard(watcher)

    async def set_text(self, device_id: str, text: str) -> None:
        """Replace what's typed in the Apple TV's focused text field."""
        config = await self._config(device_id)
        await self._call(config, lambda atv: atv.keyboard.text_set(text))
        log.debug("%s text set (%d characters)", config.identifier, len(text))

    def _keyboard_focus_changed(self, device_id: str) -> None:
        if self._keyboard_watchers.get(device_id):
            task = asyncio.get_running_loop().create_task(self._publish_keyboard(device_id))
            self._tasks.add(task)
            task.add_done_callback(self._tasks.discard)

    async def _publish_keyboard(self, device_id: str) -> None:
        config = self._lookup(device_id)
        if config is None:
            return
        try:
            state = await self._call(config, _keyboard_state)
        except Exception as e:  # the watchers keep the last state; the next change tries again
            log.warning("couldn't read the keyboard of %s: %r", device_id, e)
            return
        for watcher in list(self._keyboard_watchers.get(device_id, ())):
            watcher(state)

    def _connection_gone(self, device_id: str) -> None:
        """Keyboard events stop with the connection, so tell the watchers to start again."""
        watchers = self._keyboard_watchers.get(device_id)
        if watchers:
            for watcher in list(watchers):
                watcher(None)
            watchers.clear()

    # Pairing

    async def start_pairing(self, device_id: str, protocol_name: str) -> str:
        protocol = PAIRING_PROTOCOLS.get(protocol_name)
        if protocol is None:
            raise InvalidRequest(f"unknown protocol '{protocol_name}'")
        await self._expire_pairings()
        config = await self._config(device_id)
        handler = await self._client.pair(
            config, protocol, self._storage, self._settings.pairing_name
        )
        try:
            await handler.begin()
        except Exception:
            await handler.close()
            raise
        session_id = uuid.uuid4().hex
        self._pairings[session_id] = _PairingSession(
            config.identifier, protocol_name, handler, time.monotonic() + PAIRING_TTL
        )
        log.info("pairing %s over %s started", config.identifier, protocol_name)
        return session_id

    async def finish_pairing(
        self, device_id: str, protocol_name: str, session_id: str, pin: str
    ) -> None:
        await self._expire_pairings()
        config = await self._config(device_id)
        session = self._pairings.get(session_id)
        if (
            session is None
            or session.device_id != config.identifier
            or session.protocol != protocol_name
        ):
            raise PairingSessionNotFound("pairing session not found or expired; start again")
        del self._pairings[session_id]
        try:
            session.handler.pin(pin)
            await session.handler.finish()
            if not session.handler.has_paired:
                raise PairingFailed("pairing didn't complete")
        except (exceptions.PairingError, exceptions.AuthenticationError) as e:
            raise PairingFailed(f"pairing failed, check the PIN: {e}") from e
        finally:
            await session.handler.close()
        await self._save_storage()
        self._drop(config.identifier)  # reconnect with the new credentials
        log.info("paired %s over %s", config.identifier, protocol_name)

    async def _expire_pairings(self) -> None:
        now = time.monotonic()
        for session_id, session in list(self._pairings.items()):
            if session.expires < now:
                del self._pairings[session_id]
                await session.handler.close()

    async def _save_storage(self) -> None:
        await self._storage.save()
        if self._settings.storage_file.exists():
            self._settings.storage_file.chmod(0o600)


async def _keyboard_state(atv: AppleTV) -> KeyboardState:
    if atv.keyboard.text_focus_state != KeyboardFocusState.Focused:
        return KeyboardState(focused=False)
    # None means the field closed in the meantime.
    text = await atv.keyboard.text_get()
    return KeyboardState(focused=text is not None, text=text)


def _has_credentials(config: BaseConfig, protocol: Protocol) -> bool:
    service = config.get_service(protocol)
    return bool(service and service.credentials)
