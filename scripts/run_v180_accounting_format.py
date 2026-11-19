#!/usr/bin/env python3
"""W373B: the format boundaries of the accounting ledger (W357-DESIGN sections 4.2, 4.3 and 7.3).

`root2-upgrade` starts a world that holds a root-2 ledger with an open fluid operation on the candidate JAR, stopped
during and after the upgrade, then twice more. `root3-old-jar` starts the upgraded world on the pinned root-2 JAR,
lists every file that start changes, restores the whole pre-upgrade backup, and starts the refused copy on the
candidate twice. The root-2 JAR is candidate C3; no other file is accepted. The caller holds the heavy-JVM lock.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import shutil
import time

import accounting_cut_oracle as oracle
import escrow_accounting as accounting
import loader_cut_nbt as nbt
import run_dedicated_server_smoke as smoke
import run_v180_accounting_cuts as cuts
import w336_native as native
from run_v180_escrow_forced_stop import LEDGER
from run_v180_fuel_loader_s1 import world_hashes

# C3-SWITCH-REPORT section 2.1: the last candidate whose escrow reader and writer are root 2.
ROOT2_COMMIT = "9a15e1f42dbacf6c81b6332192f523cd25f59035"
ROOT2_SHA256 = "459669bf802ea7ca0ca88bf5a84902eb69274f4a3313305319843c6d8f81744b"
# The existing probe case that leaves one acknowledged, unapplied fluid cargo load: an open root-2 operation.
SEED_CASE = "FC_LOAD_L1_ACK"
SEED_PAUSED = re.compile(r"ARCE_RELEASE_CARGO_PAUSED cut=" + SEED_CASE + r" ")
SEED_RESULT = re.compile(r"ARCE_RELEASE_CARGO_RESULT cut=" + SEED_CASE + r" cargo=1000 device=4000 epoch=1 records=0(?:\s|$)")
FAMILIES = {"root2-upgrade": ("UPGRADE_AFTER", "UPGRADE_DURING_BACKUP", "UPGRADE_DURING_COMMIT"),
            "root3-old-jar": ("OLD_JAR_REFUSAL",)}
BACKUPS = "advancedrocketrycommunity-backups"
MIGRATION = re.compile(r"\[(ARCE-BETA-\d+)\] Beta world data check complete: managed=(\d+), migrated=(\d+), backup=(\S+)")
REFUSAL = re.compile(r"/ERROR\] .*\[(ARCE-BETA-\d+)\] Beta world data check blocked startup[;:] ?(.*)$")
# The designed reason of an escrow-aware root-2 reader that meets root 3 (WorldDataMigrationService, FUTURE_SCHEMA).
DESIGNED_REFUSAL = "advancedrocketrycommunity_resource_escrow uses future schema 3"
SETTLE_TICKS = 200
# Both endpoints of the fixture stand in chunk [4,4]; a forced chunk is loaded and entity-ticking without a probe.
FIXTURE = [(64, 100, 64), (68, 100, 64)]
HELD = re.compile(r"accounting=UNKNOWN_BASELINE(?:\s|$)")


def record_digests(world):
    """The canonical digest of every saved operation, from the typed file, so a re-encoded operation cannot pass."""
    typed = nbt.compound(nbt.read_gzip(world / "data" / LEDGER)[1], "data")
    return [accounting.digest(op) for op in nbt.compound(typed, "records").value[1]]


def read(world):
    disk = cuts.cargo_disk(world)
    if disk["ledger"] is not None:
        disk["ledger"]["record_digests"] = record_digests(world)
    return disk


def copy_world(source, target):
    native.require(not target.exists() and source.is_dir() and not smoke.is_link_or_junction(source), "Unsafe world copy")
    shutil.copytree(source, target, ignore=shutil.ignore_patterns("session.lock"))


def migration_files(world):
    """What the migrator left beside the ledger: its backup directories and any staging file."""
    backups = world / BACKUPS
    rows = {}
    if backups.is_dir():
        for directory in sorted(p for p in backups.iterdir() if p.is_dir()):
            rows[directory.name] = {f.name: smoke.digest_file(f) for f in sorted(directory.iterdir()) if f.is_file()}
    data = world / "data"
    staged = sorted(p.name for p in data.iterdir() if p.name.startswith(".") and ".migrating-" in p.name) if data.is_dir() else []
    return dict(backups=rows, staged=staged)


def errors(lines):
    return [line.rstrip() for line in lines if "/ERROR]" in line or "/FATAL]" in line]


def seed(run):
    """A world written only by the root-2 JAR: one PREPARED fluid cargo load, both stores BEFORE, killed at L1's ack."""
    native.require(smoke.digest_file(run.args.root2_jar) == ROOT2_SHA256, "The root-2 JAR is not candidate C3's pinned artifact")
    world = run.session / "seed"
    process = run.start("seed", world=world.name, properties={"escrowScenario": "start", cuts.CASE_KEY: SEED_CASE},
                        jar=run.args.root2_jar, jar_sha256=ROOT2_SHA256)
    process.wait_for(SEED_PAUSED, run.args.startup_timeout)
    process.process.kill(); process.abort(); run.process = None
    native.require(not errors(process.lines), "The root-2 seed start logged ERROR or FATAL")
    disk = read(world)
    digests = disk["ledger"]["record_digests"]
    oracle.check_root2(disk["ledger"], digests, disk["device"], disk["rocket"])
    # The whole-world backup the operator takes before the upgrade: every file, with its SHA-256.
    backup = run.session / "backup-root2"
    copy_world(world, backup)
    hashes = world_hashes(backup)
    native.require(hashes == world_hashes(world), "The whole-world backup differs from the root-2 world")
    return dict(world=world, backup=backup, hashes=hashes, digests=digests, ledger_sha256=smoke.digest_file(world / "data" / LEDGER),
                operation=accounting.identity(disk["ledger"]["records"][0]["op_id"]),
                archive=run.capture(backup, "root2-whole-world-backup"))


