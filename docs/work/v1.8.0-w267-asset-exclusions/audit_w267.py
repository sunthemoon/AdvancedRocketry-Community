"""W267 (R295) read-only audit of the remaining IMPORT exclusions.

Run from any directory, with the repository whose plan should be audited:

    python -X utf8 -B docs/work/v1.8.0-w267-asset-exclusions/audit_w267.py --base a67d55dab [--output result.json]
        [--upstream <pinned original Advanced Rocketry checkout>]

It compares the plan at --base (read with `git show`) with the working-tree plan, and checks that:

1. exactly the 41 R295 files change their first-match rule, each from IMPORT to EXCLUDE, every other legacy asset keeps
   an identical first-match rule, no rule is unused and no asset is unmatched (production first-match cross-check);
2. no tracked model, MTL, OBJ, JSON, Java, import plan or provenance record references the 41 files, and no file
   under src/ carries one of their SHA-256 values;
3. the replacements named in the reasons exist in the tree today;
4. the 35 retained NEW textures of W263 section 5 are unchanged and still unbound.

Nothing is written except the optional --output JSON. Exit 0 only when every check passes.
"""
from __future__ import annotations

import argparse
import csv
import fnmatch
import hashlib
import importlib
import io
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))

PLAN = "docs/work/v1.8.0-asset-plan.csv"
LEDGER = "docs/work/v1.8.0-content-ledger.csv"
MANIFEST = "legacy-manifest/assets.csv"
UPSTREAM_ASSET_ROOT = "src/main/resources/assets/advancedrocketry/"
NAMESPACE = "assets/advancedrocketrycommunity/"
JAVA = "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/"
GENERATED = "src/generated/v1.8/resources/" + NAMESPACE
IMPORTED = "src/imported/v1.8/resources/" + NAMESPACE

GROUPS = {
    "X1": ["textures/blocks/combustion.png", "textures/blocks/smallconnector.png"],
    "X2": ["textures/gui/borderhorizontal.png", "textures/gui/bordervertical.png",
           "textures/gui/buttons/buttongeneric.png", "textures/gui/buttons/buttongeneric_hover.png",
           "textures/gui/framebg.png", "textures/gui/genericstation.png", "textures/gui/guibuttonbluefuture.png",
           "textures/gui/guibuttonbluefuture_hover.png", "textures/gui/jetpack.png", "textures/gui/jetpackdisabled.png",
           "textures/gui/jetpackhover.png", "textures/gui/recticle.png", "textures/gui/selection.png",
           "textures/gui/starrybg.png"],
    "X3": ["models/block/models/bottomtank.mtl", "models/block/models/bottomtank.obj",
           "models/block/models/endtank.mtl", "models/block/models/toptank.mtl", "models/block/models/toptank.obj",
           "models/item/models/advRocketmotor.mtl", "models/item/models/advRocketmotor.obj",
           "models/item/models/bottomTank.mtl", "models/item/models/bottomTank.obj", "models/item/models/endTank.mtl",
           "models/item/models/middleTank.mtl", "models/item/models/middleTank.obj",
           "models/item/models/rocketmotor.mtl", "models/item/models/rocketmotor.obj",
           "models/item/models/sawBlade.mtl", "models/item/models/sawBlade.obj",
           "models/item/models/topTank.mtl", "models/item/models/topTank.obj"],
    "J0": ["models/jetpack.obj", "textures/models/jetpack.png"],
    "U1": ["textures/gui/space_boots.png", "textures/gui/space_chestplate.png", "textures/gui/space_helmet.png",
           "textures/gui/space_leggings.png"],
    "T1": ["textures/blocks/blastbrickfront_active.png"],
}
LASER_RULES = ("textures/items/basiclasergun.png", "models/item/models/basicLaserGun.*",
               "textures/models/basiclasergun.png")

