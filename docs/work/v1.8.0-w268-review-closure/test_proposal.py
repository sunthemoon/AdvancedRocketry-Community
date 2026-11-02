"""Local draft invariants; no central files or scratch worlds are edited."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import unittest

from apply_scratch import apply, ROOT

HERE = Path(__file__).resolve().parent


class ProposalTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = json.loads((HERE / "REVIEW-EVIDENCE.json").read_bytes())["files"]
        cls.changes = json.loads((HERE / "changes.json").read_bytes())

    def test_complete_partition(self):
        self.assertEqual(95, len(self.evidence))
        self.assertEqual(95, len({e["asset"] for e in self.evidence}))
        self.assertEqual({"EXCLUDE": 85, "REVIEW": 10}, Counter(e["proposed"] for e in self.evidence))
        self.assertEqual(8, len({e["group"] for e in self.evidence}))

    def test_terminal_rules_and_exact_scope(self):
        changed = set()
        for change in self.changes["asset_plan"]:
            self.assertEqual("REVIEW", change["before"]["handling"])
            self.assertEqual("EXCLUDE", change["after"]["handling"])
            self.assertEqual("-", change["after"]["plan"])
            self.assertEqual(change["before"]["units"], change["after"]["units"])
            self.assertEqual(change["before"]["order"], change["after"]["order"])
            changed.update(change["assets"])
        self.assertEqual({e["asset"] for e in self.evidence if e["proposed"] == "EXCLUDE"}, changed)

    def test_draft_allowlist_bytes(self):
        data = (HERE / "allowlist.txt.draft").read_bytes()
        self.assertNotIn(b"\r", data)
        self.assertTrue(data.endswith(b"\n"))
        self.assertEqual(162, len(data.splitlines()))
        self.assertEqual(self.changes["allowlist_after_sha256"], hashlib.sha256(data).hexdigest())

    def test_no_new_human_attestation(self):
        baseline = json.loads((ROOT / "docs/provenance/v1.8.0-origin-findings.json").read_bytes())
        draft = json.loads((HERE / "origin-findings.json.draft").read_bytes())
        self.assertEqual(baseline["findings"], draft["findings"][:len(baseline["findings"])])
        added = draft["findings"][len(baseline["findings"]):]
        self.assertEqual(85, len(added))
        for finding in added:
            self.assertEqual("<OWNER_PENDING_DECISION>", finding["reviewer"])
            self.assertEqual("PENDING_OWNER_NOT_A_REVIEW", finding["draft_status"])
            self.assertEqual("EXCLUDED", finding["decision"])
            self.assertIn("not an owner decision", finding["basis"])

    def test_evidence_and_live_files(self):
        for evidence in self.evidence:
            self.assertEqual(evidence["source_sha256"], evidence["derivation"]["sha256"])
            self.assertEqual("REVIEW", evidence["allowlist_ceiling"])
            self.assertEqual([], evidence["source_hash_copies"])
            self.assertTrue(evidence["upstream_consumers"]["direct"] or evidence["upstream_consumers"]["note"])
            replacement = evidence["replacement"]
            self.assertTrue(replacement["classes"])
            for kind in ("classes", "models", "textures", "additional_files"):
                for path in replacement[kind]:
                    self.assertTrue((ROOT / path).is_file(), path)

    def test_apply_refuses_existing_worktree(self):
        with self.assertRaisesRegex(ValueError, "must not exist"):
            apply(ROOT)


if __name__ == "__main__":
    unittest.main()
