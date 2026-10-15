import contextlib
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from scripts import collect_v180_release_inputs as collector


LEDGER_STUB = """import json, sys
for line in {stderr!r}:
    print(line, file=sys.stderr)
print({stdout!r})
sys.exit({code})
"""
RESOURCE_STUB = """import json, sys
from pathlib import Path
Path(sys.argv[sys.argv.index("--output") + 1]).write_text(json.dumps({{"result": "PASS"}}) + "\\n")
print("resource audit failed", file=sys.stderr) if {code} else None
sys.exit({code})
"""
PROVENANCE_STUB = """import sys
from pathlib import Path
folder = Path(sys.argv[sys.argv.index("--output-dir") + 1])
folder.mkdir(parents=True, exist_ok=True)
(folder / "provenance-index.md").write_text("# v1.8 provenance index\\n")
(folder / "jar-provenance-audit.json").write_text('{"result": "PASS"}\\n')
"""
MATRIX = """# PORTING_MATRIX

| 领域 | 1.12 主要位置 | 旧依赖/风险 | 1.20.1 目标 | 目标版本 | 最低验收 | 状态 |
|---|---|---|---|---|---|---|
| 基础机器 | a | b | c | `v0.2.0` | G1 | PASSED |
| 经典加工机器族与流体 | a | b | c | `v1.8.0` | C16 批次 ADR | IN_PROGRESS |
| 组件、配方图与进阶平衡 | a | b | c | `v1.8.0` | C16d 配方图报告 | IN_PROGRESS |
| OBJ/MTL 模型 | a | b | 锯片 C16b | `v1.8.0` | visual | BLOCKED |
| 旧版逐项内容清单 | a | b | [ledger](work/v1.8.0-content-ledger.csv) | `v1.8.0` | A0 | IN_PROGRESS |
| 数据网络 | a | b | c | never unless ADR | — | REJECTED |

## 使用规则
"""
LEDGER = """unit_id,disposition,plan,target,decision,notes,evidence
block:a,IMPLEMENTED,v1.8.0,block a,ADR-062,,docs/work/v1.8.0-c16a-machines/VERIFICATION.md
block:b,REDESIGNED,v1.8.0,block b,ADR-062,,docs/work/v1.8.0-c16d-components/VERIFICATION.md
item:c,PLANNED,C16b,item c,ADR-062,,
item:d,IMPLEMENTED,v0.2.0,item d,ADR-062,,
config:e,REJECTED,-,-,ADR-062,,
config:f,MERGED,-,block:a,ADR-062,,
"""
GOAL_ROUTES = {
    "goal:suited_up": "- goal:suited_up <- goal:1 (round 6)\n  - minecraft:iron_ingot (root)\n",
    "access:moon": ("- access:moon <- flight:2 (round 11)\n  - access:overworld (root)\n"
                    "  - travel:rocket <- rocket:3 (round 10)\n"),
    "event:moon_landing": "- event:moon_landing <- event:4 (round 12)\n  - access:moon (above)\n",
    "event:station_warp": "- event:station_warp <- event:5 (round 12)\n  - access:space <- flight:6 (round 11)\n",
}
EXPECTED_FILES = {
    "SHA256SUMS.txt", "collector-summary.json",
    "assets/registered-content-resources.md", "assets/resource-audit.json",
    "content/ledger-closure-run.json", "content/ledger-summary.json", "content/porting-matrix-counts.md",
    "content/progression-report.md",
    "content/recipe-graph/classic-advancement-reachability-without-tau-ceti.md",
    "content/recipe-graph/classic-advancement-reachability.md",
    "content/recipe-graph/machine-recipe-material-tags.md",
    "content/recipe-graph/registered-item-acquisition-without-tau-ceti.md",
    "content/recipe-graph/registered-item-acquisition.md",
}


def stage_items() -> list[str]:
    nodes = []
    for _, machine, extra in collector.STAGES:
        nodes += [node for node in (machine, *extra) if node.startswith(collector.NAMESPACE + ":")]
    return nodes


def item_report(title: str, unreachable: tuple[str, ...] = (), absent: tuple[str, ...] = ()) -> str:
    lines = [title, "", "## First acquisition witnesses", ""]
    for number, item in enumerate(stage_items(), 1):
        if item in absent:
            continue
        if item in unreachable:
            lines.append(f"- {item} (unreachable)")
        else:
            lines.append(f"- {item} <- recipe:{item} (round {number}); "
                         "prerequisites: [minecraft:iron_ingot, #forge:ingots/iron]")
    return "\n".join(lines) + "\n"


