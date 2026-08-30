from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts.validate_v010_release_evidence import validate_v010_release_evidence


SOURCE_ROOT = Path(__file__).resolve().parents[1]


class ValidateV010ReleaseEvidenceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        shutil.copytree(
            SOURCE_ROOT / "docs/releases/v0.1.0",
            self.root / "docs/releases/v0.1.0",
        )
        provenance = self.root / "docs/provenance/v0.1.0-minimal-content.json"
        provenance.parent.mkdir(parents=True)
        shutil.copyfile(
            SOURCE_ROOT / "docs/provenance/v0.1.0-minimal-content.json",
            provenance,
        )

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write_json(self, relative: str, value: object) -> None:
        (self.root / relative).write_text(
            json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    def test_current_bundle_is_valid(self) -> None:
        errors, details = validate_v010_release_evidence(self.root)
        self.assertEqual([], errors)
        self.assertTrue(details["artifact_ready"])
        self.assertTrue(details["server_ready"])
        self.assertTrue(details["client_ready"])
        self.assertTrue(details["provenance_ready"])
        self.assertTrue(details["checksums_ready"])

    def test_changed_screenshot_is_rejected(self) -> None:
        screenshot = (
            self.root
            / "docs/releases/v0.1.0/evidence/client/screenshots/mods_en_us.png"
        )
        screenshot.write_bytes(screenshot.read_bytes() + b"changed")

        errors, _ = validate_v010_release_evidence(self.root)

        self.assertTrue(any("screenshot" in error and "mismatch" in error for error in errors))
        self.assertTrue(any("checksum mismatch" in error for error in errors))

    def test_server_without_matching_player_cycles_is_rejected(self) -> None:
        relative = "docs/releases/v0.1.0/evidence/dedicated-server/summary.json"
        summary = json.loads((self.root / relative).read_text(encoding="utf-8"))
        summary["manual_player_cycles"] = False
        self.write_json(relative, summary)

        errors, _ = validate_v010_release_evidence(self.root)

        self.assertTrue(any("matching-client two-cycle" in error for error in errors))

    def test_pending_provenance_cannot_reuse_approved_review(self) -> None:
        relative = "docs/provenance/v0.1.0-minimal-content.json"
        record = json.loads((self.root / relative).read_text(encoding="utf-8"))
        record["review"].update(
            status="PENDING_HUMAN_REVIEW", reviewer=None, reviewed_at=None
        )
        self.write_json(relative, record)

        errors, _ = validate_v010_release_evidence(self.root)

        self.assertTrue(any("not cleanly owner-approved" in error for error in errors))


class ValidateV010ReleaseEvidenceCliTests(unittest.TestCase):
    def test_help_runs_with_isolated_python(self) -> None:
        completed = subprocess.run(
            [
                sys.executable,
                "-I",
                "-S",
                str(SOURCE_ROOT / "scripts/validate_v010_release_evidence.py"),
                "--help",
            ],
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="strict",
        )
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("--artifact", completed.stdout)


if __name__ == "__main__":
    unittest.main()