# W263 section 5, with its SHA-256 values: retained NEW textures that no projected model or runtime code binds.
NEW_TEXTURES = {
    "block/blast_brick": "4ca1ab5af3cffad860e80e68e475f19b61f9a147600de0ec5e5736e92d3ddcfa",
    "block/arc_furnace": "1f3b64d694487f5c4de1e6198690ea61d5ffd4c6c0ee3cc0c0b60665da01c0d8",
    "block/airlock_door_bottom": "f60dbae0c139fc9f44194c416ac6cb76b9a09b677594307ed99ca3210c054d62",
    "block/airlock_door_top": "9d022bffebc285a2e87ba3a75528052e5d2bc3683d9ec1686c5f6afa516bb5d1",
    "block/suit_workstation_top": "df87136c38bb16f94efb29ca83af7a9538b7f2a0a2f454b4c2d0dc404fe3d851",
    "block/thermite_torch": "562393a306790a3f2d40edab58562ebcdf01d955d008dc5d6ce96cf090b3f39d",
    "item/carbon_scrubber_cartridge": "008cddc50c44df338db05a011e95b2a2c1341e8c78cc6294ae672eddd8115cd3",
    "item/thermite": "f40ee6913b3f55cf1ca39ca9c65379c2a09203be1b257925235c36c466ecca44",
    "item/airlock_door": "6df187b04fa34a4df3530014cbb85b05ca767f7bad352ab579e94f564a7e2d95",
    "item/seal_detector": "40d23a8a6842d577e61720a46963cf587d347a8584eb892156dc9ead4e65fa3d",
    "item/jetpack": "75ec65b676f4e9f5711aa5a8a07e115526db43805325698e36283310b4c3e74f",
    "item/jackhammer": "771eaebb41fcc90c4d9622f8c807705cca67d51ca3b64784e7ee4057cfb53563",
    "item/hover_upgrade": "68d732046f9f2ba4d87bf31d8a74a142fa4c1b4ab8d3e3558a56c1e448d2441a",
    "item/flight_speed_upgrade": "f0fe2c70fe4aea556e8af811299505e88d92df5c29e7d6f51d3d1d492cf078d9",
    "item/tracking_circuit": "e78f2712e9cc2ad6de13c4ffd35906adbda6480da407f13f7741c1b39e8b0884",
    "block/advanced_rocket_motor_side": "f5107786e181c27eeb06ee653b733e875a14331007132d58376247d2ab517d68",
    "block/advanced_rocket_motor_top": "0cd09ac7f658f22532c78b9e66ea35ab603e2ee99aa0f61fbed44db0a39b7a4d",
    "block/advanced_rocket_motor_bottom": "740be5843eac7fe79087be6c216fee3c387b39ff12b5f7ec1f6f928a40f4a96c",
    "block/bipropellant_rocket_motor_side": "c6aca4d0e969f7e0489e35c9d92dac37959716b7d9d26bc97394cbda0b2376ed",
    "block/bipropellant_rocket_motor_top": "aee4e7c0398d21894dd80a78f284fe127528406c9db7975b59d4e28de4f04ce7",
    "block/bipropellant_rocket_motor_bottom": "14518ee5dffe0889110fe6cd832602a7b8ed2ffd68404d26482d62381ab04c61",
    "block/advanced_bipropellant_rocket_motor_side": "26caa7d04482fbba1ba92e33a05221588b4ddf5f643a2448954992aa8e5b9e6a",
    "block/advanced_bipropellant_rocket_motor_top": "aee4e7c0398d21894dd80a78f284fe127528406c9db7975b59d4e28de4f04ce7",
    "block/advanced_bipropellant_rocket_motor_bottom": "14518ee5dffe0889110fe6cd832602a7b8ed2ffd68404d26482d62381ab04c61",
    "block/nuclear_rocket_motor_side": "af44ad6c790a14e298b235b244ad5c9be62960e3b847373c047525e77aab30dc",
    "block/nuclear_rocket_motor_top": "601dd93c46d0674d8e928c89a8b70ce0256b2bb5b720fdd2f93fbe3a67687fcf",
    "block/nuclear_rocket_motor_bottom": "bb4b084b9067f496f0bee743192d2061a78262e03d1843403fefe90f1e3d0df4",
    "block/bipropellant_fuel_tank_side": "c6aca4d0e969f7e0489e35c9d92dac37959716b7d9d26bc97394cbda0b2376ed",
    "block/bipropellant_fuel_tank_top": "aee4e7c0398d21894dd80a78f284fe127528406c9db7975b59d4e28de4f04ce7",
    "block/bipropellant_fuel_tank_bottom": "aee4e7c0398d21894dd80a78f284fe127528406c9db7975b59d4e28de4f04ce7",
    "block/oxidizer_fuel_tank_side": "dbc81965b91afb01a2defccf2004b2b140dc303508ff2c77b3645f3de8e9be33",
    "block/oxidizer_fuel_tank_top": "f74c434c5b69a544f0c382d04914e16859fbefc99d38b5af4e2c5a3a6236f52d",
    "block/oxidizer_fuel_tank_bottom": "f74c434c5b69a544f0c382d04914e16859fbefc99d38b5af4e2c5a3a6236f52d",
    "block/saw_blade": "98a7326b340a56d1e80b1a2404efa540da1ed2f0d37fd3b6fa1d89906eaca3f1",
    "block/landing_pad": "0485c02e998660e22d79ed361554e20505089396d3a96b4992b145db6647fc2a",
}


