"""Create new vanilla-only world metadata, never import a campaign/modded world."""
from scripts import loader_cut_nbt as nbt

WORLD = "baseline"


def create_world(game):
    world = game / "saves" / WORLD
    world.mkdir(parents=True, exist_ok=False)
    def text(value):
        return nbt.Tag(nbt.STRING, value.encode("ascii"))
    def compound(**values):
        return nbt.Tag(nbt.COMPOUND, values)
    dimensions = {
        "minecraft:overworld": compound(type=text("minecraft:overworld"), generator=compound(
            type=text("minecraft:flat"), settings=compound(
                biome=text("minecraft:plains"), lakes=nbt.Tag(nbt.BYTE, 0),
                features=nbt.Tag(nbt.BYTE, 0), structure_overrides=nbt.Tag(nbt.LIST, (nbt.STRING, [])),
                layers=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [
                    compound(block=text(block), height=nbt.Tag(nbt.INT, height))
                    for block, height in (("minecraft:bedrock", 1), ("minecraft:dirt", 2),
                                          ("minecraft:grass_block", 1))]))))),
    }
    for name, settings, biome in (("the_nether", "nether", "multi_noise"), ("the_end", "end", "the_end")):
        source = compound(type=text("minecraft:" + biome))
        if name == "the_nether":
            source.value["preset"] = text("minecraft:nether")
        dimensions["minecraft:" + name] = compound(type=text("minecraft:" + name), generator=compound(
            type=text("minecraft:noise"), settings=text("minecraft:" + settings), biome_source=source))
    data = compound(
        DataVersion=nbt.Tag(nbt.INT, 3465), version=nbt.Tag(nbt.INT, 19133),
        Version=compound(Id=nbt.Tag(nbt.INT, 3465), Name=text("1.20.1"),
                         Series=text("main"), Snapshot=nbt.Tag(nbt.BYTE, 0)),
        LevelName=text("Empty baseline"), GameType=nbt.Tag(nbt.INT, 1),
        allowCommands=nbt.Tag(nbt.BYTE, 1), initialized=nbt.Tag(nbt.BYTE, 1),
        Difficulty=nbt.Tag(nbt.BYTE, 0), hardcore=nbt.Tag(nbt.BYTE, 0),
        SpawnX=nbt.Tag(nbt.INT, 0), SpawnY=nbt.Tag(nbt.INT, -60), SpawnZ=nbt.Tag(nbt.INT, 0),
        Time=nbt.Tag(nbt.LONG, 0), DayTime=nbt.Tag(nbt.LONG, 6000),
        DataPacks=compound(Enabled=nbt.Tag(nbt.LIST, (nbt.STRING, [text("vanilla")])),
                           Disabled=nbt.Tag(nbt.LIST, (nbt.STRING, []))),
        GameRules=compound(doMobSpawning=text("false"), doDaylightCycle=text("false"),
                           doWeatherCycle=text("false")),
        WorldGenSettings=compound(seed=nbt.Tag(nbt.LONG, 0), generate_features=nbt.Tag(nbt.BYTE, 0),
                                  bonus_chest=nbt.Tag(nbt.BYTE, 0),
                                  dimensions=nbt.Tag(nbt.COMPOUND, dimensions)))
    nbt.write_gzip(world / "level.dat", b"", compound(Data=data))
    return world
