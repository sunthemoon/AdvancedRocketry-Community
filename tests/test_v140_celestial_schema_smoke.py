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
    def test_catalog_counts_support_additive_planets_without_changing_fault_deltas(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "fixture.zip"
            for bodies, routes in ((3, 4), (6, 9), (2, 4), (127, 9)):
                with zipfile.ZipFile(artifact, "w") as archive:
                    for kind, count in (("celestial_bodies", bodies), ("travel_routes", routes)):
                        for index in range(count):
                            archive.writestr(f"data/{runner.HOST}/{kind}/{index}.json", "{}")
                if 3 <= bodies <= 126:
                    self.assertEqual((bodies, routes), runner.packaged_counts(artifact))
                else:
                    with self.assertRaises(runner.SmokeError):
                        runner.packaged_counts(artifact)

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

    def test_binding_capture_preserves_exact_bytes_and_unmapped_entry(self):
        baseline = {"schema_version": 1, "bindings": [
            {"body_id": runner.HOST + ":earth", "level": "minecraft:overworld"},
            {"body_id": runner.HOST + ":moon", "level": runner.HOST + ":moon"},
            {"body_id": runner.HOST + ":space", "level": runner.HOST + ":space"},
            {"body_id": runner.GAS}]}
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / runner.BINDINGS
            source.parent.mkdir(parents=True)
            raw = (json.dumps(baseline, indent=2) + "\n").encode()
            source.write_bytes(raw)
            self.assertEqual(raw, runner.capture_bindings(root, root / "capture.json"))
            self.assertEqual(raw, source.read_bytes())
            self.assertEqual(raw, (root / "capture.json").read_bytes())
            for bad in (dict(baseline, schema_version=2), dict(baseline, bindings=[]),
                        dict(baseline, bindings=baseline["bindings"] + [baseline["bindings"][0]]),
                        dict(baseline, bindings=baseline["bindings"] + [{"body_id": "test:x", "level": "minecraft:overworld"}]),
                        dict(baseline, bindings=baseline["bindings"] + [{"body_id": "test:x", "level": None}])):
                source.write_text(json.dumps(bad), encoding="utf-8")
                before = source.read_bytes()
                with self.assertRaises(runner.SmokeError):
                    runner.capture_bindings(root, root / "rejected.json")
                self.assertEqual(before, source.read_bytes())
                self.assertFalse((root / "rejected.json").exists())


if __name__ == "__main__":
    unittest.main()
