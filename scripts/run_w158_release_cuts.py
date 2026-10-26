#!/usr/bin/env python3
"""W158 native release cuts, optionally killing the first recovery at an independent boundary."""
from __future__ import annotations

import argparse
import copy
import ctypes
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid

import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_resource_restarts import read_nbt, entity_slots
from run_v180_escrow_forced_stop import record
from run_w157_rejoin_startup import HEADER, PROJECT_LOGGERS

MODES = ("seed", "recover", "check")
RECOVERY_CUTS = ("B1_CAPTURE", "S2_PENDING", "C6")
CASES = [("H1", f"C{i}") for i in (*range(7), 14, 15, 16, 17, 24)] + [("H2", f"C{i}") for i in (2, 3, 5, 6)]
JOURNAL = "advancedrocketrycommunity_rocket_transfers.dat"
SIDECAR = "advancedrocketrycommunity_rocket_rejoins.dat"
UUID_PATTERN = r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"
HELD = re.compile(r"ARCE_REJOIN_RELEASE_HELD release=" + UUID_PATTERN + " transfer=" + UUID_PATTERN
                  + " logical=" + UUID_PATTERN + " state=(GONE|CONFLICT)")


def audit_log(lines, hold, cut, mode, *, expected_outbox_holds=None):
    # Per-complete-process exact manifest. Every other header and continuation is fed to the unchanged scanner.
    manifest = json.loads(Path(__file__).with_name("w158_release_expected_log.json").read_text(encoding="utf-8"))
    row = manifest["expected_warn_counts"].get(f"{hold}-{cut}-{mode}", {})
    expected = {state: row.get(state, 0) for state in ("GONE", "CONFLICT")}
    expected["source_hold"] = int(hold == "H1" and (mode == "seed" or mode == "recover" and cut in ("C0", "C1")))
    expected["retirement_gone"] = int(hold == "H1" and cut == "C14" and mode == "seed")
    # W44's pre-existing baseline warning is timing-dependent and already has an exact
    # 0..1 allowance in the unchanged native startup manifest. A controlled fixture may
    # additionally require its count; ordinary native starts retain that original gate.
    if expected_outbox_holds is not None:
        if expected_outbox_holds not in (0, 1):
            raise smoke.SmokeError("Outbox expectation exceeds unchanged startup bound")
        expected["outbox_hold"] = expected_outbox_holds
    source = re.compile(re.escape(manifest["existing_source_hold_message"]).replace(re.escape("{uuid}"), UUID_PATTERN))
    retired = re.compile(re.escape(manifest["existing_retirement_gone_message"]).replace(re.escape("{uuid}"), UUID_PATTERN))
    counts = dict.fromkeys(expected, 0)
    remainder = []
    for line in lines:
        header = HEADER.fullmatch(line.rstrip())
        matched = HELD.fullmatch(header["message"]) if header else None
        if (header and header["logger"] in PROJECT_LOGGERS and header["thread"] == "Server thread" and header["level"] == "WARN"
                and (matched or source.fullmatch(header["message"]) or retired.fullmatch(header["message"])
                     or expected_outbox_holds is not None and header["message"] == manifest["existing_outbox_hold_message"])):
            key = matched[1] if matched else "source_hold" if source.fullmatch(header["message"]) else "retirement_gone" if retired.fullmatch(header["message"]) else "outbox_hold"
            counts[key] += 1
        else:
            remainder.append(line)
    errors = smoke.scan_log(remainder, expected_mod_version=smoke.V180_STARTUP_VERSION)
    if errors or counts != expected:
        raise smoke.SmokeError(f"W158 log manifest mismatch: expected={expected}, observed={counts}, findings={errors}")
    return dict(expected=expected, observed=counts, raw=smoke.log_audit_counts(lines))


def resources():
    disk = {drive: shutil.disk_usage(drive).free for drive in ("C:/", "D:/")}
    class Memory(ctypes.Structure):
        _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong)] + [
            (key, ctypes.c_ulonglong) for key in ("total", "available", "page_total", "page_available", "virtual", "virtual_available", "extended")]
    memory = Memory(); memory.length = ctypes.sizeof(memory)
    if not ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(memory)):
        raise smoke.SmokeError("Cannot query free physical memory")
    if min(disk.values()) < 10 * 1024**3 or memory.available < 6 * 1024**3:
        raise smoke.SmokeError("Native run requires 10 GiB on C/D and 6 GiB free RAM")
    return dict(free_disk=disk, free_memory=memory.available)


