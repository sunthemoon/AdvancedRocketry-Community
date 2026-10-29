"""W208 native observations. Ceilings request graceful cleanup, never terminate a live writer."""
from __future__ import annotations
import argparse
import ctypes
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import time
import uuid
import zipfile

import run_dedicated_server_smoke as smoke
from inspect_celestial_saved_data import NbtReader
from run_v060_flight_server_smoke import _server_command
from run_v180_landed_disassembly_cuts import chunk_record
from run_v180_rocket_settlement_completion import require_lock
from run_w152_native import block_state_at
from run_w197_i6_cost import Memory, sha256, require
from w152_native_inputs import preserve_native
from w208_availability import CASES, BOUND, conservation, check_events, near_qualified, may_reopen

OBSERVATION_SECONDS = 240
CLEANUP_SECONDS = 180
TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve()


def write(path, value):
    path.write_text(json.dumps(value, indent=2), encoding="utf-8")


class JournalReader(NbtReader):
    """Fixed 256-entry/64 MiB journal; retain each entry hash, decode one bounded snapshot at a time."""
    def _payload(self, kind, depth):
        if kind == 9 and depth == 2:
            child, count = self._u8(), self._length("transactions")
            require(child in (0, 10) and count <= 256 and (child == 10 or count == 0), "Invalid journal list")
            entries = []
            for _ in range(count):
                self.tags = 0
                start = self.offset
                entry = super()._payload(10, depth + 1)
                raw = self.payload[start:self.offset]
                if entry.get("dimension") == "advancedrocketrycommunity:w208_absent":
                    require(entry.get("type") == "ASSEMBLY", "Unexpected ballast type")
                    entry = {key: entry[key] for key in ("transaction_id", "snapshot_id", "content_hash", "dimension", "type", "phase", "progress")}
                    entry["entry_sha256"] = hashlib.sha256(raw).hexdigest()
                else:
                    require(entry.get("type") == "SEALED_DISASSEMBLY", "Unexpected real journal entry")
                    self.real_bytes = b"\x0a\x00\x00" + raw
                entries.append(entry)
            return entries
        return super()._payload(kind, depth)


def inspect_native(world, evidence, stage):
    journal = world / "data/advancedrocketrycommunity_rocket_transactions.dat"
    with gzip.open(journal, "rb") as stream:
        raw = stream.read(BOUND + 1025)
    require(len(raw) <= BOUND + 1024, "Journal exceeds checked bound plus file envelope")
    reader = JournalReader(raw, max_tags=1_048_576)
    entries = reader.read_root()["data"]["transactions"]
    ballast = [entry for entry in entries if entry["type"] == "ASSEMBLY"]
    real = [entry for entry in entries if entry["type"] == "SEALED_DISASSEMBLY"]
    require(len(real) <= 1 and len({tuple(e["transaction_id"]) for e in ballast}) == len(ballast), "Duplicate/foreign journal authority")
    if real:
        (evidence / (stage + "-u.nbt")).write_bytes(reader.real_bytes)
    entities, sealed, native, blocks, files = [], [], [], {}, {}
    for x in range(-1, 2):
        for z in range(-1, 2):
            region = world / "region" / f"r.{x >> 5}.{z >> 5}.mca"
            entity_file = world / "entities" / f"r.{x >> 5}.{z >> 5}.mca"
            chunk = chunk_record(region, x, z) or {}
            for entity in (chunk_record(entity_file, x, z) or {}).get("Entities", []):
                if "RocketEntityData" in entity:
                    entities.append(entity)
                elif entity.get("id") == "minecraft:item":
                    raise ValueError("Unexpected dropped item in sealed fixture")
            if x == 0 and z == 0:
                for at in ((8, 100, 8), (9, 100, 8)):
                    state = block_state_at(chunk, *at)
                    blocks[str(at)] = state
                    be = next((row for row in chunk.get("block_entities", []) if tuple(row.get(k) for k in ("x", "y", "z")) == at), None)
                    if state["Name"] == "advancedrocketrycommunity:sealed_rocket_block":
                        require(be and "arce_sealed_rocket_member" in be, "Missing carrier identity")
                        sealed.append(be)
                    elif state["Name"] != "minecraft:air":
                        native.append(dict(state=state, block_entity=be))
            for file in (region, entity_file):
                if file.exists():
                    files[file.relative_to(world).as_posix()] = sha256(file)
    phase = real[0]["phase"] if real else None
    observed = conservation(phase, entities, sealed, native, real[0]["snapshot"] if real else None)
    observed.update(phase=phase, records=real, ballast=ballast, entities=entities, sealed=sealed, native=native,
                    blocks=blocks, journal_sha256=sha256(journal), journal_bytes=len(raw), files=files)
    # Ballast is reproducible committed fixture data: retain hashes, not 24 redundant 64 MiB root copies.
    observed["native_inputs"] = preserve_native(world, evidence, stage, dict(files=files))
    write(evidence / (stage + "-native.json"), observed)
    return observed


