"""Exercise the complete runner with process fixtures, without a JVM or machine lock."""
from contextlib import ExitStack
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w157_rejoin_startup as runner


PROJECT = "io.gi.su.ad.AdvancedRocketryCommunity/"
SERVER = "minecraft/MinecraftServer"
BUS = "ne.mi.ev.EventBus/EVENTBUS"
MIGRATION = "io.github.sunthemoon.advancedrocketrycommunity.persistence.migration."
REPAIR = ("Keep the world stopped. Use compatible software or validated whole-world repair including all Levels, "
          "entities, POI, journals, rejoins, player data and metadata; never delete the sidecar or restore isolated files.")
VANILLA_NPE = ('java.lang.NullPointerException: Cannot invoke "net.minecraft.server.level.ServerLevel.m_6857_()" '
               'because "serverlevel2" is null')


def line(message, logger=SERVER, level="INFO", thread="Server thread"):
    return f"[01:00:00] [{thread}/{level}] [{logger}]: {message}\n"


def fixture(case, cwd):
    if case == "unused":
        return [line('Done (0.1s)! For help, type "help"'), line("Saved the game"), line("Stopping server")]
    reason = "advancedrocketrycommunity_rocket_rejoins " + (
        "uses future schema 2" if case == "future" else "failed semantic validation")
    diagnostic = "ARCE-BETA-2001" if case == "future" else "ARCE-BETA-2000"
    trace = [MIGRATION + "SavedDataMigrationException: " + reason + "\n",
             "\tat " + MIGRATION + "WorldDataMigrationService.discoverSources(WorldDataMigrationService.java:132)\n"]
    if case == "malformed":
        trace += ["Caused by: java.lang.IllegalArgumentException: Rejoin capacity or sequence\n",
                  "\tat io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.require(RocketRejoinRoot.java:129)\n"]
    return ([line(f"[{diagnostic}] {runner.REFUSAL} reason={reason}. {REPAIR}", PROJECT, "ERROR")]
            + trace + [line("Exception caught during firing event: " + reason, BUS, "ERROR")]
            + trace + [line("Encountered an unexpected exception", level="ERROR")]
            + trace + [line("Preparing crash report with UUID 352b4dc9-f401-40d7-9d1e-c1de8467f3ef", "ne.mi.co.ForgeMod/", "FATAL"),
                       line(f"This crash report has been saved to: {cwd}/./crash-reports/crash-2026-10-25_01.00.00-server.txt", level="ERROR"),
                       line("Stopping server"), line("Exception stopping the server", level="ERROR"),
                       VANILLA_NPE + "\n",
                       "\tat net.minecraft.server.MinecraftServer.m_129885_(MinecraftServer.java:513) ~[server.jar] {}\n",
                       "\tat net.minecraft.server.MinecraftServer.m_7041_(MinecraftServer.java:584) ~[server.jar] {}\n"])


