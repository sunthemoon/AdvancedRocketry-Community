"""Use primary method-reference patch instead of absent javap display spelling."""
from pathlib import Path

source = Path(__file__).with_name('verify02.py').read_text(encoding='utf-8')
source = source.replace("\"'VERIFY-01.json'\": \"'VERIFY-02.json'\",",
                        "\"'VERIFY-01.json'\": \"'VERIFY-03.json'\",\n"
                        "    \"self.assertIn('BlockEntity.onChunkUnloaded', chunk)\": "
                        "\"self.assertIn('BlockEntity::onChunkUnloaded', '\\n'.join(x['text'] for x in I['primary_source_lines']['patches/net/minecraft/world/level/chunk/LevelChunk.java.patch']))\",")
assert "'VERIFY-03.json'" in source
exec(compile(source, str(Path(__file__).resolve()), 'exec'))
