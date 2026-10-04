"""Correct reviewer log-header selector only; preserve audit02.py/red receipt."""
from pathlib import Path
p = Path(__file__).resolve().with_name('audit02.py')
source = p.read_text(encoding='utf-8')
before = "re.match(r'^\\[\\d\\d:\\d\\d:\\d\\d\\] \\[[^\\]]+\\]',l)"
after = "re.match(r'^\\[[^\\]\\r\\n]+\\] \\[[^\\]\\r\\n]+/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\]',l)"
assert source.count(before) == 1
source = source.replace(before, after)
source = source.replace("print('BATCH SAMPLE',i,l)", "pass  # All actual batch rows counted separately in gt_context04.py.")
exec(compile(source, str(p.with_name('audit03.py')), 'exec'))
