"""Read bounded NBT projections from the owned stopped passenger fixture."""
from pathlib import Path
import gzip
import hashlib
import json
import shutil
import sys
import zlib

ROOT = Path(r'D:\GitHub\AdvancedRocketry-Community')
sys.path.insert(0, str(ROOT))
from scripts.inspect_celestial_saved_data import NbtReader
from scripts.inspect_v100_world_upgrade import nbt_uuid

world, output = map(Path, sys.argv[1:])
assert world.resolve().is_relative_to(Path(r'C:\Users\Administrator\AppData\Local\Temp'))
output.mkdir(parents=True, exist_ok=False)
shutil.copy2(__file__, output / Path(__file__).name)
result = dict(files=[], players=[], rockets=[])


def payload(path):
    assert path.stat().st_size <= 16 * 1024**2 and not path.is_symlink()
    data = path.read_bytes()
    rel = path.relative_to(world)
    dest = output / 'nbt' / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    result['files'].append(dict(file=rel.as_posix(), bytes=len(data), sha256=hashlib.sha256(data).hexdigest()))
    return data


def project(entity):
    data = entity['RocketEntityData']
    flight = data['flight_data']
    snapshot = data['snapshot']
    return dict(entity=nbt_uuid(entity['UUID']), logical=nbt_uuid(data['assembly_transaction_id']),
                snapshot=snapshot['content_hash'], state=flight['state'], fuel=flight['fuel']['amount'],
                passengers=flight['passengers'], dimension=flight['current_dimension'],
                blocks=snapshot['relative_blocks'], pos=entity['Pos'])


files = list((world / 'playerdata').glob('*.dat'))
assert len(files) == 2
for path in files:
    data = NbtReader(gzip.decompress(payload(path))).read_root()
    vehicle = data.get('RootVehicle', {}).get('Entity')
    result['players'].append(dict(player=path.stem, dimension=data['Dimension'], pos=data['Pos'],
        root_vehicle=project(vehicle) if vehicle and 'RocketEntityData' in vehicle else vehicle))
regions = list(world.rglob('entities/*.mca'))
assert len(regions) < 32
for path in regions:
    raw = payload(path)
    for index in range(1024):
        sector = int.from_bytes(raw[index*4:index*4+3], 'big')
        if not sector:
            continue
        start = sector * 4096
        length = int.from_bytes(raw[start:start+4], 'big')
        assert 1 < length <= 1024**2 and start + 4 + length <= len(raw)
        assert raw[start+4] == 2
        decoder = zlib.decompressobj()
        expanded = decoder.decompress(raw[start+5:start+4+length], 4*1024**2+1)
        assert len(expanded) <= 4*1024**2 and decoder.eof
        chunk = NbtReader(expanded).read_root()
        for entity in chunk.get('Entities', []):
            if 'RocketEntityData' in entity:
                result['rockets'].append(project(entity))
result['world_owned_only'] = len(result['rockets']) == 1 and all(p['root_vehicle'] is None for p in result['players'])
(output / 'projection.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
print(json.dumps(dict(rockets=len(result['rockets']), player_vehicles=sum(p['root_vehicle'] is not None for p in result['players']), world_owned_only=result['world_owned_only'])))
