"""Read-only repeat audit of the completed packaged-consumer evidence."""
import hashlib
import json
import re
import socket
from pathlib import Path

BASE = Path(__file__).resolve().parent
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
summary = json.loads((BASE / "summary.json").read_text(encoding="utf-8"))
assert len(summary["cycles"]) == 2
for artifact in summary["artifacts"]:
    assert digest(Path(artifact["path"])) == artifact["sha256"] == digest(Path(artifact["installed"]))
for cycle in summary["cycles"]:
    assert cycle["exit_code"] == 0
    assert digest(BASE / cycle["full_log"]) == cycle["full_log_sha256"]
    assert digest(BASE / cycle["debug_log"]) == cycle["debug_log_sha256"]
    assert len(cycle["registration_lines"]) == 1
    assert cycle["status_mod_versions"]["advancedrocketrycommunity"] == "1.20.1-1.3.0-dev"
    assert cycle["status_mod_versions"]["arce_adapter_test"] == "1.0.0"
    for log_name in (cycle["full_log"], cycle["debug_log"]):
        text = (BASE / log_name).read_text(encoding="utf-8")
        assert not re.search(r"\[[^\]]+/(?:ERROR|FATAL)\]", text)
        assert not re.search(r"NoSuchMethodError|AbstractMethodError|NoClassDefFoundError|ClassNotFoundException|IncompatibleClassChangeError|LinkageError", text)
    debug = (BASE / cycle["debug_log"]).read_text(encoding="utf-8")
    assert "mod:arce_adapter_test" in debug
    for artifact in summary["artifacts"]:
        assert "Loading mod file " + artifact["installed"] in debug
try:
    connection = socket.create_connection(("127.0.0.1", summary["server_port"]), timeout=1)
except OSError:
    pass
else:
    connection.close()
    raise AssertionError("Test server port remains open")
print("PASS: two artifact identities, process exits, source/registration/status receipts, full logs and closed port")
