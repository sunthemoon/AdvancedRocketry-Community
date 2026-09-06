"""Verify completed local CI receipts against source, artifacts and raw logs."""
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(r"D:\GitHub\AdvancedRocketry-Community")
OUT = Path(r"C:\Users\Administrator\AppData\Local\Temp\arce-v100-ci")
ARTIFACT_SHA = "cf077ef750149f6b957ac4e8dc8a7da8e5cb91eafbe80703859157aac34a49b8"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


source = json.loads((OUT / "checks/ci-source-inventory.json").read_text(encoding="utf-8"))
for row in source["files"]:
    assert sha(ROOT / row["path"]) == row["sha256"], row["path"]
discovery = json.loads((OUT / "checks/python-discovery.json").read_text(encoding="utf-8"))
selected, excluded = set(discovery["selected_tests"]), set(discovery["excluded_tests"])
assert len(selected) == 569 and len(excluded) == 243 and not selected & excluded
assert len(selected | excluded) == discovery["total"] == 812
partitions = []
for path, expected, skips in (
    (OUT.parent / "arce-v100-compatibility/checks/bootstrap-full-tests.txt", 95, 0),
    (OUT / "checks/repository-unit-normalized-temp.txt", 148, 0),
    (OUT / "checks/python-remaining.txt", 569, 4),
):
    text = path.read_text(encoding="utf-8")
    match = re.search(r"Ran (\d+) tests in ([0-9.]+)s", text)
    assert match and int(match.group(1)) == expected
    assert text.strip().endswith("OK" if skips == 0 else f"OK (skipped={skips})")
    partitions.append({"log": path.name, "sha256": sha(path), "ran": int(match.group(1)),
                       "seconds": float(match.group(2)), "skipped": skips})
audit = {"source_mode": "development_worktree", "source_inputs_unchanged": len(source["files"]),
         "python_disjoint_cases": len(selected | excluded),
         "python_passed": sum(row["ran"] - row["skipped"] for row in partitions),
         "python_existing_skips": sum(row["skipped"] for row in partitions),
         "python_partitions": partitions, "jobs": [], "packaged_processes": []}
for job in ("baseline", "satellite-acceptance", "latest-compatibility"):
    directory = OUT / (job + "-local")
    summary = json.loads((directory / "summary.json").read_text(encoding="utf-8"))
    assert summary["completed_at"] and summary["workflow_sha256"] == sha(ROOT / ".github/workflows/forge-bootstrap.yml")
    failures = []
    for command in summary["commands"]:
        if command["status"] == "NOT_APPLICABLE_WINDOWS":
            assert command["workflow_argv"] == ["chmod", "+x", "./gradlew"]
            continue
        assert sha(directory / command["log"]) == command["log_sha256"]
        if command["exit_code"] != 0:
            failures.append(command["step"])
    expected_failures = ["Require a clean DataGen worktree", "Reject untracked DataGen outputs"] if job == "baseline" else []
    assert failures == expected_failures
    assert summary["failed_commands"] == len(failures)
    for entry in summary["upload_file_inventory"]:
        if entry["path"].endswith(".jar"):
            if not entry["path"].endswith("-sources.jar"):
                assert entry["sha256"] == ARTIFACT_SHA and entry["bytes"] == 1238476
        else:
            output = directory / "outputs" / entry["path"]
            assert output.stat().st_size == entry["bytes"] and sha(output) == entry["sha256"]
    identity = json.loads((directory / "outputs/build/release-evidence/build-identity.json").read_text(encoding="utf-8"))
    assert identity["properties_sha256"] == sha(ROOT / "gradle.properties")
    assert identity["build_version"] == "1.20.1-1.0.0-dev"
    env_lines = (directory / "github-env.txt").read_text(encoding="utf-8").splitlines()
    environment = dict(line.split("=", 1) for line in env_lines)
    assert environment == {"ARCE_BUILD_VERSION": identity["build_version"], "ARCE_ARTIFACT": identity["artifact"], "ARCE_SOURCES": identity["sources"]}
    suites = [ET.parse(path).getroot() for path in (directory / "outputs/build/test-results/test").glob("TEST-*.xml")]
    counts = {key: sum(int(suite.attrib.get(key, "0")) for suite in suites) for key in ("tests", "failures", "errors", "skipped")}
    assert len(suites) == 70 and counts == {"tests": 362, "failures": 0, "errors": 0, "skipped": 0}
    if job != "satellite-acceptance":
        game_log = (directory / "outputs/build/gametest/logs/latest.log").read_text(encoding="utf-8", errors="replace")
        assert re.search(r"All 44 required tests passed", game_log)
    packaged_reports = list((directory / "outputs/build").glob("*/evidence/summary.json"))
    for report_path in packaged_reports:
        report = json.loads(report_path.read_text(encoding="utf-8"))
        assert report["artifact_sha256"] == ARTIFACT_SHA
        for cycle in walk(report):
            if "full_log_file" not in cycle:
                continue
            path = report_path.parent / cycle["full_log_file"]
            assert path.name == cycle["full_log_file"] and sha(path) == cycle["full_log_sha256"]
            if cycle["exit_code"] != 0:
                assert cycle.get("termination") == "process_kill" and report.get("durable_save_before_kill") is True
            audit["packaged_processes"].append({"job": job, "report": report_path.relative_to(directory).as_posix(),
                "log": cycle["full_log_file"], "sha256": cycle["full_log_sha256"], "exit_code": cycle["exit_code"],
                "termination": cycle.get("termination", "normal_exit")})
    audit["jobs"].append({"job": job, "started_at": summary["started_at"], "completed_at": summary["completed_at"],
        "commands": len(summary["commands"]), "retained_failures": failures, "java_suites": len(suites), "java": counts,
        "game_tests": 0 if job == "satellite-acceptance" else 44, "packaged_reports": len(packaged_reports),
        "upload_inventory_files": len(summary["upload_file_inventory"]), "jar_sha256": ARTIFACT_SHA})
(OUT / "checks/final-audit.json").write_text(json.dumps(audit, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"jobs": audit["jobs"], "packaged_processes": len(audit["packaged_processes"]),
                  "source_inputs": len(source["files"]), "python_unique_cases": 812}, indent=2))
