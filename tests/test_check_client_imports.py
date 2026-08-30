import tempfile
import unittest
from pathlib import Path

from scripts.check_client_imports import find_violations


class ClientImportCheckTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)

    def write_source(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_client_package_may_reference_minecraft_client(self) -> None:
        self.write_source(
            "example/client/ClientOnly.java",
            "import net.minecraft.client.Minecraft;\n",
        )

        self.assertEqual([], find_violations(self.root))

    def test_common_package_cannot_reference_minecraft_client(self) -> None:
        self.write_source(
            "example/CommonCode.java",
            "import net.minecraft.client.Minecraft;\n",
        )

        self.assertEqual(
            ["example/CommonCode.java:1"],
            find_violations(self.root),
        )

    def test_missing_source_root_is_reported(self) -> None:
        missing = self.root / "missing"

        self.assertEqual(
            [f"Source root does not exist: {missing}"],
            find_violations(missing),
        )


if __name__ == "__main__":
    unittest.main()
