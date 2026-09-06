"""Collect this bounded logout regression's development-tree evidence once."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[2]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


previous = json.loads((ROOT / "docs/work/v1.0.0-passenger-readiness/verification/source-inventory.json").read_text())
paths = sorted(row["path"] for row in previous["files"])
artifact = ROOT / "build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar"
inventory = {
    "source_state": "development_worktree",
    "artifact_sha256": digest(artifact),
    "artifact_bytes": artifact.stat().st_size,
    "files": [dict(path=p, sha256=digest(ROOT / p), size=(ROOT / p).stat().st_size) for p in paths],
}
(OUT / "source-inventory.json").write_text(json.dumps(inventory, indent=2) + "\n", encoding="utf-8")
results = OUT / "java-results"
results.mkdir(exist_ok=False)
counts = dict(tests=0, failures=0, errors=0, skipped=0, suites=0)
for path in sorted((ROOT / "build/test-results/test").glob("TEST-*.xml")):
    shutil.copy2(path, results / path.name)
    suite = ET.parse(path).getroot()
    counts["suites"] += 1
    for key in ("tests", "failures", "errors", "skipped"):
        counts[key] += int(suite.attrib[key])
native = (OUT / "after-gametest-native.log").read_bytes()
game_pass = b"All 51 required tests passed" in native
commands = []
for name, args in [
    ("diff-check", ["git", "diff", "--check"]),
    ("generated-diff", ["git", "diff", "--exit-code", "--", "src/generated"]),
    ("worktree-diff", ["git", "diff", "--exit-code"]),
]:
    result = subprocess.run(args, cwd=ROOT, capture_output=True)
    (OUT / (name + ".txt")).write_bytes(result.stdout + result.stderr)
    commands.append(dict(command=args, exit_code=result.returncode))
summary = dict(java=counts, gametest_required_pass=game_pass, commands=commands,
               artifact_sha256=inventory["artifact_sha256"], artifact_bytes=inventory["artifact_bytes"],
               source_files=len(paths), native_clients="NOT_RUN", restart="NOT_RUN",
               soak="DEFERRED_BY_OWNER", release_gates="IN_PROGRESS")
(OUT / "build-results.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
assert counts["tests"] > 0 and counts["failures"] == counts["errors"] == counts["skipped"] == 0
assert game_pass
assert commands[0]["exit_code"] == commands[1]["exit_code"] == 0
