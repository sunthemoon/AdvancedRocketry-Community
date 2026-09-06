"""Verify retained observations, not release acceptance or intended UI input."""
from pathlib import Path
import hashlib
import json
import struct

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
ATTEMPT = HERE / "attempt-4"
EXPECTED_JAR = "fb8539b600da54da3f4e5453af07e285e7dec1c17ace12f1e52153137be71e04"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    jar = ROOT / "build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar"
    assert digest(jar) == EXPECTED_JAR
    inventory = json.loads((ROOT / "docs/work/v1.0.0-flight-console/verification/source-inventory.json").read_text())
    for item in inventory["files"]:
        path = ROOT / item["path"]
        assert path.stat().st_size == item["size"], item["path"]
        assert digest(path) == item["sha256"], item["path"]
    checkpoint = json.loads((ATTEMPT / "checkpoint-0033.json").read_text())
    assert checkpoint["artifact_sha256"] == EXPECTED_JAR
    records = checkpoint["screenshots"] + json.loads((ATTEMPT / "ui-probe-records.json").read_text())
    for record in records:
        path = ATTEMPT / record["file"]
        assert path.stat().st_size == record["bytes"]
        assert digest(path) == record["sha256"]
        raw = path.read_bytes()
        assert raw[:8] == b"\x89PNG\r\n\x1a\n"
        assert list(struct.unpack(">II", raw[16:24])) == record["client_size"]
    lines = (ATTEMPT / "server-full.txt").read_bytes().splitlines()
    intents = [line for line in lines if b"ARCE_FLIGHT_INTENT " in line]
    alpha = b"station=6283a6ae-ff15-4e02-8085-1d677b11970c"
    beta = b"station=68f9268b-e709-4e50-baf2-15663a8fbd9c"
    for action in (b"action=LAUNCH", b"action=CANCEL"):
        assert any(alpha in line and action in line and b"code=SUCCESS" in line for line in intents)
    assert any(beta in line and b"action=LAUNCH" in line and b"code=SUCCESS" in line for line in intents)
    assert not any(beta in line and b"action=CANCEL" in line for line in intents)
    cancel_index = next(i for i, line in enumerate(lines) if alpha in line and b"action=CANCEL" in line)
    assert any(b"ARCE_RELEASE_TEST_FLIGHT_REPORT " in line and b"state=FUELED fuel=1000" in line
               and b"transfer=none" in line for line in lines[cancel_index + 1:])
    assert any(b"transfer=9a109d31-0727-4be6-a12b-fb227115b74a" in line
               and b"event=landing_complete" in line and b"fuel_after=670 required=330" in line for line in lines)
    assert any(b"reason=countdown_cancelled fuel=1000" in line and b"WARN" in line for line in lines)
    assert b"NVIDIA GeForce RTX 3070 Laptop GPU" in (ATTEMPT / "client-full.txt").read_bytes()
    assert b"Stopping!" in (ATTEMPT / "client-full.txt").read_bytes()
    assert b"Stopping server" in (ATTEMPT / "server-latest.log").read_bytes()
    receipt = json.loads((ATTEMPT / "postmortem-receipt.json").read_text())
    assert receipt["driver_exit_code"] == 1
    assert receipt["server_exit_code_from_terminal"] == 0
    assert receipt["owned_pids_absent"] == [23512, 26444]
    for item in receipt["archived_logs"]:
        assert digest(ATTEMPT / item["file"]) == item["sha256"]
    checked = 0
    for line in (HERE.parent / "checksums.txt").read_text().splitlines():
        expected, name = line.split("  ", 1)
        path = (HERE.parent / name).resolve()
        assert path.is_relative_to(HERE.parent)
        assert digest(path) == expected, name
        checked += 1
    print(json.dumps({"status": "PASS_SCOPED_EVIDENCE_AUDIT", "source_inputs": len(inventory["files"]),
                      "screenshots": len(records), "checksums": checked,
                      "ui_driver_status": "FAILED_STOP_AUDIT", "release_gate_approval": False}))


if __name__ == "__main__":
    main()
