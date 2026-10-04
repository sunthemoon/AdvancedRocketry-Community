"""Correct the preserved inspect01 parser typo without changing input evidence."""
from pathlib import Path

source = Path(__file__).with_name('inspect01.py').read_text(encoding='utf-8')
old = "'flushMigrationBarrier(', 'getChunkSource().save', 'loadedRootsMatch', 'preservesRecipeInput')))}"
new = "'flushMigrationBarrier(', 'getChunkSource().save', 'loadedRootsMatch', 'preservesRecipeInput'))}"
assert source.count(old) == 1
exec(compile(source.replace(old, new), str(Path(__file__).resolve()), 'exec'))
