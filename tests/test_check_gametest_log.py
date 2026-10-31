import contextlib
import gzip
import io
import json
import os
import re
import shlex
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from scripts.check_gametest_log import (
    DEFAULT_MANIFEST,
    Entry,
    ManifestError,
    check,
    load_manifest,
    main,
    parse_log,
)
from scripts.validate_repository import parse_workflow_jobs


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

    def test_whitespace_prefixed_checked_headers_fail(self) -> None:
        for prefix in (" ", "\t", " \ufeff\t"):
            for level in ("ERROR", "WARN", "FATAL"):
                with self.subTest(prefix=prefix, level=level):
                    malformed = prefix + line(level, MOD_LOGGER, "Unrecognized event")
                    self.assert_problem(self.check(standard_log(malformed)), "malformed or unrecognized log header")

    def test_damaged_delimiters_and_unsupported_event_prefixes_fail(self) -> None:
        original = line("ERROR", MOD_LOGGER, "Unrecognized event")
        malformed = (
            original.replace("/ERROR]", "/ERROR"),
            original.replace("/ERROR]", "/ERROR ]"),
            original.replace(".206]", ".206"),
            original.replace("[Server thread/ERROR]", "[Server thread ERROR]"),
            original.replace("/ERROR]", "/UNKNOWN]"),
            "\x1b[31m" + original,
            "\x00" + original,
        )
        for value in malformed:
            with self.subTest(value=value):
                self.assert_problem(self.check(standard_log(value)), "malformed or unrecognized log header")

    def test_missing_initial_timestamp_bracket_fails(self) -> None:
        for level in ("ERROR", "WARN", "FATAL"):
            with self.subTest(level=level):
                malformed = line(level, MOD_LOGGER, "Unrecognized event")[1:]
                self.assert_problem(self.check(standard_log(malformed)), "malformed or unrecognized log header")

    def test_native_fields_with_damaged_or_absent_timestamp_fail(self) -> None:
        for level in ("ERROR", "WARN", "FATAL"):
            original = line(level, MOD_LOGGER, "Unrecognized event")
            fields = original.split("] ", 1)[1]
            malformed = (
                "X" + original[1:],
                original[3:],
                "\x1b[31m" + fields,
                "\x00" + fields,
                "Caused by: " + fields,
                "damaged timestamp " + fields.replace(f"/{level}]", f"/{level}"),
            )
            for value in malformed:
                with self.subTest(level=level, value=value):
                    self.assert_problem(self.check(standard_log(value)), "malformed or unrecognized log header")

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
        # 62 deliberate ERROR entries plus W07's 17: classic_controls server and client version
        # rejections (8 each) and one dropped unknown message index.
        # W17 adds one deliberate solar array chunk-save refusal.
        # W21cF adds one forced research debit shortfall (w21cf-research-debit-shortfall).
        # W33 adds one oversized unlit torch root chunk-save refusal (w33-unlit-torch-chunk-save-refused).
        # W44I adds one refused A1 first-warp replacement (w44-a1-refused-first-warp-replacement).
        # W37F adds one deliberate earlier respawn-listener interruption.
        # W37I adds two more, one per interrupted-window logout test (w37i-respawn-listener-interruption-logout).
        # W37J adds seven deliberate interruption windows (w37j-respawn-save-listener-interruptions).
        # W37B adds five earlier time-check interruptions: four command-window cases and one next-START save.
        # W91F adds four deliberate detached journal failures for G2, G3, Moon election and N2 removal.
        # W92 adds four injected failures of isolated landed-disassembly journals (w92-isolated-landed-journal-unacknowledged).
        # W90b adds one failed F2 countdown effect after publication (w90b-post-publication-discard-effect-failed).
        # W129a adds one injected detached transaction-journal failure streak (w129a-transaction-journal-unacknowledged).
        # W129c removes the two failed idle-launch flushes and the failed discovery-launch flush (a launch only
        # requests its write now) and adds one registry failure streak in each of those two batches
        # (w129c-satellite-registry-unacknowledged): 136 - 3 + 2.
        self.assertEqual(
            # W153/R92 adds three exact-version rejection cases in each direction.
            # W159 adds ten actual backup retries and their single abort, all in one named fixture.
            # W160F repeats that real filesystem fault in the controlled ordinary-save regression.
            # W179 adds the over-limit data-pack reload's 43 refusals (w179-recipe-*, w179-recipe-file-unparsable,
            # w179-tag-unreadable, w179-tag-missing-reference and w179-pattern-catalog-rejected): 135 + 43.
            # W193 logs the classic serializer's 36 refusals of that pack as one summary (w193-recipe-refusal-summary
            # replaces w179-recipe-text-bound to w179-recipe-identifier-bound): 178 - 36 + 1.
            143, sum(sum(item.counts.values()) for item in expectations if item.level == "ERROR")
        )

    def test_w159_player_save_failures_are_exact_and_batch_scoped(self) -> None:
        rules = {item.id: item for item in load_manifest(DEFAULT_MANIFEST) if item.id.startswith("w159-")}
        self.assertEqual({
            "w159-temporary-player-write-failure": {"rocket_rejoin_temp_failure": 1, "rocket_rejoin_temp_ordinary": 1},
            "w159-player-backup-delete-failure": {"rocket_rejoin_replace_failure": 10, "rocket_rejoin_replace_ordinary": 10},
            "w159-player-backup-delete-sequence": {"rocket_rejoin_replace_failure": 10, "rocket_rejoin_replace_ordinary": 10},
            "w159-player-backup-retries": {"rocket_rejoin_replace_failure": 10, "rocket_rejoin_replace_ordinary": 10},
            "w159-player-backup-abort": {"rocket_rejoin_replace_failure": 1, "rocket_rejoin_replace_ordinary": 1},
            "w159-old-root-vehicle-remount-refused": {"rocket_rejoin_vehicle": 1, "rocket_rejoin_vehicle_first": 1},
        }, {name: rule.counts for name, rule in rules.items()})
        self.assertTrue(all(rule.maximum is None and not rule.allows_batch("defaultBatch") for rule in rules.values()))

    def test_legacy_scalar_recovery_warning_has_one_separate_batch_count(self) -> None:
        rule = next(item for item in load_manifest(DEFAULT_MANIFEST)
                    if item.id == "legacy-scalar-keep-source-recovery")
        self.assertEqual("WARN", rule.level)
        self.assertEqual({"legacy_scalar_recovery": 1}, rule.counts)
        self.assertTrue(rule.allows_batch("legacy_scalar_recovery"))
        self.assertFalse(rule.allows_batch("flight_recovery"))
        self.assertFalse(rule.allows_batch("bay_transfer_hook"))

    def test_recipe_and_recovery_cases_have_independent_counts(self) -> None:
        expectations = load_manifest(DEFAULT_MANIFEST)
        recipes = [item for item in expectations if item.id.endswith("recipe-disabled-unbound-tag")]
        self.assertEqual(3, len(recipes))
        self.assertTrue(all(item.counts == {"recipe_signatures": 1} for item in recipes))
        recovery = [item for item in expectations if item.id.startswith("transfer-recovery-")]
        self.assertEqual(4, len(recovery))
        # W13 drives each existing recovery case once more in its separate pad-reload batch.
        self.assertTrue(all(item.counts == {"flight_recovery": 1, "landing_pads": 1} for item in recovery))

    def test_named_pad_occupancy_return_has_its_own_exact_count(self) -> None:
        rules = load_manifest(DEFAULT_MANIFEST)
        returned = next(item for item in rules if item.id == "transfer-returned-to-source")
        # W24 adds the legacy (unnamed station pad) pre-spawn refusal in its own batch.
        # W83b adds the refusal by a rocket parked in a footprint column whose entities loaded during the wait.
        self.assertEqual(
            {
                "destination_readiness": 1,
                "flight": 1,
                "planetary_changed_pad": 1,
                "named_pad_occupancy": 1,
                "legacy_pad_occupancy": 1,
                "arrival_footprint": 1,
            },
            returned.counts,
        )

    def test_pending_fixture_credit_refusal_is_not_an_expected_warning(self) -> None:
        rules = load_manifest(DEFAULT_MANIFEST)
        self.assertNotIn("station-warp-credit-refused", {item.id for item in rules})
        for name in ("station_warp_fold", "precision_unload_prepared", "passenger_logout_cleanup"):
            with self.subTest(batch=name):
                entry = Entry(1, "WARN", MOD_LOGGER,
                              "ARCE_STATION_WARP_CREDIT_REFUSED offered=1910000 credited=0 entry_room=true",
                              name, ())
                self.assertFalse(any(rule.describes(entry) for rule in rules))

    def test_chunk_save_rules_require_exact_throwable_and_frame(self) -> None:
        rules = [item for item in load_manifest(DEFAULT_MANIFEST) if item.logger == "net.minecraft.server.level.ChunkMap/"]
        self.assertEqual(2, len(rules))
        self.assertTrue(all(item.exception is not None and item.stack_contains is not None for item in rules))

    def test_eventbus_rules_reject_unrelated_same_class_methods(self) -> None:
        rules = {item.id: item for item in load_manifest(DEFAULT_MANIFEST)}
        cases = (
            ("recipe-chunk-save-refused", "Exception caught during firing event: Refusing chunk save with oversized recipe input; back up and repair first",
             "RecipeSignatureSaveEventGameTests.registeredRecipeGuardRetainsDenialAfterEveryOutgoingRootDisappears", "defaultBatch"),
            ("precision-migration-injected-save-failure", "Exception caught during firing event: Intentional one-shot Precision migration save failure",
             "PrecisionAssemblerMigrationSaveFailureGameTests$OneShotSaveFailure.accept", "precision_migration_port_failure"),
        )
        for identifier, message, frame, name in cases:
            def entry(method):
                return Entry(1, "ERROR", EVENT_LOGGER, message, name,
                             (f"\tat io.github.example.{method}(Fixture.java:1)",))
            with self.subTest(identifier=identifier):
                self.assertTrue(rules[identifier].describes(entry(frame)))
                self.assertFalse(rules[identifier].describes(entry(frame.rsplit(".", 1)[0] + ".unrelated")))

    def test_precision_diagnostic_roots_are_batch_specific(self) -> None:
        rules = {item.id: item for item in load_manifest(DEFAULT_MANIFEST)}
        prefix = ("Precision Assembler migration port marker roots are not confirmed on disk at "
                  "BlockPos{x=1, y=2, z=3} in minecraft:overworld: BlockPos{x=1, y=2, z=3}: ")
        wrong = Entry(1, "ERROR", MOD_LOGGER,
                      prefix + "arce_part_binding differs (expected hash=3, saved hash=0)",
                      "precision_migration_port_failure", ())
        self.assertFalse(rules["precision-port-roots-unconfirmed"].describes(wrong))
        self.assertFalse(rules["precision-energy-binding-roots-unconfirmed"].allows_batch(wrong.batch))


