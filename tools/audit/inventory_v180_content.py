#!/usr/bin/env python3
"""Generate or verify the v1.8.0 legacy content inventory.

The inventory lists every player-visible legacy content unit of the pinned
upstream commit: registered blocks and their metadata variants, items and
their variants, materials, fluids, block entities, entities, biomes, world
generation features, enchantments, sound events, advancements, satellites,
missions, commands, configuration keys, network packets and integrations.

Input is a read-only copy of the upstream tree outside this repository. Every
file read must equal its SHA-256 in ``legacy-manifest``; the tool copies no
upstream text except identifiers, display names and line numbers.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
UPSTREAM_REPOSITORY = "https://github.com/Advanced-Rocketry/AdvancedRocketry"
UPSTREAM_COMMIT = "c5cd5af62fc07cd4e0d24f06a16033f181c47c04"
JAVA_ROOT = "src/main/java/zmaster587/advancedRocketry/"
ASSET_ROOT = "src/main/resources/assets/advancedrocketry/"
DEFAULT_OUTPUT = Path("docs/work/v1.8.0-legacy-inventory.json")
SCHEMA = 1
MAX_SOURCE_BYTES = 4 * 1024 * 1024

MAIN = JAVA_ROOT + "AdvancedRocketry.java"
CONFIG = JAVA_ROOT + "api/ARConfiguration.java"
AUDIO = JAVA_ROOT + "util/AudioRegistry.java"
COMMAND = JAVA_ROOT + "command/WorldCommand.java"
LANG = ASSET_ROOT + "lang/en_US.lang"
CRYSTAL = JAVA_ROOT + "block/BlockCrystal.java"
KEYS = JAVA_ROOT + "client/KeyBindings.java"
# Classes whose @SubscribeEvent methods carry gameplay rules (registration plumbing and rejected bridges excluded).
EVENT_CLASSES = (
    "event/PlanetEventHandler.java", "event/RocketEventHandler.java", "event/CableTickHandler.java",
    "atmosphere/AtmosphereHandler.java", "stations/SpaceObjectManager.java", "tile/station/TileLandingPad.java",
    "tile/TileRocketAssemblingMachine.java", "capability/CapabilityProtectiveArmor.java",
    "world/decoration/MapGenLander.java", "client/render/RenderComponents.java", "client/KeyBindings.java",
    "client/ClientProxy.java",
)

# Vanilla material names that legacy recipes use through the ore dictionary.
VANILLA_MATERIALS = frozenset(
    {"Iron", "Gold", "Redstone", "Diamond", "Glowstone", "Lapis", "Quartz", "Emerald", "Coal", "Glass", "Wood", "Stone"}
)
PRODUCT_PREFIXES = ("ingot", "plate", "dust", "stick", "gear", "sheet", "coil", "block", "nugget", "ore", "boule", "crystal")
# Ore-dictionary words that are not materials (legacy block or tag names).
NOT_MATERIALS = frozenset(
    {"blockWarpCoreRim", "blockWarpCoreCore", "blockCoil", "blockTankCapacity", "blockPump", "blockLens",
     "blockHandPress", "blockMotor", "oreGen", "oreScanner"}
)


class InventoryError(Exception):
    pass


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_manifest_hashes(repository: Path) -> dict[str, str]:
    hashes: dict[str, str] = {}
    for name, column in (("java-files.csv", "path"), ("assets.csv", "source_path")):
        with (repository / "legacy-manifest" / name).open(encoding="utf-8", newline="") as handle:
            for row in csv.DictReader(handle):
                hashes[row[column]] = row["sha256"]
    return hashes


class Upstream:
    """Hash-checked, read-only access to the upstream tree."""

    def __init__(self, root: Path, hashes: dict[str, str]):
        self.root = root
        self.hashes = hashes
        self.read: dict[str, str] = {}

    def text(self, path: str) -> str:
        expected = self.hashes.get(path)
        if expected is None:
            raise InventoryError(f"{path} is not in legacy-manifest")
        data = (self.root / path).read_bytes()
        if len(data) > MAX_SOURCE_BYTES:
            raise InventoryError(f"{path} exceeds {MAX_SOURCE_BYTES} bytes")
        actual = sha256(data)
        if actual != expected:
            raise InventoryError(f"{path} hash {actual} differs from legacy-manifest {expected}")
        self.read[path] = actual
        return data.decode("utf-8", errors="replace")


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def uncommented_lines(text: str):
    for number, line in enumerate(text.splitlines(), start=1):
        stripped = line.strip()
        if not stripped.startswith("//"):
            yield number, line


def strip_java_comments(text: str) -> str:
    """Blank out // and /* */ comments, keeping string literals and line numbers."""
    out = []
    index, length = 0, len(text)
    while index < length:
        char = text[index]
        if char == '"':
            end = index + 1
            while end < length and text[end] != '"':
                end += 2 if text[end] == "\\" else 1
            out.append(text[index:end + 1])
            index = end + 1
        elif text.startswith("//", index):
            end = text.find("\n", index)
            end = length if end < 0 else end
            out.append(" " * (end - index))
            index = end
        elif text.startswith("/*", index):
            end = text.find("*/", index + 2)
            end = length if end < 0 else end + 2
            out.append("".join(c if c == "\n" else " " for c in text[index:end]))
            index = end
        else:
            out.append(char)
            index += 1
    return "".join(out)


