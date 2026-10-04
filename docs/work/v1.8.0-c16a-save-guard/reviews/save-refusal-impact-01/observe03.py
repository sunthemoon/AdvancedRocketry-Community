"""Correct Windows manifest path spelling; retain observe02 failure."""
from pathlib import Path

source = Path(__file__).with_name('observe02.py').read_text(encoding='utf-8')
start = source.index('primary = I[')
end = source.index('\n', start)
source = source[:start] + "primary = next(v for k,v in I['inputs'].items() if k.startswith('primary:') and k.endswith('forge-1.20.1-47.4.10-sources.jar'))" + source[end:]
source = source.replace("'OBSERVATIONS-02.json'", "'OBSERVATIONS-03.json'")
exec(compile(source, str(Path(__file__).resolve()), 'exec'))
