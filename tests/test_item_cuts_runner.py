import copy
import gzip
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
import uuid
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_item_cuts as runner
import item_cuts_oracle as oracle
import item_cut_fixture as fixture
import loader_cut_nbt as nbt
from test_loader_cut_nbt import compound, string, region


class ItemCutsRunnerTest(unittest.TestCase):
    def test_matrix_and_genuine_replacement_boundaries(self):
        self.assertEqual(30, len(runner.CASES))
        self.assertEqual(12, sum(v["family"] == "D-S1" for v in runner.CASES.values()))
        self.assertEqual(18, sum(v["family"] == "D-S2" for v in runner.CASES.values()))
        self.assertEqual(["IR_CONTAINER", "IR_LOOT"], list(runner.CASES)[12:14])
        for direction in ("LOAD", "UNLOAD"):
            self.assertEqual("K2", runner.CASES[f"IC_{direction}_K2"]["boundary"])
            self.assertEqual("K9", runner.CASES[f"IC_{direction}_K9"]["boundary"])
        self.assertEqual("O3J_REPLACED", runner.CASES["IS_O3J_AFTER"]["boundary"])

    def test_typed_oracle_rejects_count_tag_type_resource_and_slot_mutations(self):
        native = runner.native
        for device, count in ((False, 6), (True, 64)):
            stacks = [native.item_stack(count), {}, {}, {}] if device else [native.item_stack(count, 0)]
            self.assertEqual(count, oracle.exact_stack(stacks, device))
            for mutate in (lambda s: s[0]["tag"].update(b=native.Int(7)), lambda s: s[0].update(Count=native.Int(count)),
                           lambda s: s[0].update(id="minecraft:dirt"), lambda s: s[0]["tag"].update(la=native.IntArray([1, 2])),
                           lambda s: s[0].update(Count=native.Byte(65))):
                bad = copy.deepcopy(stacks); mutate(bad)
                with self.assertRaises(native.smoke.SmokeError): oracle.exact_stack(bad, device)
        with self.assertRaises(native.smoke.SmokeError): oracle.exact_stack([native.item_stack(6, 1)])
        with self.assertRaises(native.smoke.SmokeError): oracle.exact_stack([{}, native.item_stack(64), {}, {}], True)

    def test_missing_wrong_repeated_observations_and_refused_commands_fail(self):
        for case in runner.CASES:
            line = runner.marker(case, 0) + ("checkpoint=" if runner.CASES[case]["family"] == "D-S1" else "boundary=") + runner.CASES[case]["boundary"]
            probe = runner.CASES[case]["probe"]
            if probe.endswith("SOURCE_ONLY_STORE"): line += " selected=SOURCE"
            if probe.endswith("DESTINATION_ONLY_STORE"): line += " selected=DESTINATION"
            lines = [line]
            if case == "IR_DISASSEMBLY": lines += ["ARCE_RELEASE_CARGO_DISASSEMBLY_RETURN cut=IR_DISASSEMBLY code=1"]
            runner.observations(lines, case, 0)
            for bad in ([], lines + [line], lines + ["[Server/ERROR] failure"]):
                with self.assertRaises(runner.native.smoke.SmokeError): runner.observations(bad, case, 0)
        with self.assertRaises(runner.native.smoke.SmokeError):
            runner.observations([runner.marker("IR_DISASSEMBLY", 1), "ARCE_RELEASE_CARGO_DISASSEMBLY_RETURN cut=IR_DISASSEMBLY code=0"], "IR_DISASSEMBLY", 1)

    def test_settlement_requires_unknown_accounting_typed_projection_and_exact_remainders(self):
        rocket = dict(uuid="rocket", logical="logical", owner="owner", cargo=6, epoch=1, markers=[], bound=True,
                      x=68.5, pos=[68.5, 100.0, 64.5], passengers=[])
        device = dict(id="device", logical="logical", owner="owner", role="UNLOAD", amount=64, epoch=1, markers=[])
        receipt = dict(released=True, accounting_status="UNKNOWN", payload_amount=0, choice="ACCEPT_CURRENT", missing=[],
                       survivors=[dict(endpoint=dict(kind="ENTITY"), signature_kind="FUEL_VECTOR_V1"),
                                  dict(endpoint=dict(kind="BLOCK_ENTITY"), signature_kind="ITEM_BUFFER_V1", quantity=0)], before_choice=[])
        head = dict(outcome="READY", k="FUEL_VECTOR_V1", p="rocket", e=1, l="logical", o="owner")
        disk = dict(rockets=[rocket], rocket=rocket, device=device, journal=0, ledger=dict(records=[], tombstones=[receipt],
                    rocket_authorities=[head], floors=[], armed_flights=[], issued_launch_serial=0))
        oracle.check_settlement("IS_PRUNE_AFTER", disk, True)
        for mutate in (lambda d: d["device"].update(amount=0), lambda d: d["rocket"].update(cargo=7),
                       lambda d: d["ledger"]["tombstones"][0].update(accounting_status="KNOWN"),
                       lambda d: d["ledger"]["tombstones"][0]["survivors"][1].update(signature_kind="FLUID_TANK_V1"),
                       lambda d: d["ledger"]["tombstones"][0]["survivors"][1].update(quantity=64),
                       lambda d: d["ledger"]["rocket_authorities"][0].update(outcome="HANDED_OFF")):
            bad = copy.deepcopy(disk); mutate(bad)
            with self.assertRaises(runner.native.smoke.SmokeError): oracle.check_settlement("IS_PRUNE_AFTER", bad, True)

    def seed(self, world, case):
        stack = compound(id=string("minecraft:stone"), Count=nbt.Tag(nbt.BYTE, 10), Slot=nbt.Tag(nbt.BYTE, 0))
        block = compound(position=nbt.Tag(nbt.INTS, [0, 0, 0]), block_entity=compound(data=compound(Items=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [stack])))))
        palette = compound(id=string("minecraft:chest"), properties=compound(facing=string("north"), type=string("single"), waterlogged=string("false")))
        before = compound(relative_blocks=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [block])), block_palette=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [palette])), content_hash=string("a" * 64), retained=nbt.Tag(nbt.LONG, 12))
        after = copy.deepcopy(before); after.value["content_hash"] = string("b" * 64)
        if case == "IR_CONTAINER":
            changed = nbt.compound(after, "block_palette").value[1][0]
            changed.value["id"] = string("minecraft:barrel"); changed.value["properties"] = compound(facing=string("north"), open=string("false"))
        else:
            changed = nbt.compound(after, "relative_blocks").value[1][0].value["block_entity"]
            changed.value["data"] = compound(LootTable=string("minecraft:chests/simple_dungeon"), LootTableSeed=nbt.Tag(nbt.LONG, 143))
        capture = compound(physical=nbt.uuid_tag(uuid.UUID(int=143)), before=before, after=after)
        path = world / fixture.CANDIDATE; path.parent.mkdir(parents=True); nbt.write_gzip(path, b"", capture)
        entity = compound(UUID=capture.value["physical"], RocketEntityData=compound(snapshot=before, retained=nbt.Tag(nbt.BYTES, b"wrapper")))
        region(world / "entities", {(4, 4): compound(Entities=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [entity]))), (5, 4): compound(other=nbt.Tag(nbt.LONG, 99))})
        path = world / "data" / nbt.LEDGER_FILE; path.parent.mkdir()
        nbt.write_gzip(path, b"", compound(data=compound(issued_through=nbt.Tag(nbt.LONG, 1), records=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [compound(phase=string("PREPARED"))])))))
        return capture

    def test_offline_damage_preserves_original_bytes_unrelated_tags_and_ledger(self):
        for case in fixture.CASES:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp); world = root / "world"; captured = self.seed(world, case)
                owner = root / "owner"; owner.write_text("owned"); keep = root / "edit"
                before = (world / "entities/r.0.0.mca").read_bytes(); ledger = (world / "data" / nbt.LEDGER_FILE).read_bytes()
                other = nbt.chunk(world / "entities", 5, 4)[0]
                result = fixture.edit(world, case, keep, {"forced_stop": True}, owner, "owned")
                self.assertEqual(before, (keep / "r.0.0.mca").read_bytes()); self.assertEqual(nbt.digest(before), result["before_sha256"])
                self.assertEqual(ledger, (world / "data" / nbt.LEDGER_FILE).read_bytes()); self.assertEqual(other, nbt.chunk(world / "entities", 5, 4)[0])
                entity = nbt.compound(nbt.chunk(world / "entities", 4, 4)[0], "Entities").value[1][0]
                self.assertEqual(captured.value["after"], nbt.compound(entity, "RocketEntityData", "snapshot"))
                self.assertEqual(b"wrapper", nbt.compound(entity, "RocketEntityData", "retained").value)
                with self.assertRaises(nbt.NbtError): fixture.edit(world, case, keep, {"forced_stop": True}, owner, "owned")

    def test_offline_damage_rejects_live_foreign_failed_stops_and_extra_candidate_changes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); world = root / "world"; capture = self.seed(world, "IR_CONTAINER")
            owner = root / "owner"; owner.write_text("owned"); before = (world / "entities/r.0.0.mca").read_bytes()
            for stopped, token in (({}, "owned"), ({"forced_stop": True}, "foreign"), ({"forced_stop": True, "process_error": "crash"}, "owned")):
                with self.assertRaises(nbt.NbtError): fixture.edit(world, "IR_CONTAINER", root / "edit", stopped, owner, token)
            capture.value["after"].value["retained"] = nbt.Tag(nbt.INT, 12)
            nbt.write_gzip(world / fixture.CANDIDATE, b"", capture)
            with self.assertRaises(nbt.NbtError): fixture.edit(world, "IR_CONTAINER", root / "edit", {"forced_stop": True}, owner, "owned")
            self.assertEqual(before, (world / "entities/r.0.0.mca").read_bytes()); self.assertFalse((root / "edit").exists())

    def test_first_contradiction_retains_both_restarts_and_never_starts_next_cut(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); (root / "template/libraries").mkdir(parents=True); jar = root / "test.jar"; jar.write_bytes(b"jar")
            args = SimpleNamespace(source_commit="sha", lock_owner="owner", source_server=root / "template", jar=jar, session_dir=root / "session",
                                   evidence_dir=root / "evidence", java="java", startup_timeout=240, case=["IC_LOAD_K2", "IC_LOAD_K3"])
            process = Mock(); process.finish.return_value = 0; process.lines = []
            with patch.object(runner.native, "assert_pin"), patch.object(runner.native, "require_lock"), patch.object(runner.native, "preflight", return_value={}), \
                    patch.object(runner.native, "digest", return_value="hash"), patch.object(runner.native.smoke, "allocate_port", return_value=25565), \
                    patch.object(runner.native.smoke, "write_server_configuration"), patch.object(runner.native.smoke, "CapturedProcess", return_value=process) as starts, \
                    patch.object(runner, "retained_files", return_value={}), patch.object(runner.oracle, "read", return_value={}), \
                    patch.object(runner, "observations"), patch.object(runner, "check", side_effect=[runner.native.smoke.SmokeError("wrong remainder"), {}, {}]):
                with self.assertRaisesRegex(runner.native.smoke.SmokeError, "First contradictory cut"): runner.run(args)
                self.assertEqual(3, starts.call_count)
            result = json.loads((args.evidence_dir / "results.json").read_text())
            self.assertEqual("FAILED", result["status"]); self.assertTrue(result["cleanup"])
            self.assertEqual(1, len(result["cases"])); self.assertEqual(3, len(result["cases"][0]["starts"]))

    def test_lossless_archive_keeps_native_types_and_validates_member_hashes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); world = root / "world"; self.seed(world, "IR_LOOT")
            result = runner.retained_files(world, root / "native.zip")
            self.assertIn("arce_w143_fixture/candidate.dat", result["members"])
            self.assertEqual(runner.native.digest(root / "native.zip"), result["sha256"])


if __name__ == "__main__": unittest.main()
