"""Mocked runner checks; these do not launch Minecraft or certify native migration behaviour."""
import contextlib
import copy
import io
from pathlib import Path
import shutil
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from scripts import run_v180_first_event_cuts as cuts


class FirstEventValidationRunnerTest(unittest.TestCase):
    def exercise(self, corrupt_third_refusal=False):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            native = root / "native"
            native.mkdir()
            server = root / "source"
            arguments = server / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
            arguments.parent.mkdir(parents=True)
            arguments.write_text("mock", encoding="ascii")
            jar = root / "mock.jar"
            jar.write_bytes(b"mock jar")
            fixtures = root / "fixtures"
            for case, (name, *_rest) in cuts.CASES.items():
                fixture = fixtures / case / name
                fixture.parent.mkdir(parents=True)
                fixture.write_bytes(case.encode("ascii"))
            evidence = root / "evidence"
            args = SimpleNamespace(session_dir=native / "owned", evidence_dir=evidence, source_server=server,
                                   fixtures=fixtures, jar=jar, java="mock-java", source_commit="mock-source",
                                   case=None, startup_timeout=1)
            counts = {}

            class Process:
                def __init__(self, command, session, log):
                    properties = dict(line.split("=", 1) for line in
                                      (session / "server.properties").read_text().splitlines())
                    world = session / properties["level-name"]
                    case = world.name.removeprefix("world-")
                    start = counts.get(case, 0)
                    counts[case] = start + 1
                    name, outcome, *_ = cuts.CASES[case]
                    self.code = 0 if outcome == "MIGRATE" else 1
                    if outcome == "MIGRATE":
                        if start == 0:
                            backup = world / "advancedrocketrycommunity-backups/mock-backup"
                            backup.mkdir(parents=True)
                            shutil.copy2(world / "data" / name, backup / name)
                            marker = "[ARCE-BETA-1002] Beta world data check complete: managed=1, migrated=1, backup=mock-backup"
                        else:
                            marker = "[ARCE-BETA-1001] Beta world data check complete: managed=1, migrated=0, backup=none"
                    else:
                        marker = "[ARCE-BETA-2000] Beta world data check blocked startup"
                        if corrupt_third_refusal and case == "FW10c" and start == 2:
                            (world / "data" / name).write_bytes(b"incorrect third-start overwrite")
                    self.lines = [marker]
                    log.write_text(marker, encoding="ascii")

                def wait_for(self, marker, timeout):
                    return 0

                def command(self, value):
                    pass

                def finish(self, timeout=None):
                    return self.code

                def abort(self):
                    pass

            def read_root(path):
                case = path.parent.parent.name.removeprefix("world-")
                _, _, schema, epoch, outbox = cuts.CASES[case]
                payload = {"schema_version": schema, "format_epoch": epoch,
                           outbox: {"schema_version": 1, "state": "EMPTY"}}
                if case == "FM11b":
                    payload["transfers"] = [{"phase": "COMMITTED"}]
                if case == "FW10b":
                    payload["orbital_clock"] = 71
                return copy.deepcopy(payload)

            with contextlib.ExitStack() as stack:
                stack.enter_context(patch.object(cuts, "NATIVE_TEMP", native))
                stack.enter_context(patch.object(cuts, "read_root", read_root))
                stack.enter_context(patch.object(cuts, "_server_command", return_value=["mock-java"]))
                stack.enter_context(patch.object(cuts.smoke, "CapturedProcess", Process))
                stack.enter_context(patch.object(cuts.smoke, "allocate_port", return_value=25565))
                stack.enter_context(patch.object(cuts.shutil, "disk_usage", return_value=SimpleNamespace(free=100 * 1024**3)))
                stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
                if corrupt_third_refusal:
                    with self.assertRaisesRegex(cuts.smoke.SmokeError, "FW10c: a refused root was rewritten"):
                        cuts.run(args)
                    self.assertEqual(3, counts["FW10c"])
                else:
                    report = cuts.run(args)
                    self.assertEqual("PASS", report["status"])
                    self.assertEqual(set(cuts.CASES), set(counts))
                    for row in report["cases"]:
                        self.assertEqual(3, counts[row["case"]])
                        self.assertEqual([0, 1, 2], [entry["start"] for entry in row["starts"]])
                        self.assertEqual(["initial", "restart-1", "restart-2"],
                                         [entry["role"] for entry in row["starts"]])
            self.assertFalse(args.session_dir.exists(), "runner must remove only its own server copy")
            self.assertTrue((evidence / "results.json").is_file())

    def test_all_ten_rows_run_an_initial_start_and_two_subsequent_restarts(self):
        self.exercise()

    def test_the_second_subsequent_restart_still_checks_intact_refusal(self):
        self.exercise(corrupt_third_refusal=True)


if __name__ == "__main__":
    unittest.main()