def jar_inventory(production, diagnostic):
    with zipfile.ZipFile(production) as a, zipfile.ZipFile(diagnostic) as b:
        left = {n: hashlib.sha256(a.read(n)).hexdigest() for n in a.namelist()}
        right = {n: hashlib.sha256(b.read(n)).hexdigest() for n in b.namelist()}
    added = sorted(right.keys() - left.keys())
    require(added and all("/W208" in n for n in added), "Unexpected diagnostic additions")
    require(not any("/W208" in n for n in left), "Production JAR contains diagnostics")
    require(not left.keys() - right.keys() and all(left[n] == right[n] for n in left), "Production entry changed")
    return dict(production_sha256=sha256(production), diagnostic_sha256=sha256(diagnostic), added=added, changed=[])


def run_stage(args, session, output, stage):
    require_lock(args.lock_owner)
    for drive in ("C:/", "D:/"):
        require(shutil.disk_usage(drive).free >= 10 * 1024**3, "Disk below 10 GiB")
    command = _server_command(args.java); command[2] = "-Xmx4G"
    command[1:1] = ["-Darce.w208.enabled=true", "-Darce.w208.case=" + args.case,
                    "-Darce.w208.stage=" + stage, "-Darce.w208.commit=" + args.source_commit,
                    "-Xlog:gc*:file=gc-" + stage + ".log:time,uptime,level,tags"]
    row = dict(stage=stage, command=command, started=time.time(), status="OBSERVING")
    write(output / (stage + "-launch.json"), row)
    memory_api = ctypes.WinDLL("psapi").GetProcessMemoryInfo
    memory_api.argtypes = (ctypes.c_void_p, ctypes.POINTER(Memory), ctypes.c_ulong)
    memory_api.restype = ctypes.c_int
    process = smoke.CapturedProcess(command, session, output / (stage + ".log"))
    row["pid"] = process.process.pid
    write(output / (stage + "-launch.json"), row)
    samples = []; failure = None
    try:
        deadline = time.monotonic() + OBSERVATION_SECONDS
        while process.process.poll() is None and time.monotonic() < deadline:
            memory = Memory(); memory.cb = ctypes.sizeof(memory)
            if memory_api(int(process.process._handle), ctypes.byref(memory), memory.cb):
                samples.append(dict(time=time.time(), rss=memory.rss, peak_rss=memory.peak))
            if any("W208_DONE status=" in line for line in process.lines):
                break
            time.sleep(.5)
        row["status"] = "OBSERVED" if any("W208_DONE status=" in line for line in process.lines) else "NOT_COMPLETED"
    except Exception as error:
        failure = str(error)
    finally:
        stopped = time.monotonic()
        if process.process.poll() is None:
            try: process.command("stop")
            except (OSError, smoke.SmokeError) as error: failure = str(error)
        deadline = time.monotonic() + CLEANUP_SECONDS
        while process.process.poll() is None and time.monotonic() < deadline:
            time.sleep(.5)
        row.update(exit_code=process.process.poll(), stop_to_exit_seconds=time.monotonic() - stopped,
                   rss_samples=samples, live_process=not may_reopen(process.process.poll()))
        if not row["live_process"]:
            process.finish(5)  # Already exited; this helper's timeout/termination path is unreachable.
        else:
            row["status"] = "BLOCKED_LIVE_PROCESS"
        if failure: row["harness_error"] = failure
        write(output / (stage + "-process.json"), row)
    if row["live_process"]:
        return row
    for source, name in ((session / "world/w208.json", stage + ".json"),
                         (session / ("gc-" + stage + ".log"), stage + "-gc.log")):
        if source.exists(): shutil.copy2(source, output / name)
    require(row["exit_code"] == 0, "Native server exited nonzero")
    require(not failure, "Harness observation failed: " + str(failure))
    observation = json.loads((output / (stage + ".json")).read_text())
    require(observation["status"] != "FAILED" and "safety_failure" not in observation, "Fixture or safety failure")
    check_events(observation["events"])
    row["observation_status"] = observation["status"]
    row["native"] = inspect_native(session / "world", output, stage)
    errors = [line.strip() for line in process.lines if "/ERROR]" in line or "/FATAL]" in line]
    expected_unknown = stage == "start" and args.case.endswith("unknown")
    require(len(errors) == (1 if expected_unknown else 0) and all("ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=UNKNOWN" in e for e in errors),
            "Unexpected/missing ERROR/FATAL: " + str(errors))
    row["errors"] = errors
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ("jar", "production-jar", "libraries", "evidence"):
        parser.add_argument("--" + key, type=Path, required=True)
    parser.add_argument("--case", choices=CASES, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args(); require_lock(args.lock_owner)
    output = args.evidence / args.case; output.mkdir()
    result = dict(case=args.case, source=args.source_commit, jars=jar_inventory(args.production_jar, args.jar), stages=[], status="RUNNING")
    session = TEMP / ("W208-" + uuid.uuid4().hex); session.mkdir()
    marker = uuid.uuid4().hex; (session / ".w208-owned").write_text(marker)
    result["session"] = str(session)
    try:
        require(args.libraries.is_dir() and not smoke.is_link_or_junction(args.libraries)
                and not any(smoke.is_link_or_junction(p) for p in args.libraries.rglob("*")), "Unsafe library template")
        shutil.copytree(args.libraries, session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        props = smoke.expected_server_properties(smoke.allocate_port(), True)
        props.update({"level-type": "minecraft:flat", "max-tick-time": "60000", "view-distance": "3", "simulation-distance": "3",
                      "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
        (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())))
        (session / "eula.txt").write_text("eula=true\n")
        for stage in ("start", "recover1", "recover2"):
            row = run_stage(args, session, output, stage)
            result["stages"].append({k: v for k, v in row.items() if k not in ("native", "rss_samples")})
            if row["live_process"]:
                result["live_process"] = True
                raise RuntimeError("Live process retains world and heavy lock")
            if stage == "start":
                baseline = row["native"]["ballast"]
                observation = json.loads((output / (stage + ".json")).read_text())
                if args.case != "small-quiet":
                    require(near_qualified(observation.get("near_root_bytes", 0), observation.get("ballast", 0)), "Near fixture not qualified")
            else:
                require(row["native"]["ballast"] == baseline, "Ballast changed across restart")
        result["status"] = "MEASURED"
    except Exception as error:
        result.update(status="BLOCKED" if result.get("live_process") else "FAILED", error=str(error))
    finally:
        # Do not delete or reopen a world whose JVM is still alive, even after a harness error.
        processes = list(output.glob("*-process.json"))
        live = any(json.loads(path.read_text()).get("live_process") for path in processes)
        result["live_process"] = live
        if not live:
            require(session.resolve().parent == TEMP and not smoke.is_link_or_junction(session)
                    and (session / ".w208-owned").read_text() == marker, "Cleanup ownership changed")
            shutil.rmtree(session)
        write(output / "result.json", result)
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "MEASURED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