def settle(run, operation):
    """The legacy hold, exercised: both endpoints loaded for 200 ticks (reconciliation retries every 20), the server's
    own classification of the operation, then a full save. Without the hold the transfer would complete here, as it
    does on the root-2 JAR."""
    run.load(FIXTURE)
    first = run.tick(); deadline = time.monotonic() + 120
    while run.tick() - first < SETTLE_TICKS:
        native.require(time.monotonic() < deadline, "The server did not advance its game time")
        time.sleep(0.5)
    line = run.send("arce escrow inspect " + operation, HELD, 30)
    for x, _, z in FIXTURE[:1]:
        run.send(f"forceload remove {x} {z}")
    run.send("save-all flush", native.SAVED, 180)
    return dict(ticks=run.tick() - first, inspect=line.strip()[-400:])


def check_upgraded(world, base):
    disk = read(world)
    summary = oracle.check_upgraded(disk["ledger"], base["digests"], disk["device"], disk["rocket"])
    summary.update(writer_generation=disk["ledger"]["writer_generation"], files=disk["files"], migration=migration_files(world))
    return summary


def migrated(lines):
    found = [match.groups() for line in lines for match in [MIGRATION.search(line)] if match]
    native.require(len(found) <= 1, "The world data check reported more than once in one start")
    return dict(code=found[0][0], managed=int(found[0][1]), migrated=int(found[0][2]), backup=found[0][3]) if found else None


def candidate_start(run, label, world, base, kill=False):
    """One start on the candidate with no probe. Either killed once ready, or settled and stopped normally."""
    process = run.start(label, world=world.name)
    run.ready()
    row = dict(label=label, migration=migrated(process.lines))
    if kill:
        process.process.kill(); process.abort(); run.process = None
        row["forced_stop"] = True; row["errors"] = errors(process.lines)
    else:
        row["hold"] = settle(run, base["operation"])
        row["errors"] = errors(run.stop(label, audit=lambda lines: None))
    native.require(not row["errors"], "The candidate start logged ERROR or FATAL: " + (row["errors"] or [""])[0][:200])
    row["state"] = check_upgraded(world, base)
    return row


def completed(lines):
    """The first start finished its migration: the world data check reported, or the server became ready."""
    return any(MIGRATION.search(line) or smoke.READY_MARKER.search(line) for line in lines)


