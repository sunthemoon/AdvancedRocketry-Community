"""Correct exact named author MANIFEST filename; retain earlier failed helpers."""
from pathlib import Path
p = Path(__file__).resolve().with_name('audit02.py')
source = p.read_text(encoding='utf-8')
changes = {
    "re.match(r'^\\[\\d\\d:\\d\\d:\\d\\d\\] \\[[^\\]]+\\]',l)":
    "re.match(r'^\\[[^\\]\\r\\n]+\\] \\[[^\\]\\r\\n]+/(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\]',l)",
    "('EVIDENCE-MANIFEST.json','PACKET-MANIFEST.json')":
    "('EVIDENCE-MANIFEST.json','PACKET-MANIFEST.json','MANIFEST.json')",
    "print('BATCH SAMPLE',i,l)":
    "pass  # Independent actual batch totals measured by gt_context04.py.",
}
for before,after in changes.items():
    assert source.count(before) == 1, before
    source = source.replace(before, after)
exec(compile(source, str(p.with_name('audit04.py')), 'exec'))
