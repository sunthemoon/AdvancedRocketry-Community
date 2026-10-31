"""W221 named-pad observations and native restart oracle; UNKNOWN is never absence."""
import copy
import gzip
import re
import struct

import loader_cut_nbt as nbt
import loader_s6_flight as flight
import loader_s6_forward as forward
import loader_s6_integrity as integrity
import loader_s6_terminal as terminal
from loader_cut_oracle import require, FUEL, ROCKET, LOADER
from run_dedicated_server_smoke import SmokeError
from run_v180_escrow_forced_stop import LEDGER, ledger, record, digest

CASES = tuple("LF_S6_PAD_" + suffix for suffix in (
    "PREPARED", "REFUSED", "STOPPED", "BEFORE", "MOVED", "UNKNOWN", "RELEASED"))


def check_interval(events, cut, restart):
    require(cut in CASES and restart in (0, 1, 2) and events, "Missing named-pad interval")
    require([e["seq"] for e in events] == list(range(len(events))), "Missing/reordered named-pad observation")
    require(events[0]["label"] == ("RESTART" if restart else "PREPARED"), "Missing named-pad prefix")
    initial = events[0]; previous = initial; releases = 0; acknowledged = False
    for event in events:
        before = copy.deepcopy(initial["pads"]); actual = copy.deepcopy(event["pads"])
        require(len(before["pads"]) == len(actual["pads"]) == 1, "Changed pad count")
        claim = actual["pads"][0].pop("claim", None); original = before["pads"][0].pop("claim", None)
        require(actual == before and claim in (None, original), "Changed complete named-pad identity/epoch/claim")
        prior_claim = previous["pads"]["pads"][0].get("claim")
        if prior_claim is not None and claim is None:
            require(acknowledged and event["absence"] == 1, "Pad released before checked absence")
            releases += 1
        require(event["releases"] == releases and releases <= 1, "Duplicate/missing pad release")
        if event["label"] == "ACK":
            require(event["absence"] == 1, "Unacknowledged absence presented as ACK")
            acknowledged = True
        if event["label"] in ("REFUSED", "STOPPED"):
            require(event.get("refused") == 1 and all(event[k] == previous[k] for k in ("source", "journal", "pads")),
                    "Refused request mutated complete source/journal/pad")
        if event["label"] in ("BEFORE", "MOVED", "UNKNOWN"):
            require(claim == original and original is not None and event["absence"] == releases == 0,
                    "Pending journal removal released the named claim")
        if event["label"] == "UNKNOWN": require(event.get("outcome") == "UNKNOWN", "Missing UNKNOWN write")
        previous = event
    require(events[-1]["label"] == ("RESULT" if restart else cut.removeprefix("LF_S6_PAD_")), "Wrong named-pad boundary")


def normalize(value):
    value = forward.normalize(value)
    if isinstance(value, dict):
        return {key: flight.s6.canonical_uuid(item) if key in ("station", "pad", "physical", "owner", "claim", "transfer",
                    "station_id", "pad_id", "physical_id", "destination_station_id") or key == "id" and isinstance(item, list) else normalize(item)
                for key, item in value.items()}
    if isinstance(value, list): return [normalize(item) for item in value]
    return value


def payload(path):
    return nbt.plain(nbt.read(gzip.decompress(path.read_bytes()))[1])["data"]


