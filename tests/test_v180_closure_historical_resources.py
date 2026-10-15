import hashlib
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts import (
    manage_v020_generated_manifest as v020,
    manage_v040_generated_manifest as v040,
    manage_v080_generated_manifest as v080,
)


ROOT = Path(__file__).resolve().parents[1]
# Release-sealed inventories restored by Root in 16c1ed71; never regenerate these for v1.8 bindings.
SEALED = (
    (v020, "dc191cc836740ec5aa91b1491ee03db48f382985ce2eea008ec649f5af7f1e34"),
    (v040, "1aba512b16204295e1f8ca127affb96c7d4277d53984483188c4982b51a9a8b8"),
    (v080, "122aa57c53a014ee890844738c7239f09fcc703f610251ce67ab8d58850b7a09"),
)


class V180ClosureHistoricalResourcesTests(unittest.TestCase):
    def test_every_historical_file_matches_its_unchanged_sealed_inventory(self) -> None:
        for validator, digest in SEALED:
            with self.subTest(root=validator.GENERATED_ROOT):
                record = ROOT / validator.DEFAULT_OUTPUT
                self.assertEqual(digest, hashlib.sha256(record.read_bytes()).hexdigest())
                self.assertEqual([], validator.verify(ROOT, record))

    def test_sealed_inventory_check_detects_a_changed_model_without_touching_the_repository(self) -> None:
        for validator, _ in SEALED:
            with self.subTest(root=validator.GENERATED_ROOT), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                shutil.copytree(ROOT / validator.GENERATED_ROOT, root / validator.GENERATED_ROOT)
                record = root / validator.DEFAULT_OUTPUT
                record.parent.mkdir(parents=True)
                shutil.copyfile(ROOT / validator.DEFAULT_OUTPUT, record)
                self.assertEqual([], validator.verify(root, record))
                model = next((root / validator.GENERATED_ROOT / "assets").rglob("models/**/*.json"))
                model.write_bytes(model.read_bytes() + b"\n")
                self.assertTrue(validator.verify(root, record), "changed model bytes must fail verification")


if __name__ == "__main__":
    unittest.main()
