#!/usr/bin/env python3
"""X03 native family cargo-X03-REPAIR: skeleton of slice X03-V (W367-DESIGN section 5.1).

This file holds only the table of the gated verification cases and the builder of a verification start command, so
that the probe inventories change once, in X03-V. It starts no server. Slice X03-B completes it with the seed, the
repair cycles, the independent oracle, the refusal cases and both negative controls.
"""
from __future__ import annotations

from run_v060_flight_server_smoke import _server_command

SCENARIO = "verify"
# The launch routes the Java probe accepts: the names of RocketDestination. The runner fixes the route (section 5.1).
ROUTES = ("EARTH", "MOON", "SPACE_STATION")
# One distinct server process per case. `state` is the state of the probe's result line.
CASES = {
    "FF_X03_OBSERVE": dict(state="OBSERVED", functional=False),
    "FF_X03_UNLOAD": dict(state="UNLOADED", functional=True),
    "FF_X03_LAUNCH": dict(state="LANDED", functional=True),
}
# The functional descendant's starts, in order; each is followed by a clean stop (section 5.1).
DESCENDANT_SEQUENCE = ("FF_X03_UNLOAD", "FF_X03_OBSERVE", "FF_X03_LAUNCH", "FF_X03_OBSERVE")


def verification_command(java: str, case: str, route: str | None = None) -> list[str]:
    """The command of one verification start: the release-test hooks, the verify scenario and exactly one case."""
    if case not in CASES:
        raise ValueError(f"Unknown X03 verification case: {case!r}")
    if route is not None and (case != "FF_X03_LAUNCH" or route not in ROUTES):
        raise ValueError(f"Route {route!r} is not accepted for {case}")
    command = _server_command(java)
    command[2] = "-Xmx4G"
    properties = ["-Dadvancedrocketrycommunity.escrowScenario=" + SCENARIO,
                  "-Dadvancedrocketrycommunity.escrowCase=" + case]
    if route is not None:
        properties.append("-Dadvancedrocketrycommunity.escrowVerifyRoute=" + route)
    command[1:1] = properties
    return command


def marker(case: str) -> str:
    """The prefix of the probe's single result line for `case`."""
    return f"ARCE_RELEASE_CARGO_CUT_RESULT cut={case} state={CASES[case]['state']} "
