#!/usr/bin/env python3
"""W330 native shared per-owner cap: four real cargo devices, forced stop and two restarts."""
from __future__ import annotations

from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

import run_v180_cargo_cuts as cuts
import run_v180_cargo_s4_s5_cuts as fluid
import item_cuts_oracle as item

CASES = {f"CQ_{medium}_{direction}": dict(family="C-Q", cut="OWNER_PENDING_REFUSED",
         kind=f"{medium}_CARGO_{direction}") for medium in ("FLUID", "ITEM") for direction in ("LOAD", "UNLOAD")}
HOOKS = cuts.HOOKS + (cuts.HOOK_DIR + "EscrowCargoOwnerCapProbe.java",)
HARNESS = cuts.HARNESS + ("scripts/run_v180_cargo_owner_cap.py", "scripts/run_v180_cargo_s4_s5_cuts.py", "scripts/item_cuts_oracle.py")
raw_snapshot = cuts.raw_snapshot
require = fluid.require


def observations(lines, case, restart):
    prefix = "ARCE_RELEASE_CARGO_CUT_RESULT" if restart else "ARCE_RELEASE_CARGO_CUT_PAUSED"
    matches = [line for line in lines if prefix + " cut=" + case + " " in line]
    require(len(matches) == 1, "Missing/repeated owner-cap boundary")
    require(("pending=16 refused=OWNER_PENDING_FULL independent_epoch=1" if restart else "boundary=OWNER_PENDING_REFUSED") in matches[0],
            "Wrong owner-cap boundary/result")
    require(not any("/ERROR]" in line or "/FATAL]" in line for line in lines), "Owner-cap run logged ERROR/FATAL")


def inspect(world: Path, case: str, restarted: bool):
    if "_ITEM_" in case:
        disk = item.inspect(world, case)
        # item.inspect intentionally reads only its single-device fixture cell; read this family's second device too.
        terrain = item.read(world)["terrain"]
        roots = [e["arce_rocket_item_cargo"] for e in terrain["block_entities"] if "arce_rocket_item_cargo" in e
                 and [e.get(k) for k in ("x", "y", "z")] == [64, 100, 68]]
        require(len(roots) == 1, "Missing independent item device")
        root = roots[0]; section = root["resource_escrow"]
        disk["second"] = dict(id=root["device"], owner=root["owner"], logical=root["binding"]["logical_rocket"],
                              role=root["role"], amount=item.exact_stack(root["buffer"]["slots"], True),
                              epoch=section["resource_epoch"], markers=section["markers"])
    else:
        disk = fluid.inspect(world)
        disk["second"] = fluid.device_state(fluid.record(world / "region/r.0.0.mca"), [64, 100, 68], disk["ledger"]["ledger_id"])
    check(case, disk, restarted)
    return dict(identity=dict(rockets=[(r["uuid"], r["logical"], r["owner"]) for r in sorted(disk["rockets"], key=lambda r: r["pos"][2])],
                              devices=[(disk[k]["id"], disk[k]["owner"], disk[k]["logical"]) for k in ("device", "second")]), **disk)


def check(case, disk, restarted):
    load = case.endswith("_LOAD"); typed = "_ITEM_" in case
    rockets = sorted(disk["rockets"], key=lambda r: r["pos"][2]); root = disk["ledger"]
    require(len(rockets) == 2 and disk["device"] is not None and disk["second"] is not None, "Owner-cap endpoint missing/duplicated")
    capped, other = rockets
    require(capped["pos"] == [68.5, 100.0, 64.5] and other["pos"] == [68.5, 100.0, 68.5], "Owner-cap rocket placement changed")
    require(len(root["records"]) == 16 and len({op["op_id"] for op in root["records"]}) == 16
            and all(op["owner"] == capped["owner"] and op["phase"] == "REPAIR" and op["reason"] == "OTHER_STATE" for op in root["records"])
            and len({op["guard"]["rocket_id"] for op in root["records"]}) == 16, "Owner cap no longer has sixteen independent pending charges")
    require(root["issued_through"] == (17 if restarted else 16) and not root["tombstones"] and not root["floors"]
            and not root["rocket_authorities"] and not root["armed_flights"] and not disk["journal"], "Cap refusal/independent transfer changed authority")
    before = (60 if load else 10, 10 if load else 60) if typed else (0 if load else 5000, 5000 if load else 0)
    after = (64 if load else 6, 6 if load else 64) if typed else (1000 if load else 4000, 4000 if load else 1000)
    require(capped["owner"] != other["owner"], "Independent owner aliases capped owner")
    for rocket, device, amounts, epoch in ((capped, disk["device"], before, 0),
                                           (other, disk["second"], after if restarted else before, int(restarted))):
        require((rocket["cargo"], device["amount"]) == amounts and rocket["epoch"] == device["epoch"] == epoch
                and not rocket["markers"] and not device["markers"] and device["role"] == ("LOAD" if load else "UNLOAD")
                and rocket["owner"] == device["owner"] and rocket["logical"] == device["logical"]
                and not rocket["passengers"] and rocket["fuel"] == 1000, "Cap changed a resource, wrapper, binding or fuel")


def run(args):
    # Reuse the established pinned three-start controller, not any cargo-specific verdict or mutation oracle.
    with ExitStack() as stack:
        for key, value in dict(CASES=CASES, HOOKS=HOOKS, HARNESS=HARNESS, raw_snapshot=raw_snapshot,
                               observations=observations, inspect=inspect).items():
            stack.enter_context(patch.object(cuts, key, value))
        cuts.run(args)