def git(*args: str) -> bytes:
    return subprocess.run(["git", "-C", str(ROOT), *args], check=True, capture_output=True).stdout


def rows_of(text: str) -> list[dict[str, str]]:
    return list(csv.DictReader(io.StringIO(text)))


def first_match(rules: list[dict[str, str]], assets: list[str]) -> dict[str, int | None]:
    result: dict[str, int | None] = {}
    for asset in assets:
        result[asset] = next((index for index, rule in enumerate(rules)
                              if fnmatch.fnmatchcase(asset, rule["pattern"])), None)
    return result


def rule_key(rule: dict[str, str]) -> tuple[str, ...]:
    return tuple(rule[column] for column in ("pattern", "handling", "plan", "units", "reason"))


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def tracked() -> list[str]:
    return git("ls-files", "-z").decode("utf-8").split("\0")[:-1]


def plan_checks(base: str, plan_path: Path, assets: list[str], failures: list[str]) -> dict:
    expected = sorted(asset for files in GROUPS.values() for asset in files)
    base_rules = rows_of(git("show", f"{base}:{PLAN}").decode("utf-8"))
    rules = rows_of(plan_path.read_text(encoding="utf-8"))
    before, after = first_match(base_rules, assets), first_match(rules, assets)
    changed = []
    for asset in assets:
        old = None if before[asset] is None else rule_key(base_rules[before[asset]])
        new = None if after[asset] is None else rule_key(rules[after[asset]])
        if old != new:
            changed.append({"asset": asset, "base": old, "draft": new})
    changed_assets = sorted(item["asset"] for item in changed)
    if changed_assets != expected:
        failures.append(f"changed first-match files differ from the 41 R295 files: "
                        f"extra {sorted(set(changed_assets) - set(expected))}, "
                        f"missing {sorted(set(expected) - set(changed_assets))}")
    for item in changed:
        if item["base"] is None or item["base"][1] != "IMPORT":
            failures.append(f"{item['asset']} was not IMPORT at the base")
        if item["draft"] is None or item["draft"][1:3] != ("EXCLUDE", "-") or "R295" not in item["draft"][4]:
            failures.append(f"{item['asset']} is not an R295 EXCLUDE in the draft")
    unmatched = [asset for asset in assets if after[asset] is None]
    used = {index for index in after.values() if index is not None}
    unused = [rule["pattern"] for index, rule in enumerate(rules) if index not in used]
    if unmatched:
        failures.append(f"assets match no rule: {unmatched}")
    if unused:
        failures.append(f"rules match no asset first: {unused}")
    orders = [rule["order"] for rule in rules]
    if orders != [str(number) for number in range(1, len(rules) + 1)]:
        failures.append("draft orders are not 1..N")
    exact_or_group = {}
    for index, rule in enumerate(rules):
        files = sorted(asset for asset, owner in after.items() if owner == index)
        raw = sorted(asset for asset in assets if fnmatch.fnmatchcase(asset, rule["pattern"]))
        if any(asset in expected for asset in files):
            exact_or_group[rule["pattern"]] = files
            if raw != files:
                failures.append(f"R295 rule {rule['pattern']!r} also matches files governed by earlier rules")
    for pattern in LASER_RULES:
        old = [rule_key(rule) for rule in base_rules if rule["pattern"] == pattern]
        new = [rule_key(rule) for rule in rules if rule["pattern"] == pattern]
        if old != new or len(new) != 1:
            failures.append(f"laser-gun rule {pattern!r} changed")
    review_before = sorted(rule_key(rule) for rule in base_rules if rule["handling"] == "REVIEW")
    review_after = sorted(rule_key(rule) for rule in rules if rule["handling"] == "REVIEW")
    if review_before != review_after:
        failures.append("a REVIEW rule changed")
    ledger = {row["unit_id"] for row in rows_of((ROOT / LEDGER).read_text(encoding="utf-8"))}
    owners = {}
    for item in changed:
        base_owners = item["base"][3] if item["base"] else "-"
        draft_owners = item["draft"][3] if item["draft"] else "-"
        for owner in ([] if draft_owners == "-" else draft_owners.split()):
            if owner not in ledger:
                failures.append(f"{item['asset']}: owner {owner} is not a ledger unit")
            if owner not in ([] if base_owners == "-" else base_owners.split()):
                failures.append(f"{item['asset']}: owner {owner} was added, not trimmed")
        owners[item["asset"]] = {"base": base_owners, "draft": draft_owners}
    production = None
    if plan_path.resolve() == (ROOT / PLAN).resolve():
        # "import" is a keyword, so the package is loaded by name.
        inputs = importlib.import_module("tools.import.v180_import_checks").load_inputs(ROOT)
        mismatched = [asset for asset in assets
                      if inputs.handling(asset) != (None if after[asset] is None else rules[after[asset]]["handling"])]
        if mismatched:
            failures.append(f"production first-match handling disagrees for {mismatched}")
        production = {"assets": len(assets), "mismatched": len(mismatched)}

    def counts(chosen: dict[str, int | None], plan_rules: list[dict[str, str]]) -> dict[str, int]:
        result: dict[str, int] = {}
        for index in chosen.values():
            if index is not None:
                handling = plan_rules[index]["handling"]
                result[handling] = result.get(handling, 0) + 1
        return dict(sorted(result.items()))

    removed = sorted({rule["pattern"] for rule in base_rules} - {rule["pattern"] for rule in rules})
    added = sorted({rule["pattern"] for rule in rules} - {rule["pattern"] for rule in base_rules})
    modified = sorted(rule["pattern"] for rule in rules
                      if rule["pattern"] in {r["pattern"] for r in base_rules}
                      and rule_key(rule) != next(rule_key(r) for r in base_rules if r["pattern"] == rule["pattern"]))
    return {
        "base_rules": len(base_rules), "draft_rules": len(rules),
        "base_counts": counts(before, base_rules), "draft_counts": counts(after, rules),
        "changed_files": len(changed), "unused_rules": unused, "unmatched_assets": unmatched,
        "rules_removed": removed, "rules_added": added, "rules_modified": modified,
        "r295_rules": exact_or_group, "owners": owners, "production_cross_check": production,
        "laser_rules_unchanged": all(f"laser-gun rule {p!r} changed" not in failures for p in LASER_RULES),
        "review_rules_unchanged": "a REVIEW rule changed" not in failures,
    }


