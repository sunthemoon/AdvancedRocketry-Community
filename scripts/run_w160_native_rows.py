#!/usr/bin/env python3
"""Command-driven W160F native rows, with explicit seed and two independent restarts."""
import argparse
import copy
import hashlib
import gzip
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid

import run_w159_rejoin_cuts as rejoin
import run_w158_release_cuts as release
import run_dedicated_server_smoke as smoke
from run_v060_flight_server_smoke import _server_command
from run_v180_resource_restarts import read_nbt
from run_v180_escrow_forced_stop import record
from inspect_celestial_saved_data import NbtReader

CASES = ("C12", "C25", "C26", "C27", "C28", "C29", "C30", "C32", "C33")


class NativePlayerReader(NbtReader):
    """Keep the existing inspection bounds except for the one bounded native padding byte array."""
    def _payload(self, tag_type, depth):
        if tag_type == 7:
            length = self._unpack(">i", 4)
            if not 0 <= length <= 3*1024**2: raise smoke.SmokeError("Native player byte array exceeds fixture bound")
            raw = self._bytes(length)
            return dict(length=length, sha256=hashlib.sha256(raw).hexdigest()) if length > 4096 else list(raw)
        return super()._payload(tag_type, depth)


def read_player(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 4*1024**2:
        raise smoke.SmokeError("Native installed player exceeds capture bound")
    with gzip.open(path, "rb") as stream: raw = stream.read(4*1024**2 + 1)
    if len(raw) > 4*1024**2: raise smoke.SmokeError("Native expanded capture exceeds 4 MiB")
    return NativePlayerReader(raw).read_root()


def block(world, x, y, z):
    chunk = record(world / "region" / f"r.{x >> 9}.{z >> 9}.mca", (x >> 4 & 31) + 32 * (z >> 4 & 31))
    section = next(s for s in chunk["sections"] if s["Y"] == y >> 4)
    states = section["block_states"]; palette = states["palette"]
    if len(palette) == 1: return palette[0]
    bits = max(4, (len(palette) - 1).bit_length()); per = 64 // bits
    offset = (y & 15)*256 + (z & 15)*16 + (x & 15)
    return palette[(states["data"][offset // per] >> ((offset % per)*bits)) & ((1 << bits)-1)]


def capture(world, target, cut):
    disk = release.capture(world, target)
    disk["player"] = read_player(world / "playerdata" / (rejoin.PLAYER + ".dat"))
    padding = disk["player"].get("ForgeData", {}).pop("w160f_native_padding", None)
    if padding is not None:
        if not isinstance(padding, dict): raise smoke.SmokeError("Expanded native padding is not over 4096 bytes")
        disk["padding"] = padding
    # C12's first rider has no placement obligation. The other rows exercise both logins.
    disk["players"] = {}
    identities = {rejoin.FIRST, rejoin.PLAYER} | {rejoin.uuid_text(e["player"]) for e in disk["sidecar"]["entries"]}
    for identity in sorted(identities):
        path = world / "playerdata" / (identity + ".dat")
        if not path.exists():
            disk["players"][identity] = dict(present=False); continue
        data = read_player(path)
        disk["players"][identity] = dict(present=True, **{k: data[k] for k in ("UUID", "Health", "DeathTime", "Dimension", "Pos")},
                RootVehicle=data.get("RootVehicle"), marker=data.get("ForgeData", {}).get("arce_passenger_rejoin"))
        shutil.copy2(path, target / path.name)
    if cut == "C25": disk["restored_blocks"] = [block(world, 72, y, 72) for y in (100, 101)]
    (target / "inspection.json").write_text(json.dumps(disk, indent=2), encoding="utf-8")
    return disk


def verify(disk, cut, mode, baseline=None, seed=None):
    root = disk["sidecar"]; journal = disk["journal"]; rockets = disk["rockets"]
    if root is None or root["next_sequence"] != (2 if cut == "C28" else 10 if cut == "C30" else 1):
        raise smoke.SmokeError("Missing or reused native release sequence")
    early = mode == "seed" and cut in ("C12", "C26")
    if len(rockets) != (0 if cut == "C25" else 1): raise smoke.SmokeError("Native authority count differs")
    witness = {"journal_envelope": {k: v for k, v in journal.items() if k != "transfers"}}
    if rockets:
        entity = rockets[0]["entity"]; data = entity["RocketEntityData"]
        witness.update(uuid=entity["UUID"], snapshot=data["snapshot"], fuel=data["flight_data"]["fuel"])
        if witness["fuel"]["amount"] != 1000: raise smoke.SmokeError("Native row changed fuel")
        roster = sorted((rejoin.uuid_text(s["passenger_id"]), s["seat_index"]) for s in release.seats(data["flight_data"]))
        expected = [(rejoin.FIRST, 0), (rejoin.PLAYER, 1)] if early or cut == "C27" else []
        if roster != expected: raise smoke.SmokeError("Native row seat census differs")
    else:
        if disk["restored_blocks"] != [{"Name": "minecraft:iron_block"}]*2:
            raise smoke.SmokeError("Native disassembly did not retain both restored blocks")
        witness["restored_blocks"] = disk["restored_blocks"]
    if baseline is not None and witness != baseline: raise smoke.SmokeError("Native resources or outbox changed across restart")
    if cut in ("C29", "C32"):
        if len(journal["transfers"]) != 1 or journal["transfers"][0]["schema_version"] != 3:
            raise smoke.SmokeError("Native journal peer is not a unique governed record")
        if seed and journal["transfers"] != seed["journal"]["transfers"]:
            raise smoke.SmokeError("Native journal peer changed across restart")
        peer = journal["transfers"][0]
        if cut == "C29" and any(not any(rejoin.uuid_text(s["passenger_id"]) == rejoin.PLAYER for s in release.seats(peer[key]))
                                for key in ("source_flight", "destination_flight")):
            raise smoke.SmokeError("Newer-transfer fixture lost its conflicting rider")
        if cut == "C32" and journal["classic_first_moon_landing"]["state"] != "DELIVERED":
            raise smoke.SmokeError("Interleaved W44 event was not durably delivered")
    if cut in ("C28", "C29", "C30"):
        expected_entries = 3 if cut == "C28" else 2 if cut == "C29" else 131
        if len(root["entries"]) != expected_entries or cut != "C29" and journal["transfers"] or any(
                e["seat"] != "VERIFIED" or e["placement"] != "PENDING" or "attempt" in e for e in root["entries"]):
            raise smoke.SmokeError("Native conflict/queue timeout changed obligations")
        ids = {rejoin.PLAYER} if cut != "C30" else {rejoin.uuid_text(e["player"]) for e in root["entries"]} - {rejoin.FIRST}
        for identity in ids:
            p = disk["players"].get(identity, {})
            if not p.get("present") or p["Health"] != 1 or p["DeathTime"] != 0 or p["marker"] is not None or p["RootVehicle"] is not None:
                raise smoke.SmokeError("Protected timeout did not preserve a healthy unmounted player")
        if seed and (root != seed["sidecar"] or disk["players"] != seed["players"]):
            raise smoke.SmokeError("Native pressure restart altered obligations or installed positions")
        return witness
    if early:
        entries = sorted((rejoin.uuid_text(e["player"]), e["seat"], e["placement"]) for e in root["entries"])
        expected = [(rejoin.FIRST, "PENDING", "NOT_REQUIRED" if cut == "C12" else "PENDING"),
                    (rejoin.PLAYER, "PENDING", "PENDING")]
        if entries != expected or len(journal["transfers"]) != 1 or any("attempt" in e for e in root["entries"]):
            raise smoke.SmokeError("S1 cut lost its frozen pending census")
        if disk["player"].get("ForgeData", {}).get("arce_passenger_rejoin") is not None:
            raise smoke.SmokeError("Early cut unexpectedly placed the player")
    else:
        if root["entries"] or root["batches"] or cut != "C32" and journal["transfers"]:
            raise smoke.SmokeError("Final native row retained a placement or reconstructible transfer")
        for identity in ((rejoin.PLAYER,) if cut == "C12" else (rejoin.FIRST, rejoin.PLAYER)):
            player = disk["players"].get(identity, {})
            if (not player.get("present") or rejoin.uuid_text(player["UUID"]) != identity or player["Health"] <= 0
                    or player["DeathTime"] != 0 or player["marker"] is not None or player["RootVehicle"] is not None
                    or player["Dimension"] != "minecraft:overworld"):
                raise smoke.SmokeError("Native row lacks a healthy installed clean player")
            if seed and not (cut in ("C12", "C26") and mode == "recover") and seed["players"].get(identity, {}).get("present"):
                if player["Pos"] != seed["players"][identity]["Pos"]:
                    raise smoke.SmokeError("Completed native player moved on restart")
        if cut == "C33" and disk.get("padding", {}).get("length") != 3 * 1024**2:
            raise smoke.SmokeError("Native expanded player no longer exceeds 2 MiB")
        if cut == "C33" and seed and disk["padding"] != seed["padding"]:
            raise smoke.SmokeError("Native expanded player payload changed")
    return witness


def verify_rollback(before, after):
    if any(before[key] != after[key] for key in ("journal", "sidecar", "players")):
        raise smoke.SmokeError("External rollback changed another store")
    old = before["rockets"][0]["entity"]; new = after["rockets"][0]["entity"]
    a, b = old["RocketEntityData"], new["RocketEntityData"]
    if (old["UUID"] != new["UUID"] or a["snapshot"] != b["snapshot"] or a["flight_data"]["fuel"] != b["flight_data"]["fuel"]):
        raise smoke.SmokeError("External rollback changed resources or identity")
    roster = lambda data: sorted((rejoin.uuid_text(s["passenger_id"]), s["seat_index"]) for s in release.seats(data["flight_data"]))
    if roster(a) != [(rejoin.PLAYER, 1)] or roster(b) != [(rejoin.FIRST, 0), (rejoin.PLAYER, 1)]:
        raise smoke.SmokeError("External rollback did not replace ME with its older two-rider manifest")


def run(args):
    repo = Path(__file__).resolve().parents[1]
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
    if sha != args.source_commit or subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True):
        raise smoke.SmokeError("Exact clean committed source required")
    token = os.environ.get("W160_LOCK_TOKEN", "")
    if not token or not Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt").read_text(encoding="utf-8-sig").startswith(token):
        raise smoke.SmokeError("Owned machine lock required")
    cases = args.cases.split(",")
    if any(case not in CASES for case in cases): raise smoke.SmokeError("Unknown W160F row")
    evidence = args.evidence_dir.resolve(); evidence.mkdir(exist_ok=False)
    parent = Path("D:/GitHub/ARCE-Task-Evidence/Temp").resolve(); session = parent / ("w160f-rows-" + uuid.uuid4().hex)
    result = dict(status="IN_PROGRESS", source_commit=sha, jar_sha256=smoke.digest_file(args.jar), starts=[])
    def save(): (evidence / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    try:
        session.mkdir(); shutil.copytree(args.source_server / "libraries", session / "libraries")
        (session / "mods").mkdir(); shutil.copy2(args.jar, session / "mods" / args.jar.name)
        port = smoke.allocate_port(); smoke.write_server_configuration(session, port, True)
        for cut in cases:
            world = session / ("world-W158-H1-" + cut)
            props = smoke.expected_server_properties(port, True)
            props.update({"level-name": world.name, "level-type": "minecraft:flat", "max-tick-time": "60000",
                          "generator-settings": json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1}], "biome": "minecraft:plains"})})
            (session / "server.properties").write_text("".join(f"{k}={v}\n" for k, v in sorted(props.items())), encoding="ascii")
            baseline = seed = None
            for mode in ("seed", "recover", "check"):
                name = f"H1-{cut}-{mode}"; boundary = ("C2" if cut in ("C12", "C26") else cut) if mode == "seed" else ""
                row = dict(case=name, boundary=boundary, **release.resources()); result["starts"].append(row); save()
                command = _server_command(args.java); command[2] = "-Xmx4G"
                command[3:3] = ["-Dadvancedrocketrycommunity.rejoinReleaseCommand=true", f"-Dadvancedrocketrycommunity.rejoinReleaseProbe=H1:{cut}:{mode}"]
                row["command"] = command
                process = smoke.CapturedProcess(command, session, evidence / (name + ".log"))
                try:
                    index = process.wait_for(re.compile(r"ARCE_W158_(?:PAUSED|RESULT|FAILURE) case="), 300)
                    marker = process.lines[index]
                    if "FAILURE" in marker or bool(boundary) != ("PAUSED" in marker): raise smoke.SmokeError("Wrong row completion: " + marker)
                    if boundary:
                        if not marker.rstrip().endswith(f"ARCE_W158_PAUSED case=H1:{cut}:{mode} boundary={boundary}"):
                            raise smoke.SmokeError("Wrong row boundary")
                        process.process.kill(); process.abort(); row["exit"] = process.process.returncode
                    else:
                        process.command("stop"); row["exit"] = process.finish()
                        if row["exit"]: raise smoke.SmokeError("Native row did not stop normally")
                    if cut == "C12" and mode == "seed":
                        before = capture(world, evidence / (name + "-before-rollback"), cut)
                        old = session / "w160f-old-entities.mca"; installed = world / "entities/r.0.0.mca"
                        row["rollback"] = dict(old_sha256=smoke.digest_file(old), replaced_sha256=smoke.digest_file(installed))
                        if row["rollback"]["old_sha256"] == row["rollback"]["replaced_sha256"]:
                            raise smoke.SmokeError("C12 rollback did not change the native entity store")
                        shutil.copy2(old, installed)
                    disk = capture(world, evidence / (name + "-saved"), cut)
                    if cut == "C12" and mode == "seed":
                        verify_rollback(before, disk)
                    baseline = verify(disk, cut, mode, baseline, seed)
                    row["audit"] = audit_log(process.lines, cut, mode)
                    row["conserved"] = True
                    if mode in ("seed", "recover"): seed = copy.deepcopy(disk)
                except Exception:
                    if process.process.poll() is None: process.abort()
                    if world.is_dir():
                        try: capture(world, evidence / (name + "-failed-saved"), cut)
                        except Exception as capture_failure: row["capture_error"] = str(capture_failure)
                    raise
                finally:
                    if process.process.poll() is None: process.abort()
                save()
            shutil.rmtree(world)
        result["status"] = "PASS"
    except Exception as failure:
        result.update(status="FAIL", error=str(failure))
    finally:
        if session.is_dir() and session.resolve().parent == parent and not smoke.is_link_or_junction(session): shutil.rmtree(session)
        result["temporary_removed"] = not session.exists(); save()
    return result


