"""W373B: the exact per-fluid account oracle of the native accounting cuts (W357-DESIGN section 7.3).

It reads only independently decoded files: the ledger, and the endpoint stores a runner extracted from the region and
entity files. Every expectation is a fixture value written out here; nothing is inferred from an account hash.
"""
from __future__ import annotations

import hashlib

import escrow_accounting as accounting
from run_dedicated_server_smoke import SmokeError

WATER = "minecraft:water"
LAVA = "minecraft:lava"
FUEL = "advancedrocketrycommunity:rocket_fuel"
KNOWN, UNKNOWN_ENDPOINT = 1, 3
TOTAL, QUANTITY, ADJUSTMENT = 5000, 1000, 100
CARGO_SCOPE, FUEL_SCOPE = 1, 0


def require(ok, message):
    if not ok:
        raise SmokeError(message)


def key_digest(fluid):
    """The amount-free key of a fluid without a tag; the absent tag is its own encoding, never an empty compound."""
    return hashlib.sha256(("arce-escrow-fluid-key-1:" + fluid + "\nabsent").encode()).hexdigest()


def store(fluid=None, amount=0, epoch=0, markers=0):
    """One raw endpoint store: its fluids, its saved resource epoch and how many markers it may hold."""
    return dict(fluids={fluid: amount} if amount else {}, epoch=epoch,
                markers=tuple(markers) if isinstance(markers, (tuple, list)) else (markers,))


def row(scope, fluid, source, destination, frozen=None, knowledge=KNOWN, quantity=QUANTITY):
    """An expected account row. `frozen` lists (fluid, s, t, a); None marks an absent amount or adjustment."""
    return dict(scope=scope, baseline=[(fluid, source, destination, -quantity, quantity)],
                frozen=None if frozen is None else dict(knowledge=knowledge, entries=sorted(frozen)))


def owners(root):
    """Every operation that must own exactly one row: the live records, then the receipts without a live record."""
    live = [accounting.identity(op["op_id"]) for op in root["records"]]
    return live + [uid for uid in (accounting.identity(r["op_id"]) for r in root["tombstones"]) if uid not in live]


def check_rows(root, expected):
    """Exact rows: the same owners, and every baseline and frozen entry equal to its fixture value."""
    require(root.get("schema_version") == 3 and root.get("format_epoch") == accounting.EPOCHS[3],
            "The ledger is not root 3 with the accounting epoch")
    rows = accounting.check(root, fresh_fluid=True)
    ids = owners(root)
    require(len(ids) == len(expected) and set(ids) == rows.keys() and len(root["fluid_accounts"]) == len(expected),
            f"Account rows differ from their owners: {len(root['fluid_accounts'])} rows, {len(ids)} owners, {len(expected)} expected")
    for uid, want in zip(ids, expected):
        got = rows[uid]
        require(got["v"] == 1 and got["b"]["s"] == want["scope"], "Account row schema or scope differs")
        baseline = [(e["i"], bytes(e["d"]).hex(), e["s"], e["t"], e["x"], e["y"]) for e in got["b"]["e"]]
        require(baseline == [(f, key_digest(f), s, t, x, y) for f, s, t, x, y in want["baseline"]],
                f"Account baseline differs from the fixture: {[b[:1] + b[2:] for b in baseline]}")
        if want["frozen"] is None:
            require("f" not in got, "A planned account row acquired a frozen part")
            continue
        require("f" in got, "The settled account row has no frozen part")
        frozen = got["f"]
        require(frozen["k"] == want["frozen"]["knowledge"], f"Frozen knowledge differs: {frozen['k']}")
        require(accounting.identity(frozen["g"]) == uid and [accounting.identity(frozen["m"][i:i + 4])
                for i in range(0, len(frozen.get("m", [])), 4)] == [uid], "Frozen group owner or member list differs")
        entries = [(e["i"], bytes(e["d"]).hex(), e.get("s"), e.get("t"), e.get("a"), e["m"], accounting.identity(e["o"]))
                   for e in frozen["e"]]
        wanted = [(f, key_digest(f), s, t, a, (s is not None) | (t is not None) << 1 | (a is not None) << 2, uid)
                  for f, s, t, a in want["frozen"]["entries"]]
        require(entries == wanted, f"Frozen entries differ from the fixture: {[e[:1] + e[2:6] for e in entries]}")
        require(all((e[4] is not None) == (frozen["k"] == KNOWN) for e in entries),
                "A signed adjustment exists only for a KNOWN account")
    return rows