class WorkflowIntegrationTests(unittest.TestCase):
    def setUp(self) -> None:
        root = DEFAULT_MANIFEST.parent.parent
        self.workflow = (root / ".github/workflows/v180-development.yml").read_text(encoding="utf-8")
        self.jobs = parse_workflow_jobs(self.workflow)
        self.job_text = dict(re.findall(r"(?ms)^  ([\w-]+):\n(.*?)(?=^  [\w-]+:\n|\Z)", self.workflow))

    def step(self, job: str, name: str) -> dict[str, str]:
        matches = [step for step in self.jobs[job].steps if step.fields.get("name") == name]
        self.assertEqual(1, len(matches), f"{job}: {name}")
        return matches[0].fields

    def assert_before(self, job: str, first: dict, second: dict) -> None:
        steps = [step.fields for step in self.jobs[job].steps]
        self.assertLess(steps.index(first), steps.index(second))

    def test_hosted_pipelines_use_blocking_bash_in_every_job(self) -> None:
        self.assertEqual({"build", "gametest", "regression"}, set(self.jobs))
        for name, job in self.jobs.items():
            with self.subTest(job=name):
                self.assertTrue(job.structurally_safe)
                self.assertNotIn("continue-on-error", job.fields)
                # Explicit Bash uses GitHub's -e -o pipefail, so tee cannot mask a failure.
                self.assertIn("    defaults:\n      run:\n        shell: bash\n", self.job_text[name])
                for step in job.steps:
                    self.assertTrue(step.structurally_safe)
                    self.assertNotIn("continue-on-error", step.fields)
                    if "run" in step.fields:
                        self.assertEqual("bash", step.fields.get("shell", "bash"))
                        self.assertNotRegex(step.fields["run"], r"set\s+\+[^\n]*|\|\|\s*true")

    def test_hosted_discovery_runs_both_wiring_suites(self) -> None:
        tooling = self.step("build", "Record toolchain and check tooling")
        self.assertNotIn("if", tooling)
        commands = [shlex.split(line.split("2>&1 |", 1)[0]) for line in tooling["run"].splitlines()
                    if "-m unittest discover" in line]
        self.assertEqual([
            ["python", "-B", "-m", "unittest", "discover", "-s", "tests", "-p", "test_check_v180_ci_host.py", "-v"],
            ["python", "-B", "-m", "unittest", "discover", "-s", "tests", "-p", "*gametest*.py", "-v"],
        ], commands)

    def test_native_execution_is_mandatory_in_all_four_shards(self) -> None:
        shard = self.jobs["gametest"]
        self.assertEqual("build", shard.fields["needs"])
        self.assertNotIn("if", self.jobs["build"].fields)
        self.assertNotIn("if", shard.fields)
        self.assertRegex(self.job_text["gametest"], r"    strategy:\n      fail-fast: false\n      matrix:\n"
                                                  r"        shard: \[0, 1, 2, 3\]\n")
        native = self.step("gametest", "Run this shard in its own fresh Forge world")
        self.assertNotIn("if", native)
        command = next(line for line in native["run"].splitlines() if line.startswith("./gradlew "))
        self.assertTrue(command.startswith("./gradlew -I scripts/ci_gametest/init.gradle "
                                           "-PciShard=${{ matrix.shard }} runGameTestServer "))
        arguments = shlex.split(command.split("runGameTestServer ", 1)[1])
        self.assertNotIn("--dry-run", arguments)
        self.assertIn("--no-build-cache", arguments)
        self.assertEqual(["compileJava", "processResources", "compileAdapterTestJava",
                          "processAdapterTestResources", "compileCiGameTestJava", "processCiGameTestResources"],
                         re.findall(r"(?:^| )-x (\S+)", command))
        self.assertIn('2>&1 | tee "$EVIDENCE_DIR/gametest.log"', command)
        # Only a successful native pipeline may leave the receipt required by check-shard.
        receipt = 'printf \'0\\n\' > "$EVIDENCE_DIR/native-exit.txt"'
        self.assertIn(receipt, native["run"])
        self.assertLess(native["run"].index(command), native["run"].index(receipt))

    def test_runtime_identity_is_sealed_restored_and_verified_around_execution(self) -> None:
        for name, job in self.jobs.items():
            with self.subTest(job=name):
                self.assertEqual("${{ github.sha }}", job.env["REVIEW_COMMIT"])
                checkouts = [step.fields for step in job.steps
                             if step.fields.get("uses", "").startswith("actions/checkout@")]
                self.assertEqual(1, len(checkouts))
                self.assertEqual("${{ env.REVIEW_COMMIT }}", checkouts[0]["with.ref"])
        seal = self.step("build", "Build and seal the single GameTest runtime bundle")
        restore = self.step("gametest", "Verify JAR identity and restore only the sealed runtime bytes")
        native = self.step("gametest", "Run this shard in its own fresh Forge world")
        check = self.step("gametest", "Verify unchanged runtime and check this shard's raw logs")
        for step, command in ((seal, "seal"), (restore, "restore"), (check, "verify-runtime")):
            with self.subTest(command=command):
                self.assertIn(f'python -B scripts/ci_gametest_shards.py {command} '
                              '--bundle build/ci-gametest --commit "$REVIEW_COMMIT"', step["run"])
        self.assertNotIn("if", seal)
        self.assertNotIn("if", restore)
        self.assertEqual("always()", check["if"])
        self.assert_before("gametest", restore, native)
        self.assert_before("gametest", native, check)
        upload = self.step("build", "Upload the immutable runtime bundle for every shard")
        download = self.step("gametest", "Download the once-built runtime bundle")
        identity = "v180-gametest-runtime-${{ github.sha }}-${{ github.run_attempt }}"
        self.assertTrue(upload["uses"].startswith("actions/upload-artifact@"))
        self.assertTrue(download["uses"].startswith("actions/download-artifact@"))
        self.assertEqual(identity, upload["with.name"])
        self.assertEqual(identity, download["with.name"])
        self.assertEqual("build/ci-gametest/", upload["with.path"])
        self.assertEqual("build/ci-gametest", download["with.path"])
        self.assert_before("build", seal, upload)
        self.assert_before("gametest", download, restore)

    def test_hosted_checker_runs_after_native_command_under_pipefail(self) -> None:
        native = self.step("gametest", "Run this shard in its own fresh Forge world")
        check = self.step("gametest", "Verify unchanged runtime and check this shard's raw logs")
        self.assert_before("gametest", native, check)
        self.assertEqual("always()", check["if"])
        raw_logs = 'cp -a build/gametest/logs "$EVIDENCE_DIR/logs"'
        checker = 'python -B scripts/ci_gametest_shards.py check-shard --evidence "$EVIDENCE_DIR"'
        # check-shard passes logs/latest.log and the per-shard manifest to the unchanged checker.
        self.assertIn(raw_logs, check["run"])
        self.assertIn(checker, check["run"])
        self.assertIn("scripts/ci_gametest_shards.py verify-runtime", check["run"])
        self.assertLess(check["run"].index(raw_logs), check["run"].index(checker))
        self.assertLess(check["run"].index("scripts/ci_gametest_shards.py verify-runtime"),
                        check["run"].index(checker))
        self.assertIn(checker + ' 2>&1 | tee "$EVIDENCE_DIR/log-check.log"', check["run"])

    def test_aggregate_requires_build_and_all_four_successful_shards(self) -> None:
        aggregate = self.jobs["regression"]
        self.assertEqual("[build, gametest]", aggregate.fields["needs"])
        self.assertEqual("always()", aggregate.fields["if"])
        build = self.step("regression", "Require the build and audit job")
        self.assertNotIn("if", build)
        self.assertEqual("test '${{ needs.build.result }}' = success", build["run"])
        download = self.step("regression", "Download all shard evidence without merging filenames")
        self.assertEqual("v180-shard-*-${{ github.sha }}-${{ github.run_attempt }}", download["with.pattern"])
        self.assertEqual("false", download["with.merge-multiple"])
        self.assertEqual("${{ env.EVIDENCE_DIR }}", download["with.path"])
        union = self.step("regression", "Require every native shard, exact executed union and unchanged log budgets")
        self.assertNotIn("if", union)
        self.assertIn("for i in 0 1 2 3; do\n", union["run"])
        self.assertIn('mv "$EVIDENCE_DIR/v180-shard-$i-$REVIEW_COMMIT-${{ github.run_attempt }}" '
                      '"$EVIDENCE_DIR/shard-$i"', union["run"])
        # The CLI rejects a non-success matrix result and rechecks every shard's native receipt and raw log.
        self.assertIn('python -B scripts/ci_gametest_shards.py union --evidence "$EVIDENCE_DIR" '
                      '--bundle build/ci-gametest --commit "$REVIEW_COMMIT" '
                      "--job-result '${{ needs.gametest.result }}' 2>&1 | tee \"$EVIDENCE_DIR/union.log\"",
                      union["run"])
        self.assert_before("regression", build, download)
        self.assert_before("regression", download, union)


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