RESOURCE_FILE = re.compile(r"^(src/(?:main|generated/[^/]+|imported/[^/]+)/resources)/assets/([^/]+)/(.+)$")
OUR_NAMESPACES = {"", "advancedrocketry", "advancedrocketrycommunity", "advancedrocketrycommunity_v180"}
JAVA_LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"')


def variants(asset: str) -> set[str]:
    """The lower-case resource paths under which a legacy file would be packaged or referenced."""
    lowered = asset.lower()
    return {lowered, re.sub(r"^textures/blocks/", "textures/block/", lowered),
            re.sub(r"^textures/items/", "textures/item/", lowered)}


def named(value: str, relative_to: str | None = None) -> set[str]:
    """Resource paths (lower case, below assets/<namespace>/) that one reference value can name."""
    value = value.strip().lower()
    if not value or " " in value or len(value) > 200:
        return set()
    namespace, _, path = value.rpartition(":")
    if namespace not in OUR_NAMESPACES:
        return set()
    path = path.lstrip("/")
    if relative_to is not None and ":" not in value:
        path = f"{relative_to}/{path}"
    found = {path}
    if not path.startswith("textures/"):
        found.add("textures/" + path)  # also read as a sprite ID, such as gui/jetpack, block/x or models/x
    if re.fullmatch(r"models/[^/]+\.(obj|mtl)", path):  # a 1.12 OBJ model ID, resolved per block or item
        found |= {"models/block/" + path, "models/item/" + path}
    found |= {name + ".png" for name in list(found) if name.startswith("textures/") and not name.endswith(".png")}
    return found


def json_strings(node, key: str = "") -> list[tuple[str, str]]:
    if isinstance(node, dict):
        return [item for name, value in node.items() for item in json_strings(value, name)]
    if isinstance(node, list):
        return [item for value in node for item in json_strings(value, key)]
    return [(key, node)] if isinstance(node, str) else []


def references(path: str, text: str) -> list[tuple[int, str, set[str]]]:
    """(line, raw value, named paths) for every structural reference in one tracked file."""
    found: list[tuple[int, str, set[str]]] = []
    resource = RESOURCE_FILE.match(path)
    if resource and path.endswith((".mtl", ".obj")):
        folder = resource.group(3).rsplit("/", 1)[0]
        for number, line in enumerate(text.splitlines(), start=1):
            parts = line.split(None, 1)
            if len(parts) == 2 and (parts[0] == "mtllib" or parts[0].startswith("map_") or parts[0] == "bump"):
                found.append((number, parts[1], named(parts[1].split()[-1], folder)))
    elif path.endswith(".json") and (resource or path.startswith(("tools/import/", "docs/provenance/"))):
        try:
            data = json.loads(text)
        except ValueError:
            return [(0, "<unparsable JSON>", set())]
        for key, value in json_strings(data):
            if path.startswith(("tools/import/", "docs/provenance/")):
                # Only import entries count; derivation relatives and notes are not consumers.
                if key == "source_path" and value.startswith(UPSTREAM_ASSET_ROOT):
                    found.append((0, value, {value[len(UPSTREAM_ASSET_ROOT):].lower()}))
                elif key == "target_path" and RESOURCE_FILE.match(value):
                    found.append((0, value, {RESOURCE_FILE.match(value).group(3).lower()}))
            else:
                found.append((0, value, named(value)))
    elif path.endswith(".java"):
        for number, line in enumerate(text.splitlines(), start=1):
            for literal in JAVA_LITERAL.findall(line):
                found.append((number, literal, named(literal)))
    return found