def capture(world, target):
    target.mkdir()
    rockets, files = [], {}
    for path in sorted(world.rglob("entities/*.mca")):
        files[path.relative_to(world).as_posix()] = smoke.digest_file(path)
        for slot in entity_slots(path):
            for entity in record(path, slot).get("Entities", []):
                if "RocketEntityData" in entity:
                    rockets.append(dict(file=path.relative_to(world).as_posix(), slot=slot, entity=entity))
    saved = {}
    for name in (JOURNAL, SIDECAR):
        path = world / "data" / name
        if path.exists():
            saved[name] = read_nbt(path)["data"]
            shutil.copy2(path, target / name)
            files["data/" + name] = smoke.digest_file(path)
        else:
            saved[name] = None
    result = dict(rockets=rockets, journal=saved[JOURNAL], sidecar=saved[SIDECAR], native_file_sha256=files)
    (target / "inspection.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result


def seats(flight):
    return flight["passengers"]["assignments"]


def verify(disk, hold, cut, mode, baseline):
    rockets, journal, sidecar = disk["rockets"], disk["journal"], disk["sidecar"]
    gone = cut == "C14"
    if len(rockets) != (0 if gone else 1):
        raise smoke.SmokeError(f"Native authority count: {len(rockets)}")
    if gone:
        witness = dict(gone_resources=sidecar["batches"][0]["resources"], fixture_destroyed=1000)
        if baseline is not None and witness != baseline:
            raise smoke.SmokeError("Gone restart changed the frozen resource witness")
        baseline = witness
    if rockets:
        data = rockets[0]["entity"]["RocketEntityData"]
        flight = data["flight_data"]
        witness = dict(snapshot=data["snapshot"], fuel=flight["fuel"], uuid=rockets[0]["entity"]["UUID"])
        if flight["fuel"]["amount"] != (1000 if hold == "H1" else 500):
            raise smoke.SmokeError("Native fuel amount changed")
        if baseline is not None and witness != baseline:
            raise smoke.SmokeError("Snapshot, full fuel/debit vector or physical identity changed across restart")
        baseline = witness
        if mode != "seed" and cut != "C16" and seats(flight):
            raise smoke.SmokeError("Released seats returned after restart")
        if mode == "seed" and cut in ("C4", "C5", "C6", "C15", "C17") and seats(flight):
            raise smoke.SmokeError("B1 did not remove all fixture seats on disk")
    if mode == "seed" and cut in ("C0", "C1"):
        if sidecar is not None or len(journal["transfers"]) != 1:
            raise smoke.SmokeError("Pre-S1 cut published a release or lost T")
    else:
        if sidecar is None or sidecar["next_sequence"] != 1:
            raise smoke.SmokeError("Missing or reused release sequence")
        pending = cut == "C16" or mode == "seed" and cut in ("C2", "C3", "C4", "C5")
        if len(sidecar["entries"]) != (0 if cut == "C17" else 2):
            raise smoke.SmokeError("Sidecar entry count differs")
        if any(e["seat"] != ("PENDING" if pending else "VERIFIED") for e in sidecar["entries"]):
            raise smoke.SmokeError("Incorrect seat track at process boundary")
        if cut == "C17" and sidecar["batches"]:
            raise smoke.SmokeError("NOT_REQUIRED entries left an empty batch")
        if any(e["placement"] != "PENDING" or "attempt" in e for e in sidecar["entries"]):
            raise smoke.SmokeError("Release leaf changed placement")
    done = cut != "C16" and (mode != "seed" or cut in ("C5", "C6", "C14", "C15", "C17"))
    expected_records = 0 if done and hold == "H1" else 1
    if len(journal["transfers"]) != expected_records:
        raise smoke.SmokeError("Wrong post-J1 journal count")
    if journal["transfers"]:
        transfer = journal["transfers"][0]
        expected_seats = 1 if done and hold == "H2" else 2
        if len(seats(transfer["source_flight"])) != expected_seats or seats(transfer["source_flight"]) != seats(transfer["destination_flight"]):
            raise smoke.SmokeError("J1 passenger-only projection differs")
    return baseline


def verify_diverged(disk, mode, baseline):
    """C24: H1 MT={old,absent}, ME={newcomer,absent}; E1 must not remove the newcomer."""
    def player(n): return (22544384, 16384, -2147483648, n)
    def roster(flight): return sorted((tuple(s["passenger_id"]), s["seat_index"]) for s in seats(flight))
    root = disk["sidecar"]
    if len(disk["rockets"]) != 1 or root is None or root["next_sequence"] != 1 or len(root["batches"]) != 1:
        raise smoke.SmokeError("Diverged release lost its unique authority or frozen batch")
    entity = disk["rockets"][0]["entity"]; data = entity["RocketEntityData"]; flight = data["flight_data"]
    expected = [(player(2), 1), (player(3), 0)] if mode == "seed" else [(player(3), 0)]
    if roster(flight) != expected or flight["fuel"]["amount"] != 1000:
        raise smoke.SmokeError("Diverged release changed a retained rider or resource")
    batch = root["batches"][0]
    if sorted(map(tuple, batch["released"])) != [player(1), player(2)] or sorted((s["level"], s["x"], s["z"]) for s in batch["slots"]) != [
            ("advancedrocketrycommunity:moon", 4, 4), ("minecraft:overworld", 4, 4)]:
        raise smoke.SmokeError("Diverged release changed A or its recorded slots")
    entries = sorted((tuple(e["player"]), e["seat_index"], e["seat"], e["placement"]) for e in root["entries"])
    wanted = [(player(1), 0, "PENDING", "NOT_REQUIRED"), (player(2), 1, "PENDING", "PENDING")] if mode == "seed" else [
        (player(2), 1, "VERIFIED", "PENDING")]
    if entries != wanted or any("attempt" in e for e in root["entries"]):
        raise smoke.SmokeError("Diverged release changed its frozen classification")
    records = disk["journal"]["transfers"]
    if len(records) != (1 if mode == "seed" else 0) or any(roster(t[key]) != [(player(1), 0), (player(2), 1)]
            for t in records for key in ("source_flight", "destination_flight")):
        raise smoke.SmokeError("Diverged J1 did not retire only the recorded riders")
    witness = dict(uuid=entity["UUID"], snapshot=data["snapshot"], fuel=flight["fuel"], batch=batch,
                   journal={k: v for k, v in disk["journal"].items() if k != "transfers"})
    if baseline is not None and baseline != witness:
        raise smoke.SmokeError("Diverged restart changed resources, batch or journal envelope")
    return witness


def verify_recovery(disk, hold, cut, boundary, baseline, seed):
    """Inspect the interrupted recovery, not the completed-recovery oracle used by clean cases."""
    if cut not in ("C2", "C3", "C4", "C5") or boundary not in RECOVERY_CUTS:
        raise smoke.SmokeError("Recovery interruption requires a pending C2-C5 seed")
    if boundary == "B1_CAPTURE":
        # Before this first B1 store, E1 has changed RAM only. The recorded disk roster
        # must still be exactly the seed roster, including its player/seat identities.
        seed_flight = seed["rockets"][0]["entity"]["RocketEntityData"]["flight_data"]
        flight = disk["rockets"][0]["entity"]["RocketEntityData"]["flight_data"] if len(disk["rockets"]) == 1 else None
        if flight is None or seats(flight) != seats(seed_flight):
            raise smoke.SmokeError("B1 capture changed the pre-store seat census")
        if disk["journal"] != seed["journal"] or disk["sidecar"] != seed["sidecar"]:
            raise smoke.SmokeError("B1 capture changed a persisted release or journal")
        return verify(disk, hold, cut, "seed", baseline)
    # S2_PENDING has installed J1 but has not replaced the pending-seat sidecar.
    # C6 has replaced S2 but has not published the writer's callback.
    return verify(disk, hold, "C5" if boundary == "S2_PENDING" else "C6", "seed", baseline)


def preserved_stores(disk, previous):
    """All non-passenger fields and the frozen census survive every cut/restart."""
    journal = {k: v for k, v in disk["journal"].items() if k != "transfers"}
    transfers = copy.deepcopy(disk["journal"]["transfers"])
    for transfer in transfers:
        transfer.pop("checksum")
        for key in ("source_flight", "destination_flight"):
            transfer[key].pop("passengers")
    batches = disk["sidecar"]["batches"] if disk["sidecar"] else []
    entries = [{k: v for k, v in e.items() if k != "seat"} for e in disk["sidecar"]["entries"]] if disk["sidecar"] else []
    value = dict(journal=journal, transfers=transfers, batches=batches, entries=entries)
    if previous is not None:
        for key in value:
            if previous[key] and value[key] and previous[key] != value[key]:
                raise smoke.SmokeError("Cut changed non-passenger/frozen store: " + key)
        if previous["journal"] != journal:
            raise smoke.SmokeError("Cut changed the journal envelope or full outbox")
        for key in ("batches", "entries"):
            if previous[key] and not value[key]:
                raise smoke.SmokeError("Cut dropped frozen release obligations: " + key)
    return value


def verify_seat_census(disk, hold, cut, mode, recovery_cut=""):
    """Check exact fixture identities and seat indices, not just the number of riders."""
    def passenger(last):
        return (22544384, 16384, -2147483648, last)
    def assignments(flight):
        return sorted((tuple(seat["passenger_id"]), seat["seat_index"]) for seat in seats(flight))
    original = sorted(((passenger(1), 0), (passenger(2), 1)))
    released = original if hold == "H1" else sorted(((passenger(3), 0), (passenger(2), 1)))
    interrupted = mode == "recover" and bool(recovery_cut)
    before_store = (mode == "seed" or interrupted and recovery_cut == "B1_CAPTURE") and cut in ("C0", "C1", "C2", "C3")
    expected_live = released if before_store else []
    for rocket in disk["rockets"]:
        if assignments(rocket["entity"]["RocketEntityData"]["flight_data"]) != expected_live:
            raise smoke.SmokeError("Native player/seat census differs at cut")
    done = cut != "C16" and (cut in ("C5", "C6", "C14", "C15", "C17")
                              or mode != "seed" and not (interrupted and recovery_cut == "B1_CAPTURE"))
    expected_t = [(passenger(1), 0)] if done and hold == "H2" else original
    for transfer in disk["journal"]["transfers"]:
        if any(assignments(transfer[key]) != expected_t for key in ("source_flight", "destination_flight")):
            raise smoke.SmokeError("Journal player/seat census differs at cut")
    sidecar = disk["sidecar"]
    if sidecar is not None and cut != "C17":
        if len(sidecar["batches"]) != 1:
            raise smoke.SmokeError("Missing frozen batch census")
        batch = sidecar["batches"][0]
        if sorted((tuple(e["player"]), e["seat_index"]) for e in sidecar["entries"]) != released:
            raise smoke.SmokeError("Sidecar player/seat census differs at cut")
        if sorted(map(tuple, batch["released"])) != sorted(player for player, _ in released):
            raise smoke.SmokeError("Frozen released set differs")
        if sorted((s["level"], s["x"], s["z"]) for s in batch["slots"]) != [
                ("advancedrocketrycommunity:moon", 4, 4), ("minecraft:overworld", 4, 4)]:
            raise smoke.SmokeError("Persisted native slot census differs")


def stop_boundary(cut, mode, recovery_cut):
    if mode == "seed":
        return "C6" if cut == "C17" else "C3" if cut == "C24" else cut
    return recovery_cut if mode == "recover" else ""


def run(args):
    repo = Path(__file__).resolve().parents[1]
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    if commit != args.source_commit or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True):
        raise smoke.SmokeError("Native run requires exact clean committed source")
    token = os.environ.get("W158_LOCK_TOKEN", "")
    lock = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt")
    if not token or not lock.read_text(encoding="utf-8-sig").startswith(token):
        raise smoke.SmokeError("Caller does not own heavy-JVM lock")
    evidence = args.evidence_dir.resolve(); evidence.mkdir(exist_ok=False)
    parent = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()
    session = parent / ("w158-native-" + uuid.uuid4().hex)
    recovery_cut = args.recovery_cut
    result = dict(status="IN_PROGRESS", source_commit=commit, jar_sha256=smoke.digest_file(args.jar),
                  recovery_cut=recovery_cut, starts=[])
    try:
        session.mkdir(); shutil.copytree(args.source_server / "libraries", session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        chosen = CASES if not args.cases else [tuple(case.split("-")) for case in args.cases.split(",")]
        if any(case not in CASES for case in chosen):
            raise smoke.SmokeError("Unknown cut case")
        if recovery_cut and any(cut not in ("C2", "C3", "C4", "C5") for _, cut in chosen):
            raise smoke.SmokeError("Only C2-C5 have pending release work to interrupt; C6 is already durable")
        for hold, cut in chosen:
            world = session / f"world-W158-{hold}-{cut}"
            props = smoke.expected_server_properties(port, True)
            props.update({"level-name": world.name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                          "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
            baseline = previous = seed = None
            for mode in MODES:
                name = f"{hold}-{cut}-{mode}"; row = dict(case=name, **resources()); result["starts"].append(row)
                command = _server_command(args.java); command[2] = "-Xmx4G"
                if getattr(args, "command", False):
                    command.insert(3, "-Dadvancedrocketrycommunity.rejoinReleaseCommand=true")
                command.insert(3, f"-Dadvancedrocketrycommunity.rejoinReleaseProbe={hold}:{cut}:{mode}")
                if recovery_cut:
                    command.insert(3, f"-Dadvancedrocketrycommunity.rejoinReleaseRecoveryCut={recovery_cut}")
                row["command"] = command
                process = smoke.CapturedProcess(command, session, evidence / (name + ".log"))
                try:
                    index = process.wait_for(re.compile(r"ARCE_W158_(?:PAUSED|RESULT|FAILURE) case="), 300)
                    marker = process.lines[index]
                    boundary = stop_boundary(cut, mode, recovery_cut)
                    if "ARCE_W158_FAILURE" in marker or ("ARCE_W158_PAUSED" in marker) != bool(boundary):
                        raise smoke.SmokeError("Native fixture failed or reached wrong boundary: " + marker)
                    if boundary:
                        if not marker.rstrip().endswith(f"ARCE_W158_PAUSED case={hold}:{cut}:{mode} boundary={boundary}"):
                            raise smoke.SmokeError("Native pause did not name the selected boundary: " + marker)
                        process.process.kill(); process.abort(); row["exit"] = process.process.returncode
                    else:
                        process.command("stop"); row["exit"] = process.finish()
                        if row["exit"] != 0:
                            raise smoke.SmokeError("Restart process exited abnormally")
                    disk = capture(world, evidence / (name + "-saved"))
                    row["log_audit"] = audit_log(process.lines, hold, cut, mode)
                    if cut == "C24":
                        baseline = verify_diverged(disk, mode, baseline)
                    else:
                        baseline = (verify_recovery(disk, hold, cut, recovery_cut, baseline, seed)
                                    if mode == "recover" and recovery_cut else verify(disk, hold, cut, mode, baseline))
                        previous = preserved_stores(disk, previous)
                        verify_seat_census(disk, hold, cut, mode, recovery_cut)
                    if mode == "seed":
                        seed = disk
                    row["external_kill"] = bool(boundary)
                    row["boundary"] = boundary
                    row["conserved"] = True
                    row["intentional_fixture_deletion"] = cut == "C14"
                except Exception:
                    if process.process.poll() is None:
                        process.abort()
                    failed_capture = evidence / (name + "-failed-saved")
                    if world.is_dir() and not failed_capture.exists():
                        capture(world, failed_capture)
                    raise
                finally:
                    if process.process.poll() is None:
                        process.abort()
            if world.resolve().parent != session or world.is_symlink():
                raise smoke.SmokeError("Unsafe owned world cleanup")
            shutil.rmtree(world)
        result["status"] = "PASS"
    except Exception as failure:
        result.update(status="FAIL", error=str(failure))
    finally:
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        if session.is_dir() and session.resolve().parent == parent and not smoke.is_link_or_junction(session):
            shutil.rmtree(session)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source-server", "jar", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    for name in ("java", "source-commit"):
        parser.add_argument("--" + name, required=True)
    parser.add_argument("--cases", default="")
    parser.add_argument("--command", action="store_true", help="Admit through the registered release-absent command")
    parser.add_argument("--recovery-cut", choices=RECOVERY_CUTS, default="",
                        help="Independently kill the first recovery; omit to retain the clean-restart campaign")
    result = run(parser.parse_args()); print(json.dumps(result, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
