import copy
import json
import platform
import struct
import tempfile
import unittest
import uuid
import zipfile
import zlib
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import run_v130_adapter_recovery_smoke as runner


ENTITY_ID = "11111111-2222-3333-4444-555555555555"
TRANSACTION_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
SNAPSHOT_ID = "01234567-89ab-cdef-0123-456789abcdef"
RECEIPT = ("a" * 64, ENTITY_ID)


def nbt_uuid(value):
    return list(struct.unpack(">4i", uuid.UUID(value).bytes))


def entity():
    names = [runner.HOST + ":rocket_motor", runner.HOST + ":rocket_seat",
             runner.HOST + ":guidance_computer", runner.CARGO]
    positions = [[0, 0, 0], [0, 1, 0], [0, 2, 0], [1, 0, 0]]
    blocks = [{"position": position, "palette": index} for index, position in enumerate(positions)]
    blocks[-1]["block_entity"] = {"adapter": runner.ADAPTER,
                                  "data": {"payload_version": 1, "data": copy.deepcopy(runner.INVENTORY)}}
    data = {"schema_version": 2,
            "owner_id": nbt_uuid("00000000-0000-0000-0000-000000000005"),
            "assembly_transaction_id": nbt_uuid(TRANSACTION_ID),
            "snapshot": {"schema_version": 1, "snapshot_id": SNAPSHOT_ID,
                         "content_hash": RECEIPT[0], "source_origin": list(runner.ORIGIN),
                         "source_dimension": "minecraft:overworld", "relative_blocks": blocks,
                         "block_palette": [{"id": name, "properties": {}} for name in names]},
            "flight_data": {"state": "ASSEMBLED", "fuel": {"amount": 0},
                            "logical_rocket_id": nbt_uuid(TRANSACTION_ID)}}
    return {"id": runner.ROCKET, "UUID": nbt_uuid(ENTITY_ID), "Pos": [256.5, 101.0, 256.5],
            "RocketEntityData": data}


def disk(phase):
    recovered = phase in runner.PHASES[4:]
    data = entity()["RocketEntityData"]
    entries = []
    if phase in ("provider-skipped", "mod-uninstalled"):
        entries = [{"type": "ASSEMBLY", "phase": "EXTRACTING", "progress": 4,
                    "snapshot": copy.deepcopy(data["snapshot"]), "owner_id": data["owner_id"],
                    "transaction_id": data["assembly_transaction_id"], "snapshot_id": nbt_uuid(SNAPSHOT_ID),
                    "rocket_entity_id": nbt_uuid(ENTITY_ID), "content_hash": RECEIPT[0],
                    "dimension": "minecraft:overworld", "minimum": [256, 101, 256], "maximum": [257, 103, 256]}]
    return {"entities": [] if recovered else [entity()],
            "blocks": {str(pos): {"Name": name if recovered or pos[1] == 100 else "minecraft:air"}
                       for pos, name in runner.POSITIONS.items()},
            "block_entities": [{"id": runner.CARGO, "x": 257, "y": 101, "z": 256,
                                "FixtureInventory": {"schema_version": 1, **copy.deepcopy(runner.INVENTORY)}}]
            if recovered else [], "journal": {"schema_version": 2, "transactions": entries}}


