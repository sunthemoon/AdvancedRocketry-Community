"""Bounded gates: a request stops before it acts on a state it did not find.

``require`` asks the server and waits for a literal reply, ``require_screen`` and
``close_screen`` read whether Minecraft has released the pointer, and ``expect_log``
waits for a server line written since the request began. None of them retries an
input, and each has a fixed upper bound on waiting.
"""

MAX_GATE_SECONDS = 300


class PreconditionFailed(Exception):
    """A readback or the open-screen state is not what the request requires."""


class ExpectationFailed(Exception):
    """The server did not report the outcome the request names."""


def gate_seconds(op):
    """Worst-case wait of a ``require`` action, checked when the request is parsed."""
    return op.get("attempts", 1) * (op.get("timeout", 5) + op.get("interval", 1))


def cursor_captured(clip, client, desktop):
    """Whether the pointer is confined to the client area, as GLFW does while no screen is open.

    Each argument is ``(left, top, right, bottom)`` in screen pixels. GLFW clips the
    pointer to the client area while it is disabled (in-game, no screen) and removes
    the clip when a screen, chat or a menu shows the pointer. A window that covers
    the whole desktop cannot be told apart this way, so that case is refused.
    """
    for rect in (clip, client, desktop):
        if len(rect) != 4 or any(type(value) is not int for value in rect) or rect[0] >= rect[2] or rect[1] >= rect[3]:
            raise ValueError("Invalid rectangle")
    inside = clip[0] >= client[0] and clip[1] >= client[1] and clip[2] <= client[2] and clip[3] <= client[3]
    if tuple(clip) == tuple(desktop):
        if inside:
            raise ValueError("Pointer capture is undecidable for a window covering the desktop")
        return False
    return inside
