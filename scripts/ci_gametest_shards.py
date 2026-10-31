#!/usr/bin/env python3
"""Partition GameTests by batch and fail closed on incomplete shard evidence.

The CI-only Forge observer enumerates the actual registry, including generators,
before the existing batch selector runs. It reports terminal test callbacks without
replacing vanilla's logging. No source-code regex is an inventory oracle.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import sys
import zipfile
from collections import Counter
from pathlib import Path, PurePosixPath

if __package__:
    from . import check_gametest_log as logcheck
else:
    import check_gametest_log as logcheck

SHARD_COUNT = 4
RUNTIME_SETS = ("main", "adapterTest", "ciGameTest")
ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "scripts/gametest_expected_log.json"
# The manifest explicitly describes this unscoped warning as once per server
# start: it can fall in startup OR the first batch, depending on tick timing.
PER_PROCESS_CEILINGS = frozenset({"w44-first-moon-landing-startup-baseline"})


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, document: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def shard_for(batch: str) -> int:
    if not isinstance(batch, str) or re.fullmatch(r"[A-Za-z0-9_.-]+", batch) is None:
        raise ValueError(f"unsafe batch name: {batch!r}")
    return int.from_bytes(hashlib.sha256(batch.encode("utf-8")).digest()[:4], "big") % SHARD_COUNT


def identities(tests: list[dict]) -> set[str]:
    if not isinstance(tests, list):
        raise ValueError("test inventory must be a list")
    keys = []
    for test in tests:
        if not isinstance(test, dict) or not all(isinstance(test.get(k), str) and test[k]
                                               for k in ("batch", "name", "structure")):
            raise ValueError("test inventory needs batch, name and structure")
        shard_for(test["batch"])
        if type(test.get("required")) is not bool:
            raise ValueError("test inventory needs a required boolean")
        keys.append(json.dumps(test, sort_keys=True))
    if len(set(keys)) != len(keys):
        raise ValueError("duplicate test inventory identity")
    # Reject two definitions with the same externally observable identity too.
    labels = [(t["batch"], t["name"], t["structure"]) for t in tests]
    if len(set(labels)) != len(labels):
        raise ValueError("conflicting test inventory identity")
    return set(keys)


def partition(inventory: list[dict]) -> dict[int, list[dict]]:
    identities(inventory)
    if not inventory:
        raise ValueError("empty test inventory")
    return {i: sorted((t for t in inventory if shard_for(t["batch"]) == i),
                      key=lambda t: json.dumps(t, sort_keys=True)) for i in range(SHARD_COUNT)}


def validate_partition(inventory: list[dict], plan: dict[int, list[dict]]) -> None:
    expected = partition(inventory)
    if set(plan) != set(expected) or any(identities(plan[i]) != identities(expected[i]) for i in expected):
        raise ValueError("partition has missing, duplicated or incorrectly assigned batches/tests")


def check_union(reports: list[dict], bundle_sha256: str) -> dict:
    if (len(reports) != SHARD_COUNT or {r.get("shard") for r in reports} != set(range(SHARD_COUNT))
            or any(type(r.get("shard")) is not int for r in reports)):
        raise ValueError("missing or duplicate shard report")
    inventory = reports[0]["inventory"]
    reference = identities(inventory)
    plan = partition(inventory)
    observed = []
    if any(identities(report["inventory"]) != reference for report in reports):
        raise ValueError("shard full inventory differs")
    for report in reports:
        index = report["shard"]
        if (report.get("job_result", "success") != "success" or report.get("native_exit") != 0
                or report.get("complete") is not True or report.get("failed") != []
                or report.get("bundle_sha256") != bundle_sha256):
            raise ValueError(f"shard {index} failed, was cancelled/truncated, or used another artifact")
        if identities(report["executed"]) != identities(plan[index]):
            raise ValueError(f"shard {index} executed tests do not equal its complete partition")
        observed += report["executed"]
    if identities(observed) != reference:
        raise ValueError("executed union is not the full inventory")
    return {"tests": len(inventory), "batches": len({t["batch"] for t in inventory}),
            "shards": {str(i): len(plan[i]) for i in plan}, "bundle_sha256": bundle_sha256}


def slice_manifest(manifest: dict, batches: set[str]) -> dict:
    sliced = copy.deepcopy(manifest)
    entries = []
    allowed = set(batches) | {logcheck.STARTUP_BATCH, logcheck.SHUTDOWN_BATCH}
    for entry in sliced["expectations"]:
        if "counts" in entry:
            entry["counts"] = {b: c for b, c in entry["counts"].items() if b in allowed}
            if not entry["counts"]:
                continue
        elif "batches" in entry:
            entry["batches"] = [b for b in entry["batches"] if b in allowed]
            if not entry["batches"]:
                continue
        entries.append(entry)
    sliced["expectations"] = entries
    return sliced


def check_global_ceilings(manifest: dict, observations: list[dict]) -> None:
    # Startup warnings arise independently for each JVM. Other existing ceilings
    # remain whole-suite ceilings, not SHARD_COUNT times their reviewed value.
    for entry in manifest["expectations"]:
        if ("max" in entry and entry.get("batches") != [logcheck.STARTUP_BATCH]
                and entry["id"] not in PER_PROCESS_CEILINGS):
            total = sum(counts.get(entry["id"], 0) for counts in observations)
            if total > entry["max"]:
                raise ValueError(f"whole-suite warning ceiling {entry['id']}: {total} > {entry['max']}")


def seal(bundle: Path, commit: str) -> None:
    if re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        raise ValueError("source commit must be a full SHA")
    files = [f"{name}.jar" for name in RUNTIME_SETS] + ["release.jar"]
    write_json(bundle / "identity.json", {"schema": 1, "commit": commit,
                                         "files": {name: sha256(bundle / name) for name in files}})


def verify_bundle(bundle: Path, commit: str) -> str:
    identity = read_json(bundle / "identity.json")
    expected = {f"{name}.jar" for name in RUNTIME_SETS} | {"release.jar"}
    if identity.get("schema") != 1 or identity.get("commit") != commit or set(identity["files"]) != expected:
        raise ValueError("bundle source/manifest mismatch")
    for name, digest in identity["files"].items():
        if sha256(bundle / name) != digest:
            raise ValueError(f"bundle artifact identity mismatch: {name}")
    return sha256(bundle / "identity.json")


def jar_entries(path: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(path) as jar:
        entries = {}
        for item in jar.infolist():
            name = PurePosixPath(item.filename)
            if (name.is_absolute() or ".." in name.parts or "\\" in item.filename or ":" in item.filename
                    or (item.external_attr >> 16) & 0o170000 == 0o120000):
                raise ValueError("unsafe runtime JAR entry")
            if item.is_dir():
                continue
            if item.filename in entries:
                raise ValueError("duplicate runtime JAR entry")
            entries[item.filename] = jar.read(item)
        return entries


def restore(bundle: Path, root: Path, commit: str) -> None:
    verify_bundle(bundle, commit)
    for name in RUNTIME_SETS:
        target = root / f"build/classes/java/{name}"
        resources = root / f"build/resources/{name}"
        for directory in (target, resources):
            if directory.exists() and any(directory.rglob("*")):
                raise ValueError(f"runtime destination is not empty: {directory}")
        for entry, data in jar_entries(bundle / f"{name}.jar").items():
            output = target / entry
            if not output.resolve().is_relative_to(root.resolve()):
                raise ValueError("runtime destination escapes the workspace")
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(data)
        resources.mkdir(parents=True, exist_ok=True)
    verify_runtime(bundle, root, commit)


def verify_runtime(bundle: Path, root: Path, commit: str) -> str:
    digest = verify_bundle(bundle, commit)
    for name in RUNTIME_SETS:
        target = root / f"build/classes/java/{name}"
        observed = {p.relative_to(target).as_posix(): p.read_bytes() for p in target.rglob("*") if p.is_file()}
        if observed != jar_entries(bundle / f"{name}.jar"):
            raise ValueError(f"runtime contents differ from the built JAR: {name}")
        if any(p.is_file() for p in (root / f"build/resources/{name}").rglob("*")):
            raise ValueError(f"unexpected runtime resource override: {name}")
    return digest


def batch_counts(text: str) -> Counter[str]:
    counts = Counter()
    seen = set()
    for line in text.splitlines():
        header = logcheck.HEADER.match(line)
        if header and header["logger"] == logcheck.BATCH_LOGGER:
            match = re.fullmatch(r"Running test batch '(.+):(\d+)' \((\d+) tests\)\.\.\.", header["message"])
            if match:
                batch, segment, count = match.groups()
                if (batch, segment) in seen:
                    raise ValueError("duplicate executed batch segment")
                seen.add((batch, segment))
                counts[batch] += int(count)
    return counts


def check_shard(folder: Path, manifest_path: Path) -> tuple[dict, Counter[str]]:
    report = read_json(folder / "result.json")
    report["native_exit"] = int((folder / "native-exit.txt").read_text().strip())
    if (report.get("schema") != 1 or report.get("complete") is not True
            or report.get("failed") != [] or report["native_exit"] != 0):
        raise ValueError("failed or incomplete native shard result")
    index = report["shard"]
    if type(index) is not int or index not in range(SHARD_COUNT):
        raise ValueError("invalid shard number")
    plan = partition(report["inventory"])
    if identities(report["executed"]) != identities(plan[index]):
        raise ValueError(f"shard {index} executed inventory mismatch")
    run = logcheck.load_run(folder / "logs/latest.log")
    if run.refusal:
        raise ValueError(run.refusal)
    parsed = logcheck.parse_log(run.text)
    expected = Counter(t["batch"] for t in plan[index])
    if batch_counts(run.text) != expected or parsed.completed_tests != len(plan[index]):
        raise ValueError(f"shard {index} batch/test log counts differ from inventory")
    manifest = read_json(manifest_path)
    full_batches = {t["batch"] for t in report["inventory"]} | {"<startup>", "<shutdown>"}
    declared = {b for e in manifest["expectations"] for b in e.get("counts", {})}
    if declared - full_batches:
        raise ValueError(f"manifest names absent batches: {sorted(declared - full_batches)}")
    sliced_path = folder / "expected-log.json"
    write_json(sliced_path, slice_manifest(manifest, set(expected)))
    # Invoke the unchanged checker over the exact sliced manifest, not a new oracle.
    if logcheck.main([str(folder / "logs/latest.log"), "--manifest", str(sliced_path)]) != 0:
        raise ValueError(f"shard {index} GameTest log check failed")
    expectations = logcheck.load_manifest(sliced_path)
    counts = Counter()
    for entry in parsed.entries:
        for expectation in expectations:
            if expectation.describes(entry) and expectation.allows_batch(entry.batch):
                counts[expectation.id] += 1
    return report, counts


def dry_run(log: Path, output: Path) -> dict:
    run = logcheck.load_run(log)
    parsed = logcheck.parse_log(run.text)
    if run.refusal or parsed.problems or not (parsed.launched and parsed.shutdown and parsed.passed_tests
                                             and parsed.passed_tests == parsed.completed_tests):
        raise ValueError("dry run needs a complete successful retained full log")
    counts = batch_counts(run.text)
    if sum(counts.values()) != parsed.completed_tests:
        raise ValueError("retained batch counts do not equal completed tests")
    # Historical INFO logs have counts, not per-test success identities. Preserve
    # that distinction: these ordinal witnesses are NOT claimed as named tests.
    inventory = [{"batch": batch, "name": f"recorded-ordinal-{i}", "structure": "recorded:unknown",
                  "required": True} for batch, count in counts.items() for i in range(count)]
    plan = partition(inventory)
    validate_partition(inventory, plan)
    reports = [{"shard": i, "inventory": inventory, "executed": tests, "failed": [], "complete": True,
                "native_exit": 0, "bundle_sha256": "replay"} for i, tests in plan.items()]
    result = check_union(reports, "replay")
    result.update({"mode": "retained-batch-count-replay-not-native-shards", "log": str(log),
                   "log_sha256": sha256(log), "partition": {str(i): sorted({t['batch'] for t in tests})
                                                              for i, tests in plan.items()}})
    write_json(output, result)
    return result


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("seal", "restore", "verify-runtime"):
        cmd = commands.add_parser(name)
        cmd.add_argument("--bundle", type=Path, required=True)
        cmd.add_argument("--commit", required=True)
        cmd.add_argument("--root", type=Path, default=ROOT)
    for name in ("check-shard", "union"):
        cmd = commands.add_parser(name)
        cmd.add_argument("--evidence", type=Path, required=True)
        cmd.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
        if name == "union":
            cmd.add_argument("--bundle", type=Path, required=True)
            cmd.add_argument("--commit", required=True)
            cmd.add_argument("--job-result", required=True)
    cmd = commands.add_parser("dry-run")
    cmd.add_argument("--log", type=Path, required=True)
    cmd.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.command in ("seal", "restore", "verify-runtime"):
            function = {"seal": seal, "restore": restore, "verify-runtime": verify_runtime}[args.command]
            result = function(args.bundle, args.commit) if args.command == "seal" else function(
                args.bundle, args.root, args.commit)
        elif args.command == "check-shard":
            result = check_shard(args.evidence, args.manifest)[0]["shard"]
        elif args.command == "dry-run":
            result = dry_run(args.log, args.output)
        else:
            if args.job_result != "success":
                raise ValueError(f"matrix job result: {args.job_result}")
            digest = verify_bundle(args.bundle, args.commit)
            reports, counts = [], []
            for i in range(SHARD_COUNT):
                report, observed = check_shard(args.evidence / f"shard-{i}", args.manifest)
                if report["shard"] != i:
                    raise ValueError("shard artifact is mislabelled")
                reports.append(report)
                counts.append(observed)
            check_global_ceilings(read_json(args.manifest), counts)
            result = check_union(reports, digest)
            write_json(args.evidence / "union.json", result)
        print(f"[PASS] {args.command}: {json.dumps(result, sort_keys=True)}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as error:
        print(f"[FAIL] {args.command}: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
