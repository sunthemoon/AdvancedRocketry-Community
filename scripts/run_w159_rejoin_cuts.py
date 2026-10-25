#!/usr/bin/env python3
"""Disposable W159 player readback cuts, with two restarts and an interrupted first recovery."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid

import run_w158_release_cuts as release
import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_resource_restarts import read_nbt

PLAYER = "01580000-0000-4000-8000-000000000002"
CASES = [(hold, f"C{i}") for hold in ("H1", "H2") for i in (7, 8, 9, 10, 11, 13, 18, 19, 20)]


def uuid_text(value):
    if not isinstance(value, list) or len(value) != 4:
        raise smoke.SmokeError("Malformed UUID in installed data")
    return str(uuid.UUID(int=sum((word & 0xFFFFFFFF) << (96 - 32 * i) for i, word in enumerate(value))))


def capture(world, target):
    result = release.capture(world, target)
    file = world / "playerdata" / (PLAYER + ".dat")
    if not file.is_file() or file.is_symlink() or file.stat().st_size > 64 * 1024**2:
        raise smoke.SmokeError("Missing, nonregular or oversized installed player file")
    result["player"] = read_nbt(file)
    result["player_sha256"] = smoke.digest_file(file)
    shutil.copy2(file, target / file.name)
    # Inspect every released identity, including explicit absence rather than inventing an offline file.
    identities = {PLAYER}
    for batch in (result["sidecar"] or {}).get("batches", []):
        identities.update(uuid_text(value) for value in batch["released"])
    result["players"] = {}
    for identity in sorted(identities):
        installed = world / "playerdata" / (identity + ".dat")
        if not installed.exists():
            result["players"][identity] = {"present": False}
            continue
        if installed.is_symlink() or not installed.is_file() or installed.stat().st_size > 64 * 1024**2:
            raise smoke.SmokeError("Invalid released-player file")
        fields = read_nbt(installed)
        result["players"][identity] = dict(present=True, sha256=smoke.digest_file(installed),
            **{key: fields.get(key) for key in ("UUID", "Dimension", "Pos", "RootVehicle", "Health", "DeathTime")},
            marker=fields.get("ForgeData", {}).get("arce_passenger_rejoin"))
    (target / "inspection.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result


def pending(disk):
    return [entry for entry in disk["sidecar"]["entries"] if uuid_text(entry["player"]) == PLAYER and entry["placement"] == "PENDING"]


def audit_log(lines, hold, cut, mode):
    """Account only for the C9 fixture's exact vanilla failure; retain every other log entry."""
    manifest = json.loads(Path(__file__).with_name("w159_rejoin_expected_log.json").read_text(encoding="utf-8"))["native_c9"]
    expected = {key: count if cut == "C9" and mode == "seed" else 0 for key, count in manifest["counts"].items()}
    counts = dict.fromkeys(expected, 0); remainder = []
    path = r"\.[/\\]world-W158-" + hold + r"-C9[/\\]playerdata[/\\]" + PLAYER + r"\.dat_old"
    events = []
    for line in lines:
        header = release.HEADER.fullmatch(line.rstrip())
        if header or not events: events.append([header, [line]])
        else: events[-1][1].append(line)
    for header, event in events:
        key = None
        if header and header["thread"] == "Server thread" and header["logger"] in manifest["loggers"]:
            for name, pattern in manifest["messages"].items():
                match = re.fullmatch(pattern.replace("{path}", path), header["message"])
                if match and header["level"] == ("WARN" if name in ("delete", "sequence") else "ERROR"):
                    key = "retry_" + match[1] if name == "retry" else name
                    break
        if key is None: remainder.extend(event); continue
        continuation = [line.rstrip() for line in event[1:] if line.strip()]
        if key == "delete":
            if (not continuation or not re.fullmatch(r"java\.nio\.file\.DirectoryNotEmptyException: " + path, continuation[0])
                    or not all(re.fullmatch(r"\s+at [^\r\n]+\([^\r\n]*\)[^\r\n]*", line) for line in continuation[1:])
                    or not any("RocketRejoinPlacement.tick(" in line for line in continuation)
                    or not any("PlayerDataStorage." in line for line in continuation)):
                raise smoke.SmokeError("C9 delete entry has an unexpected exception or stack")
        elif continuation:
            raise smoke.SmokeError("C9 non-exception entry has unexpected continuation")
        counts[key] += 1
    if counts != expected:
        raise smoke.SmokeError(f"C9 log counts differ: expected={expected}, observed={counts}")
    return dict(c9_expected=expected, c9_observed=counts, base=release.audit_log(remainder, hold, cut, mode))


