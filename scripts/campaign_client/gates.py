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


class CursorVisibilityRequired(ValueError):
    """The clip alone cannot decide; the adapter needs a read-only visibility sample."""


def gate_seconds(op):
    """Worst-case wait of a ``require`` action, checked when the request is parsed."""
    return op.get("attempts", 1) * (op.get("timeout", 5) + op.get("interval", 1))


def cursor_captured(clip, client, desktop, cursor_flags=None):
    """Whether the pointer is confined to the client area, as GLFW does while no screen is open.

    The three rectangles are ``(left, top, right, bottom)`` in screen pixels. GLFW clips the
    pointer to the client area while it is disabled (in-game, no screen) and removes
    the clip when a screen, chat or a menu shows the pointer. Only when the desktop
    clip is also inside the client do we consult GetCursorInfo flags: 0 is hidden,
    1 is showing. Missing, suppressed or unknown flags refuse that ambiguous case.
    A hidden pointer must have the full client clip, as GLFW's disabled mode sets.
    """
    for rect in (clip, client, desktop):
        if len(rect) != 4 or any(type(value) is not int for value in rect) or rect[0] >= rect[2] or rect[1] >= rect[3]:
            raise ValueError("Invalid rectangle")
    inside = clip[0] >= client[0] and clip[1] >= client[1] and clip[2] <= client[2] and clip[3] <= client[3]
    if tuple(clip) == tuple(desktop):
        if inside:
            if cursor_flags is None:
                raise CursorVisibilityRequired("Pointer capture is undecidable for a window covering the desktop "
                                               "without cursor visibility")
            if type(cursor_flags) is not int or cursor_flags not in (0, 1):
                raise ValueError("Pointer capture is undecidable with suppressed or unknown cursor visibility")
            if cursor_flags == 0 and tuple(clip) != tuple(client):
                raise ValueError("Hidden cursor contradicts the GLFW client clip")
            return cursor_flags == 0
        return False
    return inside
