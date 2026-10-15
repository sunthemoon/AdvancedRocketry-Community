#!/usr/bin/env python3
"""Build pinned hosts, create a complete v1.7 registry world, upgrade and restart twice.

Run with the heavy-JVM lock already held. Only new, disjoint work/evidence directories
are accepted. No root, block or inventory is edited offline to make a check pass.
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

if __package__:
    from . import v170_world_fixture as fixture
else:
    import v170_world_fixture as fixture

require, write = fixture.require, fixture.write
ROOT = Path(__file__).resolve().parents[1]
LOCK = Path("D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm")
TEMP = Path("D:/GitHub/ARCE-Task-Evidence/Temp")
EMPTY = {"schema_version": 1, "state": "EMPTY"}
MIGRATIONS = {
    "stations": (4, 5, "v1.5.0-station-warp", "v1.8.0-station-first-warp"),
    "satellite_missions": (3, 4, "v1.6.0-satellite-missions", "v1.8.0-ground-surveys"),
    "rocket_transfers": (2, 3, "v0.9.0-beta", "v1.8.0-first-moon-landing"),
}


def indexed(rows: list, fields: tuple) -> dict:
    result = {}
    for row in rows:
        key = tuple(tuple(row[field]) if isinstance(row[field], list) else row[field] for field in fields)
        require(key not in result, f"Duplicate inventory key: {key}")
        result[key] = row
    return result


def migrate_expected(name: str, old: dict) -> dict:
    """Independent, exact projections of ADR-065, W21b and W44; no wildcard key removal."""
    expected = copy.deepcopy(old)
    if name not in MIGRATIONS:
        return expected
    before, after, old_epoch, new_epoch = MIGRATIONS[name]
    require((old["schema_version"], old["format_epoch"]) == (before, old_epoch),
            f"Unsupported historical {name} schema/epoch")
    expected.update(schema_version=after, format_epoch=new_epoch)
    if name == "rocket_transfers":
        expected["classic_first_moon_landing"] = copy.deepcopy(EMPTY)
    elif name == "stations":
        expected["classic_first_warp"] = copy.deepcopy(EMPTY)
        expected["orbital_clock"] = {"schema_version": 1, "logical_tick": 0}
        for station in expected["stations"]:
            require(station["schema_version"] == 2, "Unexpected legacy station record")
            station["schema_version"] = 3
            station["orbital_controls"] = {
                "format_version": 1, "distance": 4,
                "phases": [station["environment"]["solar_angle_milli_degrees"], 0, 0],
                "rates": [0, 0, 0], "epoch": 0,
            }
    else:
        expected["ground_surveys"] = []
        for instance in expected["instances"]:
            require(instance["schema_version"] == 1, "Unexpected legacy instance")
            instance["schema_version"] = 2
            instance["origin_kind"] = "survey_mission"
            instance["origin_id"] = instance.pop("source_mission")
    return expected


def compare_root(name: str, expected: dict, actual: dict) -> list:
    """Only clocks and write epochs may advance; domain fields are compared in full."""
    before, after = copy.deepcopy(expected), copy.deepcopy(actual)
    counters = {
        "stations": (("orbital_clock", "logical_tick"),),
        "satellite_missions": (("clock", "logical_game_time"), ("clock", "last_observed_game_time"), ("save_epoch",)),
        "endgame": (("save_epoch",),),
    }.get(name, ())
    errors = []
    for parts in counters:
        left, right = before, after
        for part in parts[:-1]:
            left, right = left.get(part, {}), right.get(part, {})
        key = parts[-1]
        a, b = left.get(key), right.get(key)
        if type(a) is not int or type(b) is not int or not 0 <= a <= b:
            errors.append(f"root {name}: invalid/regressed {'.'.join(parts)}: {a} -> {b}")
        else:
            right[key] = a
    if before != after:
        errors.append(f"root {name}: undeclared payload/schema change")
    return errors


def compare_inventories(old: dict, new: dict, *, upgraded=True) -> list[str]:
    errors = []
    for kind, key_fields in (("blocks", ("pos",)), ("items", ("pos", "slot"))):
        before, after = (indexed(value[kind], key_fields) for value in (old, new))
        for key in sorted(before.keys() | after.keys()):
            if before.get(key) != after.get(key):
                errors.append(f"{kind} {key}: {before.get(key)} -> {after.get(key)}")
    for name, old_root in old["roots"].items():
        new_root = new["roots"].get(name)
        if not new_root or new_root.get("status") != "loaded":
            errors.append(f"root {name}: missing or refused")
            continue
        require(old_root["status"] == "loaded", "Historical root was refused")
        expected = migrate_expected(name, old_root["data"]) if upgraded else old_root["data"]
        errors.extend(compare_root(name, expected, new_root["data"]))
    # New stores are allowed only in their documented empty forms. Escrow must stay absent.
    for name in new["roots"].keys() - old["roots"].keys():
        root = new["roots"][name]
        if name == "station_notices" and root["status"] == "loaded":
            # ADR-065 section 5.3 / W22: startup creates revision-zero summaries for live stations.
            stations = old["roots"].get("stations", {}).get("data", {}).get("stations", [])
            expected = {"schema_version": 1, "stations": [
                {"id": station["station_id"], "rev": 0, "acks": []} for station in stations]}
            if root["data"] != expected:
                errors.append("new station notice root differs from empty summaries of the retained stations")
        elif name != "classic_progression" or root["status"] != "loaded":
            errors.append(f"unexpected new root {name}")
        else:
            data = root["data"]
            if data != {"schema_version": 1, "format_epoch": "v1.8.0-classic-progression",
                        "first_moon_landing": {"occurred": 0}, "first_station_warp": {"occurred": 0}}:
                errors.append("new progression root is not the documented empty receipt state")
    return errors


def check_log(text: str, *, first: bool) -> dict:
    errors = [line for line in text.splitlines() if re.search(r"/(?:ERROR|FATAL)\]", line)]
    require(not errors, "Unexpected native ERROR/FATAL: " + "; ".join(errors[:3]))
    require(not re.search(r"blocked startup|ARCE_MANAGED_ROOT_UNREADABLE|Unidentified mapping", text),
            "Upgrade refused a root or registry entry")
    checks = re.findall(r"Beta world data check complete: managed=(\d+), migrated=(\d+), backup=(.*)", text)
    require(len(checks) == 1, "Missing/duplicate pre-start migration receipt")
    managed, migrated, backup = checks[0]
    require(int(managed) >= 6 and int(migrated) == (3 if first else 0), "Unexpected managed migration count")
    require((backup != "none") == first, "Unexpected migration backup receipt")
    stamps = re.findall(r"ARCE_SATELLITE_SCHEMA4_UPGRADE source_schema=(\d+)", text)
    require(stamps == (["3"] if first else []), "Missing/repeated satellite migration line")
    return {"managed": int(managed), "migrated": int(migrated), "backup": backup,
            "satellite_schema4_lines": len(stamps), "errors": errors}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True, timeout=120).strip()


def build(repository: Path, evidence: Path, name: str, commit: str) -> dict:
    wrapper = repository / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    command = [str(wrapper), "-p", str(repository), "--offline", "--console=plain", "jar"]
    with (evidence / f"{name}-build.log").open("w", encoding="utf-8") as log:
        result = subprocess.run(command, cwd=repository, stdout=log, stderr=subprocess.STDOUT, timeout=1800)
    require(result.returncode == 0, f"{name} jar build failed: {result.returncode}")
    version = "1.7.0-dev" if name == "v17" else "1.8.0-dev"
    jar = repository / f"build/libs/advancedrocketry-community-1.20.1-{version}.jar"
    with zipfile.ZipFile(jar) as archive:
        require(f'version="1.20.1-{version}"' in archive.read("META-INF/mods.toml").decode(), "Wrong jar version")
    return {"source_commit": commit, "sha256": fixture.server.digest_file(jar), "path": str(jar),
            "build_command": command, "build_exit_code": result.returncode}


def run(args, report: dict):
    require(LOCK.is_dir() and (LOCK / "owner.txt").read_text().strip() == args.lock_owner,
            "Caller does not own the heavy-JVM lock")
    require(git("rev-parse", "HEAD") == args.source_commit and not git("status", "--porcelain"),
            "Native run requires the exact committed clean source")
    require(git("merge-base", "--is-ancestor", fixture.OLD_COMMIT, "HEAD") == "", "v1.7 is not an ancestor")
    for drive in (Path("C:/"), Path("D:/")) if sys.platform == "win32" else (ROOT,):
        require(shutil.disk_usage(drive).free >= 10 * 1024**3, f"Less than 10 GiB free on {drive}")
    work, evidence = args.work_dir.resolve(), args.evidence_dir.resolve()
    require(work.parent == TEMP.resolve() and not work.exists(), "Use a new direct child of the native Temp directory")
    require(not evidence.exists() and not evidence.is_relative_to(work) and not work.is_relative_to(evidence),
            "Use new disjoint evidence directory")
    require(not work.is_relative_to(args.template.resolve()) and not args.template.resolve().is_relative_to(work),
            "Template overlaps disposable runtime")
    work.mkdir()
    (work / ".w69-owned").write_text("W69\n")
    evidence.mkdir(parents=True)
    report.update(source_commit=args.source_commit, old_commit=fixture.OLD_COMMIT, phases=[], result="FAIL",
                  evidence_created=True)
    archive = work / "v17.zip"
    git("archive", "--format=zip", f"--output={archive}", fixture.OLD_COMMIT)
    report["old_source_archive_sha256"] = fixture.server.digest_file(archive)
    old_source = work / "v17-source"
    with zipfile.ZipFile(archive) as source:
        require(all(not Path(n).is_absolute() and ".." not in Path(n).parts for n in source.namelist()),
                "Unsafe Git archive member")
        source.extractall(old_source)
    old = build(old_source, evidence, "v17", fixture.OLD_COMMIT)
    current = build(ROOT, evidence, "v18", args.source_commit)
    report.update(artifacts={"v17": old, "v18": current})
    write(evidence / "inputs.json", report)
    old_server, new_server = work / "v17-server", work / "v18-server"
    fixture.prepare(args.template, old_server, Path(old["path"]))
    plan, original = fixture.create(args.java, old_server, evidence)
    original_files = fixture.file_inventory(old_server / "world")
    write(evidence / "source-world-files.json", original_files)
    fixture.prepare(args.template, new_server, Path(current["path"]), old_server / "world")
    previous = None
    for cycle in range(3):
        name = ("upgrade", "restart-1", "restart-2")[cycle]
        try:
            receipt = fixture.launch(args.java, new_server, evidence, name)
        except Exception:
            # The process is stopped by launch before inspecting a refused/failed world.
            # Preserve the native files even when no successful-start inventory can be claimed.
            failed = evidence / (name + "-failed-roots")
            failed.mkdir()
            for path in sorted((new_server / "world/data").glob(fixture.NS + "_*.dat")):
                shutil.copy2(path, failed / path.name)
            report["source_world_unchanged"] = fixture.file_inventory(old_server / "world") == original_files
            raise
        observed = fixture.capture(new_server / "world", plan, evidence / name)
        receipt["migration_log"] = check_log((evidence / f"{name}.log").read_text(encoding="utf-8"), first=cycle == 0)
        backups = sorted((new_server / "world" / (fixture.NS + "-backups")).iterdir())
        require(len(backups) == 1 and backups[0].is_dir(), "Missing/repeated migration backup")
        for root_name in original["roots"]:
            filename = fixture.NS + "_" + root_name + ".dat"
            require((backups[0] / filename).read_bytes() == (old_server / "world/data" / filename).read_bytes(),
                    "Migration backup changed original " + root_name)
        if cycle == 0:
            shutil.copytree(backups[0], evidence / "migration-backup")
        report["source_world_unchanged"] = fixture.file_inventory(old_server / "world") == original_files
        require(report["source_world_unchanged"], "Historical world changed during upgrade")
        receipt["comparison_errors"] = compare_inventories(original, observed)
        if previous is not None:
            receipt["comparison_errors"].extend(compare_inventories(previous, observed, upgraded=False))
        report["phases"].append(receipt)
        write(evidence / "summary.json", report)
        require(not receipt["comparison_errors"], "Upgrade changed content: " + "; ".join(receipt["comparison_errors"][:8]))
        previous = observed
    require(fixture.file_inventory(old_server / "world") == original_files, "Historical source world changed")
    for artifact in (old, current):
        require(fixture.server.digest_file(Path(artifact["path"])) == artifact["sha256"], "Artifact changed during run")
    require(git("rev-parse", "HEAD") == args.source_commit and not git("status", "--porcelain"), "Source changed during run")
    report.update(result="PASS", restarts=2, source_world_unchanged=True,
                  blocks=len(original["blocks"]), items=len(original["items"]),
                  block_entity_types=len(plan["registries"]["minecraft:block_entity_type"]),
                  roots=sorted(original["roots"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("template", "work-dir", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--lock-owner", required=True)
    parser.add_argument("--java", default="C:/Program Files/Java/jdk-17.0.7/bin/java.exe")
    args = parser.parse_args()
    report = {"result": "FAIL", "command": sys.argv}
    try:
        run(args, report)
    except Exception as error:
        report["failure"] = f"{type(error).__name__}: {error}"
    finally:
        if report.get("evidence_created"):
            write(args.evidence_dir / "summary.json", report)
            total = sum(p.stat().st_size for p in args.evidence_dir.rglob("*") if p.is_file())
            if total > 100 * 1024**2:
                report.update(result="FAIL", failure="Evidence exceeds 100 MiB")
                write(args.evidence_dir / "summary.json", report)
            files = [{"path": p.relative_to(args.evidence_dir).as_posix(), "bytes": p.stat().st_size,
                      "sha256": fixture.server.digest_file(p)} for p in sorted(args.evidence_dir.rglob("*"))
                     if p.is_file() and p.name != "evidence-inventory.json"]
            total = sum(p["bytes"] for p in files)
            write(args.evidence_dir / "evidence-inventory.json", {"files": files, "total_bytes": total})
    print(json.dumps(report, indent=2))
    return 0 if report["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
