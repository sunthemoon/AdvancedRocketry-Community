#!/usr/bin/env python3
"""Force-stop O2/O3/O4 settlement acknowledgements and O-veto; restart each world twice."""
from __future__ import annotations

import argparse
import gzip
import json
from pathlib import Path
import re
import shutil
import struct
import uuid
import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_escrow_forced_stop import digest, ledger, record, LEDGER
from run_v180_rocket_settlement_intent import ACTOR
from inspect_celestial_saved_data import NbtReader

CHOICES = ("ACCEPT", "ABANDON", "CHAIN_ACCEPT", "CHAIN_ABANDON", "FLOOR", "NORMALIZE")
CASES = tuple("SR_" + choice + "__" + boundary for choice in CHOICES for boundary in (
    *(f"O2_{side}_{index}" for index in range(3 if choice.startswith("CHAIN") else 2) for side in ("BEFORE", "AFTER")),
    "O3_BEFORE", "O3_AFTER", *(("O3J_BEFORE", "O3J_AFTER") if choice == "FLOOR" else ("O3J_AFTER",)),
    "O4_BEFORE", "O4_AFTER", "VETO", *(("VETO_CHECKPOINTED",) if choice != "FLOOR" else ()),
    *(("O_BIND_BEFORE", "O_BIND_AFTER") if choice == "NORMALIZE" else ()) ))
JOURNAL = "advancedrocketrycommunity_rocket_transfers.dat"


def require_lock(owner: str | None) -> str | None:
    if owner is None:
        return None
    current = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt").read_text(encoding="utf-8-sig").strip()
    if current != owner:
        raise smoke.SmokeError("Heavy-JVM lock ownership changed; refusing continuation")
    return current


def slot_present(path: Path, slot: int) -> bool:
    if not path.is_file():
        return False
    with path.open("rb") as stream:
        stream.seek(slot * 4); location = stream.read(4)
    return len(location) == 4 and struct.unpack(">I", location)[0] != 0


def journal(world: Path) -> dict:
    with gzip.open(world / "data" / JOURNAL, "rb") as stream:
        payload = stream.read(16 * 1024 * 1024 + 1)
    if len(payload) > 16 * 1024 * 1024:
        raise smoke.SmokeError("Checked settlement journal exceeds its bound")
    data = NbtReader(payload).read_root()["data"]
    # W44's integrated root 3 is independent of the governed record schema.
    if (data.get("schema_version") != 3 or not isinstance(data.get("transfers"), list)
            or data.get("classic_first_moon_landing") != {"schema_version": 1, "state": "EMPTY"}):
        raise smoke.SmokeError("Wrong checked settlement journal root or nonempty landing outbox")
    return data


def inspect_hold(lines: list[str], cut: str, restart: int) -> str:
    """Require the actual published recovery diagnostic, not just a generic HELD result."""
    choice, boundary = cut[3:].split("__")
    checkpointed = boundary == "VETO_CHECKPOINTED"
    expected = "Frozen settlement endpoint changed" if restart and choice != "FLOOR" and not checkpointed else "BARRIER_VETO"
    pattern = re.compile(r"ARCE_RELEASE_SETTLEMENT_VETO cut=" + re.escape(cut)
                         + r" reason=(.*?) records=(\d+) checkpointed=(true|false) recovery=(true|false)")
    observed = [match.groups() for line in lines if (match := pattern.search(line))]
    wanted = (expected, str(2 if choice.startswith("CHAIN") else 1), str(checkpointed).lower(), str(bool(restart)).lower())
    if observed != [wanted] or not any("ARCE_ESCROW_SETTLEMENT_HELD" in line
                                    and f"reason={expected} records=retained" in line for line in lines):
        raise smoke.SmokeError(f"Wrong or missing actual settlement hold reason: expected {wanted}, observed {observed}")
    return expected


