"""Local draft invariants; no central files or scratch worlds are edited."""
from collections import Counter
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from apply_scratch import apply, ROOT
from adopt_findings import adopt, check_findings
from build_proposal import check_review_base, consumers, trim_references

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
        baseline = json.loads(subprocess.check_output([
            "git", "-C", str(ROOT), "show", self.changes["source_commit"] + ":docs/provenance/v1.8.0-origin-findings.json"
        ]))
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
            if evidence["group"] == "G1" and not replacement["consumer_routes"]:
                self.assertIn("No consumer in 1.12", replacement["note"])
                self.assertEqual([], replacement["targets"])
            else:
                self.assertTrue(replacement["classes"])
            for kind in ("classes", "models", "textures", "additional_files"):
                for path in replacement[kind]:
                    self.assertTrue((ROOT / path).is_file(), path)

    def test_apply_refuses_existing_worktree(self):
        with self.assertRaisesRegex(ValueError, "must not exist"):
            apply(ROOT)

    def test_actual_consumer_routes_not_plan_owners(self):
        by_asset = {e["asset"]: e for e in self.evidence}
        targets = lambda name: set(by_asset["textures/blocks/" + name + ".png"]["replacement"]["targets"])
        self.assertEqual({"lathe", "rolling_machine", "elevator_anchor", "elevator_terminal"}, targets("controlpanel"))
        self.assertEqual({"astrobody_data_processor"}, targets("monitorplanet_active"))
        self.assertEqual({"gravity_field_controller", "endgame_casing", "laser_lens"}, targets("forcefieldprojector_off"))
        hatch = by_asset["textures/blocks/guidancecomputeraccesshatch.png"]["replacement"]
        self.assertEqual(["block_variant:loader/6"], [r["ledger_unit"]["unit_id"] for r in hatch["consumer_routes"]])
        self.assertEqual([], hatch["targets"])
        orphaned = {Path(e["asset"]).stem for e in self.evidence if e["group"] == "G1" and not e["replacement"]["consumer_routes"]}
        self.assertEqual({"monitorfrontleft", "monitorfrontright", "monitormoon", "monitorrear"}, orphaned)
        for e in self.evidence:
            if e["group"] in ("G1", "G8"):
                self.assertEqual({r["file"] for r in e["upstream_consumers"]["direct"]},
                                 {r["upstream_consumer"] for r in e["replacement"]["consumer_routes"]})

    def test_duplicate_recording_and_appearance_boundaries(self):
        by_asset = {e["asset"]: e for e in self.evidence}
        self.assertEqual(by_asset["sounds/lathe.ogg"]["source_sha256"], by_asset["sounds/rollingmachine.ogg"]["source_sha256"])
        for e in self.evidence:
            if e["group"] == "G4":
                self.assertIn("could be dyed", e["replacement"]["note"])
                self.assertIn("cannot be dyed", e["replacement"]["note"])
            if e["group"] == "G5":
                self.assertIn("not animated", e["replacement"]["note"])

    def test_consumer_namespace_is_not_discarded(self):
        path = "src/main/resources/assets/advancedrocketry/blockstates/test.json"
        data = b'"libvulpes:blocks/machinegeneric"\n"advancedrocketry:blocks/machineGeneric"\n'
        refs = [dict(file=path, line=n) for n in (1, 2)]
        self.assertEqual([refs[1]], consumers("textures/blocks/machinegeneric.png", refs, {path: data})["direct"])
        casing = next(e for e in self.evidence if e["asset"] == "textures/blocks/machinegeneric.png")
        self.assertEqual(3, len(casing["upstream_consumers"]["direct"]))
        self.assertIn("other 73", casing["replacement"]["note"])

    def test_base_rejects_changed_review_identity_and_semantics(self):
        manifest = {e["asset"]: e for e in self.evidence}
        plan = list({e["rule"]["pattern"]: e["rule"] for e in self.evidence}.values())
        self.assertEqual(sorted(manifest), check_review_base(manifest, plan))
        changed = copy.deepcopy(plan)
        changed[0]["handling"] = "EXCLUDE"
        with self.assertRaisesRegex(ValueError, "expected 95"):
            check_review_base(manifest, changed)
        changed = copy.deepcopy(plan)
        changed[0]["reason"] += " changed"
        with self.assertRaisesRegex(ValueError, "semantics changed"):
            check_review_base(manifest, changed)
        # A different 95-file set must fail too, even with the same count.
        changed = copy.deepcopy(manifest)
        del changed[next(iter(changed))]
        changed["sounds/fake.ogg"] = {}
        with self.assertRaisesRegex(ValueError, "expected 95"):
            check_review_base(changed, plan)

    def test_adoption_refuses_draft_before_writing(self):
        draft = json.loads((HERE / "origin-findings.json.draft").read_bytes())
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "findings.json"
            output.write_bytes(b"unchanged output")
            with self.assertRaisesRegex(ValueError, "reviewer placeholder"):
                adopt(HERE / "origin-findings.json.draft", output)
            self.assertEqual(b"unchanged output", output.read_bytes())
            with self.assertRaisesRegex(ValueError, "reviewer placeholder"):
                adopt(HERE / "origin-findings.json.draft", Path(temporary) / "new.json")
            self.assertFalse((Path(temporary) / "new.json").exists())
        # Check every finding, not just the first newly added one.
        for finding in draft["findings"]:
            finding["reviewer"] = "fixture-reviewer"
            finding.pop("draft_status", None)
        check_findings(draft)
        draft["findings"][-1]["reviewer"] = "<OWNER_PENDING_DECISION>"
        with self.assertRaisesRegex(ValueError, "reviewer placeholder"):
            check_findings(draft)

    def test_adoption_refuses_draft_status_and_empty_reviewer(self):
        for finding, message in (({"reviewer": "fixture-reviewer", "draft_status": "PENDING"}, "draft_status"),
                                 ({"reviewer": ""}, "reviewer placeholder"), ({"reviewer": "TODO"}, "reviewer placeholder")):
            with self.subTest(finding=finding), self.assertRaisesRegex(ValueError, message):
                check_findings({"findings": [finding]})

    def test_adoption_copies_structurally_reviewed_fixture(self):
        # Synthetic fixture only; this does not attest to a real asset decision.
        with tempfile.TemporaryDirectory() as temporary:
            source, output = (Path(temporary) / name for name in ("source.json", "output.json"))
            source.write_text(json.dumps({"findings": [{"reviewer": "fixture-reviewer", "asset": "fixture-only"}]}))
            adopt(source, output)
            self.assertEqual(source.read_bytes(), output.read_bytes())

    def test_trim_preserves_scan_counts_and_strong_references(self):
        entry = dict(group="G1", current_references=[
            dict(file="src/a.java", matched=["textures/blocks/test.png"], category="Java"),
            dict(file="docs/work/v1.8.0-root-queue/a.md", matched=["test.png"], category="docs"),
            dict(file="docs/a.md", matched=["test"], category="docs")], upstream_references=[])
        trimmed = trim_references([entry])[0]
        self.assertEqual([entry["current_references"][0]], trimmed["current_references"])
        self.assertEqual({"docs": 2}, trimmed["current_references_summary"]["omitted_by_category"])
        self.assertEqual(3, trimmed["current_references_summary"]["total"])
        for e in self.evidence:
            for field in ("current_references", "upstream_references"):
                summary = e[field + "_summary"]
                self.assertEqual(len(e[field]), summary["retained"])
                self.assertEqual(summary["total"], summary["retained"] + sum(summary["omitted_by_category"].values()))


if __name__ == "__main__":
    unittest.main()
