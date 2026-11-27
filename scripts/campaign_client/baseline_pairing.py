"""Read only pinned level metadata; copy a small, verified vanilla subset, never a save."""
import copy
import gzip
import hashlib
import io
import math
from pathlib import Path
import re
import zlib

from scripts import loader_cut_nbt as nbt
from .baseline import baseline_path
from .cells import artifact
from scripts.v100_compatibility_runtime import SmokeError

# Deliberately narrow: these are vanilla 1.20.1 presets, not a namespace-prefix check.
FLAT_BLOCKS = {"minecraft:" + name for name in ("air", "bedrock", "dirt", "grass_block", "stone", "water")}
BOOL_RULES = set("""doWardenSpawning globalSoundEvents tntExplosionDropDecay doFireTick doVinesSpread
    fireDamage reducedDebugInfo waterSourceConversion disableElytraMovementCheck lavaSourceConversion
    announceAdvancements drowningDamage commandBlockOutput forgiveDeadPlayers doMobSpawning disableRaids
    doWeatherCycle mobExplosionDropDecay doDaylightCycle showDeathMessages doTileDrops universalAnger
    doInsomnia blockExplosionDropDecay doImmediateRespawn naturalRegeneration doMobLoot fallDamage
    keepInventory doEntityDrops doLimitedCrafting mobGriefing doTraderSpawning freezeDamage logAdminCommands
    spectatorsGenerateChunks sendCommandFeedback doPatrolSpawning""".split())
INT_RULES = set("""maxCommandChainLength maxEntityCramming playersSleepingPercentage snowAccumulationHeight
    randomTickSpeed spawnRadius commandModificationBlockLimit""".split())
STATE_TYPES = {
    "SpawnX": nbt.INT, "SpawnY": nbt.INT, "SpawnZ": nbt.INT, "SpawnAngle": nbt.FLOAT,
    "Time": nbt.LONG, "DayTime": nbt.LONG, "raining": nbt.BYTE, "thundering": nbt.BYTE,
    "rainTime": nbt.INT, "thunderTime": nbt.INT, "clearWeatherTime": nbt.INT,
    "Difficulty": nbt.BYTE, "DifficultyLocked": nbt.BYTE,
    "BorderCenterX": nbt.DOUBLE, "BorderCenterZ": nbt.DOUBLE, "BorderSize": nbt.DOUBLE,
    "BorderSizeLerpTime": nbt.LONG, "BorderSizeLerpTarget": nbt.DOUBLE,
    "BorderSafeZone": nbt.DOUBLE, "BorderDamagePerBlock": nbt.DOUBLE,
    "BorderWarningBlocks": nbt.DOUBLE, "BorderWarningTime": nbt.DOUBLE,
}


def refuse(field, reason):
    raise ValueError(f"Baseline pairing {field}: {reason}")


def typed(tag, kind, field):
    if not isinstance(tag, nbt.Tag) or tag.type != kind:
        refuse(field, "missing or incorrect NBT type")
    return tag.value


def text(tag, field):
    try:
        return typed(tag, nbt.STRING, field).decode("ascii")
    except UnicodeError:
        refuse(field, "only known vanilla ASCII values are supported")


def fields(tag, required, optional, field):
    value = typed(tag, nbt.COMPOUND, field)
    if not required <= value.keys() or value.keys() - required - optional:
        refuse(field, "missing or unsupported fields")
    return value


def flag(tag, field):
    if typed(tag, nbt.BYTE, field) not in (0, 1):
        refuse(field, "expected boolean byte")


