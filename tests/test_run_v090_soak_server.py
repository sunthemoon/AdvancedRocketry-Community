import json
import tempfile
import unittest
from pathlib import Path

from scripts.run_dedicated_server_smoke import (
    MINECRAFT_PROTOCOL,
    MINECRAFT_VERSION,
    MOD_ID,
    SmokeError,
)
from scripts.run_v090_soak_server import (
    CLIENT_COUNT,
    EXPECTED_VERSION,
    MAX_RSS_GROWTH_BYTES,
    MINIMUM_DURATION_SECONDS,
    _growth_summary,
    _load_migration_summary,
    _operator_report,
    probe_clients,
    summarize_soak,
    validate_duration,
)


class FakeProcess:
    def __init__(self, report: str) -> None:
        self.lines: list[str] = []
        self.report = report

    def command(self, value: str) -> None:
        self.lines.append(self.report + "\n")

    def wait_for(self, marker, timeout: float, *, start_at: int = 0) -> int:  # type: ignore[no-untyped-def]
        del timeout
        for index, line in enumerate(self.lines[start_at:], start=start_at):
            if marker.search(line):
                return index
        raise AssertionError("marker was not emitted")


class V090SoakServerTests(unittest.TestCase):
    @staticmethod
    def valid_status() -> dict:
        return {
            "version": {"name": MINECRAFT_VERSION, "protocol": MINECRAFT_PROTOCOL},
            "forgeData": {
                "mods": [{"modId": MOD_ID, "modmarker": EXPECTED_VERSION}],
            },
        }

    def test_duration_cannot_be_shortened_below_two_hours(self) -> None:
        validate_duration(MINIMUM_DURATION_SECONDS)
        with self.assertRaisesRegex(SmokeError, "between 7,200"):
            validate_duration(MINIMUM_DURATION_SECONDS - 0.001)

    def test_four_independent_status_clients_are_identified(self) -> None:
        calls: list[tuple[str, int, float]] = []

        def query(host: str, port: int, timeout: float) -> dict:
            calls.append((host, port, timeout))
            return self.valid_status()

        results = probe_clients(25565, EXPECTED_VERSION, query)

        self.assertEqual(list(range(1, CLIENT_COUNT + 1)), [row["client_id"] for row in results])
        self.assertEqual(CLIENT_COUNT, len(calls))
        self.assertTrue(all(call == ("127.0.0.1", 25565, 5.0) for call in calls))

    def test_growth_uses_early_and_late_medians(self) -> None:
        stable = _growth_summary(
            [100.0, 110.0, 105.0, 109.0, 108.0, 111.0, 107.0, 112.0],
            absolute_limit=20.0,
            unit="test",
        )
        growing = _growth_summary(
            [100.0, 101.0, 102.0, 103.0, 150.0, 151.0, 152.0, 153.0],
            absolute_limit=20.0,
            unit="test",
        )

        self.assertFalse(stable["sustained_growth"])
        self.assertTrue(growing["sustained_growth"])

    def test_summary_enforces_all_fixed_budgets(self) -> None:
        result = summarize_soak(
            duration_seconds=MINIMUM_DURATION_SECONDS,
            ticks=[10.0] * 8,
            tps=[20.0] * 8,
            rss=[400_000_000] * 8,
            old_gen=[20.0] * 8,
            cpu_percent=[2.0] * 8,
            client_probe_count=CLIENT_COUNT * int(MINIMUM_DURATION_SECONDS // 15.0),
            save_count=23,
            refill_count=119,
            report_count=11,
            ticket_samples=[0] * 12,
        )

        self.assertTrue(result["budgets_passed"])
        self.assertEqual(0, result["maximum_ticket_count"])

        with self.assertRaisesRegex(SmokeError, "sustained-memory-growth"):
            summarize_soak(
                duration_seconds=MINIMUM_DURATION_SECONDS,
                ticks=[10.0] * 8,
                tps=[20.0] * 8,
                rss=[100_000_000] * 4
                + [100_000_000 + MAX_RSS_GROWTH_BYTES + 1] * 4,
                old_gen=[20.0] * 8,
                cpu_percent=[2.0] * 8,
                client_probe_count=CLIENT_COUNT * int(MINIMUM_DURATION_SECONDS // 15.0),
                save_count=23,
                refill_count=119,
                report_count=11,
                ticket_samples=[0] * 12,
            )

    def test_operator_report_requires_exact_maximum_counts(self) -> None:
        line = (
            "ARCE-BETA-1101 build=1.20.1-0.9.0-beta.1 forge=47.4.10 jei=absent "
            "root_schema=2 operational=true roots=11111 bodies=4 transactions=0 "
            "transfers=0 stations=10 missions=100 players=0/2 atmosphere_volume=4096 "
            "atmosphere_tick=1024 protocols=life:1,celestial:1,flight:2,visual:1 "
            "flight_frame_max=39 ticket_policy=transient_transfer_only"
        )

        report = _operator_report(
            FakeProcess(line),
            EXPECTED_VERSION,
            expected_stations=10,
            expected_missions=100,
        )

        self.assertEqual(2, report["root_schema"])
        self.assertEqual(39, report["flight_frame_max"])

    def test_migration_summary_is_bound_to_commit_and_artifact(self) -> None:
        commit = "a" * 40
        artifact = "b" * 64
        value = {
            "tested_commit": commit,
            "artifact_sha256": artifact,
            "mod_version": EXPECTED_VERSION,
            "restart_current": True,
            "operator_report_operational": True,
            "backup": {"file_count": 5},
        }
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "summary.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            loaded = _load_migration_summary(
                path,
                artifact_sha256=artifact,
                expected_version=EXPECTED_VERSION,
                tested_commit=commit,
            )
            self.assertEqual(value, loaded)
            value["tested_commit"] = "c" * 40
            path.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaisesRegex(SmokeError, "exact candidate"):
                _load_migration_summary(
                    path,
                    artifact_sha256=artifact,
                    expected_version=EXPECTED_VERSION,
                    tested_commit=commit,
                )


if __name__ == "__main__":
    unittest.main()
