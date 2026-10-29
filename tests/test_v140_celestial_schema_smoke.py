import copy
import gzip
import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import run_v140_celestial_schema_smoke as runner


V180 = "1.20.1-1.8.0-dev"
V140 = "1.20.1-1.4.0-dev"
STARTUP = ("[12:00:00] [Server thread/WARN] [io.gi.su.ad.AdvancedRocketryCommunity/]: "
           "ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED")
READY = '[12:00:00] [Server thread/INFO] [minecraft/DedicatedServer]: Done (1.000s)! For help, type "help"'
SAVED = "[12:00:00] [Server thread/INFO] [minecraft/MinecraftServer]: Saved the game"
PAD = [8, 64, 8]


def console(text):
    return "[12:00:00] [Server thread/INFO] [minecraft/MinecraftServer]: " + text


# Fixed replies of the restart phase without reload modes, matched by command prefix in this order.
RESTART_REPLIES = (
    ("arce celestial validate", [console("Celestial catalog generation 1 is valid with 4 bodies")]),
    ("arce celestial list", [
        console(f"{runner.GAS} -> unmapped gravity=2.5 atmosphere={runner.GAS} "
                "landable=false orbitable=true gas_giant=true solar=0.04 radiation=0.5"),
        console(f"{runner.HOST}:moon -> {runner.HOST}:moon gravity=0.165 atmosphere={runner.HOST}:vacuum "
                "landable=false orbitable=false gas_giant=false solar=0.5 radiation=0.25")]),
    ("arce station admin inspect", [console(
        f"region=0,0..15,15 pad={PAD[0]},{PAD[1]},{PAD[2]} orbit={runner.HOST}:moon "
        "gravity_milli=0 vacuum=true warp_energy=0")]),
    ("arce station admin create", [console("Station creation failed: UNKNOWN_ORBIT_BODY")]),
    (f"execute in {runner.GAS} ", [console(f"Unknown dimension '{runner.GAS}'")]),
    ("execute in ", [console("The time is 40")]),
    ("save-all flush", [SAVED]),
)


class CycleProcess:
    """A finite fake packaged server for one restart phase; its log starts with the given lines."""

    def __init__(self, startup):
        self.lines = [READY, *startup]
        self.process = Mock(returncode=0)
        self.process.poll.return_value = 0

    def command(self, value):
        for prefix, replies in RESTART_REPLIES:
            if value.startswith(prefix):
                self.lines.extend(replies)
                return

    def wait_for(self, pattern, timeout, start_at=0):
        for index in range(start_at, len(self.lines)):
            if pattern.search(self.lines[index]):
                return index
        raise runner.SmokeError("Fake server has no matching fresh line")

    def finish(self):
        return 0

    def abort(self):
        pass


def station_document(version=None, tick=100):
    """The decoded station root: v1.8 adds root schema 5, warp energy and the orbital clock."""
    document = {"schema_version": 2, "reservations": [],
                "stations": [{"station_id": [1, 2, 3, 4], "orbit_body": runner.HOST + ":moon", "landing_pad": PAD}]}
    if version == V180:
        document.update(schema_version=5, warp_energy=[],
                        orbital_clock={"schema_version": 1, "logical_tick": tick})
    return document


