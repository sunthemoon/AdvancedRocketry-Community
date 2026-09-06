"""Executable expected behavior for the proposed v1.1 contracts."""

from __future__ import annotations

from copy import deepcopy
from pathlib import Path
import hashlib
import json
import re
import unittest
import uuid


ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
RESOURCE_LOCATION = re.compile(r"^[a-z0-9_.-]+:[a-z0-9/._-]+$")
SURFACE = "advancedrocketrycommunity:body_surface"
ORBIT = "advancedrocketrycommunity:orbit"
STATION = "advancedrocketrycommunity:station"
MISSION = "advancedrocketrycommunity:mission"
SPACE = "advancedrocketrycommunity:space"


def require_resource_location(value: object) -> str:
    if not isinstance(value, str) or not 3 <= len(value) <= 128 or not RESOURCE_LOCATION.fullmatch(value):
        raise ValueError("invalid resource location")
    return value


def validate_target(value: object) -> dict:
    if not isinstance(value, dict) or value.get("schema_version") != 1:
        raise ValueError("invalid target schema")
    target_type = require_resource_location(value.get("type"))
    if target_type in {SURFACE, ORBIT}:
        expected = {"schema_version", "type", "body_id"}
        require_resource_location(value.get("body_id"))
    elif target_type in {STATION, MISSION}:
        expected = {"schema_version", "type", "instance_id"}
        parsed = uuid.UUID(value.get("instance_id"))
        if str(parsed) != value.get("instance_id"):
            raise ValueError("non-canonical UUID")
    else:
        raise ValueError("unknown target type")
    if set(value) != expected or len(json.dumps(value, separators=(",", ":")).encode()) > 256:
        raise ValueError("invalid target shape or size")
    return value


def validate_anchor(value: object, bodies: set[str]) -> tuple[str, str]:
    if not isinstance(value, dict) or set(value) != {"type", "body_id"}:
        raise ValueError("invalid route anchor shape")
    anchor = (require_resource_location(value["type"]), require_resource_location(value["body_id"]))
    if anchor[0] not in {SURFACE, ORBIT} or anchor[1] not in bodies:
        raise ValueError("unknown route anchor")
    return anchor


def validate_routes(routes: object, bodies: set[str]) -> int:
    if not isinstance(routes, list) or not routes or len(routes) > 512:
        raise ValueError("route count outside bound")
    identifiers, edges = set(), set()
    for route in routes:
        if not isinstance(route, dict) or set(route) != {
            "schema_version", "id", "from", "to", "distance_units", "bidirectional"
        }:
            raise ValueError("invalid route shape")
        if route["schema_version"] != 1 or not isinstance(route["bidirectional"], bool):
            raise ValueError("invalid route schema")
        route_id = require_resource_location(route["id"])
        if route_id in identifiers:
            raise ValueError("duplicate route id")
        identifiers.add(route_id)
        source, target = validate_anchor(route["from"], bodies), validate_anchor(route["to"], bodies)
        if source == target or not isinstance(route["distance_units"], int) \
                or not 0 <= route["distance_units"] <= 1_000_000:
            raise ValueError("invalid route endpoints or distance")
        directed = [(source, target)]
        if route["bidirectional"]:
            directed.append((target, source))
        for edge in directed:
            if edge in edges:
                raise ValueError("duplicate directed edge")
            edges.add(edge)
    outgoing = {}
    for source, _ in edges:
        outgoing[source] = outgoing.get(source, 0) + 1
    if any(count > 64 for count in outgoing.values()):
        raise ValueError("outgoing edge count outside bound")
    return len(edges)


def target_for_legacy_enum(value: dict) -> dict:
    destination = value.get("destination")
    if destination in {"EARTH", "MOON"}:
        return {"schema_version": 1, "type": SURFACE,
                "body_id": f"advancedrocketrycommunity:{destination.lower()}"}
    if destination == "SPACE_STATION" and value.get("destination_station_id"):
        return {"schema_version": 1, "type": STATION,
                "instance_id": value["destination_station_id"]}
    raise ValueError("unknown or incomplete legacy destination")


def resolve_world_location(value: dict, stations: list[dict]) -> dict | None:
    dimension = value["current_dimension"]
    if dimension == "minecraft:overworld":
        return {"body_id": "advancedrocketrycommunity:earth", "locus": "SURFACE"}
    if dimension == "advancedrocketrycommunity:moon":
        return {"body_id": "advancedrocketrycommunity:moon", "locus": "SURFACE"}
    if dimension != SPACE:
        return None
    x, _, z = value["position"]
    for station in stations:
        minimum_x, minimum_z, maximum_x, maximum_z = station["region"]
        if station["level"] == SPACE and minimum_x <= x <= maximum_x and minimum_z <= z <= maximum_z:
            return {"body_id": station["orbit_body"], "locus": "ORBIT",
                    "instance_id": station["station_id"]}
    return None


class ContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.targets = json.loads((HERE / "samples/travel-targets.sample.json").read_text())["targets"]
        cls.routes = json.loads((HERE / "samples/routes.sample.json").read_text())["routes"]
        cls.migration = json.loads((HERE / "samples/migration-cases.sample.json").read_text())
        cls.bodies = {
            "advancedrocketrycommunity:earth",
            "advancedrocketrycommunity:moon",
            "advancedrocketrycommunity:test_mars",
        }

    def test_positive_targets(self) -> None:
        self.assertEqual(4, len([validate_target(value) for value in self.targets]))

    def test_unknown_and_mixed_target_shapes_fail(self) -> None:
        for mutation in (
            {"schema_version": 1, "type": "example:unknown", "body_id": "example:body"},
            {**self.targets[0], "instance_id": "123e4567-e89b-42d3-a456-426614174000"},
            {**self.targets[2], "body_id": "advancedrocketrycommunity:earth"},
            {**self.targets[0], "schema_version": 2},
        ):
            with self.subTest(mutation=mutation), self.assertRaises((ValueError, TypeError)):
                validate_target(mutation)

    def test_target_identifier_and_encoded_size_bounds(self) -> None:
        oversized = deepcopy(self.targets[0])
        oversized["body_id"] = "a:" + "x" * 127
        with self.assertRaises(ValueError):
            validate_target(oversized)

    def test_route_sample_is_bounded(self) -> None:
        self.assertEqual(8, validate_routes(self.routes, self.bodies))

    def test_duplicate_route_id_fails_atomically(self) -> None:
        duplicate = deepcopy(self.routes)
        duplicate.append(deepcopy(duplicate[0]))
        with self.assertRaises(ValueError):
            validate_routes(duplicate, self.bodies)

    def test_unknown_body_and_equal_endpoint_fail(self) -> None:
        unknown = deepcopy(self.routes)
        unknown[0]["to"]["body_id"] = "example:missing"
        equal = deepcopy(self.routes)
        equal[0]["to"] = deepcopy(equal[0]["from"])
        for routes in (unknown, equal):
            with self.assertRaises(ValueError):
                validate_routes(routes, self.bodies)

    def test_source_evidence_hashes_match(self) -> None:
        for row in self.migration["source_evidence"]:
            self.assertEqual(row["sha256"], hashlib.sha256((ROOT / row["path"]).read_bytes()).hexdigest())

    def test_legacy_destination_mappings(self) -> None:
        cases = [case for case in self.migration["cases"] if case["mode"] == "destination_enum"]
        for case in cases:
            with self.subTest(case=case["id"]):
                actual = validate_target(target_for_legacy_enum(case["legacy"]))
                self.assertEqual(case["expected"]["target"], actual)

    def test_two_space_regions_resolve_distinct_orbits(self) -> None:
        cases = [case for case in self.migration["cases"] if case["id"].startswith("space-position-")
                 and case["expected"]["status"] == "MIGRATED"]
        contexts = [resolve_world_location(case["legacy"], self.migration["stations"]) for case in cases]
        self.assertEqual({"advancedrocketrycommunity:earth", "advancedrocketrycommunity:moon"},
                         {context["body_id"] for context in contexts})
        for case, context in zip(cases, contexts):
            self.assertEqual(case["expected"]["context"], context)
            self.assertEqual(case["expected"]["target"]["instance_id"], context["instance_id"])

    def test_shared_space_gap_never_falls_back_to_earth(self) -> None:
        case = next(case for case in self.migration["cases"] if case["id"] == "space-position-gap")
        self.assertIsNone(resolve_world_location(case["legacy"], self.migration["stations"]))
        self.assertEqual("BLOCKED_PRESERVED", case["expected"]["status"])
        self.assertTrue(case["expected"]["preserve_legacy"])

    def test_unknown_location_remains_blocked(self) -> None:
        case = next(case for case in self.migration["cases"] if case["id"] == "unknown-body")
        self.assertIsNone(resolve_world_location(case["legacy"], self.migration["stations"]))
        self.assertEqual("UNKNOWN_LEGACY_LOCATION", case["expected"]["diagnostic"])


if __name__ == "__main__":
    unittest.main()
