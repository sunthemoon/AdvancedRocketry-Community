import re
import tempfile
import unittest
from pathlib import Path

from scripts import v130_api_version as api_version

ROOT = Path(__file__).resolve().parent.parent
JUNIT = ROOT / "src/test/java/io/github/sunthemoon/advancedrocketrycommunity/api/version/ApiVersionsTest.java"


def source(*definitions: str) -> str:
    body = "".join(f"    {definition}\n" for definition in definitions)
    return f"package x;\n\npublic final class ApiVersions {{\n{body}}}\n"


class ApiVersionSourceTests(unittest.TestCase):
    def read(self, text: str) -> str:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "ApiVersions.java"
            path.write_text(text, encoding="utf-8", newline="\n")
            return api_version.read(path)

    def test_current_is_the_java_definition_and_matches_the_junit_pin(self):
        self.assertEqual(api_version.read(api_version.SOURCE), api_version.CURRENT)
        self.assertRegex(api_version.CURRENT, r"^\d+\.\d+$")
        pinned = re.findall(r"assertEquals\(new ApiVersion\((\d+), (\d+)\), ApiVersions\.current\(\)\);",
                            JUNIT.read_text(encoding="utf-8"))
        self.assertEqual([tuple(api_version.CURRENT.split("."))], pinned)

    def test_the_logged_form_is_major_dot_minor(self):
        self.assertEqual("2.10", self.read(source("private static final ApiVersion CURRENT = new ApiVersion(2, 10);")))
        self.assertEqual("1.0", self.read(source("private static final ApiVersion CURRENT = new ApiVersion(1, 0);")))

    def test_an_absent_repeated_or_unusual_definition_is_refused(self):
        valid = "private static final ApiVersion CURRENT = new ApiVersion(1, 9);"
        for text in (source(), source(valid, valid), source(valid.replace("9", "09")),
                     source(valid.replace("CURRENT", "PREVIOUS")), source(valid.replace("(1, 9)", "(1,9)"))):
            with self.subTest(text=text), self.assertRaises(ValueError):
                self.read(text)

    def test_an_oversized_source_is_refused(self):
        padding = "//" + "x" * api_version.LIMIT + "\n"
        with self.assertRaises(ValueError):
            self.read(source("private static final ApiVersion CURRENT = new ApiVersion(1, 9);") + padding)

    def test_no_v130_smoke_pins_an_api_literal(self):
        scripts = sorted((ROOT / "scripts").glob("run_v130_*.py"))
        self.assertEqual(8, len(scripts))
        for path in scripts:
            with self.subTest(script=path.name):
                self.assertEqual([], re.findall(r"API \d", path.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