class W157RunnerTests(unittest.TestCase):
    def run_fixture(self, mutate=None):
        with tempfile.TemporaryDirectory(prefix="w157-runner-") as directory, ExitStack() as patches:
            base = Path(directory)
            (base / "temp").mkdir()
            (base / "source" / "libraries").mkdir(parents=True)
            (base / "owner.txt").write_text("test-token", encoding="utf-8")
            jar = base / "subject.jar"
            jar.write_bytes(b"non-executable test fixture")
            args = SimpleNamespace(source_commit="test-source", evidence_dir=base / "results",
                                   source_server=base / "source", jar=jar, java="not-executed-java")

            def mapped_path(value):
                return {"D:/GitHub/ARCE-Task-Evidence/Temp": base / "temp",
                        "D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm/owner.txt": base / "owner.txt"}.get(str(value), Path(value))

            class Process:
                def __init__(self, command, cwd, log):
                    self.lines = fixture(log.stem.split("-")[0], cwd)
                    self.process = SimpleNamespace(poll=lambda: 0)
                    self.exit_code = 0
                    if mutate:
                        mutate(log.stem, self, cwd)

                def wait_for(self, pattern, timeout):
                    # The real process has already been tested separately. Let the
                    # completed-log audit see missing markers as well as duplicates.
                    return next((i for i, value in enumerate(self.lines) if pattern.search(value)), None)

                def command(self, value):
                    pass

                def finish(self):
                    return self.exit_code

            def memory_status(pointer):
                pointer._obj.available = 8 * 1024**3
                return True

            patches.enter_context(patch.object(runner, "Path", mapped_path))
            patches.enter_context(patch.object(runner.subprocess, "check_output",
                                              side_effect=lambda command, **kw: "test-source\n" if "rev-parse" in command else ""))
            patches.enter_context(patch.object(runner.smoke, "CapturedProcess", Process))
            patches.enter_context(patch.object(runner.smoke, "allocate_port", return_value=25577))
            patches.enter_context(patch.object(runner.shutil, "disk_usage", return_value=SimpleNamespace(free=20 * 1024**3)))
            patches.enter_context(patch.object(runner.ctypes, "windll", create=True,
                                              new=SimpleNamespace(kernel32=SimpleNamespace(GlobalMemoryStatusEx=memory_status))))
            patches.enter_context(patch.dict(runner.os.environ, {"W157_LOCK_TOKEN": "test-token"}))
            result = runner.run(args)
            self.assertEqual([], list((base / "temp").iterdir()), "Disposable server was not cleaned")
            return result

    def change(self, target, operation):
        return self.run_fixture(lambda name, process, cwd: operation(process, cwd) if name == target else None)

    def test_clean_unused_and_exact_refusals_pass_all_six_starts(self):
        result = self.run_fixture()
        self.assertEqual("PASS", result["status"], result)
        self.assertEqual(6, len(result["starts"]))
        self.assertEqual([0, 0, 1, 1, 1, 1], [row["refusals"] for row in result["starts"]])
        self.assertFalse(any("escrowCase=" in item for row in result["starts"] for item in row["command"]))

    def test_unrelated_error_fatal_and_project_warning_fail_each_case(self):
        for case in ("unused", "malformed", "future"):
            for severity, logger in (("ERROR", PROJECT), ("ERROR", "unrelated/"), ("FATAL", "unrelated/"), ("WARN", PROJECT)):
                with self.subTest(case=case, severity=severity, logger=logger):
                    result = self.change(case + "-1", lambda p, _: p.lines.append(line("unrelated failure", logger, severity)))
                    self.assertEqual("FAIL", result["status"], result)

    def test_duplicate_or_missing_refusal_fails(self):
        for operation in (lambda p, _: p.lines.append(p.lines[0]), lambda p, _: p.lines.pop(0)):
            result = self.change("malformed-1", operation)
            self.assertEqual("FAIL", result["status"], result)

    def test_changed_sidecar_bytes_fail(self):
        result = self.change("future-2", lambda _, cwd: (cwd / "world-future" / "data" / runner.NAME).write_bytes(b"changed"))
        self.assertEqual("FAIL", result["status"], result)

    def test_unused_sidecar_creation_fails(self):
        def create(_, cwd):
            sidecar = cwd / "world-unused" / "data" / runner.NAME
            sidecar.parent.mkdir(parents=True)
            sidecar.write_bytes(b"unexpected")
        self.assertEqual("FAIL", self.change("unused-1", create)["status"])

    def test_readiness_before_refusal_fails(self):
        result = self.change("malformed-1", lambda p, _: p.lines.insert(0, line('Done (0.1s)! For help, type "help"')))
        self.assertEqual("FAIL", result["status"], result)

    def test_expected_entries_require_exact_count_logger_level_thread_and_message(self):
        for index in (0, 3, 6, 9, 10, 12):
            # Future fixtures have no nested cause: these are its six diagnostic headers.
            for mutation in (lambda value: None, lambda value: value.replace("[Server thread/", "[IO worker/"),
                             lambda value: value.replace("/ERROR]", "/WARN]").replace("/FATAL]", "/WARN]"),
                             lambda value: value.replace("]: ", "]: prefix "),
                             lambda value: value.replace("[minecraft/", "[other/").replace("[ne.mi.", "[other.")
                             .replace("[io.gi.", "[other.")):
                with self.subTest(index=index, mutation=mutation):
                    def change(p, _, index=index, mutation=mutation):
                        value = mutation(p.lines[index])
                        p.lines[index:index + 1] = [] if value is None else [value]
                    self.assertEqual("FAIL", self.change("future-1", change)["status"])

    def test_duplicate_propagation_fails(self):
        for index in (0, 3, 6, 9, 10, 12):
            with self.subTest(index=index):
                result = self.change("future-1", lambda p, _: p.lines.append(p.lines[index]))
                self.assertEqual("FAIL", result["status"], result)

    def test_crash_uuid_and_report_path_are_constrained(self):
        for index, before, after in ((9, "352b4dc9-f401-40d7-9d1e-c1de8467f3ef", "unrelated-crash"),
                                      (10, "/./crash-reports/", "/../crash-reports/"),
                                      (10, "crash-2026-10-25_01.00.00-server.txt", "unrelated.txt")):
            with self.subTest(index=index, after=after):
                def change(p, _):
                    p.lines[index] = p.lines[index].replace(before, after)
                self.assertEqual("FAIL", self.change("future-1", change)["status"])

    def test_full_logger_names_and_windows_report_path(self):
        def change(p, _):
            p.lines = [s.replace(PROJECT, "io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity/")
                       .replace(BUS, "net.minecraftforge.eventbus.EventBus/EVENTBUS")
                       .replace("ne.mi.co.ForgeMod/", "net.minecraftforge.common.ForgeMod/") for s in p.lines]
            prefix, path = p.lines[10].split("saved to: ")
            p.lines[10] = prefix + "saved to: " + path.replace("/", "\\")
        self.assertEqual("PASS", self.change("future-1", change)["status"])

    def test_refused_start_does_not_borrow_unused_start_warning_allowance(self):
        warning = line("ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED", PROJECT, "WARN")
        self.assertEqual("FAIL", self.change("future-1", lambda p, _: p.lines.append(warning))["status"])

    def test_wrong_case_or_exception_under_expected_header_fails(self):
        for before, after in (("uses future schema 2", "failed semantic validation"),
                              (MIGRATION + "SavedDataMigrationException", "java.lang.NullPointerException"),
                              (VANILLA_NPE, 'java.lang.NullPointerException: unrelated'),
                              ("MinecraftServer.m_129885_(MinecraftServer.java:513)", "MinecraftServer.unrelated(MinecraftServer.java:123)")):
            with self.subTest(before=before):
                result = self.change("future-1", lambda p, _: setattr(p, "lines", [s.replace(before, after) for s in p.lines]))
                self.assertEqual("FAIL", result["status"], result)

    def test_project_shutdown_npe_is_never_expected(self):
        def add(p, _):
            p.lines += [line("Uncaught exception in server thread", level="ERROR"),
                        'java.lang.NullPointerException: Cannot invoke "ServerLevel.getServer()" because "level" is null\n',
                        "\tat io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchRuntime.service(ResearchRuntime.java:39)\n"]
        self.assertEqual("FAIL", self.change("malformed-1", add)["status"])

    def test_unused_start_uses_versioned_strict_scanner(self):
        warning = line("ARCE_FIRST_EVENT_HELD kind=first_moon_landing reason=SOURCE_UNCONFIRMED", PROJECT, "WARN")
        self.assertEqual("PASS", self.change("unused-1", lambda p, _: p.lines.append(warning))["status"])
        self.assertEqual("FAIL", self.change("unused-1", lambda p, _: p.lines.extend([warning, warning]))["status"])


if __name__ == "__main__":
    unittest.main()
