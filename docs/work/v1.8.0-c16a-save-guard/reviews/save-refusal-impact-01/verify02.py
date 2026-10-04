"""Correct reviewer adapter binding/actual ternary spelling; retain verify01 failures."""
from pathlib import Path

source = Path(__file__).with_name('verify01.py').read_text(encoding='utf-8')
changes = {
    "return z, member": "return z, staticmethod(member)",
    "self.assertIn('65_536, 20, 4_096', migration)":
    "self.assertIn('ROOT.equals(key) ? MAX_BYTES : 65_536', migration)\n        self.assertIn('ROOT.equals(key) ? 16 : 20, ROOT.equals(key) ? 256 : 4_096', migration)",
    "self.assertIn('MAX_BYTES, 16, 256', migration)":
    "self.assertIn('private static final String JOURNAL_ROOT = \"arce_process_journal\";', migration)",
    "'VERIFY-01.json'": "'VERIFY-02.json'",
}
for old, new in changes.items():
    assert source.count(old) == 1
    source = source.replace(old, new)
exec(compile(source, str(Path(__file__).resolve()), 'exec'))
