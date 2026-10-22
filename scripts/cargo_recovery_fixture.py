"""R72 damaged durable fixtures; called only after the owned native process has stopped."""
import copy
from pathlib import Path

import loader_cut_nbt as nbt

CASES = ('FC_LOAD_R3_CURSOR', 'FC_LOAD_R3_OTHER')
CANDIDATE = Path('arce_w138_fixture/r3-cursor.dat')


def require(condition, message):
    if not condition:
        raise nbt.NbtError(message)


def cursor_snapshot(before, after):
    """The production codec made the hash; independently restrict the candidate to one coordinate change."""
    expected = copy.deepcopy(before)
    blocks = nbt.compound(expected, 'relative_blocks').value[1]
    cargo = [block for block in blocks if 'block_entity' in block.value]
    require(len(cargo) == 1 and nbt.plain(nbt.compound(cargo[0], 'position')) == [0, 0, 0], 'Cursor baseline differs')
    nbt.compound(cargo[0], 'position').value = [0, 1, 0]
    positions = [nbt.plain(nbt.compound(block, 'position')) for block in blocks]
    require(len({tuple(p) for p in positions}) == len(positions), 'Cursor target is occupied')
    nbt.compound(expected, 'bounding_box').value = [min(p[i] for p in positions) for i in range(3)] + [max(p[i] for p in positions) for i in range(3)]
    # Match RocketPosition's x/y/z order rather than retaining an obsolete list index.
    blocks.sort(key=lambda block: tuple(nbt.plain(nbt.compound(block, 'position'))))
    old_hash = nbt.compound(before, 'content_hash'); new_hash = nbt.compound(after, 'content_hash')
    require(old_hash != new_hash and new_hash.type == nbt.STRING and len(new_hash.value) == 64
            and all(c in b'0123456789abcdef' for c in new_hash.value), 'Cursor candidate hash differs from its contract')
    expected.value['content_hash'] = copy.deepcopy(new_hash)
    require(after == expected, 'Cursor candidate changed unrelated snapshot data')
    return copy.deepcopy(after)


def edit(world: Path, case: str, keep: Path) -> dict:
    require(case in CASES and not keep.exists(), 'Unknown or repeated offline fixture')
    ledger_file = world / 'data' / nbt.LEDGER_FILE
    ledger_before = ledger_file.read_bytes()
    ledger = nbt.plain(nbt.compound(nbt.read_gzip(ledger_file)[1], 'data'))
    require(ledger['issued_through'] == 1 and len(ledger['records']) == 1
            and ledger['records'][0]['seq'] == 1 and ledger['records'][0]['phase'] == 'PREPARED', 'Offline edit requires its durable PREPARED baseline')
    directory = world / ('entities' if case == CASES[0] else 'region')
    path = directory / 'r.0.0.mca'
    require(not path.is_symlink(), 'Offline edit refuses a linked region')
    original = path.read_bytes(); root, info = nbt.chunk(directory, 4, 4)
    require(not info['external'], 'Offline edit requires an inline record')
    captured = None
    if case == CASES[0]:
        capture = world / CANDIDATE
        require(not capture.is_symlink(), 'Offline edit refuses a linked candidate')
        captured = capture.read_bytes(); candidate = nbt.read_gzip(capture)[1]
        rockets = [entity for entity in nbt.compound(root, 'Entities').value[1] if 'RocketEntityData' in entity.value]
        require(len(rockets) == 1 and nbt.compound(rockets[0], 'UUID') == nbt.compound(candidate, 'physical'), 'Cursor candidate identity differs')
        rocket = nbt.compound(rockets[0], 'RocketEntityData')
        require(nbt.compound(rocket, 'snapshot') == nbt.compound(candidate, 'before'), 'Cursor candidate baseline differs')
        require(ledger['records'][0]['payload']['cursor']['p'] == [0, 0, 0], 'Pending cursor differs')
        rocket.value['snapshot'] = cursor_snapshot(nbt.compound(candidate, 'before'), nbt.compound(candidate, 'after'))
        change = 'Cargo block position [0,0,0] -> [0,1,0]; coherent codec-generated snapshot'
    else:
        entries, index = nbt.block_entity(root, 64, 100, 64)
        require(nbt.plain(nbt.compound(entries[index], 'id')) == 'advancedrocketrycommunity:rocket_fluid_loader', 'OTHER selected another device')
        device = nbt.compound(entries[index], 'arce_rocket_fluid_cargo')
        require(nbt.plain(nbt.compound(device, 'tank')) == dict(FluidName='minecraft:water', Amount=5000), 'OTHER baseline fluid differs')
        nbt.compound(device, 'tank', 'FluidName').value = b'minecraft:lava'
        change = 'Device fluid identity minecraft:water -> minecraft:lava; quantity unchanged'
    keep.mkdir()
    (keep / path.name).write_bytes(original)
    if captured is not None:
        (keep / CANDIDATE.name).write_bytes(captured)
    nbt.write_chunk(directory, 4, 4, root)
    require(nbt.chunk(directory, 4, 4)[0] == root, 'Offline edit typed readback differs')
    require(ledger_file.read_bytes() == ledger_before, 'Offline edit changed its ledger')
    result = dict(file=path.relative_to(world).as_posix(), change=change, before_sha256=nbt.digest(original),
                  after_sha256=nbt.digest(path.read_bytes()), ledger_sha256=nbt.digest(ledger_before))
    if captured is not None:
        result['candidate'] = dict(file=CANDIDATE.name, sha256=nbt.digest(captured))
    return result
