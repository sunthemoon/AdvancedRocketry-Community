import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from scripts.check_gametest_log import (
    DEFAULT_MANIFEST,
    ManifestError,
    check,
    load_manifest,
    main,
    parse_log,
)


BATCH_LOGGER = "net.minecraft.gametest.framework.GameTestBatchRunner/"
SERVER_LOGGER = "net.minecraft.gametest.framework.GameTestServer/"
MOD_LOGGER = "io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity/"
EVENT_LOGGER = "net.minecraftforge.eventbus.EventBus/EVENTBUS"
CONFIG_LOGGER = "net.minecraftforge.common.ForgeConfigSpec/CORE"


def line(level: str, logger: str, message: str, thread: str = "Server thread") -> str:
    return f"[10Oct2026 18:21:11.206] [{thread}/{level}] [{logger}]: {message}"


def batch(name: str, index: int = 1) -> str:
    return line("INFO", BATCH_LOGGER, f"Running test batch '{name}:{index}' (1 tests)...")


COMPLETE = [
    line("INFO", SERVER_LOGGER, "========= 3 GAME TESTS COMPLETE ======================"),
    line("INFO", SERVER_LOGGER, "All 3 required tests passed :)"),
]
LAUNCH = line("INFO", "cpw.mods.modlauncher.Launcher/MODLAUNCHER",
              "ModLauncher running: args [--launchTarget, forgegametestserveruserdev]")
SHUTDOWN = line("INFO", SERVER_LOGGER, "Game test server shutting down")

REFUSAL = "Exception caught during firing event: Refusing chunk save; back up and repair first"
REFUSAL_STACK = [
    "java.lang.IllegalStateException: Refusing chunk save; back up and repair first",
    "\tat TRANSFORMER/advancedrocketrycommunity@dev/io.github.example.gametest.SaveGameTests.refused(SaveGameTests.java:62)",
]


def expectation(**overrides) -> dict:
    entry = {
        "id": "save-refused",
        "level": "ERROR",
        "logger": EVENT_LOGGER,
        "message": "Exception caught during firing event: Refusing chunk save; back up and repair first",
        "stack_contains": "SaveGameTests.",
        "counts": {"defaultBatch": 2},
        "test": "SaveGameTests",
        "reason": "The test posts refused saves.",
    }
    entry.update(overrides)
    return {key: value for key, value in entry.items() if value is not None}


def config_expectation(**overrides) -> dict:
    entry = {
        "id": "config-defaulted",
        "level": "WARN",
        "logger": CONFIG_LOGGER,
        "message": r"Incorrect key [\w.]+ was corrected from null to its default, .*",
        "max": 2,
        "batches": ["<startup>"],
        "test": "environment",
        "reason": "Forge fills a new configuration file.",
    }
    entry.update(overrides)
    return {key: value for key, value in entry.items() if value is not None}


class ManifestFile:
    def __init__(self, test: unittest.TestCase, *expectations: dict, schema: int = 1) -> None:
        directory = tempfile.TemporaryDirectory()
        test.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / "manifest.json"
        self.path.write_text(
            json.dumps({"schema": schema, "expectations": list(expectations)}), encoding="utf-8"
        )

    def load(self):
        return load_manifest(self.path)


def standard_log(*extra: str, refusals: int = 2) -> str:
    lines = [
        LAUNCH,
        line("WARN", CONFIG_LOGGER, "Incorrect key worldgen.geodes was corrected from null to its default, true."),
        batch("defaultBatch"),
    ]
    for _ in range(refusals):
        lines += [line("ERROR", EVENT_LOGGER, REFUSAL), *REFUSAL_STACK]
    return "\n".join([*lines, *extra, *COMPLETE, SHUTDOWN]) + "\n"