def raw_total(stores, fluid):
    return sum(end["fluids"].get(fluid, 0) for end in stores if end is not None)


def check_store(name, got, want):
    if want is None:
        require(got is None, f"The absent {name} reappeared")
        return
    require(got is not None, f"The {name} store is missing")
    require(got["fluids"] == want["fluids"], f"Raw {name} amounts differ: {got['fluids']}, expected {want['fluids']}")
    require(got["epoch"] == want["epoch"], f"Raw {name} epoch differs: {got['epoch']}, expected {want['epoch']}")
    require(got["markers"] in want["markers"], f"Raw {name} marker count differs: {got['markers']}, expected one of {want['markers']}")


def check_plan(spec, source, destination):
    """Before O1: dS + dD + q * (aS - aD) = 0 from the raw stores, with each endpoint's saved epoch as its prefix."""
    fluid, s0, t0, x, y = spec["baseline"][0]
    require(source["epoch"] in (0, 1) and destination["epoch"] in (0, 1), "A single transfer repeated a resource epoch")
    residual = source["fluids"].get(fluid, 0) - s0 + destination["fluids"].get(fluid, 0) - t0
    require(residual + y * (source["epoch"] - destination["epoch"]) == 0,
            f"Raw stores break the planned transfer identity: residual {residual}")
    require(set(source["fluids"]) | set(destination["fluids"]) <= {fluid}, "Another fluid appeared in a planned transfer")


def check_frozen(spec, source, destination):
    """After O1: for every key, the residual of the raw stores plus the approved signed adjustment is zero."""
    baseline = {fluid: (s0, t0) for fluid, s0, t0, _, _ in spec["baseline"]}
    entries = {fluid: (s, t, a) for fluid, s, t, a in spec["frozen"]["entries"]}
    require(set(source["fluids"]) | set(destination["fluids"]) <= entries.keys(), "A raw fluid has no frozen account key")
    for fluid, (s, t, adjustment) in entries.items():
        s0, t0 = baseline.get(fluid, (0, 0))
        require(source["fluids"].get(fluid, 0) == s and destination["fluids"].get(fluid, 0) == t,
                f"Raw stores differ from the frozen observation of {fluid}")
        residual = source["fluids"].get(fluid, 0) - s0 + destination["fluids"].get(fluid, 0) - t0
        require(adjustment is not None and residual + adjustment == 0,
                f"Signed residual of {fluid} is {residual}, approved adjustment {adjustment}")


def check_receipts(root, expected):
    """Receipts are schema 4 with the expected release flag, knowledge and missing count; cargo carries no fill liability."""
    got = [(r["schema_version"], bool(r["released"]), r["accounting_status"], len(r["missing"])) for r in root["tombstones"]]
    require(got == [(4, released, status, missing) for released, status, missing in expected],
            f"Receipts differ: {got}")
    for receipt in root["tombstones"]:
        require(receipt["accounting_status"] == "UNKNOWN" or not receipt["missing"], "A missing endpoint was reported KNOWN")


