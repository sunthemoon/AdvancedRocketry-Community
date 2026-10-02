import tempfile
import unittest
import zipfile
from pathlib import Path

from tests.test_vanilla_derivation import _icon, _noise, _png
from tools.audit.screen_generated_art import main, screen


class ScreenGeneratedArtTests(unittest.TestCase):
    """The screen of a DataGen root (ADR-063 section 9): a copied vanilla texture fails, new art passes."""

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        base = Path(self.directory.name)
        self.jar = base / "client.jar"
        with zipfile.ZipFile(self.jar, "w") as archive:
            archive.writestr("assets/minecraft/textures/item/ingot.png", _png(16, 16, _icon()))
            archive.writestr("assets/minecraft/textures/block/noise.png", _png(16, 16, _noise(16, 16, 7)))
        self.root = base / "resources"
        textures = self.root / "assets" / "example" / "textures" / "item"
        textures.mkdir(parents=True)
        (textures / "new.png").write_bytes(_png(16, 16, _noise(16, 16, 99, colours=6)))
        self.copied = textures / "copied.png"
        self.copied.write_bytes(_png(16, 16, _icon()))

    def tearDown(self):
        self.directory.cleanup()

    def test_a_copied_texture_is_flagged_and_new_art_is_clear(self):
        result = screen(self.root, [f"1.20.1={self.jar}"])
        files = result["files"]
        self.assertEqual("HIT", files["assets/example/textures/item/copied.png"]["verdict"])
        self.assertEqual("1.20.1:assets/minecraft/textures/item/ingot.png",
                         files["assets/example/textures/item/copied.png"]["best"])
        self.assertEqual("CLEAR", files["assets/example/textures/item/new.png"]["verdict"])
        self.assertEqual({"CLEAR": 1, "HIT": 1}, result["counts"])

    def test_the_command_fails_while_anything_is_flagged(self):
        self.assertEqual(1, main(["--root", str(self.root), "--vanilla", f"1.20.1={self.jar}"]))
        self.copied.unlink()
        report = Path(self.directory.name) / "report.json"
        self.assertEqual(0, main(["--root", str(self.root), "--vanilla", f"1.20.1={self.jar}",
                                  "--output", str(report)]))
        self.assertIn('"CLEAR": 1', report.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