class Fixture:
    def __init__(self, root: Path):
        self.root = root
        self.reports = root / "build" / "reports"
        self.reports.mkdir(parents=True)
        for name, _, title in collector.REPORTS:
            self.report(name, title)
        self.write("docs/PORTING_MATRIX.md", MATRIX)
        self.write("docs/work/v1.8.0-content-ledger.csv", LEDGER)
        self.ledger_stub()
        self.write(collector.RESOURCE_SCRIPT, RESOURCE_STUB.format(code=0))
        self.write(collector.PROVENANCE_SCRIPT, PROVENANCE_STUB)

    def write(self, relative: str, text: str) -> Path:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8", newline="\n")
        return path

    def report(self, name: str, title: str, **items) -> None:
        if name.startswith("registered-item-acquisition"):
            text = item_report(title, **items)
        elif name == "classic-advancement-reachability.md":
            text = title + "\n" + "".join(f"\n## classic/{node}\n\n{route}" for node, route in GOAL_ROUTES.items())
        elif name == "classic-advancement-reachability-without-tau-ceti.md":
            text = title + "\n\n" + "".join(f"- classic/x -> {node} (reachable)\n" for node in GOAL_ROUTES)
        else:
            text = title + "\n\nfixture body\n"
        self.write(f"build/reports/{name}", text)

    def ledger_stub(self, code: int = 1, result: str | None = "FAIL", stderr: tuple[str, ...] = (
            "closure: 1 ledger rows are still PLANNED", "closure: 3 assets are still handled as REVIEW")) -> None:
        stdout = json.dumps({"result": result, "units": 6}, indent=2) if result else "no summary"
        self.write(collector.LEDGER_SCRIPT, LEDGER_STUB.format(stdout=stdout, stderr=list(stderr), code=code))