def generator(overworld):
    world = fields(overworld, {"type", "generator"}, set(), "overworld")
    if text(world["type"], "overworld.type") != "minecraft:overworld":
        refuse("overworld.type", "only the built-in overworld dimension type is supported")
    gen = typed(world["generator"], nbt.COMPOUND, "generator")
    kind = text(gen.get("type"), "generator.type")
    if kind == "minecraft:flat":
        fields(world["generator"], {"type", "settings"}, set(), "generator")
        settings = fields(gen["settings"], {"biome", "layers", "lakes", "features"},
                          {"structure_overrides"}, "flat.settings")
        if text(settings["biome"], "flat.biome") != "minecraft:plains":
            refuse("flat.biome", "only the built-in plains biome is supported")
        for name in ("lakes", "features"):
            flag(settings[name], "flat." + name)
        if "structure_overrides" in settings:
            item_type, items = typed(settings["structure_overrides"], nbt.LIST, "flat.structure_overrides")
            if item_type not in (0, nbt.STRING) or items:
                refuse("flat.structure_overrides", "only an empty override list is supported")
        item_type, layers = typed(settings["layers"], nbt.LIST, "flat.layers")
        if item_type != nbt.COMPOUND or not 1 <= len(layers) <= 384:
            refuse("flat.layers", "expected 1..384 compound layers")
        height = 0
        for layer in layers:
            values = fields(layer, {"block", "height"}, set(), "flat.layer")
            if text(values["block"], "flat.layer.block") not in FLAT_BLOCKS:
                refuse("flat.layer.block", "unverified vanilla or modded block")
            size = typed(values["height"], nbt.INT, "flat.layer.height")
            if not 1 <= size <= 384:
                refuse("flat.layer.height", "expected 1..384")
            height += size
        if height > 384:
            refuse("flat.layers", "total height exceeds 384")
    elif kind == "minecraft:noise":
        fields(world["generator"], {"type", "settings", "biome_source"}, set(), "generator")
        if text(gen["settings"], "noise.settings") not in {
                "minecraft:overworld", "minecraft:large_biomes", "minecraft:amplified"}:
            refuse("noise.settings", "only named built-in overworld settings are supported")
        source = fields(gen["biome_source"], {"type", "preset"}, set(), "noise.biome_source")
        if (text(source["type"], "noise.biome_source.type") != "minecraft:multi_noise"
                or text(source["preset"], "noise.biome_source.preset") != "minecraft:overworld"):
            refuse("noise.biome_source", "only the built-in overworld multi-noise preset is supported")
    else:
        refuse("generator.type", "unsupported or non-vanilla generator " + kind)


def player_view(player):
    values = typed(player, nbt.COMPOUND, "Player")
    if text(values.get("Dimension"), "Player.Dimension") != "minecraft:overworld":
        refuse("Player.Dimension", "viewpoint is not in the overworld")
    view = {"Dimension": values["Dimension"]}
    for name, kind, count, bounds in (
            ("Pos", nbt.DOUBLE, 3, (30000000, 2048, 30000000)),
            ("Rotation", nbt.FLOAT, 2, (360, 90))):
        tag = values.get(name)
        item_type, items = typed(tag, nbt.LIST, "Player." + name)
        if item_type != kind or len(items) != count:
            refuse("Player." + name, "incorrect viewpoint list shape")
        for item, bound in zip(items, bounds):
            value = typed(item, kind, "Player." + name)
            if not math.isfinite(value) or abs(value) > bound:
                refuse("Player." + name, "nonfinite or out-of-range viewpoint")
        view[name] = tag
    # Fresh creative player, not copied inventory, effects, motion, vehicles, UUIDs or mod capabilities.
    view["playerGameType"] = nbt.Tag(nbt.INT, 1)
    view["abilities"] = nbt.Tag(nbt.COMPOUND, {
        **{key: nbt.Tag(nbt.BYTE, 1) for key in ("mayfly", "flying", "invulnerable", "instabuild", "mayBuild")},
        "flySpeed": nbt.Tag(nbt.FLOAT, 0.05), "walkSpeed": nbt.Tag(nbt.FLOAT, 0.1)})
    return nbt.Tag(nbt.COMPOUND, view)