def audit_log(lines, cut, mode):
    # The only new WARN is the one production disk-ceiling refusal in C33 seed.
    limit = re.compile(r"ARCE_REJOIN_PLAYER_LIMIT player=" + re.escape(rejoin.PLAYER)
                       + r" kind=OVERSIZED size=([0-9]+) ceiling=67108864")
    count = 0; remaining = []; messages = []
    for line in lines:
        h = release.HEADER.fullmatch(line.rstrip()); m = limit.fullmatch(h["message"]) if h else None
        if h and h["logger"] in release.PROJECT_LOGGERS and h["thread"] == "Server thread" and h["level"] == "INFO":
            messages.append(h["message"])
        if h and m and h["logger"] in release.PROJECT_LOGGERS and h["thread"] == "Server thread" and h["level"] == "WARN":
            if int(m[1]) <= 64*1024**2: raise smoke.SmokeError("Oversized native log did not cross its ceiling")
            count += 1
        else: remaining.append(line)
    if count != int(cut == "C33" and mode == "seed"): raise smoke.SmokeError("Wrong native disk-ceiling warning count")
    def exactly(pattern, expected):
        observed = sum(bool(re.fullmatch(pattern, text)) for text in messages)
        if observed != expected: raise smoke.SmokeError(f"Native row observation count differs: {pattern}: {observed} != {expected}")
    if cut in ("C28", "C29", "C30"):
        queue = cut == "C30"
        exactly(f"ARCE_W160F_PRESSURE case={cut} mode={mode} players={130 if queue else 1} "
                f"capacity={str(queue).lower()} unsafe={str(queue).lower()} retained={131 if queue else 3 if cut == 'C28' else 2} timeout_ticks=200", 1)
    if cut == "C33":
        exactly(f"ARCE_W160F_EXPANDED mode={mode} payload_bytes=3145728 entries=0", 1)
        exactly(r"ARCE_W160F_DISK_CEILING bytes=[0-9]+ ceiling=67108864 pending=true", int(mode == "seed"))
    if cut == "C26" and mode != "seed":
        exactly(f"ARCE_W160F_PLAYER_FIRST mode={mode} loaded_rockets=0 guarded={str(mode == 'recover').lower()}", 1)
    if cut == "C32":
        exactly(r"ARCE_W160F_INTERLEAVE peer=" + release.UUID_PATTERN + r" sequence=[0-9]+ elected=true", int(mode == "seed"))
    return dict(disk_ceiling=count, base=release.audit_log(remaining, "H1", cut, mode))


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    for key in ("source-server", "jar", "evidence-dir"): p.add_argument("--" + key, type=Path, required=True)
    for key in ("java", "source-commit", "cases"): p.add_argument("--" + key, required=True)
    outcome = run(p.parse_args()); print(json.dumps(outcome, indent=2)); raise SystemExit(outcome["status"] != "PASS")