def lang_names(text: str) -> dict[str, str]:
    names: dict[str, str] = {}
    for line in text.splitlines():
        if "=" in line and not line.startswith("#"):
            key, _, value = line.partition("=")
            names[key.strip()] = value.strip()
    return names


def unlocalized(main: str, holder: str) -> dict[str, str]:
    """Map holder field names to their unlocalized names."""
    result: dict[str, str] = {}
    pattern = re.compile(holder + r"\.(\w+)\s*=\s*new\b(.*?);", re.S)
    for match in pattern.finditer(main):
        name = re.search(r'setUnlocalizedName\("([^"]+)"\)', match.group(2))
        if name:
            result[match.group(1)] = name.group(1).split(":")[-1]
    return result


def variants(lang: dict[str, str], prefix: str, unloc: str) -> list[tuple[int, str]]:
    found = []
    pattern = re.compile(re.escape(f"{prefix}.{unloc}.") + r"(\d+)\.name$")
    for key, value in lang.items():
        match = pattern.match(key)
        if match:
            found.append((int(match.group(1)), value))
    return sorted(found)


def unit(kind: str, name: str, path: str, line: int, **extra: object) -> dict[str, object]:
    record: dict[str, object] = {"id": f"{kind}:{name}", "kind": kind, "legacy_name": name, "source": path, "line": line}
    record.update({key: value for key, value in extra.items() if value not in (None, "", [])})
    return record


def conditional(lines: list[str], index: int) -> bool:
    previous = lines[index - 2].strip() if index >= 2 else ""
    return previous.startswith("if") or previous.startswith("else")


