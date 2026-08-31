import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from scripts.run_dedicated_server_smoke import SmokeError
from scripts.run_v020_machine_server_smoke import _verify_inputs as verify_machine_inputs
from scripts.run_v030_celestial_server_smoke import (
    EXPECTED_VERSION,
    _assert_dimension_files,
    _verify_inputs,
    _write_invalid_pack,
)


class V030CelestialServerSmokeTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.server = Path(self.temporary_directory.name) / "server"
        artifact = (
            self.server
            / "mods"
            / f"advancedrocketry-community-{EXPECTED_VERSION}.jar"
        )
        artifact.parent.mkdir(parents=True)
        artifact.write_bytes(b"fixture-artifact")
        self.summary = {
            "mod_version": EXPECTED_VERSION,
            "server_port": 25585,
            "artifact_sha256": hashlib.sha256(b"fixture-artifact").hexdigest(),
        }

    def test_current_artifact_identity_is_required(self) -> None:
        self.assertEqual(
            (25585, self.summary["artifact_sha256"]),
            _verify_inputs(self.server, self.summary, EXPECTED_VERSION),
        )

        with self.assertRaisesRegex(SmokeError, "expected artifact version"):
            _verify_inputs(self.server, self.summary, "1.20.1-0.3.1-dev")

    def test_machine_regression_smoke_accepts_current_artifact_version(self) -> None:
        self.assertEqual(
            (25585, self.summary["artifact_sha256"]),
            verify_machine_inputs(self.server, self.summary, EXPECTED_VERSION),
        )

    def test_invalid_datapack_fixture_has_a_missing_parent(self) -> None:
        pack = _write_invalid_pack(self.server)
        metadata = json.loads((pack / "pack.mcmeta").read_text(encoding="utf-8"))
        definition = json.loads(
            (
                pack
                / "data"
                / "advancedrocketrycommunity"
                / "celestial_bodies"
                / "moon.json"
            ).read_text(encoding="utf-8")
        )

        self.assertEqual(15, metadata["pack"]["pack_format"])
        self.assertEqual(
            "advancedrocketrycommunity:missing_parent", definition["parent"]
        )
        with self.assertRaisesRegex(SmokeError, "Refusing to reuse"):
            _write_invalid_pack(self.server)

    def test_both_fixed_dimension_region_sets_are_required(self) -> None:
        for name in ("moon", "space"):
            region = (
                self.server
                / "world"
                / "dimensions"
                / "advancedrocketrycommunity"
                / name
                / "region"
                / "r.0.0.mca"
            )
            region.parent.mkdir(parents=True)
            region.write_bytes(name.encode("ascii"))

        result = _assert_dimension_files(self.server)

        self.assertEqual({"moon", "space"}, set(result))
        self.assertEqual(1, result["moon"]["region_file_count"])


if __name__ == "__main__":
    unittest.main()
