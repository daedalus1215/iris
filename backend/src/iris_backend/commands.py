from dataclasses import dataclass

from pyatv.const import InputAction, TouchAction


@dataclass(frozen=True)
class Command:
    interface: str  # attribute on pyatv's AppleTV object
    method: str
    takes_action: bool = False  # accepts tap / double_tap / hold


_BUTTONS_WITH_ACTION = ("up", "down", "left", "right", "select", "menu", "home")
_BUTTONS = ("top_menu", "play_pause", "play", "pause", "next", "previous")
_SKIPS = ("skip_forward", "skip_backward")

# The only commands the API accepts. Names are what clients send.
COMMANDS: dict[str, Command] = {
    **{name: Command("remote_control", name, takes_action=True) for name in _BUTTONS_WITH_ACTION},
    **{name: Command("remote_control", name) for name in _BUTTONS + _SKIPS},
    "volume_up": Command("audio", "volume_up"),
    "volume_down": Command("audio", "volume_down"),
    "turn_on": Command("power", "turn_on"),
    "turn_off": Command("power", "turn_off"),
}

ACTIONS: dict[str, InputAction] = {
    "tap": InputAction.SingleTap,
    "double_tap": InputAction.DoubleTap,
    "hold": InputAction.Hold,
}

# A finger on the Siri Remote's touch surface: down, moving, up.
TOUCH_PHASES: dict[str, TouchAction] = {
    "press": TouchAction.Press,
    "move": TouchAction.Hold,
    "release": TouchAction.Release,
}