def collect(upstream: Upstream, repository: Path) -> list[dict[str, object]]:
    main = upstream.text(MAIN)
    main_lines = main.splitlines()
    lang = lang_names(upstream.text(LANG))
    units: list[dict[str, object]] = []

    block_unloc = unlocalized(main, "AdvancedRocketryBlocks")
    for number, line in uncommented_lines(main):
        match = re.search(r'registerBlock\(AdvancedRocketryBlocks\.(\w+)\s*\.setRegistryName\("([^"]+)"\)', line)
        if not match:
            continue
        field, name = match.groups()
        unloc = block_unloc.get(field, "")
        display = lang.get(f"tile.{unloc}.name", "")
        units.append(unit("block", name, MAIN, number, field=field, display=display,
                          conditional=conditional(main_lines, number) or None))
        for meta, label in variants(lang, "tile", unloc):
            units.append(unit("block_variant", f"{name}/{meta}", MAIN, number, display=label))
        if field == "blockCrystal":
            crystal = upstream.text(CRYSTAL)
            for found in re.finditer(r'^\s*[A-Z]+\((\d+),\s*0x[0-9a-fA-F]+,\s*"(\w+)"', crystal, re.M):
                units.append(unit("block_variant", f"{name}/{found.group(1)}", CRYSTAL, line_of(crystal, found.start()),
                                  display=lang.get(f"tile.{found.group(2)}.name", found.group(2))))

    item_unloc = unlocalized(main, "AdvancedRocketryItems")
    for number, line in uncommented_lines(main):
        match = re.search(r'registerItem\(AdvancedRocketryItems\.(\w+)\.setRegistryName\("([^"]+)"\)', line)
        if not match:
            continue
        field, name = match.groups()
        unloc = item_unloc.get(field, "")
        display = lang.get(f"item.{unloc}.name", "")
        found = variants(lang, "item", unloc)
        units.append(unit("item", name, MAIN, number, field=field, display=display,
                          conditional=conditional(main_lines, number) or None))
        for meta, label in found:
            units.append(unit("item_variant", f"{name}/{meta}", MAIN, number, display=label))

    for match in re.finditer(r'registerTileEntity\((\w+)\.class,\s*(?:new ResourceLocation\(Constants\.modId,\s*)?"([^"]+)"', main):
        number = line_of(main, match.start())
        if main_lines[number - 1].strip().startswith("//"):
            continue
        units.append(unit("block_entity", match.group(2), MAIN, number, java_class=match.group(1)))

    for match in re.finditer(r'registerModEntity\(new ResourceLocation\(Constants\.modId,\s*"([^"]+)"\)\s*,\s*(\w+)\.class', main):
        units.append(unit("entity", match.group(1), MAIN, line_of(main, match.start()), java_class=match.group(2)))

    biome_classes: dict[str, tuple[str, str]] = {}
    for match in re.finditer(r'AdvancedRocketryBiomes\.(\w+)\s*=\s*new (\w+)\(new Biome\.BiomeProperties\("([^"]+)"\)', main):
        biome_classes[match.group(1)] = (match.group(2), match.group(3))
    registered_biome_classes = set()
    for match in re.finditer(r'AdvancedRocketryBiomes\.(\w+)\.setRegistryName\(Constants\.modId,\s*"([^"]+)"\)', main):
        java_class, display = biome_classes.get(match.group(1), ("", ""))
        registered_biome_classes.add(java_class)
        units.append(unit("biome", match.group(2), MAIN, line_of(main, match.start()), field=match.group(1),
                          java_class=java_class, display=display))

    for match in re.finditer(r'AdvancedRocketryFluids\.(\w+)\s*=\s*new Fluid\("([^"]+)"', main):
        units.append(unit("fluid", match.group(2), MAIN, line_of(main, match.start()), field=match.group(1)))

    for match in re.finditer(r'enchantment\w*\.setRegistryName\(new ResourceLocation\("advancedrocketry:([^"]+)"\)\)', main):
        units.append(unit("enchantment", match.group(1), MAIN, line_of(main, match.start())))

    registered_satellites = set()
    for match in re.finditer(r'registerSatellite\("([^"]+)",\s*(\w+)\.class\)', main):
        registered_satellites.add(match.group(2))
        units.append(unit("satellite", match.group(1), MAIN, line_of(main, match.start()), java_class=match.group(2)))

    audio = upstream.text(AUDIO)
    for match in re.finditer(r'createSoundEvent\("([^"]+)"\)', audio):
        if match.group(1) != "name":
            units.append(unit("sound_event", match.group(1), AUDIO, line_of(audio, match.start())))

    config = upstream.text(CONFIG)
    seen_config = set()
    for match in re.finditer(r'config\.get\(\s*([\w.]+)\s*,\s*"([^"]+)"', config):
        key = f"{match.group(1).split('.')[-1]}.{match.group(2)}"
        if key not in seen_config:
            seen_config.add(key)
            units.append(unit("config", key, CONFIG, line_of(config, match.start())))
    for match in re.finditer(r'config\.get(?:Boolean|Int|Float|String|StringList)\(\s*"([^"]+)"\s*,\s*([\w.]+)', config):
        key = f"{match.group(2).split('.')[-1]}.{match.group(1)}"
        if key not in seen_config:
            seen_config.add(key)
            units.append(unit("config", key, CONFIG, line_of(config, match.start())))

    command = upstream.text(COMMAND)
    for match in re.finditer(r'case "(\w+)":', command):
        number = line_of(command, match.start())
        group = "planet" if number < 912 else "root"
        units.append(unit("command", f"{group}/{match.group(1)}", COMMAND, number))

    materials: dict[str, set[str]] = {}
    material_lines: dict[str, tuple[str, int]] = {}
    ar_materials = set()
    for match in re.finditer(r'registerMaterial\(new zmaster587\.libVulpes\.api\.material\.Material\("(\w+)"', main):
        ar_materials.add(match.group(1))
        material_lines.setdefault(match.group(1), (MAIN, line_of(main, match.start())))
        materials.setdefault(match.group(1), set())
    word = re.compile(r'"(' + "|".join(PRODUCT_PREFIXES) + r')([A-Z]\w+)"')
    scanned = sorted(path for path in upstream.hashes if path.startswith(JAVA_ROOT) and path.endswith(".java"))
    scanned += sorted(path for path in upstream.hashes if path.startswith(ASSET_ROOT + "recipes/"))
    for path in scanned:
        text = upstream.text(path)
        for match in word.finditer(text):
            if match.group(0).strip('"') in NOT_MATERIALS or match.group(2) in VANILLA_MATERIALS:
                continue
            materials.setdefault(match.group(2), set()).add(match.group(1))
            material_lines.setdefault(match.group(2), (path, line_of(text, match.start())))
        for match in re.finditer(r'getMaterialFromName\("(\w+)"\)', text):
            materials.setdefault(match.group(1), set())
            material_lines.setdefault(match.group(1), (path, line_of(text, match.start())))
    for name in sorted(materials):
        path, number = material_lines[name]
        units.append(unit("material", name, path, number, products=sorted(materials[name]),
                          registered_by="advancedrocketry" if name in ar_materials else "libvulpes"))

    java_files = sorted(path for path in upstream.hashes if path.startswith(JAVA_ROOT) and path.endswith(".java"))
    for path in java_files:
        relative = path[len(JAVA_ROOT):]
        stem = relative.rsplit("/", 1)[-1][:-5]
        if relative.startswith("world/biome/"):
            if stem not in registered_biome_classes:
                units.append(unit("biome_unregistered", stem, path, 1))
        elif relative.startswith(("world/decoration/", "world/gen/", "world/ore/", "world/provider/", "world/type/")) or (
                relative.startswith("world/") and relative.count("/") == 1):
            units.append(unit("worldgen", stem, path, 1))
        elif relative.startswith("satellite/") and stem not in registered_satellites:
            units.append(unit("satellite_unregistered", stem, path, 1))
        elif relative.startswith("mission/"):
            units.append(unit("mission", stem, path, 1))
        elif relative.startswith("network/"):
            units.append(unit("packet", stem, path, 1))
        elif relative.startswith("integration/jei/") and relative.count("/") == 3 and stem.endswith("Category"):
            units.append(unit("integration", "jei/" + relative.split("/")[2], path, 1))
        elif relative.startswith("integration/") and relative.count("/") == 1:
            units.append(unit("integration", stem, path, 1))
        elif relative.startswith("atmosphere/") and stem.startswith("Atmosphere") and stem not in ("AtmosphereHandler", "AtmosphereBlob"):
            units.append(unit("atmosphere", stem, path, 1))

    keys = upstream.text(KEYS)
    for number, line in uncommented_lines(keys):
        match = re.search(r'static KeyBinding (\w+)\s*=\s*new KeyBinding\(', line)
        if match:
            units.append(unit("keybinding", match.group(1), KEYS, number))

    for relative in EVENT_CLASSES:
        path = JAVA_ROOT + relative
        text = strip_java_comments(upstream.text(path))
        stem = relative.rsplit("/", 1)[-1][:-5]
        pattern = (r'@SubscribeEvent(?:\s*@\w+(?:\([^)]*\))?)*\s*public\s+(?:static\s+)?void\s+(\w+)\s*\('
                   r'\s*(?:@\w+\s+)?(?:final\s+)?([\w.]+)')
        for match in re.finditer(pattern, text):
            event_type = match.group(2).rsplit(".", 1)[-1]
            units.append(unit("event", f"{stem}.{match.group(1)}({event_type})", path, line_of(text, match.start())))

    # LibVulpes content that Advanced Rocketry gameplay depends on: fields used from Java, items named in recipes.
    libvulpes: dict[str, tuple[str, int]] = {}
    for path in sorted(p for p in upstream.hashes if p.startswith(JAVA_ROOT) and p.endswith(".java")):
        text = upstream.text(path)
        for match in re.finditer(r'LibVulpes(?:Blocks|Items)\.(\w+)', text):
            if match.group(1) not in ("registerBlock", "registerItem"):
                libvulpes.setdefault("java/" + match.group(1), (path, line_of(text, match.start())))
    for path in sorted(p for p in upstream.hashes if p.startswith(ASSET_ROOT + "recipes/")):
        text = upstream.text(path)
        for match in re.finditer(r'"libvulpes:(\w+)"', text):
            libvulpes.setdefault("recipe/" + match.group(1), (path, line_of(text, match.start())))
        for match in re.finditer(r'"ore":\s*"(blockMotor|itemBattery)"', text):
            libvulpes.setdefault("ore/" + match.group(1), (path, line_of(text, match.start())))
    for name in sorted(libvulpes):
        path, number = libvulpes[name]
        units.append(unit("libvulpes", name, path, number))

    for path in sorted(upstream.hashes):
        if path.startswith(ASSET_ROOT + "advancements/") and path.endswith(".json"):
            upstream.text(path)
            units.append(unit("advancement", path.rsplit("/", 1)[-1][:-5], path, 1))

    identifiers = [entry["id"] for entry in units]
    duplicates = sorted({identifier for identifier in identifiers if identifiers.count(identifier) > 1})
    if duplicates:
        raise InventoryError(f"duplicate unit IDs: {duplicates}")
    return sorted(units, key=lambda entry: (str(entry["kind"]), str(entry["legacy_name"]).lower(), str(entry["id"])))


