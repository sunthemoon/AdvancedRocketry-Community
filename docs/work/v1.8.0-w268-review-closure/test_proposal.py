"""Local draft invariants; no central files or scratch worlds are edited."""
from collections import Counter
import ast
import copy
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

from apply_scratch import apply, ROOT
from adopt_findings import adopt, check_findings
from build_proposal import check_review_base, consumers, trim_references
from adoption_fixtures import BEACON, TEST as LEDGER_TEST, new_releases

HERE = Path(__file__).resolve().parent


class ProposalTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence = json.loads((HERE / "REVIEW-EVIDENCE.json").read_bytes())["files"]
        cls.changes = json.loads((HERE / "changes.json").read_bytes())

    def test_complete_partition(self):
        self.assertEqual(95, len(self.evidence))
        self.assertEqual(95, len({e["asset"] for e in self.evidence}))
        self.assertEqual({"EXCLUDE": 95}, Counter(e["proposed"] for e in self.evidence))
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
        self.assertEqual(self.changes["counts"]["allowlist_after"], len(data.splitlines()))
        self.assertEqual(self.changes["allowlist_after_sha256"], hashlib.sha256(data).hexdigest())

    def test_no_new_human_attestation(self):
        baseline = json.loads(subprocess.check_output([
            "git", "-C", str(ROOT), "show", self.changes["source_commit"] + ":docs/provenance/v1.8.0-origin-findings.json"
        ]))
        draft = json.loads((HERE / "origin-findings.json.draft").read_bytes())
        self.assertEqual(baseline["findings"], draft["findings"][:len(baseline["findings"])])
        added = draft["findings"][len(baseline["findings"]):]
        self.assertEqual(95, len(added))
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
        sounds = [e for e in self.evidence if e["group"] == "G7"]
        self.assertEqual(10, len(sounds))
        self.assertTrue(all(e["proposed"] == "EXCLUDE" for e in sounds))
        self.assertEqual([], self.changes["retained_review"])
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
            finding["basis"] = "Synthetic fixture only; no real asset decision."
            finding["reviewed_at"] = "2099-01-01"
            finding["review_record"] = "docs/work/fixture-decision.md"
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
            source.write_text(json.dumps({"findings": [{"reviewer": "fixture-reviewer", "asset": "fixture-only",
                                                       "reviewed_at": "2099-01-01"}]}))
            adopt(source, output)
            self.assertEqual(source.read_bytes(), output.read_bytes())

    def test_adoption_refuses_incomplete_review_before_writing(self):
        draft = json.loads((HERE / "origin-findings.json.draft").read_bytes())
        # A name and removed draft_status are not enough (the recheck's careless adoption).
        for finding in draft["findings"]:
            finding["reviewer"] = "fixture-reviewer"
            finding.pop("draft_status", None)
        with self.assertRaisesRegex(ValueError, "draft basis"):
            check_findings(draft)
        reviewed = dict(asset=BEACON, reviewer="fixture-reviewer", basis="Synthetic review only.",
                        reviewed_at="2099-01-01", review_record="docs/work/fixture-decision.md")
        cases = [(dict(reviewed, basis=word), "draft basis")
                 for word in ("DRAFT W268 R001", "This is not an owner decision", "PREPARATION DATE only")]
        cases += [(dict(reviewed, reviewed_at=self.changes["prepared_at"]), "Preparation date"),
                  (dict(reviewed, reviewed_at=self.changes["prepared_at"] + "\n"), "Invalid review date"),
                  (dict(reviewed, reviewed_at="2099-01-01\n"), "Invalid review date"),
                  (dict(reviewed, review_record="docs/decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md"), "decision record"),
                  (dict(reviewed, review_record=""), "decision record")]
        with tempfile.TemporaryDirectory() as temporary:
            source, output = (Path(temporary) / name for name in ("source.json", "output.json"))
            for finding, message in cases:
                with self.subTest(finding=finding):
                    source.write_text(json.dumps({"findings": [finding]}), encoding="utf-8")
                    output.write_bytes(b"unchanged output")
                    with self.assertRaisesRegex(ValueError, message):
                        adopt(source, output)
                    self.assertEqual(b"unchanged output", output.read_bytes())
                    output.unlink()
                    with self.assertRaisesRegex(ValueError, message):
                        adopt(source, output)
                    self.assertFalse(output.exists())
        # Existing reviewed findings must not be treated as new proposal findings.
        baseline = json.loads(subprocess.check_output([
            "git", "-C", str(ROOT), "show", self.changes["source_commit"] + ":docs/provenance/v1.8.0-origin-findings.json"]))
        check_findings(baseline)

    def test_releases_use_bidirectional_hit_relations(self):
        data = {"assets": [dict(asset=BEACON, related=[dict(asset="direct", level="HIT"),
                                                     dict(asset="suspect", level="SUSPECT")]),
                           dict(asset="reverse", related=[dict(asset=BEACON, level="HIT")]),
                           dict(asset="unexcluded", related=[dict(asset=BEACON, level="HIT")])]}
        self.assertEqual(["direct", "reverse"], new_releases(data, {"direct", "reverse", "suspect"}))
        base = self.changes["source_commit"]
        derivation = json.loads(subprocess.check_output([
            "git", "-C", str(ROOT), "show", base + ":docs/work/v1.8.0-vanilla-derivation.json"]))
        excluded = {a for change in self.changes["asset_plan"] for a in change["assets"]}
        self.assertEqual(new_releases(derivation, excluded), self.changes["beacon_new_releases"])
        self.assertEqual(5, len(self.changes["beacon_new_releases"]))

    def test_base_counts_and_generated_wording(self):
        from build_proposal import ALLOW, proposal, rows, git
        base = self.changes["source_commit"]
        ledger = rows(git("show", base + ":docs/work/v1.8.0-content-ledger.csv"))
        counts = self.changes["counts"]
        self.assertEqual(sum(self.changes["before_counts"].values()), counts["manifest"])
        self.assertEqual(sum(r["disposition"] == "PLANNED" for r in ledger), counts["planned"])
        self.assertEqual(len(git("show", base + ":" + ALLOW).splitlines()), counts["allowlist_before"])
        self.assertEqual(len(self.changes["asset_plan"]), counts["changed_rules"])
        changed = dict(counts, manifest=1234, allowlist_before=300, allowlist_after=215, planned=99, changed_rules=79)
        text = "\n".join(proposal([], Counter(self.changes["before_counts"]), Counter(self.changes["after_counts"]),
                                  "fixture-pin", changed, "fixture-base", "fixture-scan", []))
        for phrase in ("Total 1234 files", "300 -> 215", "99 PLANNED", "79 patterns", "79 existing rules"):
            self.assertIn(phrase, text)
        proposal_text = (HERE / "PROPOSAL.md").read_text(encoding="utf-8")
        for phrase in ("..", ". .", ". ;", "  Live replacement", "consumer below"):
            self.assertNotIn(phrase, proposal_text)
        self.assertIn("Any future block of those kinds would need NEW art", proposal_text)

    def test_adoption_fixtures_pass_entire_ledger_with_simulated_acceptance(self):
        # A short external directory avoids Windows historical-evidence path limits.
        with tempfile.TemporaryDirectory(prefix="w268-", dir=ROOT.parent) as temporary:
            scratch = Path(temporary) / "s"
            apply(scratch)
            patch = HERE / "ledger-fixtures.patch.draft"
            result = subprocess.run(["git", "apply", "--unidiff-zero", "--check", str(patch)], cwd=scratch, capture_output=True)
            self.assertEqual(0, result.returncode, result.stderr.decode("utf-8", errors="replace"))
            subprocess.run(["git", "apply", "--unidiff-zero", str(patch)], cwd=scratch, check=True)
            # Prove the generated patch changes setup, not existing test assertions.
            def assertions(path):
                tree = ast.parse(path.read_text(encoding="utf-8"))
                return [ast.dump(node) for node in ast.walk(tree) if isinstance(node, ast.Assert) or
                        isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and
                        node.func.attr.startswith("assert")]
            baseline_test = Path(temporary) / "baseline_ledger_test.py"
            baseline_test.write_bytes(subprocess.check_output([
                "git", "-C", str(ROOT), "show", self.changes["source_commit"] + ":" + LEDGER_TEST]))
            self.assertEqual(assertions(baseline_test), assertions(scratch / LEDGER_TEST))
            # Only a fixture simulation; never run this on tracked ADRs.
            adr = scratch / "docs/decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md"
            text = adr.read_text(encoding="utf-8")
            self.assertIn("status: PROPOSED", text)
            adr.write_text(re.sub(r"^status: PROPOSED$", "status: ACCEPTED", text, count=1, flags=re.M),
                           encoding="utf-8", newline="\n")
            result = subprocess.run([sys.executable, "-X", "utf8", "-B", "-m", "unittest", "-v",
                                     "tests.test_validate_v180_content_ledger"], cwd=scratch,
                                    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=1800)
            self.assertEqual(0, result.returncode, result.stdout.decode("utf-8", errors="replace"))
            self.assertIn(b"Ran 58 tests", result.stdout)
            print("\nScratch-only revision-7 ACCEPTED simulation (not acceptance):\n" +
                  result.stdout.decode("utf-8", errors="replace"), flush=True)

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
