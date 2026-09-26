"""Read-only final audit of the five completed local smoke logs."""
import json
import re
import socket
import sys
from pathlib import Path
sys.dont_write_bytecode = True
REPO = Path("D:/GitHub/AdvancedRocketry-Community")
sys.path.insert(0, str(REPO / "scripts"))
import run_dedicated_server_smoke as smoke
BASE = Path(__file__).resolve().parent
logs = []
for path in sorted((BASE / "server").glob("*-full.txt")):
    lines = path.read_text(encoding="utf-8").splitlines()
    findings = smoke.scan_log(lines)
    if findings:
        raise SystemExit("Blocking finding: " + repr(findings))
    logs.append({"file": str(path.relative_to(BASE)), "sha256": smoke.digest_file(path),
        "audit_counts": smoke.log_audit_counts(lines),
        "warnings": [line for line in lines if re.search(r"\bWARN\b", line)]})
if len(logs) != 5:
    raise SystemExit(f"Expected five completed Java logs, got {len(logs)}")
baseline = json.loads((BASE / "baseline-evidence/summary.json").read_text(encoding="utf-8"))
try:
    connection = socket.create_connection(("127.0.0.1", baseline["server_port"]), timeout=1)
except OSError:
    no_listener = True
else:
    connection.close()
    raise SystemExit("Smoke port still has a listener")
scripts = {}
for name in ("run_dedicated_server_smoke.py", "run_v050_rocket_server_smoke.py",
             "inspect_v100_world_upgrade.py", "inspect_celestial_saved_data.py"):
    scripts[name] = smoke.digest_file(REPO / "scripts" / name)
report = {"logs": logs, "blocking_log_findings": 0, "port_no_longer_listening": no_listener,
          "repository_scripts_sha256": scripts}
with (BASE / "log-audit.json").open("x", encoding="utf-8", newline="\n") as stream:
    stream.write(json.dumps(report, indent=2, sort_keys=True) + "\n")
for item in logs:
    print(item["file"], json.dumps(item["audit_counts"], sort_keys=True))
print("Port no longer listening; repository helper SHA256 recorded")