def check_state(root, source, destination, expect):
    """One start of a single-transfer case. `expect` is this module's table entry for that start."""
    require(root is not None, "The world has no escrow ledger")
    phases = [op["phase"] for op in root["records"]]
    require(phases == expect["records"], f"Ledger records differ: {phases}, expected {expect['records']}")
    require(all(op["schema_version"] == 3 and "accounting_baseline" in op for op in root["records"]),
            "A fresh fluid operation took the legacy branch")
    require(all(("settlement" in op) == (op["phase"] in ("SETTLING_ACCEPT", "SETTLING_ABANDON")) for op in root["records"]),
            "The frozen intent's presence differs from the record's phase")
    require(root["issued_through"] == expect["issued"], f"Issued sequence differs: {root['issued_through']}")
    check_rows(root, expect["rows"])
    check_receipts(root, expect["receipts"])
    check_store("source", source, expect["source"])
    check_store("destination", destination, expect["destination"])
    balance = expect["balance"]
    if balance == "PLAN":
        check_plan(expect["rows"][0], source, destination)
    elif balance == "FROZEN":
        check_frozen(expect["rows"][0], source, destination)
    elif balance == "HELD":
        # The device durably holds what the frozen choice does not name: nothing may be released over it.
        entries = {fluid: (s, t) for fluid, s, t, _ in expect["rows"][0]["frozen"]["entries"]}
        differs = any(source["fluids"].get(f, 0) != s or destination["fluids"].get(f, 0) != t for f, (s, t) in entries.items())
        require(differs and not root["tombstones"] and phases == ["SETTLING_ACCEPT"],
                "The changed store did not hold the settlement")
    else:
        require(balance in ("NONE", "MISSING"), "Unknown balance expectation")
    for fluid, total in expect.get("totals", {}).items():
        require(raw_total((source, destination), fluid) == total,
                f"Raw {fluid} total is {raw_total((source, destination), fluid)}, expected {total}")
    return dict(records=phases, rows=len(root["fluid_accounts"]), receipts=len(root["tombstones"]),
                source=source, destination=destination)


def check_progress(before, after):
    """Between two starts: no new baseline, no changed frozen row, no receipt lost or un-released, no regression."""
    require(before["ledger_id"] == after["ledger_id"] and after["writer_generation"] >= before["writer_generation"]
            and after["issued_through"] >= before["issued_through"], "Ledger identity, generation or sequence regressed")
    accounting.check_progress(before, after)
    old = {accounting.identity(r["op_id"]): r for r in before["tombstones"]}
    new = {accounting.identity(r["op_id"]): r for r in after["tombstones"]}
    for uid, receipt in old.items():
        require(uid in new, "A restart lost a receipt")
        require(all(new[uid][key] == receipt[key] for key in ("accounting_digest", "accounting_status", "decision_digest",
                                                             "choice", "payload_amount", "payload_hash")),
                "A restart changed a receipt's account, decision or payload")
        require(new[uid]["released"] or not receipt["released"], "A restart withdrew a release")
    rows = {accounting.identity(r["op"]): r for r in after.get("fluid_accounts", [])}
    for uid in new:
        require(uid in rows and "f" in rows[uid], "A receipt-backed account row was removed")


# ---- Expected states of the cargo fixture: (source, destination) are (rocket, device) for unload, reversed for load.

def _ends(source, destination, epoch, markers, fluid=WATER):
    return (store(fluid, source, epoch, markers), store(fluid, destination, epoch, markers))


def _expect(records, rows, receipts, ends, issued, balance, totals=None):
    return dict(records=records, rows=rows, receipts=receipts, source=ends[0], destination=ends[1], issued=issued,
                balance=balance, totals={WATER: TOTAL} if totals is None else totals)


PLANNED = row(CARGO_SCOPE, WATER, TOTAL, 0)
SETTLED = row(CARGO_SCOPE, WATER, TOTAL, 0, [(WATER, TOTAL - QUANTITY, QUANTITY, 0)])
BEFORE = _ends(TOTAL, 0, 0, 0)
RETIRED = _expect([], [], [], _ends(TOTAL - QUANTITY, QUANTITY, 1, 0), 1, "NONE")
UNTOUCHED = _expect([], [], [], BEFORE, 0, "NONE")


