"""D7 binds the owner's decisions to the unchanged reviewed record cores."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[1]
AUTHORITY = "docs/work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261104.md"
REVIEWED_COMMIT = "521cb9b95"
STAND_IN_AUTHORITY = "docs/work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261108.md"
STAND_IN_REVIEWED_COMMIT = "e07b09627"
BATCHES = (
    "c18d-k1", "c18d-k2", "c18d-k3", "c18d-k4", "c18d-k6", "c18d-w244",
    "w246-flat-textures", "w246-rebound-item-models", "w249-motor-models",
    "w255-bipropellant-motor-models", "w256-saw-model", "w258-nuclear-motor",
    "w260-fuel-tanks", "w261-landing-pad", "w265-laser-gun", "w265-rebound-item-model",
    "c16d-acquisition", "c17a-fluid-fuel-loader", "c18b-jackhammer", "c18b-laser-gun",
    "c18d-new-art", "c18d-presentation", "cargo-tank-tag", "circuit-plates", "control-board",
    "gas-charge-pad-new-art", "item-io-board", "liquid-io-board", "tracking-circuit-and-rebalance",
    "w53-art-redraw",
)
SPEC = importlib.util.spec_from_file_location("d7_importer", ROOT / "tools/import/import_v010_assets.py")
IMPORTER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(IMPORTER)


class D7ApprovalTests(unittest.TestCase):
    def test_exact_cohort_has_bound_owner_approvals_and_unchanged_cores(self):
        self.assertEqual(30, len(set(BATCHES)))
        authority = (ROOT / AUTHORITY).read_text(encoding="utf-8")
        for batch in BATCHES:
            relative = f"docs/provenance/v1.8.0-{batch}.json"
            with self.subTest(record=relative):
                original = json.loads(subprocess.check_output(
                    ["git", "show", f"{REVIEWED_COMMIT}:{relative}"], cwd=ROOT))
                record = json.loads((ROOT / relative).read_bytes())
                core = {key: value for key, value in record.items() if key != "review"}
                self.assertEqual({key: value for key, value in original.items() if key != "review"}, core)
                review = record["review"]
                digest = hashlib.sha256(IMPORTER.canonical_json(core)).hexdigest()
                self.assertEqual(review, IMPORTER.validate_review(review, digest))
                self.assertEqual("PENDING_HUMAN_REVIEW", original["review"]["status"])
                self.assertEqual("APPROVED", review["status"])
                self.assertEqual("sunthemoon", review["reviewer"])
                self.assertEqual("2026-11-04", review["reviewed_at"])
                self.assertEqual([], review["findings"])
                self.assertIn(AUTHORITY, review["basis"])
                self.assertIn(REVIEWED_COMMIT, review["basis"])
                self.assertIn("D7", review["basis"])
                self.assertIn("v1.8.0-" + batch if not batch.startswith("c18d-k") else "-" + batch[5:], authority)

    def test_both_stand_in_records_have_bound_owner_approvals_and_unchanged_cores(self):
        authority = (ROOT / STAND_IN_AUTHORITY).read_text(encoding="utf-8")
        for batch in ("w275", "w277"):
            relative = f"docs/provenance/v1.8.0-{batch}-stand-in-art.json"
            with self.subTest(record=relative):
                original = json.loads(subprocess.check_output(
                    ["git", "show", f"{STAND_IN_REVIEWED_COMMIT}:{relative}"], cwd=ROOT))
                record = json.loads((ROOT / relative).read_bytes())
                core = {key: value for key, value in record.items() if key != "review"}
                self.assertEqual({key: value for key, value in original.items() if key != "review"}, core)
                digest = hashlib.sha256(IMPORTER.canonical_json(core)).hexdigest()
                review = record["review"]
                self.assertEqual("PENDING_HUMAN_REVIEW", original["review"]["status"])
                self.assertEqual(original["review"]["content_digest"], digest)
                self.assertEqual(review, IMPORTER.validate_review(review, digest))
                self.assertEqual({
                    "status": "APPROVED",
                    "content_digest": digest,
                    "reviewer": "sunthemoon",
                    "reviewed_at": "2026-11-08",
                    "findings": [],
                    "basis": (
                        "Owner visual and originality approval (D7, R383): " + STAND_IN_AUTHORITY +
                        ". Applies to this record in full as it stood at " + STAND_IN_REVIEWED_COMMIT +
                        ", not later replacements or release Gates."
                    ),
                }, review)
                self.assertIn(f"v1.8.0-{batch}-stand-in-art", authority)


if __name__ == "__main__":
    unittest.main()