def loose_token(asset: str) -> str:
    """A path fragment for the informational text search, such as gui/jetpack or item/models/topTank."""
    stem = asset.rsplit(".", 1)[0]
    tail = stem.split("/", 1)[1]
    return tail if "/" in tail else stem


def scope_of(path: str) -> str | None:
    if RESOURCE_FILE.match(path) and path.endswith((".json", ".mtl", ".obj", ".mcmeta")):
        return "resources"
    if path.startswith("src/main/java/") and path.endswith(".java"):
        return "generator" if "/datagen/" in path else "runtime-java"
    if path.startswith("tools/import/") and path.endswith(".json"):
        return "import-plan"
    if path.startswith("docs/provenance/") and path.endswith(".json"):
        return "provenance"
    if path.startswith(("src/test/", "tests/")) and path.endswith((".java", ".py", ".json")):
        return "test"
    return None


# Positive controls: live imported files the same scan must find, each through the named kinds of evidence, and with
# a hit in a file of the given suffix, so that a silent parser cannot pass the exclusions vacuously.
# The W260 middle-tank OBJ and MTL are rewritten copies (lower-case references), so they have no byte copy.
CONTROLS = {
    "models/block/models/middletank.obj": ({"resources", "import-plan", "provenance", "target"}, ".json"),
    "models/block/models/middletank.mtl": ({"resources", "import-plan", "provenance", "target"}, ".obj"),
    "textures/blocks/blastbrickfront.png": ({"resources", "import-plan", "provenance", "target", "bytes"}, ".json"),
    "textures/models/centrifuge.png": ({"resources", "import-plan", "provenance", "target", "bytes"}, ".mtl"),
    "models/solar_array.obj": ({"runtime-java", "import-plan", "provenance", "target", "bytes"}, ".java"),
}


def scan(chosen: list[str], files: list[str]) -> dict:
    hashes = {}
    with (ROOT / MANIFEST).open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            hashes[row["source_path"]] = row["sha256"]
    source_hash = {asset: hashes[UPSTREAM_ASSET_ROOT + asset] for asset in chosen}
    wanted = {asset: variants(asset) for asset in chosen}
    loose = {asset: re.compile(r"(?<![A-Za-z0-9_])" + re.escape(loose_token(asset)) + r"(?![A-Za-z0-9_])", re.I)
             for asset in chosen}
    hits: dict[str, dict[str, list[str]]] = {asset: {} for asset in chosen}
    mentions: dict[str, list[str]] = {asset: [] for asset in chosen}
    by_hash: dict[str, list[str]] = {}
    for asset, value in source_hash.items():
        by_hash.setdefault(value, []).append(asset)
    hash_hits: dict[str, list[str]] = {}
    scanned = {"resources": 0, "runtime-java": 0, "generator": 0, "import-plan": 0, "provenance": 0, "test": 0}
    references_seen = 0
    for path in files:
        full = ROOT / path
        if path.startswith("src/") and full.is_file():
            for asset in by_hash.get(sha256(full.read_bytes()), []):
                hash_hits.setdefault(asset, []).append(path)
        scope = scope_of(path)
        if scope is None or not full.is_file():
            continue
        scanned[scope] += 1
        text = full.read_text(encoding="utf-8", errors="replace")
        if scope == "test":
            for asset in chosen:
                if any(variant in text.lower() for variant in wanted[asset]):
                    hits[asset].setdefault("test", []).append(path)
            continue
        found = references(path, text)
        references_seen += len(found)
        for number, value, paths in found:
            for asset in chosen:
                if paths & wanted[asset]:
                    hits[asset].setdefault(scope, []).append(f"{path}:{number}: {value}")
        for number, line in enumerate(text.splitlines(), start=1):
            if line.lstrip().startswith("#") and path.endswith((".mtl", ".obj")):
                continue  # Blender header comments name the .blend file, not a resource
            for asset in chosen:
                if loose[asset].search(line) and len(mentions[asset]) < 12:
                    mentions[asset].append(f"{path}:{number}")
    roots = sorted({RESOURCE_FILE.match(path).group(1) for path in files if RESOURCE_FILE.match(path)})
    per_file = {}
    for asset in chosen:
        present = sorted({f"{root}/{NAMESPACE}{variant}" for root in roots for variant in wanted[asset] | {asset}
                          if (ROOT / root / NAMESPACE / variant).exists()})
        per_file[asset] = {"source_sha256": source_hash[asset],
                           "consumer_references": {s: h for s, h in hits[asset].items() if s != "test"},
                           "test_files_naming_it": hits[asset].get("test", []),
                           "loose_mentions_first_12": mentions[asset],
                           "byte_copies_under_src": hash_hits.get(asset, []),
                           "packaged_targets": present}
    return {"files_scanned": scanned, "structural_references_parsed": references_seen, "resource_roots": roots,
            "per_file": per_file}