def inspect(world):
    try:
        rockets, devices, pads = [], [], []
        files = {}
        # Census every native region, not just the expected two entity slots.
        for path in sorted(world.rglob("*.mca")):
            if path.parent.name not in ("entities", "region"): continue
            with path.open("rb") as stream: header = stream.read(4096)
            require(len(header) == 4096, "Truncated native region header")
            files[str(path.relative_to(world))] = digest(path)
            for slot in range(1024):
                if not struct.unpack_from(">I", header, slot * 4)[0]: continue
                data = record(path, slot)
                for entity in data.get("Entities", []):
                    if entity.get("id") == ROCKET or "RocketEntityData" in entity: rockets.append(entity)
                for entity in data.get("block_entities", []):
                    if entity.get("id") == LOADER: devices.append(entity)
                    if entity.get("id") == "advancedrocketrycommunity:landing_pad": pads.append(entity)
        return dict(read_status="KNOWN", native=dict(ledger=ledger(world / "data" / LEDGER),
            journal=flight.legacy.journal(world), pads=payload(world / "data/advancedrocketrycommunity_landing_pads.dat"),
            rockets=rockets, loaders=devices, physical_pads=pads, files=files))
    except (OSError, ValueError, KeyError, TypeError, SmokeError) as failure:
        return dict(read_status="UNKNOWN", native=None, error=str(failure))


def witness(lines, restart, cut):
    prefix = "ARCE_LOADER_S6_PAD_EVENT cut=" + cut + " "
    events = []
    for line in lines:
        if prefix not in line: continue
        match = re.search(re.escape(prefix) + r"nbt=([A-Za-z0-9+/=]+)$", line.rstrip())
        require(match is not None, "Malformed named-pad capture")
        events.append(normalize(terminal.decode_capture(match[1])))
    check_interval(events, cut, restart)
    return events


def check_logs(lines, restart, cut):
    events = witness(lines, restart, cut)
    prefix = "ARCE_LOADER_S6_PAD_"
    ticks = [int(re.search(r" index=(\d+)", line)[1]) for line in lines if prefix + "TICK cut=" + cut + " " in line]
    ends = [re.search(r"count=(\d+)$", line.rstrip()) for line in lines if prefix + "END cut=" + cut + " " in line]
    begins = [line for line in lines if prefix + "BEGIN cut=" + cut + " " in line]
    require(len(begins) == len(ends) == 1 and ends[0] and ticks == list(range(int(ends[0][1]))), "Incomplete named-pad tick interval")
    errors = [line for line in lines if "/ERROR]" in line or "/FATAL]" in line]
    # The existing UNKNOWN injector intentionally produces this checked-write diagnostic only.
    allowed = not restart and cut == "LF_S6_PAD_UNKNOWN"
    require(not errors or allowed and len(errors) == 1 and "ARCE_ROCKET_JOURNAL_WRITE_FAILED" in errors[0],
            "Unexpected named-pad ERROR/FATAL: " + str(errors))
    return events


