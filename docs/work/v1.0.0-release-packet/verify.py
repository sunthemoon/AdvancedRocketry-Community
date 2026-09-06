"""Verify the development handoff, including untracked Markdown; never approve Gates."""
from pathlib import Path
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from scripts.validate_repository import markdown_link_errors
from scripts.validate_release_checksums import parse_checksum_text

PACKET = ROOT / "docs/releases/v1.0.0"
OUT = Path(__file__).resolve().parent
REQUIRED_FILES = frozenset({
    "RELEASE-EVIDENCE.md", "GATE-STATUS.md", "TEST-REPORT.md", "MANUAL-TEST.md",
    "MIGRATION-REPORT.md", "RECOVERY-MATRIX.md", "MULTIPLAYER-REPORT.md",
    "VISUAL-VALIDATION-REPORT.md", "PERFORMANCE.md", "SECURITY-REVIEW.md",
    "KNOWN-ISSUES.md", "evidence-index.json",
})


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify():
    assert all((PACKET / name).is_file() for name in REQUIRED_FILES)
    index = json.loads((PACKET / "evidence-index.json").read_text(encoding="utf-8"))
    assert index["schema_version"] == 1 and index["status"] == "IN_PROGRESS"
    assert set(index["candidate"]) == {"commit", "artifact_sha256", "tag"}
    assert all(value is None for value in index["candidate"].values())
    rows = index["reports"]
    assert rows
    assert len({row["id"] for row in rows}) == len(rows)
    for row in rows:
        path = (ROOT / row["path"]).resolve()
        assert path.is_relative_to(ROOT) and path.is_file()
        assert sha(path) == row["report_sha256"], row["id"]
        assert row["artifact_sha256"] in path.read_text(encoding="utf-8"), row["id"]
        assert row["role"] == "supporting_development"
    snapshot = index["development_snapshot"]
    observed = json.loads((ROOT / snapshot["results"]).read_text(encoding="utf-8"))
    assert observed["artifact_sha256"] == snapshot["artifact_sha256"]
    assert observed["artifact_bytes"] == snapshot["artifact_bytes"]
    source = json.loads((ROOT / snapshot["source_inventory"]).read_text(encoding="utf-8"))
    assert source["artifact_sha256"] == snapshot["artifact_sha256"]
    for row in source["files"]:
        assert sha(ROOT / row["path"]) == row["sha256"], row["path"]
    artifact = ROOT / "build/libs/advancedrocketry-community-1.20.1-1.0.0-dev.jar"
    assert sha(artifact) == snapshot["artifact_sha256"]
    paths = sorted(PACKET.glob("*.md")) + [ROOT / "README.md", ROOT / "DOCUMENT-INDEX.md",
                                        ROOT / "docs/status/GATE_STATUS.md"]
    errors, checked = markdown_link_errors(ROOT, paths)
    assert not errors, errors
    entries, errors = parse_checksum_text((PACKET / "checksums.txt").read_text(encoding="utf-8"))
    assert not errors, errors
    expected = {p.relative_to(ROOT).as_posix() for p in PACKET.iterdir()
                if p.is_file() and p.name != "checksums.txt"}
    assert {e.path for e in entries} == expected
    for entry in entries:
        assert sha(ROOT / entry.path) == entry.sha256, entry.path
    state = (ROOT / "docs/status/GATE_STATUS.md").read_text(encoding="utf-8")
    assert "overall: IN_PROGRESS" in state and "G9: IN_PROGRESS" in state
    result = dict(status="PASS_DEVELOPMENT_HANDOFF_ONLY", reports=len(rows),
                  markdown_links_checked=checked, packet_checksums=len(entries),
                  source_hashes=len(source["files"]), artifact_sha256=sha(artifact),
                  release_approved=False)
    return result


if __name__ == "__main__":
    result = verify()
    (OUT / "verification.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
