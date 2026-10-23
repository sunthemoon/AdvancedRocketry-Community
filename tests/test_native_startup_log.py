import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import run_dedicated_server_smoke as smoke
from scripts import run_v090_forced_stop_recovery as forced
from scripts import run_v100_flight_forced_stop as flight
from scripts import run_v100_transaction_forced_stop as txn


VERSION = "1.20.1-1.8.0-dev"
MESSAGE = "ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED"
LOGGER = "io.gi.su.ad.AdvancedRocketryCommunity/"


def line(message=MESSAGE, logger=LOGGER, level="WARN", thread="Server thread"):
    return f"[13:00:00] [{thread}/{level}] [{logger}]: {message}\n"


class NativeStartupLogTests(unittest.TestCase):
    def scan(self, lines):
        return smoke.scan_log(lines, expected_mod_version=VERSION)

    def test_manifest_pins_one_line_with_source_and_reason(self):
        self.assertEqual(1, len(smoke.V180_STARTUP_LINES))
        expected = smoke.V180_STARTUP_LINES[0]
        self.assertEqual("WARN", expected.level)
        self.assertEqual(MESSAGE, expected.message)
        self.assertEqual(1, expected.maximum)
        self.assertEqual("6edef9dab0d3f0333f739bcfee93a21e1c5451a9", expected.introduced_by)
        self.assertIn("baseline", expected.reason)

    def test_optional_line_and_both_exact_logger_layouts(self):
        self.assertEqual([], self.scan([]))
        for logger in (LOGGER, "io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity/"):
            with self.subTest(logger=logger):
                self.assertEqual([], self.scan([line(logger=logger)]))
                # Counters reset between complete process logs, not between lines.
                self.assertEqual([], self.scan([line(logger=logger)]))

    def test_allowance_is_not_enabled_for_other_or_unspecified_builds(self):
        for version in (None, "1.20.1-1.0.0-dev", "1.20.1-1.7.0-dev", "1.20.1-1.8.1-dev"):
            with self.subTest(version=version):
                self.assertEqual([line().rstrip()], smoke.scan_log([line()], expected_mod_version=version))

    def test_count_is_shared_across_console_and_full_logger_names(self):
        repeated = line(logger="io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity/")
        self.assertEqual([repeated.rstrip(), line().rstrip()], self.scan(iter([line(), repeated, line()])))

    def test_unexpected_warn_and_near_matches_still_block(self):
        for changed in (
            line("unexpected warning"), line(MESSAGE + " extra"), line("prefix " + MESSAGE),
            line(MESSAGE.replace("SOURCE_UNCONFIRMED", "SOURCE_UNAVAILABLE")),
            line(MESSAGE.replace("first_moon_landing", "first_station_warp")),
            line(logger="other.AdvancedRocketryCommunity/"),
            line(logger="io.gi.su.ad.AdvancedRocketryCommunity/OTHER"),
            line(thread="IO worker"), line(level="ERROR"), line(level="FATAL"),
        ):
            with self.subTest(changed=changed):
                self.assertEqual([changed.rstrip()], self.scan([line(), changed]))

    def test_expected_warning_remains_visible_in_raw_audit_counts(self):
        counts = smoke.log_audit_counts([line()])
        self.assertEqual(1, counts["warning_count"])
        self.assertEqual(1, counts["project_warning_count"])
        self.assertEqual([], self.scan([line()]))

    def test_errors_and_client_linkage_are_not_exempted(self):
        for failure in (line(level="ERROR", logger="forge/"),
                        "NoClassDefFoundError: net.minecraft.client.Minecraft\n"):
            with self.subTest(failure=failure):
                self.assertEqual([failure.rstrip()], self.scan([line(), failure]))

    def test_transaction_clean_stop_and_forced_stop_use_versioned_audit(self):
        # Exercise the inherited stop, the transaction finish audit and the
        # inherited forced-stop recorder, not just the scanner in isolation.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            log = path / "process.txt"
            log.write_text("native test log", encoding="utf-8")
            for action in ("clean", "forced"):
                for lines, passes in (([line()], True), ([line(), line()], False),
                                      ([line(), line("unexpected")], False)):
                    with self.subTest(action=action, lines=lines):
                        harness = txn.TransactionHarness(java="java", server=path, port=12345,
                                                         expected_version=VERSION, startup_timeout=1)
                        process = Mock(lines=lines)
                        process._arce_name = "test"
                        process._arce_started_at = "test"
                        process._arce_log_path = log
                        process.finish.return_value = 0
                        with patch.object(txn, "read_journal", return_value={"transactions": []}), \
                                patch.object(smoke, "digest_file", return_value="a" * 64):
                            def run():
                                if action == "clean":
                                    return harness.finish_clean(process)
                                return forced._record_forced_process(process, harness, 1)
                            if passes:
                                run()
                            else:
                                with self.assertRaises(smoke.SmokeError):
                                    run()

    def test_flight_recovery_audit_keeps_unexpected_warn_blocking(self):
        flight.audit_recovery_log([line()], "ASSEMBLED", {}, VERSION)
        with self.assertRaises(smoke.SmokeError):
            flight.audit_recovery_log([line(), line()], "ASSEMBLED", {}, VERSION)


class OperatorSchemaTests(unittest.TestCase):
    def report(self, schema):
        return (f"ARCE-BETA-1101 build={VERSION} forge=47.4.10 jei=absent {schema} "
                "operational=true roots=11111 bodies=8 transactions=0 "
                "transfers=0 stations=0 missions=0 players=0/2 atmosphere_volume=4096")

    def test_current_and_historical_default_schema_two(self):
        for schema in ("root_schema=2", "default_root_schema=2 station_root_schema=3 satellite_root_schema=3"):
            with self.subTest(schema=schema):
                match = txn.OPERATOR.search(self.report(schema))
                self.assertIsNotNone(match)
                self.assertEqual((VERSION, "0"), match.groups())

    def test_renamed_field_matches_only_exact_default_schema_two(self):
        for value in ("1", "3", "20", "02", "2x", "-2", ""):
            with self.subTest(value=value):
                self.assertIsNone(txn.OPERATOR.search(self.report(
                    f"default_root_schema={value} station_root_schema=3 satellite_root_schema=3")))
                self.assertIsNone(txn.OPERATOR.search(self.report(f"root_schema={value}")))

    def test_new_fields_cannot_hide_missing_or_malformed_default_schema(self):
        for schema in ("default_root_schema=2", "other_root_schema=2", "prefix_default_root_schema=2",
                       "default_root_schema=2 station_root_schema=x satellite_root_schema=3",
                       "default_root_schema=3 station_root_schema=2 satellite_root_schema=2"):
            with self.subTest(schema=schema):
                self.assertIsNone(txn.OPERATOR.search(self.report(schema)))


if __name__ == "__main__":
    unittest.main()