def ordinary(cut):
    """FC_ cuts: the three expected states of an ordinary transfer cut at `cut` (the cut, then both restarts)."""
    if cut in ("L1_BEFORE", "E4L1_UNKNOWN"):
        return [UNTOUCHED, UNTOUCHED, UNTOUCHED]
    if cut in ("L1_REPLACED", "L1_ACK"):
        first = _expect(["PREPARED"], [PLANNED], [], BEFORE, 1, "PLAN")
    elif cut in ("C6_JOINT_READBACK", "E4L2_UNKNOWN"):
        first = _expect(["PREPARED"], [PLANNED], [], _ends(TOTAL - QUANTITY, QUANTITY, 1, 1), 1, "PLAN")
    elif cut in ("L2_REPLACED", "C7_ACKNOWLEDGED"):
        # The row left with its operation; the stores were last written with their markers, or already pruned.
        first = _expect([], [], [], _ends(TOTAL - QUANTITY, QUANTITY, 1, (0, 1)), 1, "NONE")
    else:
        raise SmokeError("No ordinary accounting expectation for " + cut)
    return [first, RETIRED, RETIRED]


def _settlement_ends(load, device_fluid, device_amount, markers, rocket_epoch=1, device_epoch=1):
    rocket = store(WATER, QUANTITY if load else TOTAL - QUANTITY, rocket_epoch, markers)
    device = store(device_fluid, device_amount, device_epoch, markers)
    return (device, rocket) if load else (rocket, device)


def settlement(cut, load):
    """FS_ cuts: the expected states of an operator settlement cut at `cut`, for one direction."""
    after_device = TOTAL - QUANTITY if load else QUANTITY
    after_rocket = QUANTITY if load else TOTAL - QUANTITY

    def frozen_row(device_fluid=WATER, device_amount=after_device):
        """The frozen KNOWN row of ACCEPT_CURRENT over the rocket's AFTER amount and the device's observed tank."""
        s0, t0 = TOTAL, 0
        observed = {WATER: [0, 0], device_fluid: [0, 0]}
        observed[WATER][1 if load else 0] = after_rocket
        observed[device_fluid][0 if load else 1] += device_amount
        entries = []
        for fluid, (s, t) in observed.items():
            before = (s0, t0) if fluid == WATER else (0, 0)
            entries.append((fluid, s, t, -((s - before[0]) + (t - before[1]))))
        return row(CARGO_SCOPE, WATER, TOTAL, 0, entries)

    applied = _settlement_ends(load, WATER, after_device, 1)
    pruned = _settlement_ends(load, WATER, after_device, 0)
    accepted = lambda r, ends, totals=None: _expect(["SETTLING_ACCEPT"], [r], [], ends, 1, "FROZEN", totals)
    receipt = lambda r, ends, released, records, totals=None: _expect(records, [r], [(released, "KNOWN", 0)], ends, 1, "FROZEN", totals)
    done = receipt(SETTLED, pruned, True, [])
    if cut == "O1_BEFORE":
        held = _expect(["REPAIR"], [PLANNED], [], applied, 1, "PLAN")
        return [held, held, held]
    if cut in ("O1_AFTER", "AC_O1_ACK", "O3_BEFORE"):
        return [accepted(SETTLED, applied), done, done]
    if cut in ("O3_AFTER", "AC_O3_ACK", "O4_BEFORE"):
        final = done
        if cut == "O3_AFTER":
            # The existing probe launches the released rocket on restart 1; the landed successor restarts at epoch 0.
            final = receipt(SETTLED, _settlement_ends(load, WATER, after_device, 0, rocket_epoch=0), True, [])
        return [receipt(SETTLED, applied, False, ["SETTLING_ACCEPT"]), final, final]
    if cut in ("O4_AFTER", "AC_O4_ACK", "PRUNE_BEFORE"):
        return [receipt(SETTLED, applied, True, []), done, done]
    if cut == "PRUNE_AFTER":
        return [done, done, done]
    if cut in ("AC_LOSS", "AC_GAIN"):
        amount = after_device + (-ADJUSTMENT if cut == "AC_LOSS" else ADJUSTMENT)
        totals = {WATER: after_rocket + amount}
        changed = frozen_row(WATER, amount)
        final = receipt(changed, _settlement_ends(load, WATER, amount, 0), True, [], totals)
        return [accepted(changed, _settlement_ends(load, WATER, amount, 1), totals), final, final]
    if cut == "AC_SUBSTITUTE":
        totals = {WATER: after_rocket, LAVA: after_device}
        changed = frozen_row(LAVA, after_device)
        final = receipt(changed, _settlement_ends(load, LAVA, after_device, 0), True, [], totals)
        return [accepted(changed, _settlement_ends(load, LAVA, after_device, 1), totals), final, final]
    if cut == "AC_READBACK":
        totals = {WATER: after_rocket, LAVA: after_device}
        held = _expect(["SETTLING_ACCEPT"], [SETTLED], [], _settlement_ends(load, LAVA, after_device, 1), 1, "HELD", totals)
        return [held, held, held]
    if cut in ("ABANDON_ROCKET", "ABANDON_DEVICE"):
        # The transfer was held at PREPARED, so the survivor still holds its BEFORE amount at epoch 0.
        rocket_missing = cut == "ABANDON_ROCKET"
        source_missing = rocket_missing != load
        survivor = TOTAL if not source_missing else 0
        entry = (WATER, None, survivor, None) if source_missing else (WATER, survivor, None, None)
        unknown = row(CARGO_SCOPE, WATER, TOTAL, 0, [entry], UNKNOWN_ENDPOINT)
        kept = store(WATER, survivor, 0, 0)
        ends = (None, kept) if source_missing else (kept, None)
        first = _expect(["SETTLING_ABANDON"], [unknown], [], ends, 1, "MISSING", {WATER: survivor})
        final = _expect([], [unknown], [(True, "UNKNOWN", 1)], ends, 1, "MISSING", {WATER: survivor})
        return [first, final, final]
    raise SmokeError("No settlement accounting expectation for " + cut)


