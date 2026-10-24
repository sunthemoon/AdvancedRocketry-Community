import sys
import unittest
import json
import tempfile
import zipfile
import struct
import zlib
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))


class W152NativeReaderTest(unittest.TestCase):
    def test_native_record_can_end_without_unused_sector_padding(self):
        from run_w152_native import preserve_native, replay_native
        from run_v180_landed_disassembly_cuts import chunk_record
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); world = root / "world"; out = root / "out"
            (world / "entities").mkdir(parents=True); out.mkdir()
            header = bytearray(8192); struct.pack_into(">I", header, 0, (2 << 8) | 1)
            payload = zlib.compress(b"\x0a\x00\x00\x00")
            record = struct.pack(">IB", len(payload) + 1, 2) + payload
            (world / "entities/r.0.0.mca").write_bytes(header + record)
            result = preserve_native(world, out, "start", {"files": {"entities/r.0.0.mca": "hash"}})
            replay_native(out / result["file"], root / "replayed")
            self.assertEqual({}, chunk_record(root / "replayed/entities/r.0.0.mca", 0, 0))

    def test_empty_native_region_file_stays_verbatim_empty(self):
        from run_w152_native import preserve_native, replay_native
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); world = root / "world"; out = root / "out"
            (world / "entities").mkdir(parents=True); out.mkdir()
            (world / "entities/r.-1.-1.mca").write_bytes(b"")
            result = preserve_native(world, out, "start", {"files": {"entities/r.-1.-1.mca": "hash"}})
            replay_native(out / result["file"], root / "replayed")
            self.assertEqual(b"", (root / "replayed/entities/r.-1.-1.mca").read_bytes())

    def test_native_projection_preserves_block_properties_not_only_registry_name(self):
        from run_w152_native import block_state_at
        state = {"Name": "minecraft:chest", "Properties": {"facing": "east", "type": "single", "waterlogged": "false"}}
        chunk = {"sections": [{"Y": 6, "block_states": {"palette": [state]}}]}
        self.assertEqual(state, block_state_at(chunk, 15, 100, 15))
        different = dict(state, Properties=dict(state["Properties"], facing="west"))
        self.assertNotEqual(different, block_state_at(chunk, 15, 100, 15))

    def test_native_archive_preserves_input_bytes_and_only_declared_world_inputs(self):
        from run_w152_native import preserve_native, replay_native
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); world = root / "world"; out = root / "out"
            (world / "data").mkdir(parents=True); out.mkdir()
            (world / "data/journal.dat").write_bytes(b"original native bytes")
            (world / "unrelated.log").write_text("not an input")
            result = preserve_native(world, out, "start", {"files": {"data\\journal.dat": "hash"}})
            with zipfile.ZipFile(out / result["file"]) as archive:
                self.assertIn("data/journal.dat", archive.namelist())
                self.assertNotIn("unrelated.log", archive.namelist())
                self.assertEqual(b"original native bytes", archive.read("data/journal.dat"))
            replay_native(out / result["file"], root / "replayed")
            self.assertEqual(b"original native bytes", (root / "replayed/data/journal.dat").read_bytes())

    def test_bounded_native_archive_replays_exact_records_without_unobserved_spawn_chunks(self):
        from run_w152_native import preserve_native, replay_native
        from run_v180_landed_disassembly_cuts import chunk_record
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); world = root / "world"; out = root / "out"
            (world / "region").mkdir(parents=True); out.mkdir()
            header = bytearray(8192); data = bytearray()
            for slot, label in ((0, b"kept"), (20 + 20 * 32, b"ignored")):
                payload = zlib.compress(b'\x0a\x00\x00\x08\x00\x05label' + struct.pack('>H', len(label)) + label + b'\x00')
                record = struct.pack('>IB', len(payload) + 1, 2) + payload
                header[slot * 4:slot * 4 + 4] = struct.pack('>I', ((2 + len(data) // 4096) << 8) | 1)
                data.extend(record + bytes(4096 - len(record)))
            source = world / "region/r.0.0.mca"; source.write_bytes(header + data)
            result = preserve_native(world, out, "start", {"files": {"region/r.0.0.mca": "hash"}})
            replay_native(out / result["file"], root / "replayed")
            restored = root / "replayed/region/r.0.0.mca"
            self.assertEqual(chunk_record(source, 0, 0), chunk_record(restored, 0, 0))
            self.assertEqual({"label": "ignored"}, chunk_record(source, 20, 20))
            self.assertIsNone(chunk_record(restored, 20, 20))

    def test_consumer_comparison_requires_same_implementation_and_full_native_identity(self):
        from run_w152_native import compare_consumers
        with tempfile.TemporaryDirectory() as temp:
            paths = []
            for name in ("sealed", "vanilla", "both"):
                path = Path(temp) / name; path.mkdir(); paths.append(path)
                (path / "results.json").write_text(json.dumps(dict(source_commit="sha", jar_sha256="jar",
                    status="PASS" if name == "both" else "DOCUMENTED_RESIDUAL")))
                state = dict(native=[{"owner": "native"}], phase=None, sealed=[], files={"region": name})
                for stage in ("recover1", "recover2"):
                    (path / (stage + "-native.json")).write_text(json.dumps(state))
            self.assertEqual("DOCUMENTED_RESIDUAL_MATCHES_VANILLA", compare_consumers(*paths)["status"])
            (paths[1] / "recover2-native.json").write_text(json.dumps(dict(native=[{"owner": "changed"}])))
            with self.assertRaisesRegex(ValueError, "identity"):
                compare_consumers(*paths)

    def test_residual_requires_independent_recipient_and_an_exact_source_copy(self):
        from run_w152_native import consumer_verdict
        state = dict(phase=None, authority="BLOCKS", entity_images={}, sealed_images={"original": 14},
                     native_images={}, recipient_inventory={"original": 14}, other_recipients={},
                     effective={"original": 28})
        self.assertEqual("DOCUMENTED_RESIDUAL", consumer_verdict(state, "C_SOURCE_CONSUMER_ONLY"))
        state["recipient_inventory"] = {}
        with self.assertRaisesRegex(ValueError, "recipient"):
            consumer_verdict(state, "C_SOURCE_CONSUMER_ONLY")

    def test_both_sides_saved_requires_foreign_identity_and_no_source_refill(self):
        from run_w152_native import consumer_verdict
        state = dict(phase=None, authority="BLOCKS", entity_images={}, sealed_images={},
                     native_images={"foreign": 6}, recipient_inventory={"original": 14}, other_recipients={},
                     effective={"foreign": 6, "original": 14})
        self.assertEqual("PASS", consumer_verdict(state, "BOTH_SIDES_SAVED"))
        state["native_images"] = {"foreign": 6, "original": 14}
        with self.assertRaisesRegex(ValueError, "source"):
            consumer_verdict(state, "BOTH_SIDES_SAVED")

    def test_residual_never_licenses_retained_u_or_a_duplicate_entity(self):
        from run_w152_native import consumer_verdict
        state = dict(phase="RESTORED", authority="BLOCKS", entity_images={}, sealed_images={"original": 14},
                     native_images={}, recipient_inventory={"original": 14}, other_recipients={},
                     effective={"original": 28})
        with self.assertRaisesRegex(ValueError, "publication"):
            consumer_verdict(state, "C_SOURCE_CONSUMER_ONLY")
        state.update(phase=None, entity_images={"original": 14})
        with self.assertRaisesRegex(ValueError, "publication"):
            consumer_verdict(state, "C_SOURCE_CONSUMER_ONLY")

    def test_entity_projection_keeps_non_inventory_identity_for_restart_comparison(self):
        from run_w152_native import inspect
        data = {"owner": "native-owner", "snapshot": {"items": [{"id": "minecraft:diamond", "Count": 14, "tag": {"W152": "original"}}]}}
        entity = {"id": "advancedrocketrycommunity:rocket", "UUID": [1, 2, 3, 4], "Pos": [15.5, 100., 15.5], "RocketEntityData": data}
        def image(path, x, z):
            return {"Entities": [entity]} if path.parent.name == "entities" and (x, z) == (0, 0) else None
        with patch("run_w152_native.chunk_record", side_effect=image):
            result = inspect(Path("missing-world"))
        self.assertEqual(result["entity_data"][0]["RocketEntityData"], data)

    def test_missing_baseline_is_not_conservation(self):
        from run_w152_native import inspect
        with patch("run_w152_native.chunk_record", return_value=None):
            with self.assertRaisesRegex(ValueError, "conservation"):
                inspect(Path("missing-world"))

    def test_cut_table_covers_both_journal_replacements_and_native_forcing(self):
        from run_w152_native import CUTS
        for boundary in ("C1_MOVE_BEFORE", "C1_MOVE_AFTER", "C3_MOVE_AFTER", "C5_MOVE_AFTER", "C3_UNKNOWN", "C5_UNKNOWN",
                         "PARTIAL_C_SAVED", "SEALED:TERRAIN:BARRIER_FORCE_AFTER", "ROLLBACK_ERASE_AFTER", "UNSEAL_PUBLISH_AFTER"):
            self.assertIn(boundary, CUTS)

    def test_native_cut_table_includes_failed_preparation_and_an_additional_recovery_cut(self):
        from run_w152_native import CUTS
        self.assertIn("UNSEAL_PREPARE_FAILED", CUTS)
        self.assertIn("RECOVERY_LOADED", CUTS)

    def test_stability_comparison_preserves_resources_but_not_evidence_hashes(self):
        from run_w152_native import comparable
        self.assertEqual(comparable(dict(authority="ENTITY", effective={"original": 14}, blocks={"p": "air"},
                                         entities=["id"], files={"region": "a"})),
                         comparable(dict(authority="ENTITY", effective={"original": 14}, blocks={"p": "air"},
                                         entities=["id"], files={"region": "b"})))


if __name__ == "__main__":
    unittest.main()