def inspect(world: Path, cut: str, *, restarted: bool = False) -> dict:
    choice, boundary = cut[3:].split("__")
    count = 2 if choice.startswith("CHAIN") else 1
    floor, veto = choice == "FLOOR", boundary in ("VETO", "VETO_CHECKPOINTED")
    stale_source = boundary == "VETO"
    released = restarted and not veto or boundary == "O4_AFTER"
    installed = released or boundary in ("O3_AFTER", "O3J_BEFORE", "O3J_AFTER", "O4_BEFORE")
    root = ledger(world / "data" / LEDGER)
    if (len(root["records"]) != (0 if released else count)
            or len(root["floors"]) != int(floor and not released)
            or len(root["tombstones"]) != (count if installed else 0)):
        raise smoke.SmokeError("Wrong durable settlement boundary, record/floor loss or premature retirement")
    if not released:
        intents = [op.get("settlement") for op in root["records"]]
        if not intents[0] or any(intent != intents[0] for intent in intents):
            raise smoke.SmokeError("Frozen connected decision was lost or split")
        if (intents[0]["actor"] != ACTOR or intents[0]["actor_name"] != "Server"
                or not re.fullmatch(r"[0-9a-f]{64}", intents[0]["digest"])):
            raise smoke.SmokeError("Frozen actor/digest changed")
    for receipt in root["tombstones"]:
        if (receipt.get("schema_version") != 3 or receipt.get("released") != int(released)
                or receipt.get("actor") != ACTOR or receipt.get("actor_name") != "Server"
                or receipt.get("accounting_status") != "UNKNOWN" or "audited_loss" in receipt or "loss_known" in receipt
                or (receipt.get("release_generation", 0) > 0) != released
                or not re.fullmatch(r"[0-9a-f]{64}", receipt.get("decision_digest", ""))):
            raise smoke.SmokeError("Wrong returning-identity receipt or acknowledgement state")
    transfers = journal(world)["transfers"]
    absent_journal = released or boundary in ("O3J_AFTER", "O4_BEFORE")
    if len(transfers) != int(floor and not absent_journal):
        raise smoke.SmokeError("Matching checked journal was removed before its boundary or retained after release")
    entity_file = world / "entities/r.0.0.mca"
    entity_slot_present = slot_present(entity_file, 132)
    if not entity_slot_present and not floor:
        raise smoke.SmokeError("Chosen rocket store is absent")
    entities = record(entity_file)["Entities"] if entity_slot_present else []
    rockets = [entry for entry in entities if "RocketEntityData" in entry]
    if len(rockets) != int(not floor):
        raise smoke.SmokeError("Lost or duplicated chosen physical rocket")
    if floor and released:
        for receipt in root["tombstones"]:
            for endpoint in receipt["missing"]:
                slot = (endpoint["x"] & 31) + (endpoint["z"] & 31) * 32
                if not slot_present(entity_file, slot):
                    raise smoke.SmokeError("Released floor lacks its own durable absence slot")
                for entity in record(entity_file, slot)["Entities"]:
                    if (entity.get("UUID") == endpoint["identity"] or entity.get("RocketEntityData", {}).get("assembly_transaction_id") == endpoint["logical_rocket"]):
                        raise smoke.SmokeError("Released floor still contains an unproved returning identity")
    wrapper = None
    if rockets:
        rocket = rockets[0]; wrapper = rocket["RocketEntityData"]
        section = wrapper["resource_escrow"]
        expected_fuel = (2_000 if restarted and released else 0) if choice == "NORMALIZE" else count * 1000
        expected_epoch = (2 if restarted and released else 0) if choice == "NORMALIZE" else count
        binding_matches = section.get("ledger_id") == root["ledger_id"] or (choice == "NORMALIZE" and not (restarted and released)
                and not section.get("ledger_id") and section["resource_epoch"] == 0)
        if (wrapper["schema_version"] != 3 or wrapper["flight_data"]["fuel"]["amount"] != expected_fuel
                or section["resource_epoch"] != expected_epoch or not binding_matches
                or len(section["markers"]) != (0 if choice == "NORMALIZE" or restarted and released else count)
                or rocket["Pos"] != [64.5, 100., 64.5]):
            raise smoke.SmokeError("Checkpoint/release changed resources, epoch, markers or placement")
        references = root["records"][0]["settlement"]["observations"] if root["records"] else [
            survivor for receipt in root["tombstones"] for survivor in receipt["survivors"]]
        identities = [item["endpoint"]["identity"] for item in references if item["endpoint"]["kind"] == "ENTITY"]
        if not identities or any(identity != rocket["UUID"] for identity in identities):
            raise smoke.SmokeError("Chosen physical identity changed")
    sources = [entry for entry in record(world / "region/r.0.0.mca")["block_entities"]
               if entry.get("ForgeData", {}).get("arce_escrow_fixture")]
    expected_sources = 0 if floor else count - int(choice.endswith("ABANDON") and not stale_source)
    amount = 5000 if stale_source else 4000
    if len(sources) != expected_sources or any(entry["ForgeData"]["arce_escrow_fixture"]["amount"] != amount for entry in sources):
        observed = [(entry.get("x"), entry.get("y"), entry.get("z"), entry["ForgeData"]["arce_escrow_fixture"]["amount"]) for entry in sources]
        raise smoke.SmokeError(f"Source checkpoint mutated quantities or lost an unproved returning source: expected {expected_sources} x {amount}, observed {observed}")
    return dict(ledger=root, journal=transfers, wrapper=wrapper, source_count=len(sources),
                raw_total=sum(entry["ForgeData"]["arce_escrow_fixture"]["amount"] for entry in sources)
                + (wrapper["flight_data"]["fuel"]["amount"] if wrapper is not None else 0),
                entity_slot_present=bool(entity_slot_present),
                files={name: digest(world / name) if (world / name).is_file() else "ABSENT" for name in
                       ("data/" + LEDGER, "data/" + JOURNAL, "region/r.0.0.mca", "entities/r.0.0.mca")})