def evidence_kinds(entry: dict) -> set[str]:
    kinds = set(entry["consumer_references"])
    kinds |= {"target"} if entry["packaged_targets"] else set()
    return kinds | ({"bytes"} if entry["byte_copies_under_src"] else set())


def reference_checks(files: list[str], failures: list[str]) -> dict:
    expected = sorted(asset for group in GROUPS.values() for asset in group)
    result = scan(expected, files)
    for asset, entry in result["per_file"].items():
        if entry["consumer_references"]:
            failures.append(f"{asset} is referenced by {entry['consumer_references']}")
        if entry["byte_copies_under_src"]:
            failures.append(f"{asset} bytes are present at {entry['byte_copies_under_src']}")
        if entry["packaged_targets"]:
            failures.append(f"{asset} has a packaged target {entry['packaged_targets']}")
    controls = scan(sorted(CONTROLS), files)["per_file"]
    for asset, (kinds, suffix) in CONTROLS.items():
        entry = controls[asset]
        missing = kinds - evidence_kinds(entry)
        suffix_hit = any(hit.split(":", 1)[0].endswith(suffix)
                         for hits in entry["consumer_references"].values() for hit in hits)
        entry["required"] = sorted(kinds)
        entry["result"] = "PASS" if not missing and suffix_hit else "FAIL"
        if missing:
            failures.append(f"positive control {asset} lacks evidence kinds {sorted(missing)}")
        if not suffix_hit:
            failures.append(f"positive control {asset} has no structural reference in a {suffix} file")
    result["positive_controls"] = controls
    return result


def contains(path: str, needle: str, failures: list[str], label: str) -> str:
    text = (ROOT / path).read_text(encoding="utf-8") if (ROOT / path).is_file() else ""
    if needle not in text:
        failures.append(f"replacement {label}: {path} lacks {needle!r}")
        return "FAIL"
    return "PASS"