def verify(disk, hold, cut, mode, boundary, baseline, seed):
    player, sidecar, rockets = disk["player"], disk["sidecar"], disk["rockets"]
    dead_cut = cut == "C18" and mode == "seed"
    alive = player["Health"] > 0 and player["DeathTime"] == 0
    if uuid_text(player["UUID"]) != PLAYER or not (player["Health"] == 0 and player["DeathTime"] >= 0 if dead_cut else alive):
        raise smoke.SmokeError("Player identity/liveness changed")
    if sidecar is None or sidecar["next_sequence"] != 1 or len(rockets) != 1:
        raise smoke.SmokeError("Missing sidecar or unique native authority")
    rocket = rockets[0]["entity"]; data = rocket["RocketEntityData"]
    others = sorted((e for e in sidecar["entries"] if uuid_text(e["player"]) != PLAYER and e["placement"] == "PENDING"),
                    key=lambda e: (uuid_text(e["release"]), uuid_text(e["player"])))
    witness = dict(snapshot=data["snapshot"], fuel=data["flight_data"]["fuel"], uuid=rocket["UUID"],
                   other_pending=[{k: e[k] for k in ("release", "player", "placement", "attempt") if k in e} for e in others])
    if witness["fuel"]["amount"] != (1000 if hold == "H1" else 500) or baseline is not None and witness != baseline:
        raise smoke.SmokeError("Native snapshot/fuel/debit/identity changed across restart")
    entries = pending(disk); marker = player.get("ForgeData", {}).get("arce_passenger_rejoin")
    if mode == "seed" and cut in ("C7", "C8", "C9", "C13"):
        # RocketEntity.hasExactlyOnePlayerPassenger deliberately returns false: its old seat is
        # persisted by position/manifest, never as a second RootVehicle authority in player NBT.
        if len(entries) != 1 or "attempt" not in entries[0] or marker is not None or "RootVehicle" in player:
            raise smoke.SmokeError("Pre-save cut did not retain pending attempt and an unmarked old-seat file")
        if player["Pos"] == [entries[0]["attempt"][k] for k in ("x", "y", "z")]:
            raise smoke.SmokeError("Pre-save cut unexpectedly installed the P1 position")
        if cut == "C13" and not any(e["seat"] == "PENDING" for e in sidecar["entries"]):
            raise smoke.SmokeError("C13 lost independent unfinished seat work")
    elif boundary == "C10" or mode == "seed" and cut in ("C10", "C18", "C19", "C20"):
        if len(entries) != 1:
            raise smoke.SmokeError("Matching-readback cut prematurely retired placement")
        entry = entries[0]; attempt = entry["attempt"]
        batch = next(b for b in sidecar["batches"] if b["release"] == entry["release"])
        dimension = batch["dimension"] if attempt["target"] == "PAD" else "minecraft:overworld"
        if marker != {"schema": 1, "release": entry["release"], "player": entry["player"], "generation": attempt["generation"]}:
            raise smoke.SmokeError("Installed marker is not the exact K/G/schema")
        if player["Dimension"] != dimension or player["Pos"] != [attempt[k] for k in ("x", "y", "z")]:
            raise smoke.SmokeError("Installed position is not P1")
        if "RootVehicle" in player or player["Motion"] != [0.0, 0.0, 0.0] or player["FallDistance"] != 0:
            raise smoke.SmokeError("Installed file is not unmounted and motionless")
        if seed and cut == "C10" and player["Pos"] != seed["player"]["Pos"]:
            raise smoke.SmokeError("Matching restart relocated the passenger")
        if seed and cut in ("C18", "C19"):
            old = pending(seed)[0]["attempt"]
            if attempt["generation"] == old["generation"]:
                raise smoke.SmokeError("Failed lease reused the invalidated attempt")
            if cut == "C19" and player["Pos"] == seed["player"]["Pos"]:
                raise smoke.SmokeError("Failed safety lease reused the unsafe target")
    elif mode == "seed" and cut == "C11":
        if entries or marker is None or "RootVehicle" in player:
            raise smoke.SmokeError("Post-replacement P2 did not retain only a stale marker")
    elif not boundary:
        if entries or marker is not None or "RootVehicle" in player:
            raise smoke.SmokeError("Final restart did not retire placement and remove stale live marker")
        if any(e["seat"] == "PENDING" for e in sidecar["entries"]):
            raise smoke.SmokeError("Final restart left seat proof unfinished")
        if any(uuid_text(s["passenger_id"]) == PLAYER for s in release.seats(data["flight_data"])):
            raise smoke.SmokeError("Released passenger returned to a native seat")
    return witness


