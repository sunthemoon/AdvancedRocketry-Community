import gzip
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.run_dedicated_server_smoke import SmokeError
from scripts.run_v090_migration_server_smoke import (
    FIXTURES,
    ROOT,
    seed_alpha_files,
    validate_backup,
)
from scripts.validate_v090_migration_fixtures import FIXTURE_ROOT, write_manifest


class V090MigrationServerSmokeTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.world = self.root / "world"
        self.world.mkdir()

    def test_seeded_files_are_deterministic_bounded_nbt(self) -> None:
        first = seed_alpha_files(self.world)
        first_payloads = {
            name: (self.world / "data" / name).read_bytes()
            for name in FIXTURES
        }
        second = seed_alpha_files(self.world)

        self.assertEqual(first, second)
        self.assertEqual(set(FIXTURES), set(first))
        for name, payload in first_payloads.items():
            self.assertEqual(payload, (self.world / "data" / name).read_bytes())
            expanded = gzip.decompress(payload)
            self.assertTrue(expanded.startswith(b"\x0a\x00\x00"))
            self.assertIn(b"schema_version\x00\x00\x00\x01", expanded)
            self.assertLess(len(payload), 1024)

    def test_seed_writer_rejects_fixture_contract_drift(self) -> None:
        repository = self.root / "repository"
        fixture_directory = repository / FIXTURE_ROOT
        fixture_directory.parent.mkdir(parents=True)
        shutil.copytree(ROOT / FIXTURE_ROOT, fixture_directory)
        fixture = fixture_directory / "v030-celestial-v1.snbt"
        fixture.write_text(
            "{schema_version:1,bodies:[],drift:1}\n",
            encoding="utf-8",
            newline="\n",
        )
        write_manifest(repository)

        with self.assertRaisesRegex(SmokeError, "differs from archived fixture"):
            seed_alpha_files(self.world, repository)

    def test_backup_validation_requires_every_exact_source_hash(self) -> None:
        seeded = seed_alpha_files(self.world)
        backup_name = "20260903T040506.007Z-schema1-to2"
        backup = self.world / "advancedrocketrycommunity-backups" / backup_name
        backup.mkdir(parents=True)
        entries = []
        for name, source in seeded.items():
            shutil.copy2(self.world / "data" / name, backup / name)
            entries.append(
                {
                    "file": name,
                    "bytes": source["source_bytes"],
                    "sha256": source["source_sha256"],
                }
            )
        (backup / "manifest.json").write_text(
            json.dumps({"manifestSchema": 1, "files": entries}),
            encoding="utf-8",
        )

        result = validate_backup(self.world, backup_name, seeded)
        self.assertEqual(len(FIXTURES), result["file_count"])

        (backup / sorted(FIXTURES)[0]).write_bytes(b"tampered")
        with self.assertRaisesRegex(SmokeError, "not byte-exact"):
            validate_backup(self.world, backup_name, seeded)

    def test_backup_name_is_bounded(self) -> None:
        with self.assertRaisesRegex(SmokeError, "not bounded and canonical"):
            validate_backup(self.world, "../outside", {})


if __name__ == "__main__":
    unittest.main()
