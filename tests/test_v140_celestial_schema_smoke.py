import copy
import gzip
import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from scripts import run_v140_celestial_schema_smoke as runner


class CelestialSchemaSmokeTest(unittest.TestCase):
    def test_pack_preserves_legacy_moon_values_and_gas_has_no_level(self):
        source = Path(__file__).resolve().parents[1] / (
            "src/generated/v0.3/resources/data/advancedrocketrycommunity/celestial_bodies/moon.json")
        old = json.loads(source.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact = root / "fixture.zip"
            with zipfile.ZipFile(artifact, "w") as archive:
                archive.writestr(f"data/{runner.HOST}/celestial_bodies/moon.json", source.read_bytes())
            values = runner.install_pack(root, artifact)
            self.assertEqual(old, {key: values["moon"][key] for key in old})
            self.assertEqual(2, values["moon"]["schema_version"])
            self.assertEqual({"landable": False, "orbitable": False, "gas_giant": False}, values["moon"]["capabilities"])
            self.assertNotIn("level", values["gas"])
            self.assertEqual({"landable": False, "orbitable": True, "gas_giant": True}, values["gas"]["capabilities"])
            with self.assertRaises(FileExistsError):
                runner.install_pack(root, artifact)

    def test_native_station_comparison_rejects_every_changed_field(self):
        baseline = {"schema_version": 2, "reservations": [], "stations": [{"orbit_body": "test:moon", "owner": [1, 2]}]}
        runner.validate_unchanged_station(copy.deepcopy(baseline), baseline)
        for value in ({}, dict(baseline, schema_version=3), dict(baseline, reservations=[{}]),
                      dict(baseline, stations=[]), dict(baseline, extra="new")):
            with self.assertRaises(runner.SmokeError):
                runner.validate_unchanged_station(value, baseline)
        changed = copy.deepcopy(baseline)
        changed["stations"][0]["owner"][0] = 3
        with self.assertRaises(runner.SmokeError):
            runner.validate_unchanged_station(changed, baseline)

    def test_expanded_nbt_limit_precedes_decoder(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "oversized.dat"
            path.write_bytes(gzip.compress(b"x" * (4 * 1024**2 + 1)))
            with patch.object(runner.support, "NbtReader") as reader:
                with self.assertRaises(runner.SmokeError):
                    runner.read_nbt(path)
                reader.assert_not_called()

    def test_startup_failure_aborts_only_its_process_and_retains_phase_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "evidence"
            output.mkdir()
            with patch.object(runner.server, "CapturedProcess") as process_type:
                process = process_type.return_value
                process.wait_for.side_effect = runner.SmokeError("startup fixture failure")
                process.process.poll.return_value = 1
                with self.assertRaisesRegex(runner.SmokeError, "startup fixture failure"):
                    runner.run_cycle(root, output, {}, "java", 12345, "create-reload", None, None)
                process.abort.assert_called_once_with()
            result = json.loads((output / "create-reload/observations.json").read_text(encoding="utf-8"))
            self.assertEqual("FAIL", result["result"])
            self.assertEqual(1, result["exit_code"])
            self.assertIn("startup fixture failure", result["error"])
            self.assertEqual([], json.loads((output / "create-reload/commands.json").read_text(encoding="utf-8")))

    def test_process_creation_failure_is_also_retained(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(runner.server, "CapturedProcess", side_effect=OSError("cannot spawn")):
                with self.assertRaisesRegex(OSError, "cannot spawn"):
                    runner.run_cycle(root, root, {}, "java", 12345, "restart", None, None)
            result = json.loads((root / "restart/observations.json").read_text(encoding="utf-8"))
            self.assertEqual("FAIL", result["result"])
            self.assertIsNone(result["exit_code"])

    def test_route_fixture_only_changes_distance(self):
        project = Path(__file__).resolve().parents[1]
        original = json.loads((project / "src/main/resources/data" / runner.HOST /
                               "travel_routes/earth_moon.json").read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / runner.PACK).mkdir(parents=True)
            artifact = root / "fixture.zip"
            with zipfile.ZipFile(artifact, "w") as archive:
                archive.writestr(f"data/{runner.HOST}/travel_routes/earth_moon.json", json.dumps(original))
            before = artifact.read_bytes()
            route = runner.install_route(root, artifact, 123)
            self.assertEqual(dict(original, distance_units=123), route)
            self.assertEqual(route, json.loads((root / runner.PACK / "travel_routes/earth_moon.json").read_text()))
            self.assertEqual(before, artifact.read_bytes())

    def test_rejection_cases_are_finite_and_use_declared_limits(self):
        fixtures = runner.rejection_fixtures()
        self.assertEqual(6, len(fixtures))
        self.assertEqual(6, len({entry[0] for entry in fixtures}))
        cases = {label: payload for label, _, payload, _ in fixtures}
        self.assertEqual(4097, len(cases["route-bytes"].encode("utf-8")))
        self.assertEqual(17, cases["depth"].count("["))
        self.assertEqual(runner.HOST + ":missing", json.loads(cases["missing-body"])["to"]["body_id"])

    def test_fault_log_whitelist_is_exact_not_a_general_error_filter(self):
        expected = "[12:00:00] [Server thread/ERROR] [advancedrocketrycommunity/]: Rejected planetary catalog: fixture"
        info = "[12:00:01] [Server thread/INFO] [minecraft/MinecraftServer]: Saved the game"
        runner.validate_log([expected, info], [expected])
        for lines in ([info], [expected, expected], [expected.replace("fixture", "unrelated")],
                      [expected, "[12:00:02] [Server thread/FATAL] [other/]: unrelated"]):
            with self.assertRaises(runner.SmokeError):
                runner.validate_log(lines, [expected])

    def test_route_receipt_requires_exact_generation_and_value(self):
        text = (f"Planetary route generation=2 id={runner.HOST}:earth_moon "
                f"from={runner.HOST}:body_surface/{runner.HOST}:earth "
                f"to={runner.HOST}:body_surface/{runner.HOST}:moon distance=123 bidirectional=true")
        self.assertIsNotNone(re.search(runner.route_marker(2, 123), text))
        for wrong in (text.replace("generation=2", "generation=3"), text.replace("distance=123", "distance=50")):
            self.assertIsNone(re.search(runner.route_marker(2, 123), wrong))


if __name__ == "__main__":
    unittest.main()
