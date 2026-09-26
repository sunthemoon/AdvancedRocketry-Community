"""Independent raw-file, provenance, fuel and log postcheck for four flight processes."""
import gzip
import hashlib
import json
import re
import socket
import sys
import uuid
import zlib
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, "D:/GitHub/AdvancedRocketry-Community/scripts")
import run_dedicated_server_smoke as smoke
import v120_precision_world_fixture as region
from inspect_celestial_saved_data import NbtReader

BASE = Path('C:\\Users\\Administrator\\AppData\\Local\\Temp\\arce-v130-fueled-disassembly-1790442426341')
EVIDENCE = BASE / "flight-evidence"
HOST = "advancedrocketrycommunity"
FIXTURE = "arce_adapter_test"
ROCKET, CARGO = HOST + ":rocket", FIXTURE + ":cargo_container"
EARTH, MOON = "minecraft:overworld", HOST + ":moon"
PHASES = ["moon-landing", "earth-landing", "disassembly", "container-restart"]
RELATIVE = {(-1, 0, 0): HOST + ":rocket_fuel_tank", (0, 0, 0): HOST + ":rocket_motor",
            (1, 0, 0): CARGO, (0, 1, 0): HOST + ":rocket_seat", (0, 2, 0): HOST + ":guidance_computer"}
ITEMS = [{"Slot": 0, "id": "minecraft:diamond", "Count": 17,
          "tag": {"display": {"Name": '{"text":"Public adapter cargo"}'}}},
         {"Slot": 1, "id": "minecraft:iron_ingot", "Count": 64}]
RELOCATED = {"snapshot_id", "source_dimension", "source_origin", "created_at_game_time", "content_hash"}
LINKAGE = re.compile(r"NoSuchMethodError|AbstractMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|LinkageError")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def nbt_uuid(value):
    assert isinstance(value, list) and len(value) == 4
    return str(uuid.UUID(bytes=b"".join((part & 0xffffffff).to_bytes(4, "big") for part in value)))


def read_saved(path, key):
    assert path.stat().st_size <= 1024**2
    with gzip.open(path, "rb") as stream:
        raw = stream.read(4 * 1024**2 + 1)
    assert len(raw) <= 4 * 1024**2
    root = NbtReader(raw).read_root()
    assert root["DataVersion"] == 3465
    data = root["data"]
    assert data["schema_version"] == 2 and isinstance(data[key], list)
    return data


def region_file(folder, dimension, kind, cx, cz):
    world = folder / "world"
    if dimension == MOON:
        world /= "dimensions/advancedrocketrycommunity/moon"
    else:
        assert dimension == EARTH
    return world / kind / f"r.{cx // 32}.{cz // 32}.mca"


def entity_chunk(path, cx, cz):
    if not path.exists():
        return []
    data = path.read_bytes()
    assert len(data) <= 16 * 1024**2
    offset = 4 * ((cx & 31) + 32 * (cz & 31))
    if data[offset:offset + 4] == bytes(4):
        return []
    decoder = zlib.decompressobj()
    raw = decoder.decompress(region._region_chunk(data, cx, cz), 4 * 1024**2 + 1)
    assert len(raw) <= 4 * 1024**2 and decoder.eof and not decoder.unused_data and not decoder.unconsumed_tail
    root = NbtReader(raw).read_root()
    assert root["Position"] == [cx, cz]
    return root["Entities"]