class CheckGameTestLogTests(unittest.TestCase):
    def check(self, text: str, *expectations: dict):
        manifest = ManifestFile(self, *(expectations or (expectation(), config_expectation())))
        return check(parse_log(text), manifest.load())

    def assert_problem(self, report, fragment: str) -> None:
        self.assertFalse(report.passed)
        self.assertTrue(
            any(fragment in problem for problem in report.problems),
            f"{fragment!r} not in {report.problems}",
        )

    def test_declared_entries_with_exact_counts_pass(self) -> None:
        report = self.check(standard_log())
        self.assertTrue(report.passed, report.problems)
        self.assertIn("FATAL 0, ERROR 2, WARN 1", report.notes)

    def test_batch_index_suffix_is_ignored(self) -> None:
        text = standard_log(refusals=1).replace(
            line("INFO", SERVER_LOGGER, "========= 3"),
            "\n".join([batch("defaultBatch", 2), line("ERROR", EVENT_LOGGER, REFUSAL), *REFUSAL_STACK,
                       line("INFO", SERVER_LOGGER, "========= 3")]),
        )
        self.assertTrue(self.check(text).passed)

    def test_unexpected_error_fails(self) -> None:
        report = self.check(standard_log(line("ERROR", MOD_LOGGER, "Something else broke")))
        self.assert_problem(report, "unexpected ERROR, batch 'defaultBatch'")

    def test_missing_expected_error_fails(self) -> None:
        report = self.check(standard_log(refusals=1))
        self.assert_problem(report, "expected exactly 2 ERROR entries in batch 'defaultBatch', observed 1")

    def test_extra_expected_error_fails(self) -> None:
        report = self.check(standard_log(refusals=3))
        self.assert_problem(report, "expected exactly 2 ERROR entries in batch 'defaultBatch', observed 3")

    def test_expected_message_in_an_undeclared_batch_fails(self) -> None:
        text = standard_log(batch("station"), line("ERROR", EVENT_LOGGER, REFUSAL), *REFUSAL_STACK)
        self.assert_problem(self.check(text), "ERROR of save-refused in an undeclared batch, batch 'station'")

    def test_entry_without_the_declared_stack_frame_fails(self) -> None:
        text = standard_log(refusals=1).replace(
            line("INFO", SERVER_LOGGER, "========= 3"),
            "\n".join([line("ERROR", EVENT_LOGGER, REFUSAL), "\tat some.other.Caller.run(Caller.java:1)",
                       line("INFO", SERVER_LOGGER, "========= 3")]),
        )
        report = self.check(text)
        self.assert_problem(report, "unexpected ERROR")
        self.assert_problem(report, "observed 1")

    def test_message_must_match_completely(self) -> None:
        report = self.check(standard_log(line("ERROR", EVENT_LOGGER, REFUSAL + " and more"), *REFUSAL_STACK))
        self.assert_problem(report, "unexpected ERROR")

    def test_fatal_always_fails(self) -> None:
        report = self.check(standard_log(line("FATAL", MOD_LOGGER, "Unrecoverable")))
        self.assert_problem(report, "FATAL entry")

    def test_truncated_log_fails(self) -> None:
        report = self.check(standard_log().replace(COMPLETE[0] + "\n", ""))
        self.assert_problem(report, "no 'GAME TESTS COMPLETE' banner")

    def test_entries_after_the_banner_belong_to_shutdown(self) -> None:
        text = standard_log() + line("ERROR", EVENT_LOGGER, REFUSAL) + "\n" + "\n".join(REFUSAL_STACK) + "\n"
        self.assert_problem(self.check(text), "undeclared batch, batch '<shutdown>'")

    def test_bounded_warning_passes_up_to_its_maximum(self) -> None:
        warning = line("WARN", CONFIG_LOGGER, "Incorrect key stations was corrected from null to its default, {}.")
        self.assertTrue(self.check(standard_log().replace(batch("defaultBatch"), warning + "\n" + batch("defaultBatch"))).passed)

    def test_bounded_warning_above_its_maximum_fails(self) -> None:
        warning = line("WARN", CONFIG_LOGGER, "Incorrect key stations was corrected from null to its default, {}.")
        text = standard_log().replace(batch("defaultBatch"), f"{warning}\n{warning}\n{batch('defaultBatch')}")
        self.assert_problem(self.check(text), "expected at most 2 WARN entries, observed 3")

    def test_bounded_warning_outside_its_batches_fails(self) -> None:
        warning = line("WARN", CONFIG_LOGGER, "Incorrect key stations was corrected from null to its default, {}.")
        self.assert_problem(self.check(standard_log(warning)), "WARN of config-defaulted in an undeclared batch")

    def test_unbounded_batches_accept_any_batch(self) -> None:
        overloaded = config_expectation(
            id="overloaded", logger="net.minecraft.server.MinecraftServer/",
            message=r"Can't keep up! Is the server overloaded\? Running \d+ms or \d+ ticks behind",
            max=1, batches=None,
        )
        text = standard_log(line("WARN", "net.minecraft.server.MinecraftServer/",
                                 "Can't keep up! Is the server overloaded? Running 2439ms or 48 ticks behind"))
        self.assertTrue(self.check(text, expectation(), config_expectation(), overloaded).passed)

    def test_an_entry_matching_two_expectations_fails(self) -> None:
        twin = expectation(id="save-refused-again")
        self.assert_problem(self.check(standard_log(), expectation(), twin, config_expectation()),
                            "matches several expectations (save-refused, save-refused-again)")

    def test_continuation_lines_are_not_entries(self) -> None:
        text = standard_log().replace(
            REFUSAL_STACK[0], REFUSAL_STACK[0] + "\nCaused by: [Server thread/ERROR] looks like a header but is not"
        )
        self.assertTrue(self.check(text).passed)

    def test_malformed_headers_are_not_silently_ignored(self) -> None:
        malformed = line("ERROR", MOD_LOGGER, "Unrecognized error").replace("]: ", "] : ")
        self.assert_problem(self.check(standard_log(malformed)), "malformed or unrecognized log header")

    def test_bom_prefixed_header_is_not_a_continuation(self) -> None:
        malformed = "\ufeff" + line("ERROR", MOD_LOGGER, "Unrecognized error")
        self.assert_problem(self.check(standard_log(malformed)), "malformed or unrecognized log header")

    def test_plain_exception_text_cannot_supply_a_stack_frame(self) -> None:
        text = standard_log().replace(REFUSAL_STACK[1], "java.lang.RuntimeException: SaveGameTests.refused")
        self.assert_problem(self.check(text), "unexpected ERROR")

    def test_different_class_name_cannot_supply_a_stack_frame(self) -> None:
        text = standard_log().replace("SaveGameTests.refused", "OtherSaveGameTests.refused")
        self.assert_problem(self.check(text), "unexpected ERROR")

    def test_named_method_matches_exactly(self) -> None:
        text = standard_log().replace("SaveGameTests.refused", "SaveGameTests.refusedUnexpected")
        self.assert_problem(self.check(text, expectation(stack_contains="SaveGameTests.refused"),
                                       config_expectation()), "unexpected ERROR")

    def test_nested_failure_handler_frame_is_recognized(self) -> None:
        text = standard_log().replace("SaveGameTests.refused", "SaveGameTests$Failure.accept")
        self.assertTrue(self.check(text, expectation(stack_contains="SaveGameTests$Failure.accept"),
                                   config_expectation()).passed)

    def test_throwable_must_match_independently_of_frame(self) -> None:
        expected = expectation(exception=r"java\.lang\.IllegalStateException: Refusing chunk save; back up and repair first")
        self.assertTrue(self.check(standard_log(), expected, config_expectation()).passed)
        text = standard_log().replace(REFUSAL_STACK[0], "java.io.IOException: Disk full")
        self.assert_problem(self.check(text, expected, config_expectation()), "unexpected ERROR")

    def test_truncation_after_banner_or_summary_fails(self) -> None:
        text = standard_log()
        for ending in COMPLETE:
            truncated = text[:text.index(ending) + len(ending)] + "\n"
            self.assert_problem(self.check(truncated), "no normal GameTest shutdown marker")

    def test_missing_launch_fails(self) -> None:
        self.assert_problem(self.check(standard_log().replace(LAUNCH + "\n", "")),
                            "no GameTest userdev launch header")

    def test_failed_summary_fails_even_without_a_new_error(self) -> None:
        text = standard_log().replace("All 3 required tests passed :)", "1 required tests failed :(")
        self.assert_problem(self.check(text), "unsuccessful or unrecognized required-test summary")

    def test_missing_summary_fails(self) -> None:
        self.assert_problem(self.check(standard_log().replace(COMPLETE[1] + "\n", "")),
                            "no successful required-test summary")

    def test_inconsistent_summary_count_fails(self) -> None:
        text = standard_log().replace("All 3 required tests passed", "All 4 required tests passed")
        self.assert_problem(self.check(text), "summary count is inconsistent")

    def test_duplicate_markers_fail(self) -> None:
        for marker in (LAUNCH, *COMPLETE):
            text = standard_log().replace(marker, marker + "\n" + marker)
            self.assert_problem(self.check(text), "duplicate or misplaced")

    def test_summary_before_completion_fails(self) -> None:
        text = standard_log().replace("\n".join(COMPLETE), "\n".join(reversed(COMPLETE)))
        self.assert_problem(self.check(text), "misplaced required-test summary")

    def test_batch_after_completion_fails(self) -> None:
        text = standard_log().replace(SHUTDOWN, batch("late") + "\n" + SHUTDOWN)
        self.assert_problem(self.check(text), "misplaced test batch")

    def test_content_after_shutdown_fails(self) -> None:
        self.assert_problem(self.check(standard_log() + "trailing partial entry"), "content after GameTest shutdown")

    def test_incomplete_final_line_fails(self) -> None:
        self.assert_problem(self.check(standard_log().rstrip("\n")), "incomplete line")