def run_restart_cycle(root, version, startup, saved=None):
    """Run the real restart phase against CycleProcess; only process, status and NBT decoding are faked."""
    artifact_path = root / "host-input.jar"
    with zipfile.ZipFile(artifact_path, "w") as archive:
        for kind, count in (("celestial_bodies", 3), ("travel_routes", 4)):
            for index in range(count):
                archive.writestr(f"data/{runner.HOST}/{kind}/{index}.json", "{}")
    server_root = root / "server"
    (server_root / "mods").mkdir(parents=True)
    (server_root / "mods/host.jar").write_bytes(artifact_path.read_bytes())
    (server_root / runner.STATIONS).parent.mkdir(parents=True)
    (server_root / runner.STATIONS).write_bytes(b"native stations")
    (server_root / "world/level.dat").write_bytes(b"native level")
    artifact = {"path": str(artifact_path), "name": "host.jar", "version": version,
                "sha256": runner.server.digest_file(artifact_path)}
    output = root / "evidence"
    output.mkdir()
    process = CycleProcess(startup)
    baseline = station_document(version)
    saved = station_document(version, 160) if saved is None else saved
    with patch.object(runner.support.rocket_smoke, "_server_command",
                      return_value=["java", "-Dadvancedrocketrycommunity.releaseTestHooks=true"]), \
            patch.object(runner.server, "CapturedProcess", return_value=process), \
            patch.object(runner.server, "wait_for_status", return_value={"players": {"online": 0}}), \
            patch.object(runner.server, "validate_status_identity") as identity, \
            patch.object(runner.server, "forge_mod_versions",
                         return_value={runner.HOST: version, "minecraft": "1.20.1", "forge": "47.4.10"}), \
            patch.object(runner, "read_nbt", return_value={"data": saved}):
        result = runner.run_cycle(server_root, output, artifact, "java", 12345, "restart",
                                  "00000000-0000-0001-0000-000200000003", baseline)
    identity.assert_called_once_with({"players": {"online": 0}}, version)
    return result, json.loads((output / "restart/observations.json").read_text(encoding="utf-8"))


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


class StartupAllowanceTest(unittest.TestCase):
    REJECTED = ("[12:00:01] [Server thread/ERROR] [advancedrocketrycommunity/]: "
                "Rejected planetary catalog; last valid generation remains active: fixture")

    def test_v180_startup_hold_is_counted_once_per_process_log(self):
        for lines in ([STARTUP, self.REJECTED], [self.REJECTED, STARTUP]):
            with self.subTest(lines=lines):
                runner.validate_log(lines, [self.REJECTED], V180)
        runner.validate_log([STARTUP], [], V180)
        for lines, errors in (([STARTUP, self.REJECTED, STARTUP], [self.REJECTED]), ([STARTUP, STARTUP], []),
                              ([STARTUP.replace("SOURCE_UNCONFIRMED", "SOURCE_UNAVAILABLE")], [])):
            with self.subTest(lines=lines), self.assertRaises(runner.SmokeError):
                runner.validate_log(lines, errors, V180)

    def test_other_or_unspecified_builds_get_no_startup_allowance(self):
        with self.assertRaises(runner.SmokeError):
            runner.validate_log([STARTUP, self.REJECTED], [self.REJECTED])
        for version in (None, V140, "1.20.1-1.7.0-dev", "1.20.1-1.8.1-dev"):
            with self.subTest(version=version), self.assertRaises(runner.SmokeError):
                runner.validate_log([STARTUP, self.REJECTED], [self.REJECTED], version)

    def test_restart_cycle_audits_its_process_log_with_the_artifact_build(self):
        with tempfile.TemporaryDirectory() as directory:
            station, observations = run_restart_cycle(Path(directory), V180, [STARTUP])
        self.assertEqual("00000000-0000-0001-0000-000200000003", station[0])
        self.assertEqual("PASS", observations["result"])
        self.assertEqual([], observations["expected_fault_errors"])
        self.assertEqual(1, observations["log_counts"]["project_warning_count"])

    def test_restart_cycle_refuses_a_repeated_hold_or_another_build(self):
        for version, startup in ((V180, [STARTUP, STARTUP]), (V140, [STARTUP])):
            with self.subTest(version=version, startup=startup), tempfile.TemporaryDirectory() as directory:
                with self.assertRaisesRegex(runner.SmokeError, "Blocking log findings differ"):
                    run_restart_cycle(Path(directory), version, startup)
                observations = json.loads((Path(directory) / "evidence/restart/observations.json")
                                          .read_text(encoding="utf-8"))
                self.assertEqual("FAIL", observations["result"])