class CollectV180ReleaseInputsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.base = Path(self.temporary.name)
        self.fixture = Fixture(self.base / "repository")
        self.output = self.base / "staging" / "v1.8.0"

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def collect(self, output: Path | None = None, jar: Path | None = None) -> dict:
        return collector.collect(self.fixture.root, output or self.output, jar)

    def assert_refused(self, *names: str) -> str:
        with self.assertRaises(collector.CollectError) as raised:
            self.collect()
        for name in names:
            self.assertIn(name, str(raised.exception))
        self.assertFalse(self.output.exists(), "a refused run must not leave a staging folder")
        if self.output.parent.exists():
            self.assertEqual([], list(self.output.parent.iterdir()), "a refused run must remove its partial folder")
        return str(raised.exception)

    def staged(self, relative: str) -> str:
        return (self.output / relative).read_text(encoding="utf-8")

    def test_collects_the_w62_layout_with_a_verified_manifest(self):
        result = self.collect()
        files = {path.relative_to(self.output).as_posix() for path in self.output.rglob("*") if path.is_file()}
        self.assertEqual(EXPECTED_FILES, files)
        listed = [line.split("  ", 1)[1] for line in self.staged("SHA256SUMS.txt").splitlines()]
        self.assertEqual(sorted(EXPECTED_FILES - {"SHA256SUMS.txt"}), listed)
        self.assertEqual([], collector.verify(self.output))
        self.assertEqual(self.fixture.reports.joinpath("registered-item-acquisition.md").read_bytes(),
                         (self.output / "content/recipe-graph/registered-item-acquisition.md").read_bytes())
        self.assertEqual("not collected: no --jar given", result["provenance"])
        self.assertIsNone(result["jar"])
        self.assertEqual(json.loads(self.staged("collector-summary.json")), result)

    def test_missing_inputs_fail_and_are_all_named(self):
        (self.fixture.reports / "machine-recipe-material-tags.md").unlink()
        (self.fixture.root / "docs/work/v1.8.0-content-ledger.csv").unlink()
        (self.fixture.root / collector.RESOURCE_SCRIPT).unlink()
        self.fixture.write("docs/PORTING_MATRIX.md", "")
        self.assert_refused("missing input: build/reports/machine-recipe-material-tags.md",
                            "missing input: docs/work/v1.8.0-content-ledger.csv",
                            f"missing input: {collector.RESOURCE_SCRIPT}", "empty input: docs/PORTING_MATRIX.md")
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            code = collector.main(["--repository-root", str(self.fixture.root), "--output", str(self.output)])
        self.assertEqual(1, code)
        self.assertIn("missing input: build/reports/machine-recipe-material-tags.md", stderr.getvalue())

    def test_an_empty_report_or_a_wrong_heading_is_refused(self):
        self.fixture.write("build/reports/registered-content-resources.md", "")
        self.assert_refused("empty input: build/reports/registered-content-resources.md")
        self.fixture.write("build/reports/registered-content-resources.md", "# Something else\n")
        self.assert_refused("unexpected report: build/reports/registered-content-resources.md")
        (self.fixture.reports / "registered-content-resources.md").write_bytes(b"# Registered \xff content\n")
        self.assert_refused("unreadable input: build/reports/registered-content-resources.md is not UTF-8")
        # The main report's title is a prefix of the Tau Ceti variant's; the first line must match exactly.
        self.fixture.write("build/reports/registered-item-acquisition.md",
                           "# Registered item acquisition without Tau Ceti Levels\n")
        self.assert_refused("unexpected report: build/reports/registered-item-acquisition.md")

    def test_a_named_jar_that_does_not_exist_is_refused(self):
        with self.assertRaises(collector.CollectError) as raised:
            self.collect(jar=self.base / "absent.jar")
        self.assertIn("--jar", str(raised.exception))
        self.assertIn("absent.jar", str(raised.exception))

    def test_a_failing_ledger_closure_is_recorded_and_does_not_stop_the_run(self):
        result = self.collect()
        self.assertEqual({"exit_code": 1, "result": "FAIL"}, result["ledger_closure"])
        self.assertEqual({"result": "FAIL", "units": 6}, json.loads(self.staged("content/ledger-summary.json")))
        run = json.loads(self.staged("content/ledger-closure-run.json"))
        self.assertEqual(1, run["exit_code"])
        self.assertEqual(["closure: 1 ledger rows are still PLANNED", "closure: 3 assets are still handled as REVIEW"],
                         run["stderr"])
        self.assertEqual("python -B scripts/validate_v180_content_ledger.py --closure", run["command"])

    def test_a_passing_ledger_closure_is_recorded_too(self):
        self.fixture.ledger_stub(code=0, result="PASS", stderr=())
        result = self.collect()
        self.assertEqual({"exit_code": 0, "result": "PASS"}, result["ledger_closure"])

    def test_a_crashed_or_inconsistent_ledger_run_is_refused(self):
        self.fixture.ledger_stub(code=2, result=None, stderr=("Traceback (most recent call last):",))
        self.assert_refused("ledger closure run exited 2", "Traceback")
        self.fixture.ledger_stub(code=0, result="FAIL")
        self.assert_refused("ledger closure run exited 0")

    def test_a_failing_resource_audit_is_refused(self):
        self.fixture.write(collector.RESOURCE_SCRIPT, RESOURCE_STUB.format(code=1))
        self.assert_refused(collector.RESOURCE_SCRIPT, "exit code 1", "resource audit failed")

    def test_verify_reports_a_hash_mismatch_and_missing_or_unlisted_files(self):
        self.collect()
        (self.output / "content/porting-matrix-counts.md").write_text("tampered\n", encoding="utf-8")
        (self.output / "assets/resource-audit.json").unlink()
        (self.output / "assets/extra.txt").write_text("extra\n", encoding="utf-8")
        self.assertEqual(["missing file: assets/resource-audit.json", "hash mismatch: content/porting-matrix-counts.md",
                          "unlisted file: assets/extra.txt"], collector.verify(self.output))
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()) as stderr:
            self.assertEqual(1, collector.main(["--verify", str(self.output)]))
        self.assertIn("hash mismatch: content/porting-matrix-counts.md", stderr.getvalue())

    def test_an_input_that_changes_during_collection_is_refused(self):
        derive = collector.progression_report

        def rewrite_then_derive(reports):
            self.fixture.write("build/reports/machine-recipe-material-tags.md",
                               "# Machine recipe material tags (W57)\n\nrewritten by a concurrent test run\n")
            return derive(reports)

        with mock.patch.object(collector, "progression_report", side_effect=rewrite_then_derive):
            self.assert_refused("inputs changed during collection", "build/reports/machine-recipe-material-tags.md")

    def test_the_manifest_and_every_staged_file_are_deterministic(self):
        first = self.base / "first"
        self.collect(output=first)
        for name, _, title in reversed(collector.REPORTS):
            path = self.fixture.reports / name
            content = path.read_bytes()
            path.unlink()
            path.write_bytes(content)
            os.utime(path, (1_000_000_000, 1_000_000_000))
        second = self.base / "second"
        self.collect(output=second)
        manifest = (first / "SHA256SUMS.txt").read_bytes()
        self.assertEqual(manifest, (second / "SHA256SUMS.txt").read_bytes())
        self.assertNotIn(b"\r", manifest)
        for path in first.rglob("*"):
            if path.is_file():
                text = path.read_bytes()
                self.assertEqual(text, (second / path.relative_to(first)).read_bytes(), path.name)
                self.assertNotIn(str(self.base).encode("utf-8"), text, f"{path.name} names a local path")

    def test_the_output_must_be_new_or_empty_and_outside_docs_releases(self):
        with self.assertRaises(collector.CollectError) as raised:
            self.collect(output=self.fixture.root / "docs" / "releases" / "v1.8.0")
        self.assertIn("refusing to write into docs/releases", str(raised.exception))
        self.output.mkdir(parents=True)
        (self.output / "stale.txt").write_text("stale\n", encoding="utf-8")
        with self.assertRaises(collector.CollectError) as raised:
            self.collect()
        self.assertIn("output exists and is not an empty folder", str(raised.exception))
        (self.output / "stale.txt").unlink()
        self.collect()
        self.assertEqual([], collector.verify(self.output))

    def test_a_jar_adds_the_provenance_index_and_result(self):
        jar = self.base / "advancedrocketry-community-test.jar"
        jar.write_bytes(b"PK fixture jar")
        result = self.collect(jar=jar)
        self.assertEqual("collected", result["provenance"])
        self.assertEqual({"name": jar.name, "sha256": collector.sha256(b"PK fixture jar")}, result["jar"])
        self.assertEqual("# v1.8 provenance index\n", self.staged("assets/provenance-index.md"))
        self.assertEqual({"result": "PASS"}, json.loads(self.staged("assets/jar-provenance-audit.json")))
        self.assertEqual([], collector.verify(self.output))

    def test_porting_matrix_counts_group_by_batch_with_the_matrix_rows(self):
        self.collect()
        counts = self.staged("content/porting-matrix-counts.md")
        self.assertIn("Matrix row for the whole ledger: 旧版逐项内容清单 (IN_PROGRESS).", counts)
        for row in (
                "| C16a | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 经典加工机器族与流体 (IN_PROGRESS) |",
                "| C16b | 0 | 0 | 1 | 0 | 0 | 0 | 1 | 经典加工机器族与流体 (IN_PROGRESS); OBJ/MTL 模型 (BLOCKED) |",
                # C16d is named in an acceptance cell, so the family token C16 does not cover it.
                "| C16d | 0 | 1 | 0 | 0 | 0 | 0 | 1 | 组件、配方图与进阶平衡 (IN_PROGRESS) |",
                "| v0.2.0 | 1 | 0 | 0 | 0 | 0 | 0 | 1 | 基础机器 (PASSED) |",
                "| MERGED | 0 | 0 | 0 | 1 | 0 | 0 | 1 | no matrix row |",
                "| REJECTED | 0 | 0 | 0 | 0 | 0 | 1 | 1 | 数据网络 (REJECTED) |",
                "| Total | 2 | 1 | 1 | 1 | 0 | 1 | 6 |  |",
                "| config | 0 | 0 | 0 | 1 | 0 | 1 | 2 |",
                "| Total | 2 | 1 | 1 | 1 | 0 | 1 | 6 |"):
            self.assertIn(row + "\n", counts)

    def test_the_progression_report_marks_stages_the_graph_cannot_reach(self):
        unreachable, absent = collector.STAGES[5][1], collector.STAGES[16][1]
        self.fixture.report("registered-item-acquisition.md", "# Registered item acquisition",
                            unreachable=(unreachable,), absent=(absent,))
        result = self.collect()
        self.assertEqual([collector.STAGES[5][0], collector.STAGES[16][0]], result["progression"]["unreachable"])
        report = self.staged("content/progression-report.md")
        self.assertIn(f"Unreachable: {collector.STAGES[5][0]}, {collector.STAGES[16][0]}.", report)
        self.assertIn(f"| 6 | {collector.STAGES[5][0]} | `{unreachable}` | - | **UNREACHABLE**: `{unreachable}` "
                      f"unreachable | reachable |", report)
        self.assertIn(f"**UNREACHABLE**: `{absent}` not in the witness routes", report)
        self.assertIn("- `event:station_warp` <- `event:5` (round 12); prerequisites: `access:space`", report)
        self.assertIn("- `event:moon_landing` <- `event:4` (round 12); prerequisites: `access:moon`", report)

    def test_reports_outside_the_list_are_named_not_staged(self):
        self.fixture.write("build/reports/other-report.md", "# Other\n")
        result = self.collect()
        self.assertEqual(["other-report.md"], result["uncollected_reports"])
        self.assertFalse(any(path.name == "other-report.md" for path in self.output.rglob("*")))


if __name__ == "__main__":
    unittest.main()