class ManifestValidationTests(unittest.TestCase):
    def assert_rejected(self, fragment: str, *expectations: dict, schema: int = 1) -> None:
        with self.assertRaises(ManifestError) as raised:
            ManifestFile(self, *expectations, schema=schema).load()
        self.assertIn(fragment, str(raised.exception))

    def test_error_expectations_need_exact_counts(self) -> None:
        self.assert_rejected("only a WARN expectation may use 'max'", expectation(counts=None, max=3))

    def test_counts_and_max_are_exclusive(self) -> None:
        self.assert_rejected("exactly one of 'counts' and 'max'", config_expectation(counts={"a": 1}))

    def test_batches_only_limit_max(self) -> None:
        self.assert_rejected("'batches' only limits a 'max' expectation", expectation(batches=["a"]))

    def test_counts_must_be_positive(self) -> None:
        self.assert_rejected("positive integers", expectation(counts={"defaultBatch": 0}))
        self.assert_rejected("positive integers", expectation(counts={"defaultBatch": True}))

    def test_reason_and_test_are_required(self) -> None:
        self.assert_rejected("'reason'", expectation(reason=" "))
        self.assert_rejected("'test'", expectation(test=None))

    def test_unknown_keys_and_duplicate_ids_are_rejected(self) -> None:
        self.assert_rejected("unknown keys ['count']", expectation(count=2))
        self.assert_rejected("repeats id 'save-refused'", expectation(), expectation())

    def test_invalid_pattern_and_schema_are_rejected(self) -> None:
        self.assert_rejected("not a valid pattern", expectation(message="("))
        self.assert_rejected("schema 1", expectation(), schema=2)
        self.assert_rejected("schema 1", expectation(), schema=True)

    def test_exception_pattern_is_validated(self) -> None:
        self.assert_rejected("exception must be a non-empty string", expectation(exception=" "))
        self.assert_rejected("not a valid pattern", expectation(exception="("))

    def test_repository_manifest_is_valid(self) -> None:
        expectations = load_manifest(DEFAULT_MANIFEST)
        self.assertTrue(all(item.level == "WARN" for item in expectations if item.maximum is not None))
        self.assertEqual(
            62, sum(sum(item.counts.values()) for item in expectations if item.level == "ERROR")
        )

    def test_recipe_and_recovery_cases_have_independent_counts(self) -> None:
        expectations = load_manifest(DEFAULT_MANIFEST)
        recipes = [item for item in expectations if item.id.endswith("recipe-disabled-unbound-tag")]
        self.assertEqual(3, len(recipes))
        self.assertTrue(all(item.counts == {"recipe_signatures": 1} for item in recipes))
        recovery = [item for item in expectations if item.id.startswith("transfer-recovery-")]
        self.assertEqual(4, len(recovery))
        self.assertTrue(all(item.counts == {"flight_recovery": 1} for item in recovery))

    def test_chunk_save_rules_require_exact_throwable_and_frame(self) -> None:
        rules = [item for item in load_manifest(DEFAULT_MANIFEST) if item.logger == "net.minecraft.server.level.ChunkMap/"]
        self.assertEqual(2, len(rules))
        self.assertTrue(all(item.exception is not None and item.stack_contains is not None for item in rules))