def block_name(chunk, x, y, z):
    sections = [entry for entry in chunk["sections"] if entry["Y"] == y // 16]
    assert len(sections) == 1
    states = sections[0]["block_states"]
    palette = states["palette"]
    if len(palette) == 1:
        return palette[0]["Name"]
    bits = max(4, (len(palette) - 1).bit_length())
    per_word = 64 // bits
    cell = (y & 15) * 256 + (z & 15) * 16 + (x & 15)
    word = states["data"][cell // per_word] & ((1 << 64) - 1)
    index = (word >> ((cell % per_word) * bits)) & ((1 << bits) - 1)
    return palette[index]["Name"]


def check_snapshot(snapshot):
    assert snapshot["schema_version"] == 1
    actual, payloads = {}, []
    for block in snapshot["relative_blocks"]:
        pos = tuple(block["position"])
        assert pos not in actual
        state = snapshot["block_palette"][block["palette"]]
        assert state["properties"] == {}
        actual[pos] = state["id"]
        if "block_entity" in block:
            assert state["id"] == CARGO
            payloads.append(block["block_entity"])
    assert actual == RELATIVE
    assert payloads == [{"adapter": FIXTURE + ":cargo_inventory",
                         "data": {"payload_version": 1, "data": {"Items": ITEMS}}}]


def fuel(data, amount, transfers):
    assert data["capacity"] == 1000 and data["amount"] == amount
    assert [nbt_uuid(entry["transaction_id"]) for entry in data["committed_debits"]] == transfers


def main():
    summary = json.loads((EVIDENCE / "summary.json").read_text(encoding="utf-8"))
    assert [entry["phase"] for entry in summary["cycles"]] == PHASES
    identities = {"artifacts": [{"copy": item["path"], "sha256": item["sha256"]} for item in summary["artifacts"]]}
    for artifact in identities["artifacts"]:
        assert digest(Path(artifact["copy"])) == artifact["sha256"]
        assert digest(Path(summary["server"]) / "mods" / Path(artifact["copy"]).name) == artifact["sha256"]
    observations, snapshots, transfers = [], [], []
    logical = previous_data = native = None
    refuel_lines, launch_lines = [], []
    for index, phase in enumerate(PHASES):
        folder = EVIDENCE / phase
        result = json.loads((folder / "result.json").read_text(encoding="utf-8"))
        assert result["exit_code"] == 0 and result["observation_ticks"] >= 20
        assert smoke.forge_mod_versions(json.loads((folder / "status.json").read_text(encoding="utf-8"))) == {
            HOST: "1.20.1-1.3.0-dev", FIXTURE: "1.0.0", "minecraft": "1.20.1", "forge": ""}
        log_reports = []
        for name in ("stdout.txt", "debug.log", "latest.log"):
            text = (folder / name).read_text(encoding="utf-8")
            assert not smoke.scan_log(text.splitlines()), (phase, name, smoke.scan_log(text.splitlines()))
            assert not LINKAGE.search(text), (phase, name)
            log_reports.append({"name": name, "sha256": digest(folder / name),
                                "counts": smoke.log_audit_counts(text.splitlines())})
        stdout = (folder / "stdout.txt").read_text(encoding="utf-8")
        refuel_lines.extend(line for line in stdout.splitlines() if "ARCE_RELEASE_TEST_REFUEL " in line)
        launch_lines.extend(line for line in stdout.splitlines() if "ARCE_RELEASE_TEST_LAUNCH " in line)
        registration = [line for line in stdout.splitlines() if "Registered rocket adapter " in line]
        assert len(registration) == 1 and "arce_adapter_test:cargo_inventory (payload 1, API 1.1, event 1)" in registration[0]
        debug = (folder / "debug.log").read_text(encoding="utf-8")
        sources = []
        for artifact in identities["artifacts"]:
            installed = str(Path(summary["server"]) / "mods" / Path(artifact["copy"]).name)
            hits = [line for line in debug.splitlines() if "Loading mod file " + installed in line]
            assert hits, installed
            sources.extend(hits)
        assembly = read_saved(folder / "world/data/advancedrocketrycommunity_rocket_transactions.dat", "transactions")
        journal = read_saved(folder / "world/data/advancedrocketrycommunity_rocket_transfers.dat", "transfers")
        assert assembly["transactions"] == []
        if index < 2:
            assert len(journal["transfers"]) == 1
            entry = journal["transfers"][0]
            assert entry["schema_version"] == 2 and entry["phase"] == "COMMITTED" and entry["required_fuel"] == 372
            transfer = nbt_uuid(entry["transfer_id"])
            assert transfer not in transfers
            source, destination = entry["source_snapshot"], entry["destination_snapshot"]
            check_snapshot(source)
            check_snapshot(destination)
            assert {k: v for k, v in source.items() if k not in RELOCATED} == {k: v for k, v in destination.items() if k not in RELOCATED}
            assert source["snapshot_id"] != destination["snapshot_id"] and source["content_hash"] != destination["content_hash"]
            assert (source["source_dimension"], destination["source_dimension"]) == ((EARTH, MOON) if index == 0 else (MOON, EARTH))
            if index == 0:
                assert source["source_origin"] == [264, 101, 264]
                snapshots.append(source)
                logical = entry["logical_rocket_id"]
            else:
                assert source == previous_data["snapshot"]
                assert entry["logical_rocket_id"] == logical
            assert nbt_uuid(entry["owner_id"]) == "00000000-0000-0000-0000-000000000005"
            assert entry["source_entity_id"] != entry["destination_entity_id"]
            fuel(entry["source_flight"]["fuel"], 1000 - index * 372, transfers)
            transfers.append(transfer)
            fuel(entry["destination_flight"]["fuel"], 1000 - len(transfers) * 372, transfers)
            snapshots.append(destination)
        else:
            assert journal["transfers"] == []
        chunks = set()
        for snapshot in snapshots:
            x, _, z = snapshot["source_origin"]
            for cx in range((x - 2) // 16, (x + 2) // 16 + 1):
                for cz in range((z - 1) // 16, (z + 1) // 16 + 1):
                    chunks.add((snapshot["source_dimension"], cx, cz))
        assert len(chunks) <= 12
        rockets, cargo, decoded = [], [], {}
        for dimension, cx, cz in sorted(chunks):
            path = region_file(folder, dimension, "region", cx, cz)
            raw = path.read_bytes()
            assert len(raw) <= 16 * 1024**2
            chunk = region._decode_chunk(region._region_chunk(raw, cx, cz), cx, cz)
            decoded[(dimension, cx, cz)] = chunk
            cargo.extend((dimension, be) for be in chunk["block_entities"] if be["id"] == CARGO)
            entities = entity_chunk(region_file(folder, dimension, "entities", cx, cz), cx, cz)
            assert not any(entity["id"] == "minecraft:item" for entity in entities)
            rockets.extend((dimension, entity) for entity in entities if entity["id"] == ROCKET)
        restored = index >= 2
        assert len(rockets) == (0 if restored else 1) and len(cargo) == (1 if restored else 0)
        final = snapshots[-1]
        for snapshot in snapshots:
            dimension = snapshot["source_dimension"]
            x, y, z = snapshot["source_origin"]
            for (dx, dy, dz), block in RELATIVE.items():
                pos = (x + dx, y + dy, z + dz)
                expected = block if restored and snapshot is final else "minecraft:air"
                assert block_name(decoded[(dimension, pos[0] // 16, pos[2] // 16)], *pos) == expected
        if restored:
            dimension, be = cargo[0]
            x, y, z = final["source_origin"]
            assert dimension == EARTH and [be[axis] for axis in ("x", "y", "z")] == [x + 1, y, z]
            assert be["FixtureInventory"] == {"schema_version": 1, "Items": ITEMS}
            if native is None:
                native = be
            assert be == native
        else:
            dimension, entity = rockets[0]
            assert dimension == final["source_dimension"] and entity["UUID"] == entry["destination_entity_id"]
            data = entity["RocketEntityData"]
            assert data["snapshot"] == final and data["assembly_transaction_id"] == logical
            current = data["flight_data"]
            assert current["state"] == "LANDED" and current["logical_rocket_id"] == logical
            assert current["current_dimension"] == dimension and current["current_origin"] == final["source_origin"]
            assert current["passengers"] == {"seat_capacity": 1, "assignments": []}
            fuel(current["fuel"], 1000 - len(transfers) * 372, transfers)
            previous_data = data
        observations.append({"phase": phase, "exit": result["exit_code"], "ticks": result["observation_ticks"],
                             "logs": log_reports, "loaded_sources": sources, "watched_chunks": sorted(chunks),
                             "rocket_count": len(rockets), "cargo_be_count": len(cargo),
                             "transfer_count": len(journal["transfers"]), "assembly_journal_count": len(assembly["transactions"])})
    assert len(refuel_lines) == 1 and "amount=1000 capacity=1000" in refuel_lines[0]
    assert len(launch_lines) == 2
    assert "required_fuel=372 fuel_before=1000" in launch_lines[0]
    assert "required_fuel=372 fuel_before=628" in launch_lines[1]
    try:
        connection = socket.create_connection(("127.0.0.1", summary["port"]), timeout=1)
    except OSError:
        pass
    else:
        connection.close()
        raise AssertionError("Owned server endpoint remains open")
    with (Path('C:\\Users\\Administrator\\AppData\\Local\\Temp\\arce-v130-rocket04-independent-fe6b134aa32c48a986f89d9c433e65a2') / "native-observations.json").open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps({"phases": observations, "refuels": refuel_lines, "launches": launch_lines,
                                "committed_transfers": transfers, "final_fuel_before_disassembly": 256,
                                "port_closed": summary["port"],
                                "scope": "One round trip, four clean processes, bounded touched chunks; no passengers or soak"}, indent=2, sort_keys=True) + "\n")
    print("PASS: four raw-state phases, exact external payload/native BE, one fill/two debits, source provenance, full log audit and closed endpoint")


if __name__ == "__main__":
    main()