def check(image, restart, identity, cut, events):
    require(image.get("read_status") == "KNOWN" and isinstance(image.get("native"), dict), "UNKNOWN named-pad read")
    check_interval(events, cut, restart)
    try:
        disk = normalize(image["native"]); last = events[-1]
        if not restart:
            first = events[0]; row = first["journal"]["transfers"][0]; base = first["baseline"]
            require(len(first["journal"]["transfers"]) == 1 and row["phase"] == "PREPARED" and row["schema_version"] == 3,
                    "Missing governed PREPARED authority")
            require(row["source_snapshot"] == base["RocketEntityData"]["snapshot"] and row["required_fuel"] == 500,
                    "Changed complete source or typed debit")
            require(first["pads"]["pads"][0]["claim"] == row["transfer_id"], "Pad claim is not this T")
            entry = first["pads"]["pads"][0]; plan = row["source_flight"]["plan"]
            require(plan["destination_station_id"] == entry["station"] and plan["landing_pad"] == dict(id=entry["pad"], epoch=entry["epoch"])
                    and plan["propulsion_debit"]["primary"] == 500 and plan["propulsion_debit"]["oxidizer"] == 0
                    and plan["propulsion_debit"]["revision"] == base["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["revision"],
                    "Changed named destination or authorized typed debit")
            require(len(disk["physical_pads"]) == 1 and disk["physical_pads"][0]["landing_pad"] == dict(schema=1,
                    physical=entry["physical"], station=entry["station"], pad=entry["pad"], epoch=entry["epoch"]),
                    "Physical pad disagrees with the complete named binding")
            integrity.pair_identity(dict(loaders=disk["loaders"], rocket=base), disk["ledger"])
            identity = dict(baseline=flight.projection(base), transfer=row, pads=first["pads"],
                physical_pads=disk["physical_pads"], loader=first["loader"], root=first["ledger"], journal=first["journal"])
        require(identity is not None and len(disk["loaders"]) == len(disk["physical_pads"]) == 1, "Lost loader or physical pad")
        require(disk["physical_pads"] == identity["physical_pads"], "Changed physical pad binding")
        loader = copy.deepcopy(disk["loaders"][0]); expected_loader = copy.deepcopy(identity["loader"])
        # F1 can leave the retired fill marker on disk; it is checked against the exact captured marker.
        if not restart:
            wrapper = loader["arce_fluid_fuel_loader"]["resource_escrow"]
            markers = wrapper["markers"]
            require(markers == [] or markers == events[0]["retired_loader_markers"], "Changed retired loader marker")
            wrapper["markers"] = []
        require(loader == expected_loader, "Changed complete loader identity, binding, tank, epoch or marker")
        root = disk["ledger"]; expected_root = copy.deepcopy(identity["root"])
        row = identity["transfer"]; prepared = not restart and cut in CASES[:3]
        released = restart or cut == CASES[-1]
        expected_root["writer_generation"] = 4 if prepared else 6 if released else 5
        if released: expected_root["armed_flights"] = []
        elif not prepared: expected_root["armed_flights"][0]["outcome"] = "HANDED_OFF"
        require(root == expected_root, "Changed complete named flight authority/generation")
        journal = copy.deepcopy(identity["journal"])
        if restart or cut in CASES[4:]: journal["transfers"] = []
        elif not prepared:
            journal["transfers"][0].update(phase="COMMITTED", destination_entity_id=flight.s6.canonical_uuid(row["native_transfer"]["armed"]["d"]))
        require(disk["journal"] == journal, "Changed exact named-pad journal boundary")
        expected_pad = copy.deepcopy(identity["pads"])
        if released: expected_pad["pads"][0].pop("claim")
        require(disk["pads"] == expected_pad, "Changed durable named pad identity/epoch/claim")
        require(len(disk["rockets"]) == 1, "Missing or duplicated native rocket")
        survivor = disk["rockets"][0]; expected = copy.deepcopy(identity["baseline"])
        if not prepared:
            expected.update(UUID=flight.s6.canonical_uuid(row["native_transfer"]["armed"]["d"]))
            origin = row["destination_snapshot"]["source_origin"]
            expected["Pos"] = [origin[0] + .5, float(origin[1]), origin[2] + .5]
            state = copy.deepcopy(row["destination_flight"])
            state.update(state="LANDED", state_started_game_time=last["arrival"]["RocketEntityData"]["flight_data"]["state_started_game_time"])
            state.pop("plan"); state.pop("active_transfer_id")
            expected["RocketEntityData"].update(snapshot=row["destination_snapshot"], flight_data=state,
                resource_escrow=dict(schema_version=1, ledger_id=root["ledger_id"], resource_epoch=0, markers=[]))
            require(flight.projection(last["arrival"]) == expected, "Changed complete live arrival")
        require(flight.projection(survivor) == expected, "Changed complete native snapshot, typed fuel, pose or identity")
        if restart:
            if "survivor" in identity: require(expected == identity["survivor"], "Second restart changed survivor")
            identity = dict(identity, survivor=expected)
        return dict(identity=identity, raw_total=5000 if prepared else 4500, authorized_debit=0 if prepared else 500,
                    loss=0 if restart else None)
    except (KeyError, TypeError, IndexError, ValueError) as failure:
        raise SmokeError("Malformed named-pad image: " + str(failure)) from failure