def artifacts(world, kind):
    """The migrator's artifacts of one kind, by name: the backup root and its directories, or the staged files."""
    if kind == "BACKUP":
        backups = world / BACKUPS
        return {BACKUPS} | {BACKUPS + "/" + p.name for p in backups.iterdir() if p.is_dir()} if backups.is_dir() else set()
    native.require(kind == "COMMIT", "Unknown upgrade watcher kind")
    data = world / "data"
    return {p.name for p in data.iterdir() if ".migrating-" in p.name} if data.is_dir() else set()


def watch_and_kill(run, process, world, kind, before):
    """Kill as soon as this attempt's backup directory (BACKUP) or staged file (COMMIT) is seen; never wait for it.

    `before` holds the artifact names that existed before the start, so an older attempt's leftovers are never taken
    for this one's. Completion is checked first, and again in the captured log after the kill: a first start whose
    migration completed is not a stop during the migration, whatever artifact was seen.
    """
    deadline = time.monotonic() + run.args.startup_timeout
    seen, created, checked = None, [], 0
    while seen is None:
        lines = process.lines[checked:]
        checked += len(lines)
        if completed(lines):
            seen = "NOT_SEEN_BEFORE_COMPLETION"
            break
        created = sorted(artifacts(world, kind) - before)
        if created:
            seen = "BACKUP_DIRECTORY" if kind == "BACKUP" else "STAGED_FILE"
        elif process.process.poll() is not None or time.monotonic() > deadline:
            raise smoke.SmokeError("The candidate ended or timed out before the upgrade was observed")
    process.process.kill(); process.abort(); run.process = None
    return dict(seen=seen, created=created, existed_before=sorted(before), completed=completed(process.lines))


def upgrade_case(run, case, base):
    world = run.session / ("world-" + case)
    copy_world(base["backup"], world)
    row = dict(case=case, starts=[], findings=[])
    try:
        if case == "UPGRADE_AFTER":
            first = candidate_start(run, case + "-0", world, base, kill=True)
            native.require(first["migration"] is not None and first["migration"]["migrated"] == 1 and first["migration"]["backup"] != "none",
                           "The first candidate start did not report exactly one migrated file and its backup")
            saved = first["state"]["migration"]["backups"]
            native.require(list(saved) == [first["migration"]["backup"]] and saved[first["migration"]["backup"]].get(LEDGER) == base["ledger_sha256"],
                           "The migrator's backup does not hold the byte-equal root-2 ledger")
            row["starts"].append(first)
        else:
            kind = case.removeprefix("UPGRADE_DURING_")
            # The names present before the start: only what this attempt creates counts as its artifact.
            before = artifacts(world, kind)
            process = run.start(case + "-0", world=world.name)
            watched = watch_and_kill(run, process, world, kind, before)
            ledger_sha = smoke.digest_file(world / "data" / LEDGER)
            first = dict(label=case + "-0", forced_stop=True, **watched, errors=errors(process.lines), migration_files=migration_files(world),
                         ledger_sha256=ledger_sha, migration=migrated(process.lines))
            # The complete captured log decides, also when the watcher's own two looks missed the line.
            first["completed"] = first["completed"] or first["migration"] is not None or completed(process.lines)
            row["starts"].append(first)
            if ledger_sha == base["ledger_sha256"]:
                # The kill landed before the commit: the root-2 file is untouched, byte for byte, and both stores
                # are exactly the fixture's BEFORE state. The decoded snapshot is kept.
                first["landed"] = "BEFORE_COMMIT"
                disk = read(world)
                raw = run.evidence / f"{case}-0-raw.json"
                raw.write_text(json.dumps(disk, indent=1), encoding="utf-8")
                first["raw"] = dict(file=raw.name, sha256=smoke.digest_file(raw))
                first["state"] = oracle.check_root2(disk["ledger"], base["digests"], disk["device"], disk["rocket"])
            else:
                # The kill landed after the atomic replacement: the file must be a complete, valid root 3.
                first["landed"] = "AFTER_COMMIT"
                first["state"] = check_upgraded(world, base)
            native.require(not first["errors"], "The killed candidate start logged ERROR or FATAL")
        for restart in (1, 2):
            entry = candidate_start(run, f"{case}-{restart}", world, base)
            row["starts"].append(entry)
            expected = 1 if restart == 1 and row["starts"][0].get("landed") == "BEFORE_COMMIT" else 0
            native.require(entry["migration"] is not None and entry["migration"]["migrated"] == expected,
                           f"Restart {restart} migrated {entry['migration']} file(s), expected {expected}")
        states = [start["state"] for start in row["starts"][1:]]
        row["final_backups"] = sorted(states[-1]["migration"]["backups"]); row["final_staged"] = states[-1]["migration"]["staged"]
    except (OSError, ValueError, KeyError, TypeError, IndexError, smoke.SmokeError) as failure:
        if run.process is not None:
            run.process.abort(); run.process = None
        row["findings"].append(str(failure))
        row["failure_world"] = run.capture(world, case + "-failed-world")
    row["status"] = "FAILED" if row["findings"] else "PASS"
    first = row["starts"][0] if row["starts"] else {}
    if row["status"] == "PASS" and (first.get("completed") or first.get("seen") == "NOT_SEEN_BEFORE_COMPLETION"):
        # The first start completed its migration: a valid stop after the upgrade, not one during it, whatever
        # artifact the watcher saw.
        row.update(status="NOT_COVERED", reason="The kill followed the completed upgrade; not evidence for a stop during it")
    return row


