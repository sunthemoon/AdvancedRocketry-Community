"""Offline fixture checks only: these tests do not execute the Java codecs or load C4."""
from __future__ import annotations

import copy
import io
import json
import re
import tempfile
import unittest
import zipfile
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from scripts.campaign_prep import k06_packs as k06
from scripts import run_v130_atmosphere_boundary_smoke as atmosphere
from scripts import run_v130_satellite_payload_smoke as payload


def tracked_baseline() -> dict:
    """Use tracked JSON for offline unit inputs; real generation requires the hashed C4 JAR."""
    roots = [k06.ROOT / "src/main/resources"] + sorted((k06.ROOT / "src/generated").glob("v*/resources"))
    result = {}
    for root in roots:
        for directory in ("celestial_bodies", "travel_routes", "satellite_definitions"):
            for path in sorted((root / "data" / k06.HOST / directory).glob("*.json")):
                result[path.relative_to(root).as_posix()] = json.loads(path.read_text(encoding="utf-8"))
    return result


def overlay(base: dict, pack: k06.Pack) -> dict:
    """A test-only resource projection, not a substitute for Forge resource loading."""
    filters = pack.files["pack.mcmeta"].get("filter", {}).get("block", [])
    result = {}
    for path, value in base.items():
        _, namespace, relative = path.split("/", 2)
        if not any(re.fullmatch(rule["namespace"], namespace) and re.fullmatch(rule["path"], relative)
                   for rule in filters):
            result[path] = copy.deepcopy(value)
    result.update({path: copy.deepcopy(value) for path, value in pack.files.items() if path.startswith("data/")})
    return result