class WorkflowIntegrationTests(unittest.TestCase):
    def test_hosted_checker_runs_after_native_command_under_pipefail(self) -> None:
        root = DEFAULT_MANIFEST.parent.parent
        workflow = (root / ".github/workflows/v180-development.yml").read_text(encoding="utf-8")
        step = workflow.split("- name: Run all Forge GameTests\n", 1)[1].split("\n      - name:", 1)[0]
        self.assertLess(step.index("./gradlew runGameTestServer"),
                        step.index("python -B scripts/check_gametest_log.py build/gametest/logs/latest.log"))
        self.assertIn("shell: bash", workflow)
        self.assertNotIn("continue-on-error", step)
        self.assertIn("-p test_check_gametest_log.py", workflow)


class CommandLineTests(unittest.TestCase):
    def run_main(self, *arguments: str) -> int:
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return main(list(arguments))

    def write(self, name: str, text: str) -> Path:
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / name
        path.write_text(text, encoding="utf-8")
        return path

    def test_exit_codes(self) -> None:
        manifest = ManifestFile(self, expectation(), config_expectation()).path
        self.assertEqual(0, self.run_main(str(self.write("ok.log", standard_log())), "--manifest", str(manifest)))
        self.assertEqual(1, self.run_main(str(self.write("bad.log", standard_log(refusals=1))),
                                          "--manifest", str(manifest)))
        self.assertEqual(2, self.run_main(str(Path(manifest).with_name("missing.log")), "--manifest", str(manifest)))
        self.assertEqual(2, self.run_main(str(self.write("ok.log", standard_log())),
                                          "--manifest", str(self.write("bad.json", "{"))))

    def test_inventory_lists_groups_without_a_manifest(self) -> None:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = main([str(self.write("ok.log", standard_log())), "--inventory",
                         "--manifest", "does-not-exist.json"])
        self.assertEqual(0, code)
        self.assertIn("   2  ERROR  defaultBatch", output.getvalue())
        self.assertIn("io.github.example.gametest.SaveGameTests.refused", output.getvalue())


if __name__ == "__main__":
    unittest.main()