def refusal_findings(refusals):
    """R-044's verdict: a refusal must name the designed reason, keep the ledger and change no world file. Not waived."""
    findings = []
    for index, attempt in enumerate(refusals):
        if not attempt["refused"]:
            findings.append(f"old JAR start {index}: the world loaded instead of being refused")
        elif not attempt["designed_reason"]:
            findings.append(f"old JAR start {index}: refused for another reason: {attempt['reason']}")
        if not attempt["ledger_byte_equal"]:
            findings.append(f"old JAR start {index}: the ledger file changed")
        changed = attempt["files"]["changed"] + attempt["files"]["added"] + attempt["files"]["removed"]
        if changed:
            findings.append(f"old JAR start {index}: the refusal changed world files: {', '.join(changed)}")
    return findings


def old_jar_start(run, label, world):
    """One start of the root-2 JAR on a root-3 world. It must refuse before the world loads; every file change is listed."""
    before = world_hashes(world)
    process = run.start(label, world=world.name, jar=run.args.root2_jar, jar_sha256=ROOT2_SHA256)
    try:
        index = process.wait_for(re.compile(REFUSAL.pattern + "|" + smoke.READY_MARKER.pattern), run.args.startup_timeout)
        refused = REFUSAL.search(process.lines[index])
        if refused is None:
            process.process.kill(); process.abort(); run.process = None
            code = None
        else:
            code = process.finish(run.args.exit_timeout); run.process = None
    except smoke.SmokeError:
        process.abort(); run.process = None
        raise
    after = world_hashes(world)
    ledger_key = next(key for key in before if key.replace("\\", "/") == "data/" + LEDGER)
    return dict(label=label, refused=refused is not None, exit_code=code,
                diagnostic=refused[1] if refused else None, reason=refused[2].strip() if refused else None,
                designed_reason=bool(refused and DESIGNED_REFUSAL in refused[2]),
                ledger_byte_equal=before[ledger_key] == after.get(ledger_key),
                files=oracle.changed_files(before, after), unchanged=sum(before[k] == after.get(k) for k in before),
                world=run.capture(world, label + "-world"))