class StationRegistryShapeTest(unittest.TestCase):
    STATION = "00000001-0000-0002-0000-000300000004"

    def test_root_schema_follows_only_the_exact_v180_build(self):
        self.assertEqual(5, runner.station_root_schema(V180))
        for version in (None, V140, "1.20.1-1.7.0-dev", "1.20.1-1.8.1-dev"):
            with self.subTest(version=version):
                self.assertEqual(2, runner.station_root_schema(version))

    def test_new_station_requires_its_build_root_schema_and_creation_receipt(self):
        runner.validate_new_station(station_document(V180), self.STATION, V180)
        runner.validate_new_station(station_document(), self.STATION, V140)
        runner.validate_new_station(station_document(), self.STATION)
        for document, version in ((station_document(V180), V140), (station_document(V180), None),
                                  (station_document(), V180)):
            with self.subTest(schema=document["schema_version"], version=version), \
                    self.assertRaisesRegex(runner.SmokeError, "Initial native station shape"):
                runner.validate_new_station(document, self.STATION, version)
        for change in ({"reservations": [{}]}, {"stations": []}):
            with self.subTest(change=change), self.assertRaisesRegex(runner.SmokeError, "Initial native station shape"):
                runner.validate_new_station(dict(station_document(V180), **change), self.STATION, V180)
        moved = station_document(V180)
        moved["stations"][0]["orbit_body"] = runner.HOST + ":earth"
        for document, station in ((moved, self.STATION), (station_document(V180), str(runner.uuid.UUID(int=1)))):
            with self.subTest(station=station), self.assertRaisesRegex(runner.SmokeError, "creation receipt"):
                runner.validate_new_station(document, station, V180)

    def test_v180_clock_may_only_advance_with_its_exact_shape(self):
        baseline = station_document(V180, 100)
        for tick in (100, 101, 10**12):
            runner.validate_unchanged_station(station_document(V180, tick), baseline, V180)
        self.assertEqual(station_document(V180, 100), baseline)

        def changed(mutate):
            document = station_document(V180, 160)
            mutate(document)
            return document

        for document in (station_document(V180, 99), changed(lambda value: value.pop("orbital_clock")),
                         changed(lambda value: value["orbital_clock"].update(schema_version=2)),
                         changed(lambda value: value["orbital_clock"].update(extra=1)),
                         changed(lambda value: value["orbital_clock"].update(logical_tick=True)),
                         changed(lambda value: value["orbital_clock"].update(logical_tick=160.0)),
                         changed(lambda value: value.update(orbital_clock=[1, 160]))):
            with self.subTest(clock=document.get("orbital_clock")), \
                    self.assertRaisesRegex(runner.SmokeError, "orbital clock"):
                runner.validate_unchanged_station(document, baseline, V180)
        for mutate in (lambda value: value.update(warp_energy=[{}]), lambda value: value.update(schema_version=4),
                       lambda value: value["stations"][0].update(landing_pad=[8, 65, 8]),
                       lambda value: value.update(extra="new")):
            with self.subTest(mutate=mutate), self.assertRaisesRegex(runner.SmokeError, "station authority"):
                runner.validate_unchanged_station(changed(mutate), baseline, V180)

    def test_other_builds_keep_exact_equality_even_for_a_clock(self):
        baseline = station_document(V180, 100)
        for version in (None, V140, "1.20.1-1.8.1-dev"):
            with self.subTest(version=version):
                runner.validate_unchanged_station(copy.deepcopy(baseline), baseline, version)
                with self.assertRaisesRegex(runner.SmokeError, "station authority"):
                    runner.validate_unchanged_station(station_document(V180, 101), baseline, version)

    def test_restart_cycle_checks_the_saved_root_with_the_artifact_build(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(runner.SmokeError, "orbital clock"):
                run_restart_cycle(Path(directory), V180, [STARTUP], station_document(V180, 99))


if __name__ == "__main__":
    unittest.main()