class RecoveryOracleTests(unittest.TestCase):
    def test_all_six_disk_states_have_exact_expected_authority(self):
        baseline = entity()["RocketEntityData"]
        staged = disk("provider-skipped")["journal"]
        for phase in runner.PHASES:
            with self.subTest(phase=phase):
                runner.validate_disk(disk(phase), phase, RECEIPT, baseline, staged if phase == "mod-uninstalled" else None)

    def test_each_journal_binding_is_checked_before_it_becomes_baseline(self):
        for field, replacement in {
            "type": "DISASSEMBLY", "phase": "SPAWNED", "progress": 3, "snapshot": {},
            "owner_id": nbt_uuid(ENTITY_ID), "transaction_id": nbt_uuid(ENTITY_ID),
            "snapshot_id": nbt_uuid(ENTITY_ID), "rocket_entity_id": nbt_uuid(TRANSACTION_ID),
            "content_hash": "b" * 64, "dimension": "minecraft:the_nether", "minimum": [0, 0, 0],
            "maximum": [256, 103, 256],
        }.items():
            with self.subTest(field=field):
                state = disk("provider-skipped")
                state["journal"]["transactions"][0][field] = replacement
                with self.assertRaises(runner.SmokeError):
                    runner.validate_disk(state, "provider-skipped", RECEIPT, entity()["RocketEntityData"], None)

    def test_complete_journal_not_just_record_count_is_compared(self):
        state = disk("mod-uninstalled")
        staged = copy.deepcopy(state["journal"])
        state["journal"]["unexpected_mutation"] = 1
        with self.assertRaisesRegex(runner.SmokeError, "silently mutated"):
            runner.validate_disk(state, "mod-uninstalled", RECEIPT, entity()["RocketEntityData"], staged)

    def test_complete_entity_root_is_compared(self):
        state = disk("entity-restart")
        state["entities"][0]["RocketEntityData"]["future_field"] = "changed"
        with self.assertRaisesRegex(runner.SmokeError, "Opaque RocketEntityData"):
            runner.validate_disk(state, "entity-restart", RECEIPT, entity()["RocketEntityData"], None)

    def test_missing_duplicate_and_dropped_authorities_rejected(self):
        for entities in ([], [entity(), entity()], [entity(), {"id": "minecraft:item"}]):
            with self.subTest(entities=entities):
                state = disk("entity-restart")
                state["entities"] = entities
                with self.assertRaises(runner.SmokeError):
                    runner.validate_disk(state, "entity-restart", RECEIPT, entity()["RocketEntityData"], None)

    def test_world_inventory_cannot_coexist_with_snapshot(self):
        state = disk("entity-restart")
        state["block_entities"] = [{"id": "other:orphan", "x": 257, "y": 101, "z": 256}]
        with self.assertRaisesRegex(runner.SmokeError, "retained an inventory"):
            runner.validate_disk(state, "entity-restart", RECEIPT, entity()["RocketEntityData"], None)

    def test_item_count_metadata_version_and_adapter_mutations_rejected(self):
        for change in ("count", "name", "envelope", "adapter", "position", "owner", "fuel"):
            with self.subTest(change=change):
                value = entity()
                data = value["RocketEntityData"]
                payload = data["snapshot"]["relative_blocks"][-1]["block_entity"]
                if change == "count":
                    payload["data"]["data"]["Items"][0]["Count"] = 18
                elif change == "name":
                    payload["data"]["data"]["Items"][0]["tag"]["display"]["Name"] = "changed"
                elif change == "envelope":
                    payload["data"]["payload_version"] = 2
                elif change == "adapter":
                    payload["adapter"] = "other:adapter"
                elif change == "position":
                    value["Pos"][0] = 0.5
                elif change == "owner":
                    data["owner_id"] = nbt_uuid(ENTITY_ID)
                else:
                    data["flight_data"]["fuel"]["amount"] = 1
                with self.assertRaises(runner.SmokeError):
                    runner.validate_entity(value, RECEIPT)

    def test_restored_inventory_schema_and_exact_metadata_are_checked(self):
        for change in ("schema", "extra_slot", "name", "duplicate"):
            with self.subTest(change=change):
                state = disk("container-restart")
                inventory = state["block_entities"][0]["FixtureInventory"]
                if change == "schema":
                    inventory["schema_version"] = 2
                elif change == "extra_slot":
                    inventory["Items"].append({"Slot": 2, "id": "minecraft:diamond", "Count": 1})
                elif change == "name":
                    inventory["Items"][0].pop("tag")
                else:
                    state["block_entities"].append(copy.deepcopy(state["block_entities"][0]))
                with self.assertRaises(runner.SmokeError):
                    runner.validate_disk(state, "container-restart", RECEIPT, entity()["RocketEntityData"], None)

    def test_restored_phase_rejects_pending_journal_and_duplicate_blocks(self):
        state = disk("container-restart")
        state["journal"]["transactions"] = [{}]
        with self.assertRaisesRegex(runner.SmokeError, "pending or repeated"):
            runner.validate_disk(state, "container-restart", RECEIPT, entity()["RocketEntityData"], None)
        state = disk("entity-restart")
        state["blocks"][str(runner.ORIGIN)] = {"Name": runner.HOST + ":rocket_motor"}
        with self.assertRaisesRegex(runner.SmokeError, "world blocks"):
            runner.validate_disk(state, "entity-restart", RECEIPT, entity()["RocketEntityData"], None)


