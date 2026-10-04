import pytest
from fastapi.testclient import TestClient
from pyatv import exceptions
from pyatv.const import InputAction

from iris_backend.api import create_app
from iris_backend.devices import DeviceManager
from iris_backend.settings import Settings

from .fakes import GOOD_PIN, FakeClient, FakeConfig

DEVICE = "B6:B8:78:10:43:D0"


@pytest.fixture
def fake(tmp_path):
    return FakeClient([FakeConfig(DEVICE), FakeConfig("UNPAIRED", name="Den", paired=False)])


def make_client(fake, tmp_path, **overrides) -> TestClient:
    settings = Settings(_env_file=None, data_dir=tmp_path, **overrides)
    return TestClient(create_app(settings, DeviceManager(settings, fake)))


@pytest.fixture
def client(fake, tmp_path):
    with make_client(fake, tmp_path) as client:
        yield client


def command(client, name, device=DEVICE, **body):
    return client.post(f"/api/devices/{device}/commands/{name}", json=body or None)


def test_health(client):
    assert client.get("/api/health").json() == {"status": "ok", "env": "local", "version": "0.1.0"}


def test_devices_scans_once_and_reports_pairing(client, fake):
    devices = client.get("/api/devices").json()
    client.get("/api/devices")

    assert fake.scans == 1
    assert devices[0] == {
        "id": DEVICE,
        "name": "Bedroom",
        "address": "172.16.0.242",
        "model": "Apple TV 4K",
        "os": "tvOS 26.6",
        "paired": {"companion": True, "airplay": True},
        "connected": False,
    }
    assert devices[1]["paired"] == {"companion": False, "airplay": False}


@pytest.mark.parametrize(
    ("name", "body", "expected"),
    [
        ("up", {}, ("remote_control.up", ())),
        ("select", {"action": "hold"}, ("remote_control.select", (InputAction.Hold,))),
        ("play_pause", {}, ("remote_control.play_pause", ())),
        ("volume_up", {}, ("audio.volume_up", ())),
        ("turn_off", {}, ("power.turn_off", ())),
    ],
)
def test_command_reaches_the_right_pyatv_call(client, fake, name, body, expected):
    assert command(client, name, **body).status_code == 204
    assert fake.calls == [expected]


def test_connection_is_reused_between_commands(client, fake):
    for name in ("up", "down", "left"):
        command(client, name)

    assert len(fake.connections) == 1
    assert client.get("/api/devices").json()[0]["connected"] is True


def test_device_found_by_alternate_identifier(client, fake):
    assert command(client, "up", device=f"{DEVICE}-alt").status_code == 204


def test_reconnects_after_connection_lost(client, fake):
    command(client, "up")
    fake.connections[0].listener.connection_lost(Exception("tv restarted"))
    command(client, "down")

    assert len(fake.connections) == 2


def test_retries_once_when_the_connection_dropped_silently(client, fake):
    command(client, "up")
    fake.fail["remote_control.down"] = exceptions.ConnectionLostError("gone")

    assert command(client, "down").status_code == 204
    assert len(fake.connections) == 2
    assert fake.calls[-1] == ("remote_control.down", ())


@pytest.mark.parametrize(
    ("name", "body", "status"),
    [
        ("rm -rf", {}, 400),
        ("up", {"action": "spin"}, 400),
        ("play_pause", {"action": "hold"}, 400),
    ],
)
def test_bad_commands_are_rejected(client, fake, name, body, status):
    assert command(client, name, **body).status_code == status
    assert fake.calls == []


def test_unknown_device_is_404(client):
    assert command(client, "up", device="nope").status_code == 404


def test_unpaired_device_is_409(client):
    assert command(client, "up", device="UNPAIRED").status_code == 409


def test_unreachable_apple_tv_is_503(client, fake):
    command(client, "up")
    fake.fail["remote_control.left"] = exceptions.ConnectionFailedError("no route")

    # The command fails on the open connection, then reconnecting fails too.
    async def broken_connect(config, storage):
        raise exceptions.ConnectionFailedError("no route")

    fake.connect = broken_connect
    assert command(client, "left").status_code == 503


def test_token_is_required_when_configured(fake, tmp_path):
    with make_client(fake, tmp_path, auth_token="s3cret") as client:
        assert client.get("/api/health").status_code == 200
        assert client.get("/api/devices").status_code == 401
        headers = {"Authorization": "Bearer s3cret"}
        assert client.get("/api/devices", headers=headers).status_code == 200


def test_pairing_flow(client, fake):
    started = client.post("/api/devices/UNPAIRED/pairing/companion")
    session = started.json()["session"]
    finished = client.post(
        "/api/devices/UNPAIRED/pairing/companion/pin", json={"session": session, "pin": GOOD_PIN}
    )

    assert finished.json() == {"paired": True}
    handler = fake.handlers[0]
    assert handler.began and handler.closed
    assert handler.name == "Iris (local)"
    assert fake.storage_obj.saves == 1
    paired = client.get("/api/devices").json()[1]["paired"]
    assert paired == {"companion": True, "airplay": False}


def test_wrong_pin_fails_and_ends_the_session(client, fake):
    session = client.post("/api/devices/UNPAIRED/pairing/airplay").json()["session"]
    url = "/api/devices/UNPAIRED/pairing/airplay/pin"

    assert client.post(url, json={"session": session, "pin": "9999"}).status_code == 400
    assert client.post(url, json={"session": session, "pin": GOOD_PIN}).status_code == 400
    assert fake.handlers[0].closed


@pytest.mark.parametrize(
    ("protocol", "pin", "status"),
    [("bluetooth", None, 400), ("companion", "12ab", 422)],
)
def test_pairing_rejects_bad_input(client, protocol, pin, status):
    if pin is None:
        response = client.post(f"/api/devices/{DEVICE}/pairing/{protocol}")
    else:
        response = client.post(
            f"/api/devices/{DEVICE}/pairing/{protocol}/pin", json={"session": "x", "pin": pin}
        )
    assert response.status_code == status


def test_serves_the_web_remote(fake, tmp_path):
    web = tmp_path / "web"
    web.mkdir()
    (web / "index.html").write_text("<h1>Iris</h1>")

    with make_client(fake, tmp_path, static_dir=web) as client:
        assert "Iris" in client.get("/").text
        assert client.get("/api/health").status_code == 200


def test_settings_from_env(monkeypatch):
    monkeypatch.setenv("IRIS_SCAN_HOSTS", "172.16.0.242, 10.0.0.5")
    monkeypatch.setenv("IRIS_STATIC_DIR", "")
    monkeypatch.setenv("IRIS_DATA_DIR", "~/iris-data")

    settings = Settings(_env_file=None)

    assert settings.scan_hosts == ["172.16.0.242", "10.0.0.5"]
    assert settings.static_dir is None
    assert not str(settings.data_dir).startswith("~")