def load_pairing(base, spec):
    """Pin precisely the level.dat bytes consumed, not unrelated chunks or player/account data."""
    if isinstance(spec, dict) and isinstance(spec.get("path"), Path):
        spec = dict(spec, path=str(spec["path"]))
    record = artifact(base, spec)
    try:
        path = baseline_path(base, spec["path"])
    except (OSError, SmokeError) as exc:
        refuse("level.dat", str(exc))
    if path.name != "level.dat" or not path.is_file() or path.stat().st_size > nbt.MAX_COMPRESSED:
        refuse("level.dat", "expected a bounded, regular level.dat file")
    # Hash the very bytes decoded; a separate hash-then-read could bind different snapshots.
    with path.open("rb") as stream:
        raw = stream.read(nbt.MAX_COMPRESSED + 1)
    if len(raw) > nbt.MAX_COMPRESSED or hashlib.sha256(raw).hexdigest() != record["sha256"]:
        refuse("level.dat.sha256", "candidate metadata hash mismatch")
    try:
        with gzip.GzipFile(fileobj=io.BytesIO(raw)) as stream:
            root = nbt.read(stream.read(nbt.MAX_DECODED + 1))[1]
    except (OSError, EOFError, ValueError, zlib.error) as exc:
        refuse("level.dat", "invalid or oversized NBT: " + str(exc))
    data = typed(typed(root, nbt.COMPOUND, "root").get("Data"), nbt.COMPOUND, "Data")
    if typed(data.get("DataVersion"), nbt.INT, "DataVersion") != 3465:
        refuse("DataVersion", "only Minecraft 1.20.1 (3465) is supported")
    packs = fields(data.get("DataPacks"), {"Enabled", "Disabled"}, set(), "DataPacks")
    item_type, enabled = typed(packs["Enabled"], nbt.LIST, "DataPacks.Enabled")
    if item_type != nbt.STRING or not enabled or len(enabled) > 64:
        refuse("DataPacks.Enabled", "expected a bounded string list beginning with vanilla")
    pack_names = [text(item, "DataPacks.Enabled") for item in enabled]
    unsupported = set(pack_names) - {"vanilla", "mod:forge", "mod:advancedrocketrycommunity"}
    if unsupported:
        refuse("DataPacks.Enabled", "external or unsupported packs cannot be reproduced: " + ", ".join(sorted(unsupported)))
    if len(set(pack_names)) != len(pack_names) or pack_names[0] != "vanilla":
        refuse("DataPacks.Enabled", "duplicate packs or vanilla is not first")
    gen = fields(data.get("WorldGenSettings"), {"seed", "dimensions", "generate_features", "bonus_chest"},
                 set(), "WorldGenSettings")
    typed(gen["seed"], nbt.LONG, "seed")
    for name in ("generate_features", "bonus_chest"):
        flag(gen[name], name)
    dimensions = typed(gen["dimensions"], nbt.COMPOUND, "dimensions")
    overworld = dimensions.get("minecraft:overworld")
    generator(overworld)
    state = {}
    for name, kind in STATE_TYPES.items():
        if name not in data and name not in {"SpawnX", "SpawnY", "SpawnZ", "Time", "DayTime"}:
            continue
        value = typed(data.get(name), kind, name)
        if not math.isfinite(value):
            refuse(name, "nonfinite state")
        state[name] = data[name]
    rules = typed(data.get("GameRules"), nbt.COMPOUND, "GameRules")
    if len(rules) > len(BOOL_RULES | INT_RULES):
        refuse("GameRules", "too many rules")
    for name, tag in rules.items():
        value = text(tag, "GameRules." + name)
        if name in BOOL_RULES and value in ("true", "false"):
            continue
        if name in INT_RULES and re.fullmatch(r"0|[1-9][0-9]{0,9}", value) and int(value) <= 2147483647:
            continue
        refuse("GameRules." + name, "unknown rule or unsupported value")
    state["GameRules"] = data["GameRules"]
    if "Player" in data:
        state["Player"] = player_view(data["Player"])
    return {"path": path, "sha256": record["sha256"], "state": copy.deepcopy(state),
            "worldgen": copy.deepcopy({key: tag for key, tag in gen.items() if key != "dimensions"}),
            "overworld": copy.deepcopy(overworld), "viewpoint_source": "Player.Pos/Rotation" if "Player" in state
            else "spawn only; no saved Player camera; stage matching capture viewpoint explicitly"}


def pairing_identity(pairing):
    return {"path": pairing["path"], "sha256": pairing["sha256"],
            "world_seed": pairing["worldgen"]["seed"].value,
            "viewpoint_source": pairing["viewpoint_source"],
            "copied_state": nbt.plain(nbt.Tag(nbt.COMPOUND, pairing["state"])),
            "overworld": nbt.plain(pairing["overworld"])}
