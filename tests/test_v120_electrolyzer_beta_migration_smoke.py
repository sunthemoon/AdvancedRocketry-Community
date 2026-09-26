import copy
import hashlib
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import MagicMock, patch

from scripts import run_v120_electrolyzer_beta_migration_smoke as smoke
from scripts import v120_electrolyzer_world_fixture as fixture


EVIDENCE = fixture.common.ROOT / "docs/work/v1.2.0-migration/electrolyzer-beta-restart"


class ElectrolyzerBetaMigrationTests(unittest.TestCase):
    def setUp(self):
        self.summary = json.loads((EVIDENCE / "summary.json").read_text(encoding="utf-8"))
        _, self.original = fixture._source(fixture.SUMMARY, fixture.STATE)

    def archive(self, entries):
        stream = io.BytesIO()
        records = []
        with zipfile.ZipFile(stream, "w") as archive:
            for name, data in entries:
                archive.writestr("world/" + name, data)
                records.append({"file": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
        return stream.getvalue(), records

    def test_actual_beta_world_members_match_every_original_hash(self):
        summary, _ = fixture._json(fixture.SUMMARY, 128 * 1024)
        raw = fixture._read(fixture.ARCHIVE, fixture.MAX_ARCHIVE_BYTES)
        files = smoke._archive_files(raw, summary["original_world_manifest"])
        self.assertEqual(59, len(files))
        self.assertEqual(21832756, sum(map(len, files.values())))

    def test_unsafe_paths_and_case_collisions_are_rejected_before_extraction(self):
        for name in ("../escape", "/escape", "C:/escape", "x\\escape", "x//escape", "./level.dat"):
            with self.subTest(name=name):
                raw, records = self.archive([("level.dat", b"level"), (name, b"bad")])
                with self.assertRaisesRegex(smoke.SmokeError, "unsafe"):
                    smoke._archive_files(raw, records)
        raw, records = self.archive([("level.dat", b"level"), ("LEVEL.DAT", b"other")])
        with self.assertRaisesRegex(smoke.SmokeError, "unsafe"):
            smoke._archive_files(raw, records)

    def test_member_set_size_hash_and_total_are_checked(self):
        raw, records = self.archive([("level.dat", b"level")])
        for field, value, reason in (("bytes", 6, "size"), ("sha256", "0" * 64, "hash")):
            changed = copy.deepcopy(records)
            changed[0][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(smoke.SmokeError, reason):
                smoke._archive_files(raw, changed)
        other, _ = self.archive([("level.dat", b"level"), ("extra.dat", b"extra")])
        with self.assertRaisesRegex(smoke.SmokeError, "member set"):
            smoke._archive_files(other, records)
        with patch.object(smoke, "MAX_WORLD_BYTES", 4), self.assertRaisesRegex(smoke.SmokeError, "bound"):
            smoke._archive_files(raw, records)

    def test_zip_symlink_is_rejected(self):
        stream = io.BytesIO()
        entry = zipfile.ZipInfo("world/level.dat")
        entry.create_system = 3
        entry.external_attr = 0o120777 << 16
        with zipfile.ZipFile(stream, "w") as archive:
            archive.writestr(entry, b"target")
        records = [{"file": "level.dat", "bytes": 6, "sha256": hashlib.sha256(b"target").hexdigest()}]
        with self.assertRaisesRegex(smoke.SmokeError, "linked"):
            smoke._archive_files(stream.getvalue(), records)

    def test_saved_upgrade_and_restart_chunks_match_exact_recorded_roots(self):
        previous = None
        for snapshot in self.summary["snapshots"]:
            compressed = fixture._read(EVIDENCE / snapshot["chunk"], fixture.common.MAX_COMPRESSED_CHUNK)
            self.assertEqual(snapshot["chunk_sha256"], hashlib.sha256(compressed).hexdigest())
            machines = smoke._machines(compressed)
            self.assertEqual(snapshot["machines"], machines)
            smoke._assert_migrated(machines, self.original)
            if previous is not None:
                self.assertEqual(previous, machines)
            previous = machines

    def test_machine_identity_count_and_position_changes_are_rejected(self):
        compressed = (EVIDENCE / self.summary["snapshots"][0]["chunk"]).read_bytes()
        original = fixture.common._decode_chunk(compressed, 0, 0)
        for change in (lambda root: root["block_entities"].pop(),
                       lambda root: root["block_entities"][0].update(x=1),
                       lambda root: root["block_entities"][0].update(id="minecraft:furnace")):
            root = copy.deepcopy(original)
            change(root)
            with patch.object(fixture.common, "_decode_chunk", return_value=root):
                with self.assertRaises(smoke.SmokeError):
                    smoke._machines(compressed)

    def test_resource_progress_revision_signature_and_subject_mutations_are_rejected(self):
        mutations = [
            ("paused", "arce_machine", "progress", 39),
            ("running", "arce_machine", "energy", 12020),
            ("paused", "arce_process", "resource_revision", 1),
            ("running", "arce_process", "resource_revision", 5),
            ("paused", "arce_process", "consumed_energy", 0),
            ("paused", "arce_process", "recipe_signature", "0" * 64),
            ("paused", "arce_process", "failure_subject", ""),
            ("running", "arce_process", "failure_subject", "unexpected"),
            ("running", "arce_process", "last_applied_transaction", ""),
        ]
        for name, root, key, value in mutations:
            machines = copy.deepcopy(self.summary["snapshots"][0]["machines"])
            machines[name][root][key] = value
            with self.subTest(name=name, field=key), self.assertRaises(smoke.SmokeError):
                smoke._assert_migrated(machines, self.original)
        machines = copy.deepcopy(self.summary["snapshots"][0]["machines"])
        machines["running"]["arce_process_journal"] = {}
        with self.assertRaisesRegex(smoke.SmokeError, "journal"):
            smoke._assert_migrated(machines, self.original)

    def test_interrupted_runtime_wait_aborts_only_the_owned_process(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            runtime = root / "runtime"
            runtime.mkdir()
            artifact = root / "candidate.jar"
            artifact.write_bytes(b"candidate")
            argv = ["smoke", "--runtime", str(runtime), "--session", str(root / "work"),
                    "--artifact", str(artifact), "--evidence-dir", str(root / "evidence"),
                    "--tested-commit", "a" * 40]
            source, _ = fixture._json(fixture.SUMMARY, 128 * 1024)
            harness = MagicMock()
            owned = harness.start.return_value
            with (patch("sys.argv", argv), patch.object(smoke, "require_stopped_port"),
                  patch.object(smoke, "_prepare"),
                  patch.object(smoke, "_archive_files", return_value={}),
                  patch.object(smoke, "world_manifest", return_value=source["original_world_manifest"]),
                  patch.object(smoke.server, "resolve_java", return_value=("java", "17")),
                  patch.object(smoke.server, "digest_file", return_value="a" * 64),
                  patch.object(smoke, "WorldHarness", return_value=harness),
                  patch.object(smoke, "wait_condition", side_effect=KeyboardInterrupt)):
                with self.assertRaises(KeyboardInterrupt):
                    smoke.main()
            owned.abort.assert_called_once_with()
            harness.stop.assert_not_called()
            self.assertFalse((root / "evidence").exists())


if __name__ == "__main__":
    unittest.main()
