"""Check this diagnostic slice's evidence; not a release approval."""
from pathlib import Path
import hashlib
import json
import struct
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT))
from scripts.run_dedicated_server_smoke import scan_log


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


inventory = json.loads((HERE / "source-inventory.json").read_text())
assert len(inventory["files"]) == 736
for item in inventory["files"]:
    assert sha(ROOT / item["path"]) == item["sha256"], item["path"]
assert inventory["changed_from_quote"] == [
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketTransferService.java"
]
before = ET.parse(HERE / "before.xml").getroot()
assert int(before.attrib["tests"]) == 2 and int(before.attrib["failures"]) == 1
counts = dict(tests=0, failures=0, errors=0, skipped=0)
results = list((HERE / "java-results").glob("TEST-*.xml"))
assert len(results) == 74
for path in results:
    suite = ET.parse(path).getroot()
    for key in counts:
        counts[key] += int(suite.attrib.get(key, 0))
assert counts == dict(tests=387, failures=0, errors=0, skipped=0), counts
native = (HERE / "gametest-native.log").read_bytes().splitlines()
assert any(b"All 44 required tests passed" in line for line in native)
assert any(b"/INFO]" in line and b"reason=countdown_cancelled" in line for line in native)
assert any(b"/WARN]" in line and b"reason=destination_pad_blocked" in line for line in native)
summary = json.loads((HERE / "packaged/summary.json").read_text())
assert summary["status"] == "PASS" and not summary["cleanup_errors"]
assert not summary["authentication_tested"]
assert {p["kind"] for p in summary["processes"]} == {"client", "server", "restart"}
for process in summary["processes"]:
    assert process["exit_code"] == 0
    log = HERE / "packaged" / process["full_log_file"]
    assert sha(log) == process["full_log_sha256"]
    assert not scan_log(log.read_text(encoding="utf-8").splitlines()), log
    for entry in process["native_logs"]:
        assert sha(HERE / "packaged" / entry["file"]) == entry["sha256"]
for action in ("launch", "cancel"):
    receipt = summary[action + "_receipt"]
    assert "action=" + action.upper() in receipt and "code=SUCCESS" in receipt
    assert summary["owner"] in receipt and summary["entity"] in receipt
assert "[Server thread/INFO]" in summary["return_diagnostic"]
assert "reason=countdown_cancelled fuel=1000" in summary["return_diagnostic"]
for phase in ("before", "after"):
    report = summary["report_" + phase + "_restart"]
    assert summary["entity"] in report
    assert "state=FUELED fuel=1000 capacity=1000 passengers=0 transfer=none" in report
assert len(summary["screenshots"]) == 4
for shot in summary["screenshots"]:
    path = HERE / "packaged" / shot["file"]
    raw = path.read_bytes()
    assert sha(path) == shot["sha256"]
    assert len(raw) == shot["bytes"] and raw[:8] == b"\x89PNG\r\n\x1a\n"
    assert list(struct.unpack(">II", raw[16:24])) == shot["client_size"]
jar = ROOT / "build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar"
assert sha(jar) == inventory["artifact_sha256"] == summary["artifact_sha256"]
checksums = 0
for line in (HERE.parent / "checksums.txt").read_text().splitlines():
    expected, name = line.split("  ", 1)
    path = (HERE.parent / name).resolve()
    assert path.is_relative_to(HERE.parent) and sha(path) == expected, name
    checksums += 1
print(json.dumps(dict(status="PASS_SCOPED_AUDIT", source_inputs=736,
    java_tests=counts["tests"], game_tests=44, native_processes=3,
    screenshots=4, checksums=checksums, artifact_sha256=sha(jar),
    full_visual_or_release_approval=False)))
