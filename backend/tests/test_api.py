import pytest
from fastapi.testclient import TestClient
from pyatv import exceptions
from pyatv.const import InputAction, TouchAction
from starlette.testclient import WebSocketDenialResponse
from starlette.websockets import WebSocketDisconnect

from iris_backend.api import create_app
from iris_backend.devices import DeviceManager
from iris_backend.settings import Settings

from .fakes import GOOD_PIN, FakeClient, FakeConfig

DEVICE = "AA:BB:CC:00:00:01"


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
        "address": "192.0.2.10",
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


def touch_url(device=DEVICE):
    return f"/api/devices/{device}/touch"


def test_touch_events_reach_pyatv_in_order(client, fake):
    with client.websocket_connect(touch_url()) as ws:
        ws.send_json({"phase": "press", "x": 300, "y": 500})
        ws.send_json({"phase": "move", "x": 450, "y": 520})
        ws.send_json({"phase": "release", "x": 700, "y": 500})

    assert fake.calls == [
        ("touch.action", (300, 500, TouchAction.Press)),
        ("touch.action", (450, 520, TouchAction.Hold)),
        ("touch.action", (700, 500, TouchAction.Release)),
    ]
    assert len(fake.connections) == 1


@pytest.mark.parametrize(
    "event",
    [
        {"phase": "spin", "x": 0, "y": 0},
        {"phase": "press", "x": 1001, "y": 0},
        {"phase": "press"},
        "not json",
    ],
)
def test_bad_touch_events_get_an_error_and_the_socket_stays_open(client, fake, event):
    with client.websocket_connect(touch_url()) as ws:
        if isinstance(event, str):
            ws.send_text(event)
        else:
            ws.send_json(event)
        assert ws.receive_json()["status"] == 400

        ws.send_json({"phase": "press", "x": 0, "y": 0})
        ws.send_json({"phase": "release", "x": 0, "y": 0})

    assert [args[2] for _, args in fake.calls] == [TouchAction.Press, TouchAction.Release]


def test_touch_on_an_unpaired_device_reports_409(client):
    with client.websocket_connect(touch_url("UNPAIRED")) as ws:
        ws.send_json({"phase": "press", "x": 500, "y": 500})
        error = ws.receive_json()

    assert error == {"detail": "'Den' isn't paired yet", "status": 409}


def test_dropping_the_socket_mid_drag_lifts_the_finger(client, fake):
    with client.websocket_connect(touch_url()) as ws:
        ws.send_json({"phase": "press", "x": 500, "y": 500})
        ws.send_json({"phase": "move", "x": 620, "y": 480})
        ws.close()

    assert fake.calls[-1] == ("touch.action", (620, 480, TouchAction.Release))


def sent_touches(fake):
    return [content for name, (_, content) in fake.calls if name == "companion.event"]


def test_touch_times_from_the_client_set_the_spacing_however_they_arrive(client, fake):
    # Sent back to back, as when Wi-Fi delivers a burst at once.
    with client.websocket_connect(touch_url()) as ws:
        ws.send_json({"phase": "press", "x": 300, "y": 500, "t": 1000.0})
        ws.send_json({"phase": "move", "x": 400, "y": 500, "t": 1016.0})
        ws.send_json({"phase": "move", "x": 500, "y": 500, "t": 1032.5})
        ws.send_json({"phase": "release", "x": 520, "y": 500, "t": 1040.5})

    events = sent_touches(fake)
    times = [event["_ns"] for event in events]
    assert [b - a for a, b in zip(times, times[1:], strict=False)] == [
        16_000_000,
        16_500_000,
        8_000_000,
    ]
    assert [(e["_tPh"], e["_cx"], e["_cy"], e["_tFg"]) for e in events] == [
        (TouchAction.Press.value, 300, 500, 1),
        (TouchAction.Hold.value, 400, 500, 1),
        (TouchAction.Hold.value, 500, 500, 1),
        (TouchAction.Release.value, 520, 500, 1),
    ]


def test_each_drag_starts_at_the_present_and_times_never_go_back(client, fake):
    with client.websocket_connect(touch_url()) as ws:
        ws.send_json({"phase": "press", "x": 500, "y": 500, "t": 90_000.0})
        ws.send_json({"phase": "release", "x": 600, "y": 500, "t": 90_400.0})
        # A client whose clock started again, e.g. a reloaded page.
        ws.send_json({"phase": "press", "x": 500, "y": 500, "t": 5.0})
        ws.send_json({"phase": "release", "x": 400, "y": 500, "t": 105.0})

    times = [event["_ns"] for event in sent_touches(fake)]
    assert times == sorted(times) and len(set(times)) == 4
    assert times[3] - times[2] == 100_000_000


