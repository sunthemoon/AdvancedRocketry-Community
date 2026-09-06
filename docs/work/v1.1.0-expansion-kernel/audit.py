"""Reproduce the v1.0 destination baseline and validate v1.1 contract samples."""

from __future__ import annotations

from pathlib import Path
import json
import re
import subprocess
import uuid


ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
BASELINE = "a4ae20190e9f9a36b2a3cbabf1ea44b6746bc7d6"
RESOURCE_LOCATION = re.compile(r"^[a-z0-9_.-]+:[a-z0-9/._-]+$")
TARGET_TYPES = {
    "advancedrocketrycommunity:body_surface": "body_id",
    "advancedrocketrycommunity:orbit": "body_id",
    "advancedrocketrycommunity:station": "instance_id",
    "advancedrocketrycommunity:mission": "instance_id",
}


def git(*arguments: str) -> str:
    result = subprocess.run(
        ["git", *arguments],
        cwd=ROOT,
        check=True,
        text=True,
        encoding="utf-8",
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout


def baseline_text(path: str) -> str:
    return git("show", f"{BASELINE}:{path}")


def require_resource_location(value: object) -> str:
    assert isinstance(value, str)
    assert 3 <= len(value) <= 128 and RESOURCE_LOCATION.fullmatch(value)
    return value


def validate_target(value: object) -> None:
    assert isinstance(value, dict)
    target_type = require_resource_location(value.get("type"))
    assert value.get("schema_version") == 1 and target_type in TARGET_TYPES
    identity = TARGET_TYPES[target_type]
    assert set(value) == {"schema_version", "type", identity}
    if identity == "body_id":
        require_resource_location(value[identity])
    else:
        parsed = uuid.UUID(value[identity])
        assert str(parsed) == value[identity]


def validate_anchor(value: object) -> tuple[str, str]:
    assert isinstance(value, dict) and set(value) == {"type", "body_id"}
    anchor_type = require_resource_location(value["type"])
    assert anchor_type in {
        "advancedrocketrycommunity:body_surface",
        "advancedrocketrycommunity:orbit",
    }
    return anchor_type, require_resource_location(value["body_id"])


# The audit reads every baseline value through ``git show``. Requiring the
# implementation branch to remain exactly at that commit would make the
# archived audit stop working as soon as v1.1 is committed.
git("merge-base", "--is-ancestor", BASELINE, "HEAD")
paths = git("ls-tree", "-r", "--name-only", BASELINE).splitlines()
java_paths = [
    path for path in paths
    if path.endswith(".java") and (path.startswith("src/main/") or path.startswith("src/test/"))
]
destination_references = {}
for path in java_paths:
    count = baseline_text(path).count("RocketDestination")
    if count:
        destination_references[path] = count
assert len(destination_references) == 23

flight_codec = baseline_text(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/persistence/RocketFlightNbtCodec.java"
)
for key in (
    "current_body",
    "current_dimension",
    "source_body",
    "destination_body",
    "source_dimension",
    "destination_dimension",
    "destination_station_id",
):
    assert f'"{key}"' in flight_codec

station_codec = baseline_text(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/persistence/StationNbtCodec.java"
)
station_model = baseline_text(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/model/StationRegistryModel.java"
)
catalog = baseline_text(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/service/CelestialCatalog.java"
)
assert '"orbit_body"' in station_codec
assert "Map<StationGridCell, UUID> occupiedCells" in station_model
assert "Optional<StationState> findAt(int x, int z)" in station_model
assert "Map<ResourceKey<Level>, CelestialBodyDefinition> definitionsByLevel" in catalog
assert "byLevel.putIfAbsent(definition.levelKey(), definition)" in catalog

network = baseline_text(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/network/RocketFlightNetwork.java"
)
flight_limits = baseline_text(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/RocketFlightLimits.java"
)
transfer_data = baseline_text(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/persistence/RocketTransferSavedData.java"
)
station_limits = baseline_text(
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/station/model/StationLimits.java"
)
protocol = re.search(r'PROTOCOL_VERSION = "(\d+)"', network).group(1)
flight_schema = int(re.search(r"FLIGHT_DATA_SCHEMA_VERSION = (\d+)", flight_limits).group(1))
transfer_schema = int(re.search(r"TRANSFER_JOURNAL_SCHEMA_VERSION = (\d+)", flight_limits).group(1))
transfer_root_schema = int(re.search(r"ROOT_SCHEMA_VERSION = (\d+)", transfer_data).group(1))
station_state_schema = int(re.search(r"STATE_SCHEMA_VERSION = (\d+)", station_limits).group(1))
station_root_schema = int(re.search(r"REGISTRY_SCHEMA_VERSION = (\d+)", station_limits).group(1))

target_schema = json.loads((OUT / "contracts/travel-target.schema.json").read_text(encoding="utf-8"))
route_schema = json.loads((OUT / "contracts/route-definition.schema.json").read_text(encoding="utf-8"))
assert target_schema["$schema"].endswith("2020-12/schema")
assert route_schema["$schema"].endswith("2020-12/schema")

target_sample = json.loads((OUT / "samples/travel-targets.sample.json").read_text(encoding="utf-8"))
assert target_sample["schema_version"] == 1 and len(target_sample["targets"]) == 4
for target in target_sample["targets"]:
    validate_target(target)

route_sample = json.loads((OUT / "samples/routes.sample.json").read_text(encoding="utf-8"))
assert route_sample["schema_version"] == 1 and len(route_sample["routes"]) <= 512
route_ids = set()
edges = set()
for route in route_sample["routes"]:
    assert set(route) == {
        "schema_version", "id", "from", "to", "distance_units", "bidirectional"
    }
    assert route["schema_version"] == 1 and isinstance(route["bidirectional"], bool)
    route_id = require_resource_location(route["id"])
    assert route_id not in route_ids
    route_ids.add(route_id)
    source, target = validate_anchor(route["from"]), validate_anchor(route["to"])
    assert source != target
    assert 0 <= route["distance_units"] <= 1_000_000
    assert (source, target) not in edges
    edges.add((source, target))
    if route["bidirectional"]:
        assert (target, source) not in edges
        edges.add((target, source))

baseline_distances = {
    route["id"]: route["distance_units"]
    for route in route_sample["routes"]
    if not route["id"].endswith("test_earth_mars")
}
assert baseline_distances == {
    "advancedrocketrycommunity:earth_moon": 50,
    "advancedrocketrycommunity:earth_surface_orbit": 25,
    "advancedrocketrycommunity:moon_earth_orbit": 25,
}

mars = json.loads((OUT / "samples/test-mars-celestial.sample.json").read_text(encoding="utf-8"))
assert require_resource_location(mars["id"]) == "advancedrocketrycommunity:test_mars"
assert all("test_mars" not in baseline_text(path) for path in java_paths)

result = {
    "status": "PASS_V110_CONTRACT_BASELINE",
    "baseline_commit": BASELINE,
    "rocket_destination_reference_files": len(destination_references),
    "rocket_destination_occurrences": sum(destination_references.values()),
    "rocket_destination_references": destination_references,
    "v1_schema": {
        "rocket_network_protocol": protocol,
        "flight_data": flight_schema,
        "transfer_record": transfer_schema,
        "transfer_root": transfer_root_schema,
        "station_state": station_state_schema,
        "station_root": station_root_schema,
    },
    "persistent_resource_location_fields": 6,
    "persistent_station_uuid_field": True,
    "station_orbit_body_already_stable": True,
    "station_region_index_reusable": True,
    "level_index_currently_discards_ambiguity": True,
    "sample_targets": len(target_sample["targets"]),
    "sample_routes": len(route_sample["routes"]),
    "sample_directed_edges": len(edges),
    "test_mars_requires_production_java_change": False,
}
(OUT / "audit-result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, indent=2))
