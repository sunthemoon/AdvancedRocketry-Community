"""Generate deterministic K06 inputs, not candidate-load or campaign acceptance evidence.

Run from the repository root with ``python -B -m scripts.campaign_prep.k06_packs``.
Only a new output directory is written. No world, JVM or source file is modified.
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import io
import json
import re
import zipfile
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA = "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/"
HOST = "advancedrocketrycommunity"
CANDIDATE = "9172113ad4ecfb9047432f02b97d240fc06130b6"
EVIDENCE = Path("D:/GitHub/ARCE-Task-Evidence")
DEFAULT_JAR = EVIDENCE / "root-queue/C5/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar"
DEFAULT_OUTPUT = EVIDENCE / "final-campaign/9172113ad/fixtures/K06"
FIXED_TIME = (1980, 1, 1, 0, 0, 0)
ID_LIMIT, BODY_LIMIT, NAME_LIMIT, REFRESH_TICKS = 128, 128, 48, 100
STATION_NAME = "K06 station " + "n" * 36
WARP_TARGET = "k06:warp_target"
PLANETARY_ACCEPT = "Accepted planetary catalog generation {} with {} bodies, {} routes and {} anchors"
PLANETARY_REJECT = "Rejected planetary catalog; last valid generation remains active: {}"

# Markers resolve to actual source lines and hashes in the generated manifest.
REFS = {
    "planetary": ("celestial/data/PlanetaryDefinitionReloadListener.java", "Accepted planetary catalog generation"),
    "rejection": ("celestial/data/PlanetaryDefinitionReloadListener.java", "Rejected planetary catalog;"),
    "resource_id": ("celestial/data/PlanetaryDefinitionReloadListener.java", "definition resource ID exceeds its bound"),
    "count": ("celestial/data/PlanetaryDefinitionReloadListener.java", 'errors.add(directory + ": resource count exceeds'),
    "id_limit": ("celestial/model/BoundedCelestialCodecs.java", "MAX_RESOURCE_LOCATION_CHARS = 128"),
    "body_limit": ("celestial/service/CelestialCatalog.java", "MAX_BODIES = 128"),
    "body_codec": ("celestial/model/CelestialDefinitionCodec.java", "ResourceLocation id = fields.read"),
    "baseline": ("celestial/service/CelestialCatalog.java", "Catalog must define Earth, Moon, and Space"),
    "anchor": ("travel/route/model/RouteAnchor.java", "Unknown route anchor type"),
    "unknown": ("travel/route/service/RouteCatalog.java", "references missing body"),
    "routes": ("travel/route/model/RouteDefinition.java", "RAW_CODEC ="),
    "route_readback": ("celestial/command/PlanetaryRouteCommands.java", "Planetary route generation="),
    "fuel": ("travel/route/service/TravelFuelFormula.java", "long massCost ="),
    "discovery": ("rocket/server/RocketFlightService.java", "RocketFlightPlanCode.DISCOVERY_REQUIRED"),
    "surface": ("celestial/model/CelestialBodyDefinition.java", "Definition eligibility only"),
    "name": ("station/model/StationLimits.java", "MAX_NAME_LENGTH = 48"),
    "refresh": ("rocket/menu/RocketNavigation.java", "CATALOG_REFRESH_TICKS = 100"),
    "bindings": ("celestial/binding/PlanetaryBindings.java", "cannot change its recorded Level binding"),
    "binding_limit": ("celestial/binding/PlanetaryBindings.java", "Planetary lifetime bindings exceed"),
    "warp_class": ("station/warp/WarpCostClass.java", "return from.isPresent()"),
    "warp_defaults": ("station/warp/WarpSettings.java", "DEFAULTS ="),
    "warp_commit": ("station/warp/StationWarpService.java", "current.quote().costClass() != confirmed.costClass()"),
    "warp_missing": ("station/warp/StationWarpService.java", "StationManagementCode.WARP_TARGET_UNAVAILABLE"),
    "tag": ("atmosphere/server/ServerLevelVolumeWorldView.java", "state.is(ModBlockTags.ATMOSPHERE_SEALING)"),
    "satellite": ("satellite/model/SatelliteDefinition.java", "RAW_CODEC ="),
    "satellite_log": ("satellite/service/SatelliteDefinitionReloadListener.java", "Accepted satellite catalog generation"),
    "sky": ("celestial/visual/SkyProfile.java", "Unsupported sky profile schema"),
    "sky_limit": ("celestial/visual/SkyProfile.java", "MAX_BYTES = 16_384"),
    "byte_limit": ("celestial/data/BoundedDefinitionJson.java", '"definition exceeds " + maxBytes'),
    "sky_reject": ("client/sky/SkyProfileReloadListener.java", "Rejected sky profiles; previous presentation retained"),
    "sky_missing": ("celestial/visual/SkySelection.java", "SkyProfile profile = profiles.get"),
}


def json_bytes(value: object) -> bytes:
    # Same JSON serialization as the v1.3 runner's write_json, including LF on Windows.
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")


def digest(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def resource(directory: str, identity: str) -> str:
    namespace, path = identity.split(":", 1)
    return f"data/{namespace}/{directory}/{path}.json"


def metadata(description: str) -> dict:
    return {"pack": {"pack_format": 15, "description": description}}


def deterministic_zip(files: dict[str, object]) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED) as archive:
        for name in sorted(files):
            if name.startswith("/") or "\\" in name or any(p in ("", ".", "..") for p in name.split("/")):
                raise ValueError("Unsafe archive member: " + name)
            entry = zipfile.ZipInfo(name, FIXED_TIME)
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, json_bytes(files[name]))
    return output.getvalue()


def code_references(root: Path = ROOT) -> dict:
    result = {}
    for key, (relative, marker) in REFS.items():
        path = JAVA + relative
        raw = (root / path).read_bytes()
        lines = raw.decode("utf-8").splitlines()
        line = next((i for i, value in enumerate(lines, 1) if marker in value), None)
        if line is None:
            raise ValueError(f"Source citation changed: {path}: {marker}")
        result[key] = {"path": path, "line": line, "excerpt": lines[line - 1].strip(), "sha256": digest(raw)}
    return result


def load_candidate(path: Path, expect_sha256: str) -> dict[str, dict]:
    if re.fullmatch(r"[0-9a-f]{64}", expect_sha256) is None:
        raise ValueError("Expected SHA-256 must be 64 lowercase hexadecimal characters")
    raw = path.read_bytes()
    if digest(raw) != expect_sha256:
        raise ValueError("Candidate JAR SHA-256 does not match --expect-sha256")
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        return {name: json.loads(archive.read(name)) for name in sorted(archive.namelist())
                if name.startswith(f"data/{HOST}/") and name.endswith(".json")
                and name.split("/")[2] in ("celestial_bodies", "travel_routes", "satellite_definitions")}


def logical_body(identity: str, parent: str = HOST + ":earth") -> dict:
    return {"schema_version": 2, "id": identity, "parent": parent,
            "gravity_multiplier": 0, "atmosphere": {"pressure": 0, "breathable": False,
            "temperature_kelvin": 3, "profile": HOST + ":vacuum"},
            "orbit": {"distance": 1, "period_ticks": 1, "inclination_degrees": 0},
            "visual_profile": HOST + ":space", "capabilities": {"landable": False,
            "orbitable": True, "gas_giant": False}, "solar_intensity": 1, "radiation": 0,
            "environment_effects": False, "discovery_required": False}


@dataclass
class Pack:
    name: str
    rows: list[str]
    files: dict
    outcome: str
    reason: str
    values: dict
    readback: list[str]
    refs: list[str]
    prerequisites: str = "C4 defaults, no other K06 packs; fresh disposable baseline copy with a valid active catalog and only C4's 11 lifetime body bindings."
    side: str = "server-data"


def pack(name, rows, files, *, outcome="accepted", reason=PLANETARY_ACCEPT,
         values=None, readback=(), refs=("planetary",), prerequisites=None, side="server-data") -> Pack:
    files = copy.deepcopy(files)
    files.setdefault("pack.mcmeta", metadata("K06 " + name))
    item = Pack(name, rows.split(), files, outcome, reason, values or {}, list(readback), list(refs), side=side)
    if prerequisites is not None:
        item.prerequisites = prerequisites
    return item


def route_packs(baseline: dict, root: Path = ROOT) -> list[Pack]:
    moon_path = resource("travel_routes", HOST + ":earth_moon")
    original = copy.deepcopy(baseline[moon_path])
    valid = dict(original, distance_units=75)
    invalid = copy.deepcopy(valid)
    invalid["to"]["type"] = HOST + ":invalid_anchor"
    unknown = copy.deepcopy(valid)
    unknown["to"]["body_id"] = "k06:unknown_body"
    common = ["reload; capture generation, counts and log; reopen the same rocket quote",
              "arce celestial route advancedrocketrycommunity:earth_moon; record distance/endpoints",
              "record mass, gravityAffectsFuels, propulsion/fuel catalog and the server's actual fuel quote"]
    items = []
    for name, route, reason, refs in (
        ("route_baseline", original, PLANETARY_ACCEPT, ["planetary", "routes"]),
        ("route_valid", valid, PLANETARY_ACCEPT, ["planetary", "routes", "fuel", "route_readback"]),
        ("route_invalid_reference", invalid, "Unknown route anchor type " + HOST + ":invalid_anchor", ["anchor", "rejection"]),
        ("route_unknown_body", unknown, "references missing body k06:unknown_body", ["unknown", "rejection"]),
    ):
        refused = name in ("route_invalid_reference", "route_unknown_body")
        items.append(pack(name, "B03 B06", {moon_path: route}, outcome="refused" if refused else "accepted",
                          reason=reason, refs=refs, values={"route": route, "fuel_quote": "to_be_observed",
                          "distance_change_from_C4": route["distance_units"] - original["distance_units"]},
                          readback=common + (["previous valid generation, graph and quote must remain active; restore valid pack before restart"] if refused else [])))
    mars_path = resource("celestial_bodies", HOST + ":mars")
    for locked in (True, False):
        body = dict(copy.deepcopy(baseline[mars_path]), discovery_required=locked)
        items.append(pack("route_locked" if locked else "route_discovery_rule_open", "B06 E01 E02",
                          {mars_path: body}, values={"body_id": body["id"], "discovery_required": locked,
                          "route_id": HOST + ":earth_mars", "distance_units": 80,
                          "conditional_flight_result": "DISCOVERY_REQUIRED" if locked else "discovery check allows; other checks still apply"},
                          readback=["use an undiscovered Mars copy; never erase existing discovery or earned balances",
                                    "record held-menu generation, lock reason and actual quote before/after reload"],
                          refs=["body_codec", "discovery", "planetary"]))
    seeds = root / "src/test/resources/data" / HOST
    body = json.loads((seeds / "test_celestial_bodies/test_mars.json").read_text(encoding="utf-8"))
    route = json.loads((seeds / "test_travel_routes/test_earth_mars.json").read_text(encoding="utf-8"))
    items.append(pack("data_only_test_mars", "B03 B06", {
        resource("celestial_bodies", body["id"]): body, resource("travel_routes", route["id"]): route},
        values={"body": body, "route": route, "is_builtin_mars": False, "adds_dimension": False,
                "fuel_quote": "to_be_observed; test Level does not exist in C4"},
        readback=["expect one extra body/route; arce celestial route advancedrocketrycommunity:test_earth_mars",
                  "check test_mars identity separately from built-in mars; no Java or dimension definition is supplied",
                  "observe UI labels and unavailable-Level refusal; catalog acceptance does not prove travel is possible",
                  "for a removed selection, remove this pack after holding its selection, reload and record refusal; an invalid unknown-body reload cannot create an active unknown destination"],
        refs=["body_codec", "surface", "planetary", "route_readback"]))
    return items


def catalog_packs(baseline: dict) -> list[Pack]:
    count = sum("/celestial_bodies/" in name for name in baseline)
    items = []
    for total in (100, BODY_LIMIT, BODY_LIMIT + 1):
        files = {resource("celestial_bodies", f"k06:body_{i:03d}"): logical_body(f"k06:body_{i:03d}")
                 for i in range(total - count)}
        items.append(pack(f"catalog_{total}", "E05", files,
                          outcome="refused" if total > BODY_LIMIT else "accepted",
                          reason=f"celestial_bodies: resource count exceeds {BODY_LIMIT}" if total > BODY_LIMIT else PLANETARY_ACCEPT,
                          values={"total_body_count": total, "C4_body_count": count, "added_body_count": len(files),
                                  "body_ids_added": [value["id"] for value in files.values()],
                                  "catalog_refresh_ticks": REFRESH_TICKS, "station_name": STATION_NAME},
                          readback=["read total loaded bodies, not ZIP file count; base C4 definitions/routes remain",
                                    "cycle every node; new bodies are orbitable logical bodies with no surface Level or routes",
                                    "K08 must create a permitted station with the manifest's 48-character name; no name field exists in the body codec",
                                    "read the effective full-catalog cooldown and retry ticks; no client/GPU evidence is produced here",
                                    "129-body case is refusal-only; confirm last valid pair is retained"],
                          refs=["count", "body_limit", "body_codec", "baseline", "refresh", "name", "binding_limit"]))
    for length in (ID_LIMIT, ID_LIMIT + 1):
        identity = "k06:" + "l" * (length - len("k06:"))
        items.append(pack(f"catalog_id_{length}", "E05", {resource("celestial_bodies", identity): logical_body(identity)},
                          outcome="refused" if length > ID_LIMIT else "accepted",
                          reason="definition resource ID exceeds its bound or is empty" if length > ID_LIMIT else PLANETARY_ACCEPT,
                          values={"body_id": identity, "id_characters": length, "total_body_count": count + 1,
                                  "station_name": STATION_NAME, "station_name_characters": NAME_LIMIT},
                          readback=["128 includes namespace and colon; inspect focused node, details and exact identity",
                                    "129 is refused by resource-name validation before body decoding; preserve last valid pair",
                                    "the 48-character station name is a K08 setup value, not an unsupported body JSON field"],
                          refs=["resource_id", "id_limit", "body_codec", "name", "binding_limit"]))
    return items


def station_packs() -> list[Pack]:
    path = resource("celestial_bodies", WARP_TARGET)
    base = logical_body(WARP_TARGET)
    changed = logical_body(WARP_TARGET, HOST + ":tau_ceti")
    removal = metadata("K06 station target removal")
    # As in v140_migration_fixture.removal_resources, filter lower-priority definitions.
    removal["filter"] = {"block": [{"namespace": "k06", "path": re.escape("celestial_bodies/warp_target.json")}]}
    common = {"station_origin_body": HOST + ":earth", "target": WARP_TARGET,
              "default_in_system_cost": 2_000_000, "default_interstellar_cost": 8_000_000,
              "station_name": STATION_NAME, "actual_cost": "to_be_observed from effective C4 common config"}
    return [
        pack("station_baseline", "F05", {path: base}, values=dict(common, cost_class="IN_SYSTEM"),
             readback=["keep this lower-priority pack enabled throughout F05; station orbits Earth",
                       "target needs no discovery, is orbitable and has no Level; record request/confirm and due tick"],
             refs=["body_codec", "warp_class", "warp_defaults", "bindings"]),
        pack("station_target_removal", "F05", {"pack.mcmeta": removal}, values=dict(common, target_present=False),
             readback=["enable above station_baseline then reload before due commit; body absent, catalog still valid",
                       "expect WARP_TARGET_UNAVAILABLE when due; original orbit and energy unchanged",
                       "disable only this overlay and reload; no confirmation resurrects; perform two orderly restarts"],
             refs=["planetary", "warp_missing", "bindings"],
             prerequisites="station_baseline enabled below this overlay; pending Earth -> k06:warp_target warp; no other K06 packs."),
        pack("station_cost_change", "F05", {path: changed}, values=dict(common, cost_class_before="IN_SYSTEM",
             cost_class_after="INTERSTELLAR", parent_before=HOST + ":earth", parent_after=HOST + ":tau_ceti"),
             readback=["enable above station_baseline then reload before due commit; target remains, parent/root changes",
                       "both effective costs must differ; station has at least the larger cost, no in-motion rocket/elevator or other refusal",
                       "expect WARP_QUOTE_CHANGED, no debit/relocation; common config is not edited or reloaded by this pack",
                       "disable overlay, reload, obtain fresh consent, and perform two orderly restarts"],
             refs=["planetary", "warp_class", "warp_defaults", "warp_commit", "bindings"],
             prerequisites="station_baseline enabled below this overlay; pending Earth -> k06:warp_target warp; energy >= both effective costs.")]


def api_packs() -> list[Pack]:
    items = []
    for enabled in (True, False):
        items.append(pack("atmosphere_tag_enabled" if enabled else "atmosphere_tag_disabled", "D04", {
            "pack.mcmeta": metadata("Boundary reload fixture"),
            f"data/{HOST}/tags/blocks/atmosphere_sealing.json": {
                "replace": False, "values": ["arce_adapter_test:state_boundary"] if enabled else []}},
            outcome="to_be_observed",
            reason="tag JSON matches runner tag_pack; actual tag merge acceptance to_be_observed with compatibility fixture installed",
            values={"boundary": "arce_adapter_test:state_boundary", "enabled": enabled},
            readback=["install K01's compatible consumer; record registration receipt and resolved tag membership after reload",
                      "use K21's Moon room and measure vent/boundary state; empty additive values remove only this pack's contribution",
                      "without the fixture block the required tag entry can fail; not an uninstalled-consumer setup"],
            refs=["tag"], prerequisites="C4 plus K01 compatibility fixture and K21 Moon room; use only one tag variant."))
    identity = "arce_adapter_test:research_payload"
    original = {"schema_version": 1, "id": identity, "mission_duration_ticks": 400, "research_yield": 137,
                "discovery_cost": 11, "allowed_targets": [HOST + ":earth", HOST + ":moon"]}
    changed = dict(original, mission_duration_ticks=20, research_yield=211)
    for name, definition in (("satellite_payload", original), ("satellite_payload_changed", changed)):
        items.append(pack(name, "D08 D09", {"pack.mcmeta": metadata("Satellite payload fixture"),
                          resource("satellite_definitions", identity): definition},
                          reason="Accepted satellite catalog generation {} with {} definitions",
                          values={"definition": definition, "old_mission_values": "captured at mission start; read back under lock"},
                          readback=["record payload registration, satellite generation and effective definition values",
                                    "start mission on original, hold menu, replace with changed variant, reload and record stale-intent refusal",
                                    "read old mission's captured values and a fresh mission's changed values; no replayed claim award",
                                    "K18 supplies bounded stale/range probes; this pack sends no intents"],
                          refs=["satellite", "satellite_log"],
                          prerequisites="C4 plus K01 registered payload, K21 terminal scene; use one payload variant; planetary catalog valid."))
    return items


def sky_packs() -> list[Pack]:
    # K06's source package also calls for E06 presentation reload variants. These are
    # client resource packs, never server data packs or environment authorities.
    identity = "k06:campaign_sky"
    profile = {"schema_version": 1, "day_color": 0x775544, "night_color": 0x111122,
               "fog_color": 0x665544, "sun_color": 0xFFEEDD, "sun_radius": 4,
               "sun_opacity": 1, "star_brightness": 0.75, "fog_start": 0.1,
               "fog_end": 128, "sound_volume": 0}
    name = "assets/k06/celestial_visuals/campaign_sky.json"
    items = []
    for variant in ("valid", "invalid_schema", "oversized", "missing"):
        value = copy.deepcopy(profile)
        if variant == "invalid_schema":
            value["schema_version"] = 2
        if variant == "oversized":
            value["padding"] = "x" * 16_384
        # Override Mars's existing profile, so selection of a new profile ID does not
        # require a server body change or turn a visual test into an environment test.
        path = f"assets/{HOST}/celestial_visuals/mars.json"
        files = {} if variant == "missing" else {name: value, path: value}
        if variant == "missing":
            meta = metadata("K06 missing Mars profile")
            meta["filter"] = {"block": [{"namespace": HOST, "path": re.escape("celestial_visuals/mars.json")}]}
            files["pack.mcmeta"] = meta
        refusal = variant in ("invalid_schema", "oversized")
        items.append(pack("sky_" + variant, "E06", files, side="client-resources",
                          outcome="refused" if refusal else "accepted",
                          reason=("Unsupported sky profile schema" if variant == "invalid_schema" else
                                  "definition exceeds 16384 UTF-8 bytes" if variant == "oversized" else
                                  "client profile candidate accepted; missing profile uses presentation fallback"),
                          values={"profile_id": identity, "overridden_profile": HOST + ":mars", "max_profile_bytes": 16_384},
                          readback=["install in client resourcepacks, not world/datapacks; reload client resources",
                                    "capture lastAccepted/diagnostics and Mars day/night appearance on real GPUs; no visual pass claimed",
                                    "invalid/oversized retains previous presentation; missing fallback and exact diagnostics to_be_observed"],
                          refs=["sky", "sky_limit", "byte_limit", "sky_reject", "sky_missing"], prerequisites="C4 client, one sky variant; keep server data unchanged."))
    return items


def campaign_values(baseline: dict) -> dict:
    routes = [value for name, value in sorted(baseline.items()) if "/travel_routes/" in name]
    return {"C4_body_ids": sorted(value["id"] for name, value in baseline.items() if "/celestial_bodies/" in name),
            "C4_routes": routes,
            "E01_surface_itinerary": [HOST + ":" + body for body in ("earth", "mars", "earth", "venus", "earth")],
            "E01_route_legs": [HOST + ":" + route for route in ("earth_mars", "earth_mars", "earth_venus", "earth_venus")],
            "E01_distances": [80, 80, 95, 95], "direct_mars_venus_definition": False,
            "server_quotes": "to_be_observed; no rocket mass, fuel settings or propulsion configuration is assumed",
            "fuel_formula_reference": "fuel", "station_name_48": STATION_NAME,
            "E01_E02_builtin_mission": baseline[resource("satellite_definitions", HOST + ":data_satellite")],
            "catalog_refresh_ticks": REFRESH_TICKS}


def resource_counts(baseline: dict, item: Pack) -> dict:
    """Count prospective winning data files; not a codec or live publication check."""
    current = dict(baseline)
    if item.name.startswith("station_") and item.name != "station_baseline":
        current[resource("celestial_bodies", WARP_TARGET)] = logical_body(WARP_TARGET)
    filters = item.files["pack.mcmeta"].get("filter", {}).get("block", [])
    for path in list(current):
        _, namespace, relative = path.split("/", 2)
        if any(re.fullmatch(rule["namespace"], namespace) and re.fullmatch(rule["path"], relative)
               for rule in filters):
            del current[path]
    current.update({path: value for path, value in item.files.items() if path.startswith("data/")})
    return {directory: sum(f"/{directory}/" in path for path in current)
            for directory in ("celestial_bodies", "travel_routes", "satellite_definitions")}


def generate(output: Path, baseline: dict, root: Path = ROOT, *, candidate_sha256: str) -> dict:
    citations = code_references(root)
    packs = route_packs(baseline, root) + catalog_packs(baseline) + station_packs() + api_packs() + sky_packs()
    blobs = {}
    entries = []
    for item in packs:
        name = item.name + ".zip"
        raw = deterministic_zip(item.files)
        blobs[name] = raw
        entries.append({"path": name, "sha256": digest(raw), "bytes": len(raw), "rows": item.rows,
                        "side": item.side, "expected_outcome": {"load": item.outcome, "reason": item.reason,
                        "basis": "source inspection only", "observed_on_candidate": False},
                        "values": item.values, "prerequisites": item.prerequisites,
                        "candidate_resource_counts": resource_counts(baseline, item),
                        "load_check": item.readback, "code_references": item.refs,
                        "members": [{"path": path, "sha256": digest(json_bytes(value))}
                                    for path, value in sorted(item.files.items())]})
    manifest = {"schema_version": 1, "package": "K06", "candidate_commit": CANDIDATE,
                "candidate_jar_sha256": candidate_sha256, "candidate_loads_run": False,
                "installation": "Use separate complete baseline copies for catalog variants: removed IDs retain lifetime bindings (max 128). Use one variant at a time; station overlays additionally need station_baseline below them. Never use refusal packs as setup or restart with an invalid initial catalog.",
                "zip_format": {"compression": "stored", "timestamp": list(FIXED_TIME), "sorted_members": True},
                "campaign_values": campaign_values(baseline), "code_references": citations, "packs": entries}
    blobs["manifest.json"] = json_bytes(manifest)
    blobs["SHA256SUMS.txt"] = "".join(f"{digest(raw)}  {name}\n" for name, raw in sorted(blobs.items())).encode("ascii")
    # Refuse existing output, rather than overwriting fixtures another worker may own.
    output.mkdir(parents=True, exist_ok=False)
    for name, raw in sorted(blobs.items()):
        (output / name).write_bytes(raw)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate-jar", type=Path, default=DEFAULT_JAR)
    parser.add_argument("--expect-sha256", required=True, help="SHA-256 of the selected candidate JAR")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    manifest = generate(args.output, load_candidate(args.candidate_jar, args.expect_sha256),
                        candidate_sha256=args.expect_sha256)
    print(f"Generated {len(manifest['packs'])} deterministic K06 packs: {args.output}")
    print("Candidate load checks remain to be observed under the heavy-JVM lock.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