STAMP = "10Oct2026 18:21:11.206"  # every event time that line() writes
BEFORE_MIDNIGHT = "10Oct2026 23:59:58.000"
AFTER_MIDNIGHT = "11Oct2026 00:00:01.000"
LAST_REFUSAL = line("ERROR", EVENT_LOGGER, REFUSAL)
CRASHED_RUN = standard_log().split(COMPLETE[0])[0]  # a run that stopped inside its test batch


def restamp(text: str, first: str, rest: str | None = None) -> str:
    """Give the first event of a synthetic log the time `first` and every later event `rest` (default `first`)."""
    head, marker, tail = text.partition(f"[{STAMP}]")
    return head + marker.replace(STAMP, first) + tail.replace(f"[{STAMP}]", f"[{rest or first}]")


def midnight_split(text: str, before: str = BEFORE_MIDNIGHT, after: str = AFTER_MIDNIGHT) -> tuple[str, str]:
    """Split a log before its last refusal, inside the test batch, as Forge's midnight roll would."""
    at = text.rindex(LAST_REFUSAL)
    return restamp(text[:at], before), restamp(text[at:], after)


class LogFolder:
    """A temporary Forge logs folder: latest.log and the rolled <date>-<n>.log.gz parts beside it."""

    def __init__(self, test: unittest.TestCase) -> None:
        directory = tempfile.TemporaryDirectory()
        test.addCleanup(directory.cleanup)
        self.path = Path(directory.name)

    def write(self, name: str, text: str, newline: str = "\n") -> Path:
        path = self.path / name
        if name.endswith(".gz"):
            path.write_bytes(gzip.compress(text.replace("\n", newline).encode("utf-8")))
        else:
            path.write_text(text, encoding="utf-8")
        return path