def replacement_checks(files: list[str], upstream: Path | None, failures: list[str]) -> dict:
    results: dict[str, str] = {}

    def check(label: str, path: str, needle: str) -> None:
        results[f"{label}: {path} contains {needle}"] = contains(path, needle, failures, label)

    def exists(label: str, path: str) -> None:
        ok = (ROOT / path).is_file()
        results[f"{label}: {path} exists"] = "PASS" if ok else "FAIL"
        if not ok:
            failures.append(f"replacement {label}: {path} is missing")

    check("X1 combustion", GENERATED + "models/block/rocket_motor.json",
          '"model": "advancedrocketrycommunity:models/block/models/rocketmotor.obj"')
    check("X1 combustion", IMPORTED + "models/block/models/rocketmotor.mtl",
          "map_Kd advancedrocketrycommunity:block/combustion_classic")
    exists("X1 combustion", IMPORTED + "textures/block/combustion_classic.png")
    check("X1 smallconnector", JAVA + "client/model/ObjModelFixture.java", 'ModIdentity.id("models/solar_array.obj")')
    exists("X1 smallconnector", IMPORTED + "models/solar_array.obj")
    exists("X1 smallconnector", GENERATED + "textures/block/solar_array_front.png")
    check("X1 smallconnector", GENERATED + "models/block/solar_array_panel.json",
          '"top": "advancedrocketrycommunity:block/solar_array_panel"')
    exists("X1 smallconnector", GENERATED + "models/block/power_output_plug.json")
    client = [path for path in files if path.startswith(JAVA + "client/") and path.endswith(".java")]
    binding = []
    for path in [path for path in files if path.startswith("src/main/java/") and path.endswith(".java")]:
        text = (ROOT / path).read_text(encoding="utf-8")
        gui = [literal for literal in JAVA_LITERAL.findall(text)
               if any(name.startswith("textures/gui/") for name in named(literal))]
        if gui or (path in client and re.search(r"\.blit\(|setShaderTexture", text)):
            binding.append(f"{path}: {gui}")
    results[f"X2: no main Java literal names a mod textures/gui file, and none of {len(client)} client files calls "
            "blit( or setShaderTexture"] = "PASS" if not binding else "FAIL"
    if binding:
        failures.append(f"replacement X2: GUI texture binding code in {binding}")
    check("X2 HUD", JAVA + "client/LifeSupportHud.java", "SuitHudClient.Summary suit = SuitHudClient.current();")
    check("X2 HUD", JAVA + "client/LifeSupportHud.java", "graphics.fill(0, 0, bounds.width(), bounds.height()")
    check("X2 jetpack states", JAVA + "equipment/motion/SuitMotionService.java",
          "RATE_LIMITED, IGNORED, THRUST, ENABLED_NORMAL, ENABLED_HOVER, DISABLED, MODE_NORMAL, MODE_HOVER")
    check("X2 station", JAVA + "client/station/StationControllerScreen.java", "addRenderableWidget(Button.builder(")
    check("X2 classic", JAVA + "client/ClassicControllerScreen.java", "graphics.fill(leftPos, topPos")
    check("X2 star map", JAVA + "client/starmap/RocketStarMap.java", "Original code-drawn map hosted by the flight screen")
    for item, block, mesh in (("rocket_motor", "rocket_motor", "rocketmotor.obj"),
                              ("advanced_rocket_motor", "advanced_rocket_motor", "advrocketmotor.obj"),
                              ("rocket_fuel_tank", "rocket_fuel_tank", "middletank.obj"),
                              ("bipropellant_fuel_tank", "bipropellant_fuel_tank", "middletank.obj"),
                              ("oxidizer_fuel_tank", "oxidizer_fuel_tank", "middletank.obj"),
                              ("saw_blade", "saw_blade", "sawblade.obj")):
        check("X3", GENERATED + f"models/item/{item}.json", f'"parent": "advancedrocketrycommunity:block/{block}"')
        check("X3", GENERATED + f"models/block/{block}.json",
              f'"model": "advancedrocketrycommunity:models/block/models/{mesh}"')
    for tank in ("rocket_fuel_tank", "bipropellant_fuel_tank", "oxidizer_fuel_tank", "nuclear_fuel_tank"):
        found = [path for path in files if path.endswith(f"/blockstates/{tank}.json")]
        bad = [path for path in found if "tankstates" in (ROOT / path).read_text(encoding="utf-8")]
        results[f"X3: {tank} blockstates have no tankstates property ({len(found)} files)"] = \
            "PASS" if found and not bad else "FAIL"
        if not found or bad:
            failures.append(f"replacement X3: {tank} blockstates {found} {bad}")
    check("X3 assembled rockets", JAVA + "client/RocketEntityRenderer.java", "getBlockRenderer().renderSingleBlock(")
    check("J0", JAVA + "client/equipment/SuitComponentLayer.java",
          'ModIdentity.id("textures/entity/suit_components.png")')
    exists("J0", GENERATED + "textures/entity/suit_components.png")
    check("U1", JAVA + "equipment/workstation/SuitWorkstationMenu.java", "new SimpleContainer(2)")
    check("U1", JAVA + "client/equipment/SuitWorkstationScreen.java", "g.fill(leftPos + slot.x - 1")
    check("T1", GENERATED + "models/block/arc_furnace.json", '"front": "advancedrocketrycommunity:block/blastbrickfront"')
    check("T1", JAVA + "machine/classic/runtime/ClassicControllerBlock.java", "{ builder.add(FACING); }")
    arc = json.loads((ROOT / GENERATED / "blockstates/arc_furnace.json").read_text(encoding="utf-8"))
    keys = sorted(arc.get("variants", {}))
    ok = keys == ["facing=east", "facing=north", "facing=south", "facing=west"]
    results["T1: arc_furnace blockstate has only the four facing variants"] = "PASS" if ok else "FAIL"
    if not ok:
        failures.append(f"replacement T1: arc_furnace variants {keys}")
    jetpack = {}
    if upstream is not None:
        text = (upstream / UPSTREAM_ASSET_ROOT / "models/jetpack.obj").read_text(encoding="utf-8")
        material = False
        faces = without = 0
        for line in text.splitlines():
            if line.startswith("usemtl "):
                material = True
            elif line.startswith("f "):
                faces += 1
                without += 0 if material else 1
        jetpack = {"faces": faces, "faces_before_any_usemtl": without,
                   "usemtl_lines": sum(1 for line in text.splitlines() if line.startswith("usemtl "))}
        results["J0: legacy jetpack.obj has faces without a material statement"] = "PASS" if without else "FAIL"
        if not without:
            failures.append("J0: the legacy jetpack.obj gives every face a material")
    return {"checks": results, "legacy_jetpack_obj": jetpack}


