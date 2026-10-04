"""Schema-aware historical manifest inspection; preserve earlier failed helpers."""
from pathlib import Path

source = Path(__file__).with_name('inspect01.py').read_text(encoding='utf-8')
changes = {
    "'flushMigrationBarrier(', 'getChunkSource().save', 'loadedRootsMatch', 'preservesRecipeInput')))}":
    "'flushMigrationBarrier(', 'getChunkSource().save', 'loadedRootsMatch', 'preservesRecipeInput'))}",
    "manifest=json.loads(z.read('EVIDENCE-MANIFEST.json'))['files']":
    "manifest={e['path']:{k:e[k] for k in ('bytes','sha256')} for e in json.loads(z.read('EVIDENCE-MANIFEST.json'))['entries']}",
}
for old, new in changes.items():
    assert source.count(old) == 1
    source = source.replace(old, new)
exec(compile(source, str(Path(__file__).resolve()), 'exec'))
