"""Run the disjoint remainder after the recorded bootstrap and CI partitions."""
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(r"D:\GitHub\AdvancedRocketry-Community")
OUT = Path(r"C:\Users\Administrator\AppData\Local\Temp\arce-v100-ci\checks")
sys.path.insert(0, str(ROOT))
EXCLUDED = {"tests.test_validate_bootstrap_provenance", "tests.test_bootstrap_version_dispatch",
            "tests.test_validate_repository", "tests.test_ci_artifact_identity"}


def flatten(suite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            yield from flatten(item)
        else:
            yield item


files = sorted((ROOT / "tests").glob("test_*.py"))
if set(files) != set((ROOT / "tests").rglob("test_*.py")):
    raise RuntimeError("Nested tests require an explicit discovery update")
loader = unittest.TestLoader()
tests = list(flatten(loader.loadTestsFromNames(["tests." + path.stem for path in files])))
if loader.errors:
    raise RuntimeError(loader.errors)
selected = [test for test in tests if test.__class__.__module__ not in EXCLUDED]
document = {"total": len(tests), "selected": len(selected), "excluded_modules": sorted(EXCLUDED),
            "selected_tests": [test.id() for test in selected],
            "excluded_tests": [test.id() for test in tests if test.__class__.__module__ in EXCLUDED]}
(OUT / "python-discovery.json").write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
with (OUT / "python-remaining.txt").open("w", encoding="utf-8", newline="\n") as stream:
    result = unittest.TextTestRunner(stream=stream, verbosity=2).run(unittest.TestSuite(selected))
print(f"Remaining partition: ran={result.testsRun}, failures={len(result.failures)}, errors={len(result.errors)}, skips={len(result.skipped)}")
sys.exit(0 if result.wasSuccessful() else 1)