def run(args) -> dict:
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    if session.exists() or session.is_symlink() or evidence == session or session in evidence.parents:
        raise smoke.SmokeError("Unsafe or occupied disposable session")
    libraries = args.source_server.resolve() / "libraries"
    if (not libraries.is_dir() or any(path.is_symlink() for path in libraries.rglob("*"))
            or not (libraries / "net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt").is_file()):
        raise smoke.SmokeError("Unsafe or missing pinned Forge libraries")
    session.mkdir(); evidence.mkdir(parents=True, exist_ok=True)
    token = uuid.uuid4().hex; marker = session / ".arce-settlement-completion-owned"; marker.write_text(token, encoding="ascii")
    report = dict(status="IN_PROGRESS", source_commit=args.source_commit, jar_sha256=digest(args.jar), cases=[])
    process = None
    try:
        shutil.copytree(libraries, session / "libraries"); (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        for cut in args.case or CASES:
            properties = smoke.expected_server_properties(port, True)
            properties.update({"level-name": "world-" + cut, "level-type": "minecraft:flat", "max-tick-time": "60000",
                               "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{key}={value}\n" for key, value in sorted(properties.items())), encoding="ascii")
            row = dict(cut=cut, restarts=[]); report["in_progress"] = row
            for restart in range(3):
                lock_owner = require_lock(args.lock_owner)
                free = {str(drive): shutil.disk_usage(drive).free for drive in (Path("C:/"), Path("D:/"))}
                if min(free.values()) < 10 * 1024**3:
                    raise smoke.SmokeError("Native start refused below 10 GiB free")
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command.insert(1, "-Dadvancedrocketrycommunity.escrowScenario=" + ("recover" if restart else "start"))
                command.insert(1, "-Dadvancedrocketrycommunity.escrowCase=" + cut)
                process = smoke.CapturedProcess(command, session, evidence / f"{cut}-restart-{restart}.log")
                row.setdefault("starts", []).append(dict(command=command, free_bytes=free, restart=restart, lock_owner=lock_owner))
                if restart == 0:
                    process.wait_for(re.compile(r"ARCE_RELEASE_SETTLEMENT_COMPLETION_PAUSED cut=" + cut), args.startup_timeout)
                    process.process.kill(); process.abort()
                    if "__VETO" in cut: row["cut_hold_reason"] = inspect_hold(process.lines, cut, restart)
                    process = None
                    require_lock(args.lock_owner)
                    row["cut_disk"] = inspect(session / ("world-" + cut), cut)
                else:
                    state = "HELD" if "__VETO" in cut else "RELEASED"
                    process.wait_for(re.compile(r"ARCE_RELEASE_SETTLEMENT_COMPLETION_RESULT cut=" + cut + " state=" + state), args.startup_timeout)
                    if state == "HELD":
                        process.process.kill(); process.abort(); code = process.process.returncode
                    else:
                        process.command("stop"); code = process.finish()
                        if code != 0 or any("/ERROR]" in line or "/FATAL]" in line for line in process.lines):
                            raise smoke.SmokeError(f"Native restart failed or logged ERROR: {cut}/{restart}")
                    reason = inspect_hold(process.lines, cut, restart) if state == "HELD" else None
                    process = None; require_lock(args.lock_owner); disk = inspect(session / ("world-" + cut), cut, restarted=True)
                    if state == "HELD" and disk["ledger"]["records"] != row["cut_disk"]["ledger"]["records"]:
                        raise smoke.SmokeError("Restart altered the retained frozen settlement")
                    row["restarts"].append(dict(restart=restart, exit_code=code, hold_reason=reason, disk=disk))
            row["status"] = "PASS"; report["cases"].append(row); report.pop("in_progress", None)
            (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            print(f"{cut}: PASS (forced stop, two restarts)", flush=True)
        report["status"] = "PASS"; return report
    except Exception as failure:
        report.update(status="FAILED", error=str(failure)); raise
    finally:
        if process is not None: process.abort()
        (evidence / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if not args.retain_session and (session.exists() and not session.is_symlink() and marker.is_file() and not marker.is_symlink() and marker.read_text(encoding="ascii") == token):
            shutil.rmtree(session)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("source_server", type=Path)
    for key in ("jar", "session-dir", "evidence-dir"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--source-commit", required=True); parser.add_argument("--java", required=True)
    parser.add_argument("--lock-owner", help="Require this machine-wide lock owner before each JVM start")
    parser.add_argument("--case", action="append", choices=CASES); parser.add_argument("--startup-timeout", type=float, default=240)
    parser.add_argument("--retain-session", action="store_true", help="Leave this owned disposable copy for validated external cleanup")
    args = parser.parse_args()
    try: run(args); return 0
    except (OSError, smoke.SmokeError) as failure: print(f"FAIL: {failure}"); return 1


if __name__ == "__main__":
    raise SystemExit(main())