def run(args):
    repo = Path(__file__).resolve().parents[1]
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    if commit != args.source_commit or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True):
        raise smoke.SmokeError("Native run requires exact clean committed source")
    token = os.environ.get("W159_LOCK_TOKEN", "")
    lock = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt")
    if not token or not lock.read_text(encoding="utf-8-sig").startswith(token):
        raise smoke.SmokeError("Caller does not own heavy-JVM lock")
    evidence = args.evidence_dir.resolve(); evidence.mkdir(exist_ok=False)
    parent = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve(); session = parent / ("w159-native-" + uuid.uuid4().hex)
    result = dict(status="IN_PROGRESS", source_commit=commit, jar_sha256=smoke.digest_file(args.jar), starts=[])
    try:
        session.mkdir(); shutil.copytree(args.source_server / "libraries", session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        chosen = CASES if not args.cases else [tuple(case.split("-")) for case in args.cases.split(",")]
        if any(case not in CASES for case in chosen):
            raise smoke.SmokeError("Unknown W159 cut")
        for hold, cut in chosen:
            world = session / f"world-W158-{hold}-{cut}"
            props = smoke.expected_server_properties(port, True)
            props.update({"level-name": world.name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                          "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
            baseline = seed = None
            for mode in ("seed", "recover", "check"):
                recovery = "C10" if seed and pending(seed) else "B1_CAPTURE" if seed and any(e["seat"] == "PENDING" for e in seed["sidecar"]["entries"]) else ""
                boundary = cut if mode == "seed" else recovery if mode == "recover" else ""
                name = f"{hold}-{cut}-{mode}"; row = dict(case=name, **release.resources()); result["starts"].append(row)
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command.insert(3, f"-Dadvancedrocketrycommunity.rejoinReleaseProbe={hold}:{cut}:{mode}")
                if mode == "recover" and recovery:
                    command.insert(3, f"-Dadvancedrocketrycommunity.rejoinReleaseRecoveryCut={recovery}")
                row["command"] = command
                process = smoke.CapturedProcess(command, session, evidence / (name + ".log"))
                try:
                    index = process.wait_for(re.compile(r"ARCE_W158_(?:PAUSED|RESULT|FAILURE) case="), 300)
                    marker = process.lines[index]
                    if "ARCE_W158_FAILURE" in marker or bool(boundary) != ("ARCE_W158_PAUSED" in marker):
                        raise smoke.SmokeError("Native fixture failed or reached wrong boundary: " + marker)
                    if boundary:
                        if not marker.rstrip().endswith(f"ARCE_W158_PAUSED case={hold}:{cut}:{mode} boundary={boundary}"):
                            raise smoke.SmokeError("Wrong native pause: " + marker)
                        if cut == "C20" and mode == "seed":
                            process.command("stop"); row["exit"] = process.finish()
                            if row["exit"] != 0 or sum("ARCE_W159_SHUTDOWN_CLEAN threads=0 tickets=0" in line for line in process.lines) != 1:
                                raise smoke.SmokeError("In-flight shutdown did not close its reader and native tickets")
                        else:
                            process.process.kill(); process.abort(); row["exit"] = process.process.returncode
                    else:
                        process.command("stop"); row["exit"] = process.finish()
                        if row["exit"] != 0:
                            raise smoke.SmokeError("Normal restart exited abnormally")
                    disk = capture(world, evidence / (name + "-saved"))
                    baseline = verify(disk, hold, cut, mode, boundary, baseline, seed)
                    row.update(external_kill=bool(boundary) and not (cut == "C20" and mode == "seed"), boundary=boundary, conserved=True)
                    row["log_audit"] = audit_log(process.lines, hold, cut, mode)
                    if mode == "seed": seed = disk
                except Exception:
                    if process.process.poll() is None: process.abort()
                    failed = evidence / (name + "-failed-saved")
                    if world.is_dir() and not failed.exists(): capture(world, failed)
                    raise
                finally:
                    if process.process.poll() is None: process.abort()
            if world.resolve().parent != session or world.is_symlink(): raise smoke.SmokeError("Unsafe world cleanup")
            shutil.rmtree(world)
        result["status"] = "PASS"
    except Exception as failure:
        result.update(status="FAIL", error=str(failure))
    finally:
        (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        if session.is_dir() and session.resolve().parent == parent and not smoke.is_link_or_junction(session): shutil.rmtree(session)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source-server", "jar", "evidence-dir"): parser.add_argument("--" + name, type=Path, required=True)
    for name in ("java", "source-commit"): parser.add_argument("--" + name, required=True)
    parser.add_argument("--cases", default="")
    result = run(parser.parse_args()); print(json.dumps(result, indent=2)); return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