def texture_slots(node, inside: bool = False) -> list[str]:
    """String values under a model's "textures" object (or list), the only JSON places that bind a sprite."""
    if isinstance(node, dict):
        return [value for key, child in node.items() for value in texture_slots(child, inside or key == "textures")]
    if isinstance(node, list):
        return [value for child in node for value in texture_slots(child, inside)]
    return [node] if inside and isinstance(node, str) else []


def new_texture_checks(files: list[str], failures: list[str]) -> dict:
    """Bindings are texture slots, MTL maps and runtime Java literals; model IDs and parents are not bindings."""
    slots: list[tuple[str, str]] = []
    for path in files:
        if not RESOURCE_FILE.match(path):
            continue
        text = (ROOT / path).read_text(encoding="utf-8", errors="replace") if path.endswith((".json", ".mtl")) else ""
        if path.endswith(".json"):
            try:
                slots += [(path, value) for value in texture_slots(json.loads(text))]
            except ValueError:
                failures.append(f"{path} is not valid JSON")
        elif path.endswith(".mtl"):
            slots += [(path, line.split(None, 1)[1]) for line in text.splitlines()
                      if line.startswith("map_") and len(line.split(None, 1)) == 2]
    literals = [(path, literal) for path in files
                if path.startswith("src/main/java/") and path.endswith(".java") and "/datagen/" not in path
                for literal in JAVA_LITERAL.findall((ROOT / path).read_text(encoding="utf-8"))]
    result = {}
    for name, recorded in NEW_TEXTURES.items():
        path = GENERATED + f"textures/{name}.png"
        current = sha256((ROOT / path).read_bytes()) if (ROOT / path).is_file() else None
        sprite = {f"textures/{name}.png"}
        bindings = sorted({f"{source}: {value}" for source, value in slots + literals if named(value) & sprite})
        result[path] = {"sha256": current, "matches_w263": current == recorded, "bindings": bindings,
                        "proposed": "keep as history pending human review" if not bindings else "keep as live art"}
        if current != recorded:
            failures.append(f"{path} differs from W263's SHA-256")
        if bindings:
            failures.append(f"{path} is bound by {bindings}")
    # W263's live-art controls: the same binding search must find these (a model slot, an MTL-free item layer and a
    # runtime Java literal).
    controls = {}
    for name in ("block/quartz_crucible", "block/suit_workstation_side", "item/basic_laser_gun",
                 "entity/suit_components"):
        sprite = {f"textures/{name}.png"}
        controls[name] = sorted({f"{source}: {value}" for source, value in slots + literals if named(value) & sprite})
        if not controls[name]:
            failures.append(f"live-art control {name} was not found bound")
    return {"retained": result, "live_art_controls": controls}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base", required=True, help="the branch base whose plan the draft changes")
    parser.add_argument("--plan", type=Path, default=ROOT / PLAN, help="the plan to audit (default: working tree)")
    parser.add_argument("--upstream", type=Path, help="pinned original Advanced Rocketry checkout (optional)")
    parser.add_argument("--output", type=Path, help="write the full result JSON here")
    args = parser.parse_args(argv)
    failures: list[str] = []
    manifest = (ROOT / MANIFEST).read_bytes()
    if manifest != git("show", f"{args.base}:{MANIFEST}"):
        failures.append("the legacy manifest differs from the base")
    with io.StringIO(manifest.decode("utf-8")) as handle:
        assets = sorted(row["source_path"][len(UPSTREAM_ASSET_ROOT):] for row in csv.DictReader(handle)
                        if row["source_path"].startswith(UPSTREAM_ASSET_ROOT))
    files = tracked()
    result = {
        "head": git("rev-parse", "HEAD").decode().strip(), "base": git("rev-parse", args.base).decode().strip(),
        "plan_sha256": sha256(args.plan.read_bytes()), "legacy_assets": len(assets),
        "first_match": plan_checks(args.base, args.plan, assets, failures),
        "references": reference_checks(files, failures),
        "replacements": replacement_checks(files, args.upstream, failures),
        "new_textures": new_texture_checks(files, failures),
    }
    result["failures"] = failures
    result["result"] = "PASS" if not failures else "FAIL"
    if args.output:
        args.output.write_bytes((json.dumps(result, indent=1) + "\n").encode("utf-8"))
    summary = {key: result["first_match"][key] for key in ("base_rules", "draft_rules", "base_counts", "draft_counts",
                                                           "changed_files", "unused_rules", "unmatched_assets",
                                                           "production_cross_check")}
    print(json.dumps({"result": result["result"], "first_match": summary, "failures": failures}, indent=1))
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
