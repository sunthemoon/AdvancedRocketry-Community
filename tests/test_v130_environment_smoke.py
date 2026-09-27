import contextlib
import copy
import io
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

from scripts import run_v130_environment_smoke as runner


def nbt_uuid(value):
    raw = uuid.UUID(value).bytes
    return [int.from_bytes(raw[index:index + 4], "big", signed=True) for index in range(0, 16, 4)]


def fixture():
    receipts = {body: {"id": str(uuid.UUID(int=index + 1)), "pad": [index * 1024, 128, 0]}
                for index, body in enumerate(("earth", "moon"))}
    data = {"schema_version": 2, "reservations": [], "stations": [
        {"schema_version": 1, "station_id": nbt_uuid(receipt["id"]), "landing_pad": receipt["pad"],
         "orbit_body": runner.HOST + ":" + body,
         "environment": {"gravity_milli": 0, "vacuum": 1, "solar_angle_milli_degrees": 270000}}
        for body, receipt in receipts.items()]}
    return receipts, data


class EnvironmentSmokeTest(unittest.TestCase):
    def test_lifecycle_requires_single_current_handle_and_ordered_expiry(self):
        runner.validate_lifecycle([runner.READY, runner.EXPIRED])
        for lines in ([], [runner.READY], [runner.EXPIRED, runner.READY],
                      [runner.READY, runner.READY, runner.EXPIRED],
                      [runner.READY.replace("1.6", "1.5"), runner.EXPIRED]):
            with self.assertRaises(runner.SmokeError):
                runner.validate_lifecycle(lines)

    def test_probe_requires_exact_snapshot_identity_and_unloaded_state(self):
        expected = runner.surface("moon", 0.4)
        valid = {"label": "moon", "dimension": runner.HOST + ":moon", "resolved": True,
                 "loaded_before": False, "loaded_after": False, "snapshot": expected}
        runner.validate_probe(valid, "moon", runner.HOST + ":moon", expected, True)
        for field, value in (("label", "earth"), ("dimension", "minecraft:overworld"),
                             ("resolved", False), ("loaded_before", True), ("loaded_after", True),
                             ("loaded_before", 0), ("snapshot", runner.surface("moon", 0.165))):
            bad = copy.deepcopy(valid)
            bad[field] = value
            with self.assertRaises(runner.SmokeError):
                runner.validate_probe(bad, "moon", runner.HOST + ":moon", expected, True)

    def test_unresolved_result_cannot_carry_fabricated_earth(self):
        valid = {"label": "gap", "dimension": runner.HOST + ":space", "resolved": False,
                 "loaded_before": False, "loaded_after": False}
        runner.validate_probe(valid, "gap", runner.HOST + ":space", None, True)
        bad = dict(valid, snapshot=runner.surface("earth", 1))
        with self.assertRaises(runner.SmokeError):
            runner.validate_probe(bad, "gap", runner.HOST + ":space", None, True)

    def test_native_context_and_station_identity_survive_restart_unchanged(self):
        receipts, data = fixture()
        runner.validate_stations(data, receipts, None)
        runner.validate_stations(copy.deepcopy(data), receipts, data)
        for key, value in (("orbit_body", runner.HOST + ":moon"), ("landing_pad", [0, 0, 0]),
                           ("station_id", nbt_uuid(str(uuid.UUID(int=4)))), ("schema_version", 9),
                           ("environment", {"gravity_milli": 400, "vacuum": 1, "solar_angle_milli_degrees": 270000})):
            bad = copy.deepcopy(data)
            bad["stations"][0][key] = value
            with self.assertRaises(runner.SmokeError):
                runner.validate_stations(bad, receipts, data)
        bad = copy.deepcopy(data)
        bad["stations"][0]["extra"] = "changed"
        with self.assertRaises(runner.SmokeError):
            runner.validate_stations(bad, receipts, data)

    def test_native_decode_requires_exact_schema_and_finite_station_count(self):
        import gzip
        payload = gzip.compress(b"fixture")
        _, data = fixture()
        with patch.object(runner.recovery, "NbtReader") as reader:
            reader.return_value.read_root.return_value = {"data": data}
            self.assertEqual(data, runner.decode_stations(payload))
            for bad in ({}, {"data": {"schema_version": 9, "stations": [], "reservations": []}},
                        {"data": dict(data, reservations=[{}])}, {"data": dict(data, stations=data["stations"][:1])}):
                reader.return_value.read_root.return_value = bad
                with self.assertRaises(runner.SmokeError):
                    runner.decode_stations(payload)

    def test_invalid_java_reports_original_error_without_creating_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "new-evidence"
            argv = ["smoke", directory, "--host-jar", "host", "--fixture-jar", "fixture",
                    "--evidence-dir", str(output), "--java", "invalid-java", "--accept-eula"]
            error = io.StringIO()
            with patch("sys.argv", argv), patch.object(runner.recovery, "validate_inputs",
                    return_value=(Path(directory), output, [], Path("args"))), patch.object(runner.server,
                    "resolve_java", side_effect=runner.SmokeError("invalid Java selection")), contextlib.redirect_stderr(error):
                self.assertEqual(1, runner.main())
            self.assertIn("invalid Java selection", error.getvalue())
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