class RecoveryParsingTests(unittest.TestCase):
    def test_entity_chunk_reuses_bounded_nbt_reader(self):
        raw = b"\x0a\x00\x00\x0b\x00\x08Position" + struct.pack(">iii", 2, 16, 16)
        raw += b"\x09\x00\x08Entities\x0a\x00\x00\x00\x00\x00"
        compressed = zlib.compress(raw)
        self.assertEqual([], runner.decode_entity_chunk(compressed)["Entities"])
        for invalid in (compressed + b"extra", compressed[:-2], zlib.compress(raw + b"extra"),
                        zlib.compress(b"x" * (runner.region_nbt.MAX_EXPANDED_CHUNK + 1))):
            with self.subTest(invalid=invalid[:10]), self.assertRaises((ValueError, runner.SmokeError)):
                runner.decode_entity_chunk(invalid)

    def test_padded_palette_word_lookup_and_bounds(self):
        palette = [{"Name": f"example:block{index}"} for index in range(17)]
        words = [0] * 342  # 5 bits; 12 entries per 64-bit word, not crossing words.
        position = (257, 101, 256)
        index = 5 * 256 + 1
        words[index // 12] = 16 << ((index % 12) * 5)
        chunk = {"sections": [{"Y": 6, "block_states": {"palette": palette, "data": words}}]}
        self.assertEqual(palette[16], runner.block_at(chunk, position))
        words[index // 12] = 31 << ((index % 12) * 5)
        with self.assertRaisesRegex(runner.SmokeError, "palette index"):
            runner.block_at(chunk, position)
        words.pop()
        with self.assertRaisesRegex(runner.SmokeError, "length"):
            runner.block_at(chunk, position)

    def test_uniform_palette_and_missing_section(self):
        block = {"Name": "minecraft:air"}
        self.assertEqual(block, runner.block_at({"sections": [{"Y": 6, "block_states": {"palette": [block]}}]}, runner.ORIGIN))
        with self.assertRaises(runner.SmokeError):
            runner.block_at({"sections": []}, runner.ORIGIN)

    def test_registration_modes_distinguish_mod_absence_from_no_provider(self):
        for phase in runner.PHASES:
            lines = [] if phase == "mod-uninstalled" else [runner.SKIPPED if phase == "provider-skipped" else runner.REGISTERED]
            runner.validate_registration(lines, phase)
        with self.assertRaises(runner.SmokeError):
            runner.validate_registration([runner.REGISTERED] * 2, "assemble")
        with self.assertRaises(runner.SmokeError):
            runner.validate_registration([runner.SKIPPED], "mod-uninstalled")
        with self.assertRaises(runner.SmokeError):
            runner.validate_registration([runner.REGISTERED.replace("API 1.1", "API 1.0")], "assemble")

    def test_actual_status_checks_mod_set_version_and_no_players(self):
        mods = {runner.HOST: "1.20.1-1.3.0-dev", runner.FIXTURE: "1.0.0", "forge": "", "minecraft": "1.20.1"}
        status = {"version": {"name": "1.20.1", "protocol": 763}, "players": {"online": 0},
                  "forgeData": {"mods": [{"modId": key, "modmarker": value} for key, value in mods.items()]}}
        runner.validate_status(status, True, mods[runner.HOST])
        with self.assertRaises(runner.SmokeError):
            runner.validate_status(status, False, mods[runner.HOST])
        status["players"]["online"] = 1
        with self.assertRaises(runner.SmokeError):
            runner.validate_status(status, True, mods[runner.HOST])

    def test_only_exact_loader_missing_mapping_error_is_accepted(self):
        line = "[Server thread/ERROR] [net.minecraftforge.registries.GameData/REGISTRIES]: " + runner.MISSING_MAPPING_ERROR
        self.assertEqual([line], runner.audit_log([line], "mod-uninstalled"))
        for bad, phase in ((line, "entity-restart"), (line + " extra", "mod-uninstalled"),
                           (line.replace("GameData", "Other"), "mod-uninstalled"),
                           ("[Server thread/ERROR] [example]: broken", "mod-uninstalled")):
            with self.subTest(bad=bad, phase=phase), self.assertRaises(runner.SmokeError):
                runner.audit_log([bad], phase)

    def test_missing_block_diagnostic_requires_only_the_fixture_id(self):
        header = "[main/ERROR] [ne.mi.re.GameData/REGISTRIES]: " + runner.MISSING_BLOCK_ERROR
        body = "\t" + runner.CARGO + ": 1022"
        self.assertEqual([header], runner.audit_log([header, body, ""], "mod-uninstalled"))
        for lines, phase in (([header, body, ""], "provider-skipped"),
                             ([header, ""], "mod-uninstalled"),
                             ([header, body, body, ""], "mod-uninstalled"),
                             ([header, body.replace(runner.CARGO, "other:container"), ""], "mod-uninstalled"),
                             ([header.replace("minecraft:block", "minecraft:item"), body, ""], "mod-uninstalled"),
                             ([header.replace("GameData", "Other"), body, ""], "mod-uninstalled"),
                             ([header, body, "[main/ERROR] [example]: broken"], "mod-uninstalled")):
            with self.subTest(lines=lines, phase=phase), self.assertRaises(runner.SmokeError):
                runner.audit_log(lines, phase)


class RecoverySafetyTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.server = self.root / "server"
        self.server.mkdir()
        args_name = "win_args.txt" if platform.system() == "Windows" else "unix_args.txt"
        args = self.server / "libraries/net/minecraftforge/forge" / runner.server_smoke.FORGE_COORDINATE / args_name
        args.parent.mkdir(parents=True)
        args.write_text("prepared test args")
        self.evidence = self.root / "evidence"
        self.host = self.jar("host.jar", runner.HOST, "1.20.1-1.3.0-dev")
        self.fixture = self.jar("fixture.jar", runner.FIXTURE, "1.0.0")

    def jar(self, name, mod_id, version, extra=None):
        path = self.root / name
        entry = "arceadaptertest/AdapterTestMod" if mod_id == runner.FIXTURE else runner.HOST + "/AdvancedRocketryCommunity"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("META-INF/mods.toml", f'[[mods]]\nmodId="{mod_id}"\nversion="{version}"\n')
            archive.writestr("io/github/sunthemoon/" + entry + ".class", b"fixture, not executable Java")
            if extra:
                archive.writestr(extra, b"extra")
        return path

    def validate(self):
        return runner.validate_inputs(self.server, self.evidence, self.host, self.fixture, "1.20.1-1.3.0-dev")

    def test_fresh_prepared_inputs_are_read_only(self):
        result = self.validate()
        self.assertEqual(2, len(result[2]))
        self.assertEqual({"libraries"}, {path.name for path in self.server.iterdir()})
        self.assertFalse(self.evidence.exists())

    def test_server_runtime_or_unexpected_files_are_rejected(self):
        for name in ("world", "mods", "server.properties", "unexpected"):
            with self.subTest(name=name):
                path = self.server / name
                path.touch()
                with self.assertRaisesRegex(runner.SmokeError, "only the prepared"):
                    self.validate()
                path.unlink()

    def test_existing_overlapping_and_linked_evidence_rejected(self):
        self.evidence.mkdir()
        with self.assertRaisesRegex(runner.SmokeError, "new and disjoint"):
            self.validate()
        for target in (self.server / "evidence", self.root):
            with self.subTest(target=target), self.assertRaises(runner.SmokeError):
                runner.validate_inputs(self.server, target, self.host, self.fixture, "1.20.1-1.3.0-dev")
        with patch.object(runner.server_smoke, "is_link_or_junction", side_effect=lambda path: path == self.root):
            with self.assertRaisesRegex(runner.SmokeError, "ancestor"):
                runner.safe_path(self.root / "new" / "output")

    def test_parent_traversal_and_library_links_are_rejected(self):
        with self.assertRaisesRegex(runner.SmokeError, "traversal"):
            runner.safe_path(self.root / "child" / ".." / "other")
        library = self.server / "libraries" / "foreign.jar"
        library.touch()
        with patch.object(runner.server_smoke, "is_link_or_junction", side_effect=lambda path: path == library):
            with self.assertRaisesRegex(runner.SmokeError, "Linked"):
                self.validate()

    def test_artifact_identity_and_bundled_class_boundaries(self):
        self.fixture = self.jar("wrong.jar", runner.FIXTURE, "2.0.0")
        with self.assertRaisesRegex(runner.SmokeError, "identity"):
            self.validate()
        self.fixture = self.jar("bundled.jar", runner.FIXTURE, "1.0.0", "net/minecraft/Hidden.class")
        with self.assertRaisesRegex(runner.SmokeError, "non-fixture"):
            self.validate()
        self.fixture = self.jar("valid.jar", runner.FIXTURE, "1.0.0")
        self.host = self.jar("host-fixture.jar", runner.HOST, "1.20.1-1.3.0-dev", "io/github/sunthemoon/arceadaptertest/Hidden.class")
        with self.assertRaisesRegex(runner.SmokeError, "bundles the fixture"):
            self.validate()

    def test_failed_phase_preserves_result_exit_and_commands(self):
        self.evidence.mkdir()
        run = runner.RecoveryRun(self.server, self.evidence, [{"version": "1.20.1-1.3.0-dev"}], "unused-java", 12345, 1)
        process = Mock()
        process.lines = []
        process.wait_for.side_effect = runner.SmokeError("test startup failure")
        process.process.poll.return_value = -15
        with patch.object(run, "mods", return_value={}), patch.object(runner.server_smoke, "CapturedProcess", return_value=process):
            with self.assertRaisesRegex(runner.SmokeError, "startup failure"):
                run.run_phase("assemble")
        process.abort.assert_called_once()
        report = json.loads((self.evidence / "assemble/result.json").read_text())
        self.assertEqual("FAIL", report["result"])
        self.assertEqual(-15, report["exit_code"])
        self.assertTrue((self.evidence / "assemble/commands.json").is_file())

    def test_air_shell_loads_and_waits_for_all_four_chunks_before_fill(self):
        self.evidence.mkdir()
        run = runner.RecoveryRun(self.server, self.evidence, [{"version": "1.20.1-1.3.0-dev"}], "unused-java", 12345, 1)
        process = Mock()
        process.lines = [runner.REGISTERED]
        process.process.poll.return_value = -15
        observed = []
        process.command.side_effect = observed.append
        condition = ("execute if loaded 255 101 255 if loaded 258 101 255 "
                     "if loaded 255 101 257 if loaded 258 101 257")
        with patch.object(run, "mods", return_value={}), \
                patch.object(runner.server_smoke, "CapturedProcess", return_value=process), \
                patch.object(runner.server_smoke, "wait_for_status", return_value={}), \
                patch.object(runner, "validate_status", return_value={}), \
                patch.object(runner, "wait_condition", side_effect=lambda *args: observed.append(args[1])) as wait, \
                patch.object(run, "query", side_effect=runner.SmokeError("stop before assembly")):
            with self.assertRaisesRegex(runner.SmokeError, "stop before assembly"):
                run.run_phase("assemble")
        wait.assert_called_once_with(process, condition, "V130_FIXTURE_LOADED", 30.0)
        forced = observed.index("forceload add 255 255 258 257")
        loaded = observed.index(condition)
        cleared = observed.index("fill 255 100 255 258 104 257 minecraft:air")
        self.assertLess(forced, loaded)
        self.assertLess(loaded, cleared)


if __name__ == "__main__":
    unittest.main()