# ---- The fuel loader: one or two fills of 1,000 mB of rocket fuel into the same bank.

def loader_states(disk):
    """The loader runner's independently decoded disk as (tanks, bank): each tank's and the rocket's raw store."""
    tanks = []
    for device in disk["loaders"]:
        root = device["arce_fluid_fuel_loader"]; tank = root["tank"]
        require(not tank or "Tag" not in tank, "A loader tank holds a tagged fluid")
        tanks.append(dict(fluids={tank["FluidName"]: tank["Amount"]} if tank else {}, epoch=root["resource_escrow"]["resource_epoch"],
                          markers=len(root["resource_escrow"]["markers"]), device=root["device"]))
    data = disk["rocket"]["RocketEntityData"]; propulsion = data["flight_data"]["fuel"]["propulsion"]
    fluids = {}
    for bank in ("primary", "oxidizer"):
        entry = propulsion[bank]
        if entry.get("amount"):
            # Amounts are aggregated per endpoint and key: the same fluid in both banks is one key.
            fluid = entry.get("fluid")
            # RocketPropulsionFuelCodec: a FLUID bank saves `fluid` and, only for a tagged fluid, `tag`.
            require(isinstance(fluid, str) and "tag" not in entry, "A filled bank holds an unnamed or tagged fluid")
            fluids[fluid] = fluids.get(fluid, 0) + entry["amount"]
    bank = dict(fluids=fluids, epoch=data["resource_escrow"]["resource_epoch"], markers=len(data["resource_escrow"]["markers"]))
    return tanks, bank