class K06PackTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.baseline = tracked_baseline()
        cls.packs = {p.name: p for p in (k06.route_packs(cls.baseline) + k06.catalog_packs(cls.baseline)
                                       + k06.station_packs() + k06.api_packs() + k06.sky_packs())}

    def test_declared_limits_are_current_source_values(self):
        for file, constant, expected in (
            ("celestial/model/BoundedCelestialCodecs.java", "MAX_RESOURCE_LOCATION_CHARS", k06.ID_LIMIT),
            ("travel/route/model/RouteLimits.java", "MAX_RESOURCE_LOCATION_CHARS", k06.ID_LIMIT),
            ("celestial/service/CelestialCatalog.java", "MAX_BODIES", k06.BODY_LIMIT),
            ("station/model/StationLimits.java", "MAX_NAME_LENGTH", k06.NAME_LIMIT),
            ("rocket/menu/RocketNavigation.java", "CATALOG_REFRESH_TICKS", k06.REFRESH_TICKS),
            ("celestial/visual/SkyProfile.java", "MAX_BYTES", 16_384),
        ):
            with self.subTest(constant=constant, file=file):
                source = (k06.ROOT / k06.JAVA / file).read_text(encoding="utf-8")
                match = re.search(r"\b" + constant + r"\s*=\s*([\d_]+)\s*;", source)
                self.assertIsNotNone(match)
                self.assertEqual(int(match[1].replace("_", "")), expected)
        self.assertEqual((k06.ID_LIMIT, k06.BODY_LIMIT, k06.NAME_LIMIT, k06.REFRESH_TICKS), (128, 128, 48, 100))

    def test_c4_shaped_tracked_inputs(self):
        self.assertEqual(sum("/celestial_bodies/" in p for p in self.baseline), 11)
        self.assertEqual(sum("/travel_routes/" in p for p in self.baseline), 12)
        self.assertEqual(sum("/satellite_definitions/" in p for p in self.baseline), 5)

    def test_route_valid_and_refusal_fields(self):
        path = k06.resource("travel_routes", k06.HOST + ":earth_moon")
        original = self.baseline[path]
        self.assertEqual(self.packs["route_baseline"].files[path], original)
        expected = dict(original, distance_units=75)
        self.assertEqual(self.packs["route_valid"].files[path], expected)
        invalid = self.packs["route_invalid_reference"].files[path]
        expected_invalid = copy.deepcopy(expected)
        expected_invalid["to"]["type"] = k06.HOST + ":invalid_anchor"
        self.assertEqual(invalid, expected_invalid)
        unknown = self.packs["route_unknown_body"].files[path]
        self.assertEqual(unknown["to"]["body_id"], "k06:unknown_body")
        self.assertNotIn(k06.resource("celestial_bodies", "k06:unknown_body"), self.baseline)
        self.assertEqual(self.packs["route_invalid_reference"].outcome, "refused")
        self.assertEqual(self.packs["route_unknown_body"].outcome, "refused")

    def test_locked_and_open_discovery_do_not_edit_progress(self):
        path = k06.resource("celestial_bodies", k06.HOST + ":mars")
        for name, required in (("route_locked", True), ("route_discovery_rule_open", False)):
            with self.subTest(name=name):
                item = self.packs[name]
                self.assertEqual(item.files[path], dict(self.baseline[path], discovery_required=required))
                self.assertEqual(set(item.files), {path, "pack.mcmeta"})
                self.assertEqual(item.outcome, "accepted")

    def test_test_mars_preserves_exact_data_only_seeds(self):
        item = self.packs["data_only_test_mars"]
        root = k06.ROOT / "src/test/resources/data" / k06.HOST
        for directory, name in (("test_celestial_bodies", "test_mars"), ("test_travel_routes", "test_earth_mars")):
            seed = json.loads((root / directory / (name + ".json")).read_text(encoding="utf-8"))
            path = k06.resource(directory.removeprefix("test_"), seed["id"])
            self.assertEqual(item.files[path], seed)
        self.assertEqual(len(item.files), 3)
        self.assertFalse(item.values["is_builtin_mars"])
        self.assertFalse(any("/dimension/" in path for path in item.files))
        self.assertEqual(item.values["route"]["distance_units"], 225)

    def test_catalog_counts_include_baseline_and_preserve_graph(self):
        for count in (100, 128, 129):
            with self.subTest(count=count):
                item = self.packs[f"catalog_{count}"]
                merged = overlay(self.baseline, item)
                bodies = {v["id"]: v for p, v in merged.items() if "/celestial_bodies/" in p}
                self.assertEqual(len(bodies), count)
                self.assertEqual(item.values["total_body_count"], count)
                self.assertEqual(item.values["added_body_count"], count - 11)
                for p, v in self.baseline.items():
                    self.assertEqual(merged[p], v)
                for p, body in item.files.items():
                    if "/celestial_bodies/" in p:
                        self.assertIn(body["parent"], bodies)
                        self.assertNotIn("level", body)
                        self.assertFalse(body["capabilities"]["landable"])
                        self.assertGreater(body["orbit"]["distance"], 0)
                self.assertEqual(item.outcome, "refused" if count == 129 else "accepted")

    def test_identifier_boundary_and_station_name(self):
        for length in (128, 129):
            item = self.packs[f"catalog_id_{length}"]
            identity = item.values["body_id"]
            self.assertEqual(len(identity), length)
            self.assertEqual(item.files[k06.resource("celestial_bodies", identity)]["id"], identity)
            self.assertEqual(item.outcome, "accepted" if length == 128 else "refused")
        self.assertEqual(len(k06.STATION_NAME), 48)
        self.assertTrue(k06.STATION_NAME.isascii())
        for item in self.packs.values():
            for path, value in item.files.items():
                if "/celestial_bodies/" in path:
                    self.assertNotIn("name", value, "station names must not be invented body codec fields")

    def test_station_removal_filter_and_cost_class_inputs(self):
        setup = self.packs["station_baseline"]
        path = k06.resource("celestial_bodies", k06.WARP_TARGET)
        initial = overlay(self.baseline, setup)
        removed = overlay(initial, self.packs["station_target_removal"])
        self.assertNotIn(path, removed)
        self.assertEqual(removed, self.baseline)
        changed = overlay(initial, self.packs["station_cost_change"])
        self.assertEqual(set(changed), set(initial))
        expected = dict(initial[path], parent=k06.HOST + ":tau_ceti")
        self.assertEqual(changed[path], expected)
        self.assertFalse(changed[path]["discovery_required"])
        self.assertTrue(changed[path]["capabilities"]["orbitable"])
        self.assertNotIn("level", changed[path], "reparenting must not change the recorded Level binding")
        costs = (k06.ROOT / k06.JAVA / "station/warp/WarpSettings.java").read_text(encoding="utf-8")
        self.assertIn("new WarpSettings(true, 2_000_000, 8_000_000)", costs)

    def test_atmosphere_bytes_match_runner_builder(self):
        with tempfile.TemporaryDirectory(prefix="arce-k06-tag-") as temporary:
            root = Path(temporary)
            capture = root / "capture"
            capture.mkdir()
            runner = SimpleNamespace(server=root)
            for enabled in (True, False):
                atmosphere.AtmosphereRun.tag_pack(runner, enabled, capture)
                directory = root / "world/datapacks/arce_boundary_fixture"
                item = self.packs["atmosphere_tag_enabled" if enabled else "atmosphere_tag_disabled"]
                actual = {p.relative_to(directory).as_posix(): p.read_bytes() for p in directory.rglob("*") if p.is_file()}
                self.assertEqual(actual, {p: k06.json_bytes(v) for p, v in item.files.items()})
                self.assertEqual(item.outcome, "to_be_observed")

    def test_payload_original_registration_and_changed_runner_bytes(self):
        path = k06.resource("satellite_definitions", payload.DEFINITION)
        original = self.packs["satellite_payload"].files[path]
        self.assertEqual((original["mission_duration_ticks"], original["research_yield"], original["discovery_cost"]), (400, 137, 11))
        fixture = (k06.ROOT / "src/adapterTest/java/io/github/sunthemoon/arceadaptertest/SatellitePayloadFixture.java").read_text(encoding="utf-8")
        self.assertIn('400, 137, 11, List.of(host("earth"), host("moon"))', fixture)
        with tempfile.TemporaryDirectory(prefix="arce-k06-payload-") as temporary:
            root = Path(temporary)
            payload.SatelliteRun.write_override(SimpleNamespace(server=root))
            directory = root / payload.PACK
            actual = {p.relative_to(directory).as_posix(): p.read_bytes() for p in directory.rglob("*") if p.is_file()}
            self.assertEqual(actual, {p: k06.json_bytes(v) for p, v in self.packs["satellite_payload_changed"].files.items()})
        changed = self.packs["satellite_payload_changed"].files[path]
        self.assertEqual((changed["mission_duration_ticks"], changed["research_yield"], changed["discovery_cost"]), (20, 211, 11))

    def test_sky_variants_are_client_resources_not_server_environment(self):
        path = f"assets/{k06.HOST}/celestial_visuals/mars.json"
        valid = self.packs["sky_valid"]
        self.assertEqual(valid.files[path]["schema_version"], 1)
        self.assertEqual(self.packs["sky_invalid_schema"].files[path]["schema_version"], 2)
        self.assertGreater(len(k06.json_bytes(self.packs["sky_oversized"].files[path])), 16_384)
        self.assertEqual(set(self.packs["sky_missing"].files), {"pack.mcmeta"})
        rule = self.packs["sky_missing"].files["pack.mcmeta"]["filter"]["block"][0]
        self.assertIsNotNone(re.fullmatch(rule["path"], "celestial_visuals/mars.json"))
        for name in ("sky_valid", "sky_invalid_schema", "sky_oversized", "sky_missing"):
            self.assertEqual(self.packs[name].side, "client-resources")
            self.assertFalse(any(p.startswith("data/") for p in self.packs[name].files))

    def test_itinerary_uses_actual_c4_routes_and_no_assumed_direct_leg(self):
        values = k06.campaign_values(self.baseline)
        by_id = {v["id"]: v for v in values["C4_routes"]}
        bodies = values["E01_surface_itinerary"]
        for i, identity in enumerate(values["E01_route_legs"]):
            route = by_id[identity]
            self.assertEqual({route["from"]["body_id"], route["to"]["body_id"]}, set(bodies[i:i + 2]))
            self.assertTrue(route["bidirectional"])
            self.assertEqual(route["distance_units"], values["E01_distances"][i])
        self.assertFalse(values["direct_mars_venus_definition"])
        mission = values["E01_E02_builtin_mission"]
        self.assertEqual((mission["mission_duration_ticks"], mission["research_yield"], mission["discovery_cost"]), (200, 120, 100))

    def test_manifest_hashes_citations_and_full_output_determinism(self):
        with tempfile.TemporaryDirectory(prefix="arce-k06-output-") as temporary:
            one, two = Path(temporary) / "one", Path(temporary) / "two"
            manifest = k06.generate(one, self.baseline)
            k06.generate(two, dict(reversed(list(self.baseline.items()))))
            self.assertEqual({p.name: p.read_bytes() for p in one.iterdir()}, {p.name: p.read_bytes() for p in two.iterdir()})
            self.assertEqual(len(manifest["packs"]), len(self.packs))
            self.assertFalse(manifest["candidate_loads_run"])
            checksums = (one / "SHA256SUMS.txt").read_text(encoding="ascii").splitlines()
            self.assertEqual(len(checksums), len(self.packs) + 1)
            self.assertEqual([s.split("  ")[1] for s in checksums], sorted(s.split("  ")[1] for s in checksums))
            for line in checksums:
                sha, relative = line.split("  ")
                self.assertEqual(k06.digest((one / relative).read_bytes()), sha)
            references = manifest["code_references"]
            for reference in references.values():
                raw = (k06.ROOT / reference["path"]).read_bytes()
                self.assertEqual(k06.digest(raw), reference["sha256"])
                self.assertEqual(raw.decode("utf-8").splitlines()[reference["line"] - 1].strip(), reference["excerpt"])
            for item in manifest["packs"]:
                self.assertTrue(item["rows"])
                self.assertTrue(item["load_check"])
                self.assertTrue(item["code_references"])
                self.assertFalse(item["expected_outcome"]["observed_on_candidate"])
                self.assertTrue(set(item["code_references"]).issubset(references))
                raw = (one / item["path"]).read_bytes()
                self.assertEqual(k06.digest(raw), item["sha256"])
                with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                    self.assertEqual(archive.namelist(), sorted(archive.namelist()))
                    for entry, member in zip(archive.infolist(), item["members"]):
                        self.assertEqual(entry.date_time, k06.FIXED_TIME)
                        self.assertEqual(entry.compress_type, zipfile.ZIP_STORED)
                        self.assertEqual(entry.filename, member["path"])
                        self.assertEqual(k06.digest(archive.read(entry)), member["sha256"])

    def test_archive_member_order_and_traversal_rejection(self):
        self.assertEqual(k06.deterministic_zip({"z.json": {}, "a.json": {}}),
                         k06.deterministic_zip({"a.json": {}, "z.json": {}}))
        for bad in ("../escape.json", "/absolute.json", "a\\b.json", "a//b.json"):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                k06.deterministic_zip({bad: {}})

    def test_existing_output_and_unpinned_candidate_are_refused(self):
        with tempfile.TemporaryDirectory(prefix="arce-k06-guard-") as temporary:
            root = Path(temporary)
            sentinel = root / "owned.txt"
            sentinel.write_bytes(b"do not overwrite")
            with self.assertRaises(FileExistsError):
                k06.generate(root, self.baseline)
            self.assertEqual(list(root.iterdir()), [sentinel])
            fake = root / "wrong.jar"
            fake.write_bytes(b"not C4")
            with self.assertRaisesRegex(ValueError, "pinned C4"):
                k06.load_candidate(fake)
            with zipfile.ZipFile(fake, "w") as archive:
                for name, value in self.baseline.items():
                    archive.writestr(name, k06.json_bytes(value))
                archive.writestr("assets/ignored.json", "{}")
            with patch.object(k06, "CANDIDATE_SHA256", k06.digest(fake.read_bytes())):
                self.assertEqual(k06.load_candidate(fake), self.baseline)


if __name__ == "__main__":
    unittest.main()