def build(upstream_root: Path, repository: Path) -> dict[str, object]:
    upstream = Upstream(upstream_root, load_manifest_hashes(repository))
    units = collect(upstream, repository)
    counts: dict[str, int] = {}
    for entry in units:
        counts[str(entry["kind"])] = counts.get(str(entry["kind"]), 0) + 1
    return {
        "schema": SCHEMA,
        "upstream_repository": UPSTREAM_REPOSITORY,
        "upstream_commit": UPSTREAM_COMMIT,
        "license": "MIT",
        "generator": "tools/audit/inventory_v180_content.py",
        "sources": dict(sorted(upstream.read.items())),
        "counts": dict(sorted(counts.items())),
        "units": units,
    }


def render(inventory: dict[str, object]) -> bytes:
    return (json.dumps(inventory, ensure_ascii=False, indent=1, sort_keys=False) + "\n").encode("utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--upstream", required=True, type=Path, help="read-only upstream tree at the pinned commit")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true", help="fail if the committed inventory differs")
    arguments = parser.parse_args(argv)
    try:
        data = render(build(arguments.upstream, REPOSITORY_ROOT))
    except (InventoryError, OSError) as error:
        print(f"inventory: {error}", file=sys.stderr)
        return 2
    output = arguments.output if arguments.output.is_absolute() else REPOSITORY_ROOT / arguments.output
    if arguments.check:
        if not output.exists() or output.read_bytes() != data:
            print(f"inventory: {output} differs from a regenerated inventory", file=sys.stderr)
            return 1
        print("inventory: up to date")
        return 0
    output.write_bytes(data)
    print(f"inventory: wrote {output} ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