def check_loader(disk, cut, restart):
    """LF_M_ cuts: exact fuel-scope rows, with a chained second baseline projected from the first fill."""
    root = disk["ledger"]
    tanks, bank = loader_states(disk)
    name = cut.removeprefix("LF_M_")
    chain = name in ("C2", "C2_PARTIAL_REPLACED")
    require(len(tanks) == (2 if chain else 1), "Wrong loader count for the accounting cut")
    planned = lambda filled: row(FUEL_SCOPE, FUEL, TOTAL, filled * QUANTITY)
    if restart:
        untouched = name in ("D2_C1_BEFORE", "E4L1_UNKNOWN")
        records, rows, fills = [], [], 0 if untouched else len(tanks)
    elif chain:
        # C2 holds both fills; the partial cut has retired the first and still holds the second, projected one.
        records = ["PREPARED"] if name == "C2_PARTIAL_REPLACED" else ["PREPARED", "PREPARED"]
        rows = [planned(1)] if name == "C2_PARTIAL_REPLACED" else [planned(0), planned(1)]
        fills = 2
    else:
        table = {"D2_C1_BEFORE": (0, 0), "E4L1_UNKNOWN": (0, 0), "D2_C1_REPLACED": (1, 0), "D2_C1_ACK": (1, 0),
                 "D2_C7_BEFORE": (1, 1), "E4L2_UNKNOWN": (1, 1), "D2_C7_REPLACED": (0, 1), "D2_C7_ACK": (0, 1)}
        require(name in table, "No loader accounting expectation for " + cut)
        live, fills = table[name]
        records, rows = ["PREPARED"] * live, [planned(0)] * live
    if root is None:
        require(not records and not fills, "A fill exists without a ledger")
    else:
        phases = [op["phase"] for op in root["records"]]
        require(phases == records, f"Loader ledger records differ: {phases}, expected {records}")
        require(all(op["schema_version"] == 3 and op["kind"] == "FLUID_PROPULSION_FILL" for op in root["records"]),
                "A fresh fill took the legacy branch")
        require(not root["tombstones"], "An ordinary fill acquired a receipt")
        check_rows(root, rows)
        if chain and not restart:
            # The chain's rows are in sequence order, and each destination baseline continues the previous AFTER.
            require([op["seq"] for op in root["records"]] == ([2] if name == "C2_PARTIAL_REPLACED" else [1, 2]),
                    "The chain's records are not its contiguous suffix")
    for index, tank in enumerate(tanks):
        # Each loader is debited at most once; a chain's two fills come from two loaders.
        require(tank["fluids"] == {FUEL: TOTAL - (QUANTITY if fills else 0)}, f"Raw loader tank {index} differs: {tank['fluids']}")
    require(bank["fluids"] == ({FUEL: fills * QUANTITY} if fills else {}), f"Raw bank differs: {bank['fluids']}")
    # No resource created or lost: every loader started with 5,000 mB and the bank with none.
    require(raw_total(tanks + [bank], FUEL) == TOTAL * len(tanks), "Raw rocket fuel total changed")
    return dict(records=records, rows=len(rows), bank=bank["fluids"], tanks=[tank["fluids"] for tank in tanks])


# ---- Format boundaries (root-2 upgrade and the old JAR's refusal); the runner supplies digests and decoded roots.

def check_upgraded(root, legacy_digests, source, destination):
    """A migrated ledger: root 3, no row invented, every legacy operation byte-equal, nothing applied or retired."""
    require(root["schema_version"] == 3 and root["format_epoch"] == accounting.EPOCHS[3], "The ledger is not root 3")
    accounting.check(root, fresh_fluid=False)
    require(root["fluid_accounts"] == [], "The migration invented an account row")
    require([op["schema_version"] for op in root["records"]] == [2] * len(legacy_digests)
            and all("accounting_baseline" not in op for op in root["records"]), "A legacy operation was promoted")
    require(root["record_digests"] == legacy_digests, "A legacy operation's bytes changed")
    require([op["phase"] for op in root["records"]] == ["PREPARED"] and not root["tombstones"],
            "The legacy UNKNOWN_BASELINE operation was retired, settled or changed its phase")
    check_store("source", source, store(WATER, TOTAL, 0, 0))
    check_store("destination", destination, store(None, 0, 0, 0))
    return dict(records=1, rows=0, legacy=True)


def check_root2(root, legacy_digests):
    require(root["schema_version"] == 2 and root["format_epoch"] == accounting.EPOCHS[2], "The ledger is not root 2")
    accounting.check(root, fresh_fluid=False)
    require(root["record_digests"] == legacy_digests and [op["phase"] for op in root["records"]] == ["PREPARED"],
            "The root-2 ledger's open operation changed")


def changed_files(before, after):
    """Every world file that a start changed, added or removed, by SHA-256."""
    return dict(changed=sorted(k for k in before.keys() & after.keys() if before[k] != after[k]),
                added=sorted(after.keys() - before.keys()), removed=sorted(before.keys() - after.keys()))
