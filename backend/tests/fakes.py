"""Stand-ins for pyatv objects, so tests run without a network or an Apple TV."""

import weakref
from types import SimpleNamespace

from pyatv import exceptions
from pyatv.const import OperatingSystem, Protocol

GOOD_PIN = "1234"


class FakeService:
    def __init__(self, credentials: str | None = None) -> None:
        self.credentials = credentials


class FakeConfig:
    def __init__(self, identifier: str, name: str = "Bedroom", paired: bool = True) -> None:
        self.identifier = identifier
        self.all_identifiers = [identifier, f"{identifier}-alt"]
        self.name = name
        self.address = "192.0.2.10"
        self.device_info = SimpleNamespace(
            operating_system=OperatingSystem.TvOS, version="26.6", model_str="Apple TV 4K"
        )
        creds = "creds" if paired else None
        self.services = {
            Protocol.Companion: FakeService(creds),
            Protocol.AirPlay: FakeService(creds),
        }

    def get_service(self, protocol: Protocol) -> FakeService | None:
        return self.services.get(protocol)


class FakeInterface:
    """Records every method call; methods named in `fail` raise their exception once."""

    def __init__(self, name: str, calls: list, fail: dict) -> None:
        self._name = name
        self._calls = calls
        self._fail = fail

    def __getattr__(self, method: str):
        async def call(*args):
            key = f"{self._name}.{method}"
            if key in self._fail:
                raise self._fail.pop(key)
            self._calls.append((key, args))

        return call


class FakeAppleTV:
    def __init__(self, calls: list, fail: dict) -> None:
        self._listener = None
        self.closed = False
        self.remote_control = FakeInterface("remote_control", calls, fail)
        self.audio = FakeInterface("audio", calls, fail)
        self.power = FakeInterface("power", calls, fail)
        self.touch = FakeInterface("touch", calls, fail)

    # Held weakly, like pyatv: whoever sets a listener has to keep it alive.
    @property
    def listener(self):
        return self._listener() if self._listener else None

    @listener.setter
    def listener(self, value) -> None:
        self._listener = weakref.ref(value) if value is not None else None

    def close(self) -> set:
        self.closed = True
        return set()


class FakePairingHandler:
    def __init__(self, config: FakeConfig, protocol: Protocol, name: str) -> None:
        self.config = config
        self.protocol = protocol
        self.name = name
        self.began = False
        self.closed = False
        self.has_paired = False
        self.device_provides_pin = True
        self._pin = None

    async def begin(self) -> None:
        self.began = True

    def pin(self, pin: str) -> None:
        self._pin = pin

    async def finish(self) -> None:
        if self._pin != GOOD_PIN:
            raise exceptions.PairingError("wrong pin")
        self.config.services[self.protocol].credentials = "new-creds"
        self.has_paired = True

    async def close(self) -> None:
        self.closed = True


class FakeStorage:
    def __init__(self) -> None:
        self.saves = 0

    async def load(self) -> None:
        pass

    async def save(self) -> None:
        self.saves += 1


class FakeClient:
    def __init__(self, configs: list[FakeConfig]) -> None:
        self.configs = configs
        self.calls: list = []
        self.fail: dict = {}  # "interface.method" -> exception to raise once
        self.connections: list[FakeAppleTV] = []
        self.handlers: list[FakePairingHandler] = []
        self.scans = 0
        self.storage_obj = FakeStorage()

    def storage(self, path: str) -> FakeStorage:
        return self.storage_obj

    async def scan(self, storage, hosts):
        self.scans += 1
        return list(self.configs)

    async def connect(self, config, storage) -> FakeAppleTV:
        atv = FakeAppleTV(self.calls, self.fail)
        self.connections.append(atv)
        return atv

    async def pair(self, config, protocol, storage, name) -> FakePairingHandler:
        handler = FakePairingHandler(config, protocol, name)
        self.handlers.append(handler)
        return handler