def test_pyatv_touch_internals():
    """The pyatv internals _send_touch relies on, since there's no public way to pass a time."""
    import inspect

    from pyatv.core.relayer import Relayer
    from pyatv.protocols.companion import CompanionTouchGestures
    from pyatv.protocols.companion.api import CompanionAPI

    assert inspect.iscoroutinefunction(CompanionAPI._send_event)
    assert "self._base_timestamp = time.time_ns()" in inspect.getsource(CompanionAPI._touch_start)
    hid_event = inspect.getsource(CompanionAPI.hid_event)
    assert all(
        key in hid_event for key in ('"_hidT"', '"_ns"', '"_tFg"', '"_cx"', '"_tPh"', '"_cy"')
    )
    assert "self.api = api" in inspect.getsource(CompanionTouchGestures.__init__)
    assert callable(Relayer.get)


def keyboard_url(device=DEVICE):
    return f"/api/devices/{device}/keyboard"


def test_keyboard_reports_focus_and_text_as_they_change(client, fake):
    with client.websocket_connect(keyboard_url()) as ws:
        assert ws.receive_json() == {"focused": False, "text": None}

        client.portal.call(fake.keyboard.focus, "star")
        assert ws.receive_json() == {"focused": True, "text": "star"}

        client.portal.call(fake.keyboard.unfocus)
        assert ws.receive_json() == {"focused": False, "text": None}


def test_typed_text_replaces_the_focused_field(client, fake):
    fake.keyboard.text = "sta"
    with client.websocket_connect(keyboard_url()) as ws:
        assert ws.receive_json() == {"focused": True, "text": "sta"}
        ws.send_json({"text": "star wars"})

    assert fake.calls == [("keyboard.text_set", ("star wars",))]
    assert fake.keyboard.text == "star wars"


@pytest.mark.parametrize("message", [{"txt": "hi"}, {"text": "x" * 1001}, "not json"])
def test_bad_keyboard_messages_get_an_error_and_the_socket_stays_open(client, fake, message):
    fake.keyboard.text = ""
    with client.websocket_connect(keyboard_url()) as ws:
        ws.receive_json()
        if isinstance(message, str):
            ws.send_text(message)
        else:
            ws.send_json(message)
        assert ws.receive_json()["status"] == 400

        ws.send_json({"text": "ok"})

    assert fake.calls == [("keyboard.text_set", ("ok",))]


def test_keyboard_on_an_unpaired_device_reports_409_and_closes(client):
    with client.websocket_connect(keyboard_url("UNPAIRED")) as ws:
        assert ws.receive_json() == {"detail": "'Den' isn't paired yet", "status": 409}
        with pytest.raises(WebSocketDisconnect) as closed:
            ws.receive_json()

    assert closed.value.code == 1011


def test_keyboard_socket_closes_when_the_apple_tv_connection_is_lost(client, fake):
    with client.websocket_connect(keyboard_url()) as ws:
        ws.receive_json()
        listener = fake.connections[0].listener
        client.portal.call(listener.connection_lost, Exception("tv restarted"))
        with pytest.raises(WebSocketDisconnect) as closed:
            ws.receive_json()

    assert closed.value.code == 1011
    # Opening it again reconnects and watches the new connection.
    with client.websocket_connect(keyboard_url()) as ws:
        assert ws.receive_json() == {"focused": False, "text": None}
        client.portal.call(fake.keyboard.focus, "")
        assert ws.receive_json() == {"focused": True, "text": ""}
    assert len(fake.connections) == 2


def test_token_is_required_when_configured(fake, tmp_path):
    with make_client(fake, tmp_path, auth_token="s3cret") as client:
        assert client.get("/api/health").status_code == 200
        assert client.get("/api/devices").status_code == 401
        headers = {"Authorization": "Bearer s3cret"}
        assert client.get("/api/devices", headers=headers).status_code == 200

        with pytest.raises(WebSocketDenialResponse) as denied:
            client.websocket_connect(touch_url()).__enter__()
        assert denied.value.status_code == 401
        with client.websocket_connect(touch_url(), headers=headers) as ws:
            ws.send_json({"phase": "press", "x": 500, "y": 500})


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
    monkeypatch.setenv("IRIS_SCAN_HOSTS", "192.0.2.10, 192.0.2.11")
    monkeypatch.setenv("IRIS_STATIC_DIR", "")
    monkeypatch.setenv("IRIS_DATA_DIR", "~/iris-data")

    settings = Settings(_env_file=None)

    assert settings.scan_hosts == ["192.0.2.10", "192.0.2.11"]
    assert settings.static_dir is None
    assert not str(settings.data_dir).startswith("~")