class MidnightRollTests(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = ManifestFile(self, expectation(), config_expectation()).path

    def run_main(self, log: Path) -> tuple[int, str, str]:
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = main([str(log), "--manifest", str(self.manifest)])
        return code, out.getvalue().replace(str(log), "<log>"), err.getvalue()

    def assert_checked_as(self, log: Path, text: str) -> str:
        """Assert that the log is checked exactly as `text` alone in a file of its own; return the source note."""
        code, out, err = self.run_main(log)
        expected_code, expected_out, expected_err = self.run_main(LogFolder(self).write("joined.log", text))
        self.assertEqual(expected_code, code, err)
        self.assertEqual(expected_err, err)
        source, *notes = out.splitlines()
        self.assertIn("continues a run that a midnight roll split", source)
        expected_notes = expected_out.splitlines()
        if code == 0:
            self.assertIn("of the run rebuilt from", notes.pop())
            expected_notes.pop()
        self.assertEqual(expected_notes, notes)
        return source

    def assert_refused(self, folder: LogFolder, fragment: str,
                       alone: str = "no GameTest userdev launch header") -> None:
        code, out, err = self.run_main(folder.path / "latest.log")
        self.assertEqual(1, code)
        self.assertNotIn("continues a run", out)
        refusal = err.splitlines()[0]
        self.assertIn("its run cannot be rebuilt from the rolled parts beside it", refusal)
        self.assertIn(fragment, refusal)
        # latest.log is still checked alone, and fails as it did before rebuilding existed.
        self.assertIn(alone, err)

    def test_split_run_is_checked_exactly_as_the_joined_text(self) -> None:
        for refusals in (2, 1):
            with self.subTest(refusals=refusals):
                head, tail = midnight_split(standard_log(refusals=refusals))
                folder = LogFolder(self)
                folder.write("2026-10-10-1.log.gz", head, newline="\r\n")
                source = self.assert_checked_as(folder.write("latest.log", tail), head + tail)
                lines = head.count("\n")
                self.assertIn(f"2026-10-10-1.log.gz (lines 1-{lines}), latest.log (lines {lines + 1}-", source)

    def test_run_across_two_midnights_is_rebuilt_from_three_parts(self) -> None:
        text = standard_log()
        first_cut, second_cut = text.index(batch("defaultBatch")), text.rindex(LAST_REFUSAL)
        first = restamp(text[:first_cut], BEFORE_MIDNIGHT)
        middle = restamp(text[first_cut:second_cut], AFTER_MIDNIGHT, "11Oct2026 23:59:58.000")
        last = restamp(text[second_cut:], "12Oct2026 00:00:01.000")
        folder = LogFolder(self)
        folder.write("2026-10-10-1.log.gz", first)
        folder.write("2026-10-11-1.log.gz", middle)
        source = self.assert_checked_as(folder.write("latest.log", last), first + middle + last)
        self.assertIn("2026-10-10-1.log.gz (lines 1-2), 2026-10-11-1.log.gz (lines 3-6), latest.log (lines 7-", source)

    def test_previous_runs_part_beside_the_split_run_is_not_used(self) -> None:
        head, tail = midnight_split(standard_log())
        for name, previous in (("completed", standard_log()), ("crashed", CRASHED_RUN)):
            with self.subTest(previous=name):
                folder = LogFolder(self)
                # Forge names the current run's first part -1 and the previous run's -2, and writes the
                # previous one first; swap both so that neither the name nor the time can decide.
                current = folder.write("2026-10-10-2.log.gz", head)
                earlier = folder.write("2026-10-10-1.log.gz", restamp(previous, "10Oct2026 23:40:00.000"))
                os.utime(current, (1_000_000_000, 1_000_000_000))
                os.utime(earlier, (1_000_000_100, 1_000_000_100))
                source = self.assert_checked_as(folder.write("latest.log", tail), head + tail)
                self.assertIn("2026-10-10-2.log.gz (lines 1-", source)
                self.assertNotIn("2026-10-10-1.log.gz", source)

    def test_missing_first_part_is_refused(self) -> None:
        _, tail = midnight_split(standard_log())
        cases = (
            ("no rolled part", {}, "the first part of the run is missing"),
            ("only the previous run, completed just before midnight",
             {"2026-10-10-1.log.gz": restamp(standard_log(), "10Oct2026 23:59:00.000")},
             "holds a GameTest shutdown, so it belongs to an earlier run"),
            ("only the previous run, crashed long before midnight",
             {"2026-10-10-1.log.gz": restamp(CRASHED_RUN, "10Oct2026 23:00:00.000")},
             "a part is missing or 2026-10-10-1.log.gz belongs to another run"),
            ("only debug parts", {"debug-1.log.gz": restamp(CRASHED_RUN, BEFORE_MIDNIGHT)},
             "the first part of the run is missing"),
        )
        for name, parts, fragment in cases:
            with self.subTest(name):
                folder = LogFolder(self)
                for part, text in parts.items():
                    folder.write(part, text)
                folder.write("latest.log", tail)
                self.assert_refused(folder, fragment)

    def test_gap_between_parts_is_refused(self) -> None:
        cases = (
            ("a missing day", BEFORE_MIDNIGHT, "12Oct2026 00:00:01.000", "1 day, 0:00:03 before latest.log begins"),
            ("a silent half hour", "10Oct2026 23:30:00.000", AFTER_MIDNIGHT, "0:30:01 before latest.log begins"),
            ("no midnight", BEFORE_MIDNIGHT, "10Oct2026 23:59:59.000", "so no midnight roll separates them"),
        )
        for name, before, after, fragment in cases:
            with self.subTest(name):
                head, tail = midnight_split(standard_log(), before, after)
                folder = LogFolder(self)
                folder.write("2026-10-10-1.log.gz", head)
                folder.write("latest.log", tail)
                self.assert_refused(folder, fragment)

    def test_two_candidate_parts_are_refused(self) -> None:
        head, tail = midnight_split(standard_log())
        unstamped = standard_log()[:standard_log().rindex(LAST_REFUSAL)]
        cases = (
            ("an identical copy", head, "2026-10-10-1.log.gz and 2026-10-10-2.log.gz both end at"),
            ("an overlapping part", restamp(unstamped, "10Oct2026 23:59:57.000", "10Oct2026 23:59:59.000"),
             "2026-10-10-1.log.gz (2026-10-10 23:59:58.000 to 2026-10-10 23:59:58.000) overlaps the rebuilt run"),
        )
        for name, other, fragment in cases:
            with self.subTest(name):
                folder = LogFolder(self)
                folder.write("2026-10-10-1.log.gz", head)
                folder.write("2026-10-10-2.log.gz", other)
                folder.write("latest.log", tail)
                self.assert_refused(folder, fragment)

    def test_latest_log_holding_another_launch_is_refused(self) -> None:
        head, tail = midnight_split(standard_log())
        folder = LogFolder(self)
        folder.write("2026-10-10-1.log.gz", head)
        shutdown = restamp(SHUTDOWN, AFTER_MIDNIGHT)
        folder.write("latest.log", tail.replace(shutdown, restamp(LAUNCH, AFTER_MIDNIGHT) + "\n" + shutdown))
        self.assert_refused(folder, "latest.log holds a GameTest launch after its first line",
                            alone="duplicate or misplaced GameTest launch")

    def test_unsplit_logs_are_checked_alone_as_before(self) -> None:
        head, tail = midnight_split(standard_log())
        folder = LogFolder(self)
        folder.write("2026-10-10-1.log.gz", head)
        for text in (standard_log(), standard_log(refusals=1), "\n" + standard_log()):
            with self.subTest(text=text[:20]):
                alone = self.run_main(LogFolder(self).write("latest.log", text))
                self.assertEqual(alone, self.run_main(folder.write("latest.log", text)))
                self.assertEqual(alone, self.run_main(folder.write("run.log", text)))
        # Only a latest.log is rebuilt: another name is checked alone even when parts lie beside it.
        code, out, err = self.run_main(folder.write("copy.log", tail))
        self.assertEqual(1, code)
        self.assertNotIn("rebuilt", out + err)
        self.assertIn("no GameTest userdev launch header", err)

    def test_oversized_rolled_parts_are_refused(self) -> None:
        head, tail = midnight_split(standard_log())
        size = len(head.encode("utf-8"))
        folder = LogFolder(self)
        folder.write("2026-10-10-1.log.gz", head)
        folder.write("latest.log", tail)
        with mock.patch("scripts.check_gametest_log.MAX_PART_BYTES", size):
            self.assertEqual(0, self.run_main(folder.path / "latest.log")[0])
        with mock.patch("scripts.check_gametest_log.MAX_PART_BYTES", size - 1):
            self.assert_refused(folder, f"2026-10-10-1.log.gz expands to more than {size - 1:,} bytes")
        folder.write("2026-10-11-1.log.gz", "\0" * 1_000_000)  # read after the run's part, so the budget refuses it
        with mock.patch("scripts.check_gametest_log.MAX_ROLLED_BYTES", 1_000_000 + size - 1):
            self.assert_refused(folder, f"the rolled parts expand to more than {1_000_000 + size - 1:,} bytes")
        with mock.patch("scripts.check_gametest_log.MAX_ROLLED_PARTS", 1):
            self.assert_refused(folder, "the folder holds 2 rolled parts, more than 1")

    def test_unreadable_rolled_parts_are_refused(self) -> None:
        head, tail = midnight_split(standard_log())
        cases = (
            ("not gzip", b"plain text"),
            ("truncated", gzip.compress(head.encode("utf-8"))[:-12]),
            ("not UTF-8", gzip.compress(b"\xff" + head.encode("utf-8"))),
        )
        for name, data in cases:
            with self.subTest(name):
                folder = LogFolder(self)
                folder.write("2026-10-10-1.log.gz", head)
                (folder.path / "2026-10-09-1.log.gz").write_bytes(data)
                folder.write("latest.log", tail)
                self.assert_refused(folder, "cannot read 2026-10-09-1.log.gz")

    def test_part_without_a_timestamped_first_event_is_refused(self) -> None:
        head, tail = midnight_split(standard_log())
        folder = LogFolder(self)
        folder.write("2026-10-10-1.log.gz", "\tat a.continuation.Line.run(Line.java:1)\n" + head)
        folder.write("latest.log", tail)
        self.assert_refused(folder, "2026-10-10-1.log.gz does not begin with a timestamped log event")


if __name__ == "__main__":
    unittest.main()
