"""W143 stopped-copy edits with typed readback; no edit is applied to a live endpoint."""
import copy
from pathlib import Path
import loader_cut_nbt as nbt

CASES = ("IR_CONTAINER", "IR_LOOT")
CANDIDATE = Path("arce_w143_fixture/candidate.dat")

def require(value, message):
    if not value:
        raise nbt.NbtError(message)

def candidate(before, after, case):
    require(case in CASES, "Unknown item damage")
    expected = copy.deepcopy(before)
    blocks = nbt.compound(expected, "relative_blocks").value[1]
    require(len(blocks) == 1, "Damage fixture does not have one container")
    if case == "IR_CONTAINER":
        palette = nbt.compound(expected, "block_palette").value[1]
        require(len(palette) == 1 and nbt.plain(palette[0])["id"] == "minecraft:chest", "Changed-container baseline differs")
        palette[0].value["id"] = nbt.Tag(nbt.STRING, b"minecraft:barrel")
        palette[0].value["properties"] = nbt.Tag(nbt.COMPOUND, {"facing": nbt.Tag(nbt.STRING, b"north"), "open": nbt.Tag(nbt.STRING, b"false")})
    else:
        payload = nbt.compound(blocks[0], "block_entity", "data")
        require(set(payload.value) == {"Items"}, "Loot baseline differs")
        del payload.value["Items"]
        payload.value["LootTable"] = nbt.Tag(nbt.STRING, b"minecraft:chests/simple_dungeon")
        payload.value["LootTableSeed"] = nbt.Tag(nbt.LONG, 143)
    old, new = nbt.compound(before, "content_hash"), nbt.compound(after, "content_hash")
    require(old != new and new.type == nbt.STRING and len(new.value) == 64 and all(c in b"0123456789abcdef" for c in new.value), "Candidate hash is not new")
    expected.value["content_hash"] = copy.deepcopy(new)
    require(expected == after, "Candidate changed unrelated data or tag types")
    return copy.deepcopy(after)

def edit(world, case, keep, stopped, owner, token):
    require(stopped.get("forced_stop") and "process_error" not in stopped and owner.read_text(encoding="ascii") == token,
            "Offline item fixture requires its stopped owned process")
    require(case in CASES and not keep.exists(), "Unknown/repeated offline item fixture")
    ledger_file = world / "data" / nbt.LEDGER_FILE; ledger_bytes = ledger_file.read_bytes()
    ledger = nbt.plain(nbt.compound(nbt.read_gzip(ledger_file)[1], "data"))
    require(ledger["issued_through"] == 1 and len(ledger["records"]) == 1 and ledger["records"][0]["phase"] == "PREPARED", "Damage lacks durable PREPARED")
    root, info = nbt.chunk(world / "entities", 4, 4)
    require(not info["external"], "Damage fixture must be inline")
    capture = nbt.read_gzip(world / CANDIDATE)[1]
    rockets = [e for e in nbt.compound(root, "Entities").value[1] if "RocketEntityData" in e.value]
    require(len(rockets) == 1 and nbt.compound(rockets[0], "UUID") == nbt.compound(capture, "physical"), "Candidate physical identity differs")
    data = nbt.compound(rockets[0], "RocketEntityData")
    require(nbt.compound(data, "snapshot") == nbt.compound(capture, "before"), "Candidate baseline differs")
    data.value["snapshot"] = candidate(nbt.compound(capture, "before"), nbt.compound(capture, "after"), case)
    region = world / "entities/r.0.0.mca"; require(not region.is_symlink(), "Linked region refused")
    original = region.read_bytes(); keep.mkdir()
    (keep / region.name).write_bytes(original); (keep / "candidate.dat").write_bytes((world / CANDIDATE).read_bytes())
    nbt.write_chunk(world / "entities", 4, 4, root)
    require(nbt.chunk(world / "entities", 4, 4)[0] == root and ledger_file.read_bytes() == ledger_bytes, "Offline typed readback or ledger differs")
    return dict(before_sha256=nbt.digest(original), after_sha256=nbt.digest(region.read_bytes()), ledger_sha256=nbt.digest(ledger_bytes))