def old_jar_case(run, base):
    row = dict(case="OLD_JAR_REFUSAL", findings=[], old_commit=ROOT2_COMMIT, old_sha256=ROOT2_SHA256)
    try:
        # 1. The candidate upgrades a copy of the root-2 world and stops normally.
        upgraded = run.session / "world-upgraded"
        copy_world(base["backup"], upgraded)
        row["upgrade"] = candidate_start(run, "upgrade", upgraded, base)
        native.require(row["upgrade"]["migration"]["migrated"] == 1, "The candidate did not migrate the root-2 ledger")
        # 2. The root-2 JAR starts a copy of the root-3 world, three times.
        refused = run.session / "world-refused"
        copy_world(upgraded, refused)
        row["refusals"] = []
        for attempt in range(3):
            row["refusals"].append(old_jar_start(run, f"old-{attempt}", refused))
            native.write_json(run.evidence / "old-jar-observations.json", row["refusals"])
        # 3. The whole pre-upgrade backup, restored into a fresh directory, starts on the root-2 JAR and finishes its
        #    open transfer exactly once from the original ledger.
        restored = run.session / "world-restored"
        copy_world(base["backup"], restored)
        native.require(world_hashes(restored) == base["hashes"], "The whole-world backup did not restore exactly")
        process = run.start("restored-old", world=restored.name, jar=run.args.root2_jar, jar_sha256=ROOT2_SHA256,
                            properties={"escrowScenario": "recover", cuts.CASE_KEY: SEED_CASE})
        process.wait_for(SEED_RESULT, run.args.startup_timeout)
        logged = errors(run.stop("restored-old", audit=lambda lines: None))
        disk = cuts.cargo_disk(restored)
        native.require(not logged, "The restored root-2 start logged ERROR or FATAL")
        native.require(disk["ledger"]["schema_version"] == 2 and not disk["ledger"]["records"] and disk["ledger"]["issued_through"] == 1,
                       "The restored world's ledger is not the original root 2 with its one transfer retired")
        oracle.check_store("source", disk["device"], oracle.store(oracle.WATER, oracle.TOTAL - oracle.QUANTITY, 1, 0))
        oracle.check_store("destination", disk["rocket"], oracle.store(oracle.WATER, oracle.QUANTITY, 1, 0))
        row["restored"] = dict(status="PASS", ledger_schema=2, records=0, device=disk["device"]["fluids"], rocket=disk["rocket"]["fluids"])
        # 4. The copy the root-2 JAR refused starts on the candidate twice; the result is recorded as it is.
        row["candidate_after_refusal"] = []
        for restart in (1, 2):
            entry = dict(label=f"refused-candidate-{restart}")
            row["candidate_after_refusal"].append(entry)
            try:
                entry.update(candidate_start(run, entry["label"], refused, base), status="PASS")
            except (OSError, ValueError, KeyError, TypeError, IndexError, smoke.SmokeError) as failure:
                if run.process is not None:
                    run.process.abort(); run.process = None
                entry.update(status="FAILED", error=str(failure))
                row["findings"].append(f"candidate after refusal {restart}: {failure}")
                break
    except (OSError, ValueError, KeyError, TypeError, IndexError, StopIteration, smoke.SmokeError) as failure:
        if run.process is not None:
            run.process.abort(); run.process = None
        row["findings"].append(str(failure))
    # A refusal that rewrites world files is not whole-world byte preservation, whatever the later steps did.
    row["findings"] += refusal_findings(row.get("refusals", []))
    row["status"] = "FAILED" if row["findings"] else "PASS"
    return row


def family(run):
    cases = run.args.case or FAMILIES[run.args.family]
    native.require(set(cases) <= set(FAMILIES[run.args.family]), "A selected case is not in the requested family")
    base = seed(run)
    report = dict(family=run.args.family, partial=bool(run.args.case), root2=dict(commit=ROOT2_COMMIT, sha256=ROOT2_SHA256),
                  seed=dict(ledger_sha256=base["ledger_sha256"], record_digests=base["digests"], files=len(base["hashes"]),
                            backup=base["archive"]), cases=[])
    for case in cases:
        row = old_jar_case(run, base) if case == "OLD_JAR_REFUSAL" else upgrade_case(run, case, base)
        report["cases"].append(row)
        native.write_json(run.evidence / "results.json", dict(status="IN_PROGRESS", **report))
        print(f"{case}: {row['status']}" + ("" if not row["findings"] else " " + "; ".join(row["findings"])[:600]), flush=True)
    # NOT_COVERED is not a pass: the family reports FAILED and the case row says why.
    report["status"] = "PASS" if all(row["status"] == "PASS" for row in report["cases"]) else "FAILED"
    return report


def main():
    parser = native.parser(__doc__)
    parser.add_argument("--family", required=True, choices=FAMILIES)
    parser.add_argument("--case", action="append", choices=[case for cases in FAMILIES.values() for case in cases])
    parser.add_argument("--root2-jar", required=True, type=Path, help="Candidate C3's JAR; its SHA-256 is pinned here")
    return native.execute(parser.parse_args(), family)


if __name__ == "__main__":
    raise SystemExit(main())
