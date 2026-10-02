import csv
import io
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.validate_v180_content_ledger import (ALLOWLIST, ASSET_PLAN, DERIVATION, INVENTORY, LEDGER,
                                                  ORIGIN_FINDINGS, ROOT, validate)
from tools.audit.inventory_v180_content import lang_names, unlocalized, variants

REGISTRY = Path("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/registry")
COPIED = (
    Path("legacy-manifest"),
    Path("docs/decisions"),
    Path("docs/versions"),
    Path("docs/provenance"),
    REGISTRY,
    Path("src/generated"),
    Path("src/main/resources"),
)


class V180ContentLedgerTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        for relative in COPIED:
            shutil.copytree(ROOT / relative, self.root / relative)
        for relative in (INVENTORY, LEDGER, ASSET_PLAN, ALLOWLIST, DERIVATION):
            (self.root / relative).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, self.root / relative)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _rows(self, relative: Path) -> list[dict[str, str]]:
        with (self.root / relative).open(encoding="utf-8", newline="") as handle:
            return list(csv.DictReader(handle))

    def _write_rows(self, relative: Path, rows: list[dict[str, str]]) -> None:
        buffer = io.StringIO()
        writer = csv.DictWriter(buffer, fieldnames=list(rows[0].keys()), lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
        (self.root / relative).write_text(buffer.getvalue(), encoding="utf-8", newline="")

    def _edit_ledger(self, unit_id: str, **changes: str) -> None:
        rows = self._rows(LEDGER)
        for row in rows:
            if row["unit_id"] == unit_id:
                row.update(changes)
        self._write_rows(LEDGER, rows)

    def _errors(self, require_accepted: bool = False, closure: bool = False) -> list[str]:
        return validate(self.root, require_accepted, closure)[1]

    def _set_rule(self, pattern: str, **changes: str) -> None:
        rows = self._rows(ASSET_PLAN)
        for row in rows:
            if row["pattern"] == pattern:
                row.update(changes)
        self._write_rows(ASSET_PLAN, rows)

    def _write_findings(self, findings: list[dict[str, str]]) -> None:
        (self.root / ORIGIN_FINDINGS).write_text(json.dumps({"schema_version": 1, "findings": findings}),
                                                 encoding="utf-8")

    def test_repository_passes(self) -> None:
        summary, errors = validate(ROOT)
        self.assertEqual([], errors)
        self.assertEqual("PASS", summary["result"])
        self.assertEqual(645, summary["units"])
        self.assertEqual(898, sum(summary["assets"].values()))

    def test_copied_tree_passes(self) -> None:
        self.assertEqual([], self._errors())

    def test_missing_and_extra_rows_fail(self) -> None:
        rows = self._rows(LEDGER)
        removed = rows.pop(0)["unit_id"]
        rows.append(dict(rows[0], unit_id="block:notALegacyBlock"))
        self._write_rows(LEDGER, rows)
        errors = self._errors()
        self.assertIn(f"ledger: {removed} has no disposition", errors)
        self.assertIn("ledger: block:notALegacyBlock is not an inventory unit", errors)

    def test_duplicate_row_fails(self) -> None:
        rows = self._rows(LEDGER)
        rows.append(dict(rows[3]))
        self._write_rows(LEDGER, rows)
        self.assertIn(f"ledger: duplicate row {rows[3]['unit_id']}", self._errors())

    def test_rejected_and_deferred_need_an_adr(self) -> None:
        self._edit_ledger("block:rocketfire", decision="V0.5.0")
        self._edit_ledger("block:terraformer", decision="")
        errors = self._errors()
        self.assertIn("ledger: block:rocketfire: REJECTED needs an ADR", errors)
        self.assertIn("ledger: block:terraformer: DEFERRED needs an ADR", errors)

    def test_unknown_adr_and_unknown_token_fail(self) -> None:
        self._edit_ledger("block:lathe", decision="ADR-999")
        self._edit_ledger("block:centrifuge", decision="later")
        errors = self._errors()
        self.assertIn("ledger: block:lathe: ADR-999 does not exist", errors)
        self.assertTrue(any("block:centrifuge: decision token 'later'" in error for error in errors))

    def test_planned_needs_a_v18_batch(self) -> None:
        self._edit_ledger("block:lathe", plan="C20a")
        self.assertIn("ledger: block:lathe: PLANNED plan 'C20a' is not a v1.8 batch", self._errors())

    def test_implemented_plan_must_not_be_future(self) -> None:
        self._edit_ledger("block:railgun", plan="v1.9.0")
        self.assertTrue(any("block:railgun: IMPLEMENTED plan 'v1.9.0'" in error for error in self._errors()))

    def test_v18_delivery_needs_evidence(self) -> None:
        self._edit_ledger("block:centrifuge", disposition="REDESIGNED", plan="v1.8.0", target="anything")
        self.assertIn("ledger: block:centrifuge: REDESIGNED in v1.8.0 needs the delivering batch's evidence file",
                      self._errors())

    def test_v18_delivery_with_evidence_passes(self) -> None:
        evidence = self.root / "docs/work/v1.8.0-c16c-machines/VERIFICATION.md"
        evidence.parent.mkdir(parents=True)
        evidence.write_text("x", encoding="utf-8")
        self._edit_ledger("block:centrifuge", disposition="REDESIGNED", plan="v1.8.0", target="anything",
                          evidence="docs/work/v1.8.0-c16c-machines/VERIFICATION.md")
        self.assertEqual([], self._errors())

    def test_planned_row_cannot_carry_evidence(self) -> None:
        self._edit_ledger("block:lathe", evidence="docs/work/v1.8.0-content-audit.md")
        self.assertIn("ledger: block:lathe: evidence is recorded only for rows delivered in v1.8.0", self._errors())

    def test_unknown_disposition_fails(self) -> None:
        self._edit_ledger("block:lathe", disposition="UNKNOWN")
        self.assertTrue(any("block:lathe: disposition 'UNKNOWN'" in error for error in self._errors()))

    def test_merge_target_must_exist_and_not_chain(self) -> None:
        self._edit_ledger("block_entity:ARTileLathe", target="block:missing")
        self._edit_ledger("block_entity:ARCentrifuge", target="block_entity:ARTileLathe")
        errors = self._errors()
        self.assertIn("ledger: block_entity:ARTileLathe: merge target 'block:missing' names no unit", errors)
        self.assertIn("ledger: block_entity:ARCentrifuge: merge target block_entity:ARTileLathe is itself MERGED",
                      errors)

    def test_tag_names_are_not_registered_ids(self) -> None:
        self._edit_ledger("block:railgun", target="advancedrocketrycommunity:machine_casings")
        self.assertIn("ledger: block:railgun: target advancedrocketrycommunity:machine_casings is not registered",
                      self._errors())

    def test_implemented_id_must_be_in_the_matching_family(self) -> None:
        self._edit_ledger("block:railgun", target="advancedrocketrycommunity:rocket")
        self.assertIn("ledger: block:railgun: IMPLEMENTED content needs a registered modern block/item ID",
                      self._errors())

    def test_rejected_rows_need_the_player_impact_adr(self) -> None:
        self._edit_ledger("entity:laserNode", decision="ADR-055")
        self.assertIn("ledger: entity:laserNode: REJECTED needs ADR-062, which records the player impact",
                      self._errors())

    def test_closure_requires_no_planned_rows(self) -> None:
        self.assertTrue(any(error.startswith("closure: ") and "PLANNED" in error
                            for error in self._errors(closure=True)))

    def test_loosening_an_exclusion_fails(self) -> None:
        self._set_rule("textures/font.png", handling="IMPORT", plan="C18d")
        self.assertIn("asset plan: textures/font.png is IMPORT beyond its allowlist ceiling none", self._errors())

    def test_review_needs_a_cleared_finding_to_import(self) -> None:
        self._set_rule("textures/blocks/beacon.png", handling="IMPORT")
        self.assertIn("asset plan: textures/blocks/beacon.png is IMPORT beyond its allowlist ceiling REVIEW",
                      self._errors())
        self._write_findings([{"asset": "textures/blocks/beacon.png", "decision": "CLEARED", "reviewer": "maintainer",
                               "reviewed_at": "2026-10-02", "basis": "compared with vanilla"}])
        self.assertEqual([], self._errors())

    def test_excluded_finding_forces_exclusion(self) -> None:
        self._write_findings([{"asset": "textures/blocks/beacon.png", "decision": "EXCLUDED", "reviewer": "maintainer",
                               "reviewed_at": "2026-10-02", "basis": "vanilla-derived"}])
        self.assertIn("asset plan: textures/blocks/beacon.png was excluded by an origin finding but is REVIEW",
                      self._errors())

    def test_import_for_a_deferred_unit_fails(self) -> None:
        self._set_rule("textures/blocks/beacon.png", units="block:terraformer")
        self.assertTrue(any("REVIEW for block:terraformer, which is DEFERRED" in error for error in self._errors()))

    def _set_verdict(self, asset: str, verdict: str) -> None:
        path = self.root / DERIVATION
        data = json.loads(path.read_text(encoding="utf-8"))
        for entry in data["assets"]:
            if entry["asset"] == asset:
                entry["verdict"] = verdict
        path.write_text(json.dumps(data), encoding="utf-8")

    def test_vanilla_derived_assets_cannot_be_imported(self) -> None:
        self._set_verdict("textures/blocks/blastbrick.png", "HIT")
        self.assertIn("asset plan: textures/blocks/blastbrick.png is vanilla-derived (HIT) but handled as IMPORT",
                      self._errors())

    def test_suspect_assets_need_a_cleared_finding(self) -> None:
        self._set_verdict("textures/blocks/blastbrick.png", "SUSPECT")
        self.assertIn("asset plan: textures/blocks/blastbrick.png has derivation verdict SUSPECT and no CLEARED "
                      "origin finding", self._errors())

    def test_allowlist_is_pinned(self) -> None:
        path = self.root / ALLOWLIST
        path.write_text(path.read_text(encoding="utf-8") + "textures/env/sun.png\tIMPORT\n", encoding="utf-8")
        self.assertIn("asset plan: v1.8.0-asset-import-allowlist.txt does not match the digest pinned in ADR-062",
                      self._errors())

    def test_modern_target_must_be_registered(self) -> None:
        self._edit_ledger("block:railgun", target="advancedrocketrycommunity:rail_cannon")
        self.assertIn("ledger: block:railgun: target advancedrocketrycommunity:rail_cannon is not registered",
                      self._errors())

    def test_implemented_content_needs_a_modern_id(self) -> None:
        self._edit_ledger("block:railgun", target="railgun")
        self.assertIn("ledger: block:railgun: IMPLEMENTED content needs a registered modern block/item ID", self._errors())

    def test_require_accepted_reports_proposed_adrs(self) -> None:
        errors = self._errors(require_accepted=True)
        self.assertTrue(any("depend on ADR-062, which is not ACCEPTED" in error for error in errors))

    def test_unmatched_asset_and_dead_rule_fail(self) -> None:
        rows = self._rows(ASSET_PLAN)
        rows = [row for row in rows if row["pattern"] != "textures/particle/*"]
        rows.append(dict(rows[-1], pattern="textures/nothing/*.png"))
        for order, row in enumerate(rows, start=1):
            row["order"] = str(order)
        self._write_rows(ASSET_PLAN, rows)
        errors = self._errors()
        self.assertIn("asset plan: textures/particle/soft.png matches no rule", errors)
        self.assertTrue(any("pattern 'textures/nothing/*.png' matches no asset" in error for error in errors))

    def test_imported_handling_needs_a_record(self) -> None:
        rows = self._rows(ASSET_PLAN)
        for row in rows:
            if row["pattern"] == "textures/blocks/blastbrick*.png":
                row.update(handling="IMPORTED", plan="v1.8.0")
        self._write_rows(ASSET_PLAN, rows)
        self.assertIn("asset plan: textures/blocks/blastbrick.png is handled as IMPORTED without a provenance record",
                      self._errors())

    def test_recorded_import_must_be_handled_as_imported(self) -> None:
        rows = self._rows(ASSET_PLAN)
        for row in rows:
            if row["pattern"] == "textures/blocks/machinevent.png":
                row.update(handling="EXCLUDE", plan="-")
        self._write_rows(ASSET_PLAN, rows)
        self.assertIn("asset plan: textures/blocks/machinevent.png has a provenance record but is not handled as "
                      "IMPORTED", self._errors())

    def test_inventory_must_match_the_manifest(self) -> None:
        path = self.root / INVENTORY
        inventory = json.loads(path.read_text(encoding="utf-8"))
        first = next(iter(inventory["sources"]))
        inventory["sources"][first] = "0" * 64
        inventory["units"] = [unit for unit in inventory["units"] if unit["id"] != "block:lathe"]
        inventory["counts"]["block"] -= 1
        path.write_text(json.dumps(inventory), encoding="utf-8")
        errors = self._errors()
        self.assertIn(f"inventory: source {first} hash differs from legacy-manifest", errors)
        self.assertTrue(any(error.startswith("inventory: block units differ") and "lathe" in error
                            for error in errors))


class V180InventoryParsingTests(unittest.TestCase):
    def test_variants_and_unlocalized_names(self) -> None:
        lang = lang_names("item.circuitIC.0.name=Basic Circuit\nitem.circuitIC.1.name=Tracking Circuit\n"
                          "# comment\nitem.other.name=Other\n")
        self.assertEqual([(0, "Basic Circuit"), (1, "Tracking Circuit")], variants(lang, "item", "circuitIC"))
        main = ('AdvancedRocketryItems.itemIC = new ItemIngredient(6).setUnlocalizedName("advancedrocketry:circuitIC")'
                '.setCreativeTab(tab);')
        self.assertEqual({"itemIC": "circuitIC"}, unlocalized(main, "AdvancedRocketryItems"))


if __name__ == "__main__":
    unittest.main()
