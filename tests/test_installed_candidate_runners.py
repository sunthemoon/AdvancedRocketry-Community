"""W341: no runner may start a process on an unverified session JAR."""
from contextlib import ExitStack
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import bay_integrated_evidence as evidence
import run_v180_bay_forced_stop as bay
import run_v180_bay_released_flight as released
import run_v180_bay_exit_cuts as exits
import run_v180_r042_native as r042
import run_v180_cargo_integrated as cargo
import w336_native as native

ORIGINAL, REPLACEMENT = b"candidate A", b"different candidate B"
SHA = hashlib.sha256(ORIGINAL).hexdigest()
HEAD = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=evidence.REPOSITORY, text=True).strip()


def arguments(root):
    server, parent = root / "server", root / "Temp"
    (server / "libraries").mkdir(parents=True)
    parent.mkdir()
    jar = root / "candidate.jar"
    jar.write_bytes(ORIGINAL)
    return SimpleNamespace(source_server=server, session_dir=parent / "session", evidence_dir=root / "evidence",
                           session_root=parent, session_parent=parent, jar=jar, jar_sha256=SHA, source_commit=HEAD,
                           candidate_commit=HEAD, candidate_sha256=SHA, candidate_pin=dict(jar_sha256=SHA),
                           lock_owner="W341 simulated lock", tranche=None, case=["B1_STAGED"], java="no-java",
                           startup_timeout=1, exit_timeout=1, dev=False, scratch=None)


def redirected_path(parent):
    return lambda value: parent if value == "D:/GitHub/ARCE-Task-Evidence/Temp" else Path(value)


def replace_during_libraries(jar):
    real = bay.shutil.copytree
    def copying(source, target, *args, **kwargs):
        result = real(source, target, *args, **kwargs)
        jar.write_bytes(REPLACEMENT)
        return result
    return copying


class InstalledCandidateTests(unittest.TestCase):
    def setUp(self):
        # The runners check C: and D: free space before a start; the hosted Linux runner has neither drive.
        # Report ample space so these tests reach the installed-JAR checks on every host (R414).
        usage = patch("shutil.disk_usage", return_value=SimpleNamespace(total=1 << 50, used=0, free=1 << 50))
        usage.start()
        self.addCleanup(usage.stop)

    def test_shared_helper_records_copied_bytes_and_refuses_later_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar, installed = root / "input.jar", root / "installed.jar"
            jar.write_bytes(ORIGINAL)
            pin = evidence.install_candidate(jar, installed, SHA)
            self.assertEqual(dict(installed_jar=str(installed), installed_jar_sha256=SHA), pin)
            self.assertEqual(SHA, evidence.recheck_installed(pin))
            installed.write_bytes(REPLACEMENT)
            with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                evidence.recheck_installed(pin)
            with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                evidence.recheck_candidate(dict(jar=str(jar), jar_sha256=SHA, **pin))

    def test_copy_time_replacement_is_refused_even_if_input_is_restored(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar, installed = root / "input.jar", root / "installed.jar"
            jar.write_bytes(ORIGINAL)
            real = evidence.shutil.copy2
            def copying(source, target):
                jar.write_bytes(REPLACEMENT)
                real(source, target)
                jar.write_bytes(ORIGINAL)
            with patch.object(evidence.shutil, "copy2", side_effect=copying):
                with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                    evidence.install_candidate(jar, installed, SHA)
            self.assertEqual(SHA, evidence.file_sha256(jar))

    def test_every_bay_orchestrator_refuses_w338_library_copy_race_before_process_construction(self):
        for module in (bay, released, exits, r042):
            with self.subTest(runner=module.__name__), tempfile.TemporaryDirectory() as directory:
                args = arguments(Path(directory))
                with ExitStack() as stack:
                    stack.enter_context(patch.object(module, "Path", side_effect=redirected_path(args.session_root)))
                    stack.enter_context(patch.object(bay, "assert_pin"))
                    stack.enter_context(patch.object(r042, "assert_pin"))
                    stack.enter_context(patch.object(bay.shutil, "copytree", side_effect=replace_during_libraries(args.jar)))
                    process = stack.enter_context(patch.object(bay.smoke, "CapturedProcess"))
                    if module is exits:
                        stack.enter_context(patch.object(exits, "jar_evidence", return_value={}))
                        stack.enter_context(patch.object(exits, "check_jar"))
                    with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                        module.run(args)
                    process.assert_not_called()
                self.assertFalse(args.session_dir.exists())
                self.assertEqual("FAILED", json.loads((args.evidence_dir / "results.json").read_text())["status"])

    def test_r042_probe_and_both_launch_forms_use_and_recheck_the_installed_copy(self):
        with tempfile.TemporaryDirectory() as directory:
            args = arguments(Path(directory))
            args.session_dir.mkdir(); args.evidence_dir.mkdir()
            (args.session_dir / "mods").mkdir()
            run = r042.Run(args, args.session_dir, args.evidence_dir)
            installed = args.session_dir / "mods/candidate.jar"
            run.candidate = evidence.install_candidate(args.jar, installed, SHA)
            with patch.object(r042.presence, "probe", return_value="probe observations") as probe:
                self.assertEqual("probe observations", run.probe_unknown(Path("world"), "file.dat", Path("probe.log")))
                self.assertEqual(str(installed), probe.call_args.args[1]["installed_jar"])
                args.jar.write_bytes(REPLACEMENT)
                run.probe_unknown(Path("world"), "file.dat", Path("probe.log"))
                self.assertEqual(str(installed), probe.call_args.args[1]["installed_jar"],
                                 "the source JAR must never enter the probe classpath")
                args.jar.write_bytes(ORIGINAL)
            with patch.object(bay, "require_lock"), patch.object(bay.smoke, "CapturedProcess") as process:
                run.start("U_BAY", 0, {})
                run.start_refused("U_BAY")
                self.assertEqual(2, process.call_count)
                self.assertEqual([0, 1], [row["restart"] for row in run.starts])
                self.assertTrue(all(row["installed_jar"] == str(installed) and row["installed_jar_sha256"] == SHA
                                    for row in run.starts))
                installed.write_bytes(REPLACEMENT)
                for start in (lambda: run.start("U_BAY", 2, {}), lambda: run.start_refused("U_BAY")):
                    with self.assertRaisesRegex((bay.smoke.SmokeError, evidence.CandidateError), "Installed JAR SHA-256"):
                        start()
                self.assertEqual(2, process.call_count)
            with patch.object(r042.presence, "probe") as probe:
                with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                    run.probe_unknown(Path("world"), "file.dat", Path("probe.log"))
                probe.assert_not_called()
            installed.write_bytes(ORIGINAL)
            def tamper(*args): installed.write_bytes(REPLACEMENT); return "probe observations"
            with patch.object(r042.presence, "probe", side_effect=tamper):
                with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                    run.probe_unknown(Path("world"), "file.dat", Path("probe.log"))

    def test_r042_refusal_start_rechecks_after_command_construction(self):
        with tempfile.TemporaryDirectory() as directory:
            args = arguments(Path(directory))
            args.session_dir.mkdir(); args.evidence_dir.mkdir()
            run = r042.Run(args, args.session_dir, args.evidence_dir)
            installed = args.session_dir / "installed.jar"
            run.candidate = evidence.install_candidate(args.jar, installed, SHA)
            def command(java):
                installed.write_bytes(REPLACEMENT)
                return [java, "-Xms1G", "-Xmx4G", "server"]
            with patch.object(bay, "require_lock"), patch.object(bay, "_server_command", side_effect=command), \
                    patch.object(bay.smoke, "CapturedProcess") as process:
                with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                    run.start_refused("U_BAY")
                process.assert_not_called()

    def test_r042_presence_helper_uses_installed_classpath_and_rechecks_after_argument_writes(self):
        for tamper in (False, True):
            with self.subTest(tamper=tamper), tempfile.TemporaryDirectory() as directory:
                args = arguments(Path(directory))
                args.session_dir.mkdir(); args.evidence_dir.mkdir()
                installed = args.session_dir / "installed.jar"
                pin = evidence.install_candidate(args.jar, installed, SHA)
                write = Path.write_text
                def writing(path, *values, **kwargs):
                    result = write(path, *values, **kwargs)
                    if tamper and path.name == "presence-probe.args": installed.write_bytes(REPLACEMENT)
                    return result
                with patch.object(Path, "write_text", writing), patch.object(r042.presence.subprocess, "run",
                        return_value=SimpleNamespace(returncode=0, stdout="observations")) as process:
                    if tamper:
                        with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                            r042.presence.probe("no-java", pin, args.session_dir, Path("world"), "file.dat", args.evidence_dir / "probe.log")
                        process.assert_not_called()
                    else:
                        self.assertEqual("observations", r042.presence.probe("no-java", pin, args.session_dir,
                                         Path("world"), "file.dat", args.evidence_dir / "probe.log"))
                        process.assert_called_once()
                arguments_text = (args.session_dir / "presence-probe.args").read_text()
                self.assertIn(installed.resolve().as_posix(), arguments_text)
                self.assertNotIn(args.jar.resolve().as_posix(), arguments_text)

    def test_r042_orchestration_records_the_installed_file_and_refuses_final_tampering(self):
        for tamper in (False, True):
            with self.subTest(tamper=tamper), tempfile.TemporaryDirectory() as directory:
                args = arguments(Path(directory)); args.case = ["N_BAY"]
                process = SimpleNamespace(lines=[], wait_for=lambda *args: 0, abort=lambda: None)
                def case(run, name):
                    run.start(name, 0, {})
                    if tamper: Path(run.candidate["installed_jar"]).write_bytes(REPLACEMENT)
                    return dict(case=name)
                with patch.object(r042, "Path", side_effect=redirected_path(args.session_root)), \
                        patch.object(r042, "assert_pin"), patch.object(bay, "require_lock"), \
                        patch.object(bay.smoke, "CapturedProcess", return_value=process), \
                        patch.object(bay.smoke, "allocate_port", return_value=32000), patch.dict(r042.RUNNERS, N=case):
                    if tamper:
                        with self.assertRaisesRegex(bay.smoke.SmokeError, "Installed JAR SHA-256"):
                            r042.run(args)
                    else:
                        self.assertEqual("PASS", r042.run(args)["status"])
                report = json.loads((args.evidence_dir / "results.json").read_text())
                self.assertEqual("FAILED" if tamper else "PASS", report["status"])
                self.assertEqual(SHA, report["candidate"]["installed_jar_sha256"])
                starts = json.loads((args.evidence_dir / "installed-starts.json").read_text())
                self.assertEqual(report["candidate"]["installed_jar"], starts[0]["installed_jar"])
                self.assertFalse(args.session_dir.exists())

    def test_bay_launch_and_restart_are_bound_to_the_verified_installed_file(self):
        with tempfile.TemporaryDirectory() as directory:
            args = arguments(Path(directory))
            args.session_dir.mkdir(); args.evidence_dir.mkdir()
            (args.session_dir / "mods").mkdir()
            run = bay.Session(args, args.session_dir, args.evidence_dir)
            run.candidate = evidence.install_candidate(args.jar, args.session_dir / "mods/candidate.jar", SHA)
            with patch.object(bay, "require_lock"), patch.object(bay.smoke, "CapturedProcess") as process:
                run.start("test", 0, {})
                self.assertEqual(1, process.call_count)
                self.assertEqual(SHA, run.starts[0]["installed_jar_sha256"])
                Path(run.candidate["installed_jar"]).write_bytes(REPLACEMENT)
                with self.assertRaisesRegex(bay.smoke.SmokeError, "Installed JAR SHA-256"):
                    run.start("test", 1, {})
                self.assertEqual(1, process.call_count)
                self.assertEqual(SHA, evidence.file_sha256(args.jar))

    def test_accepted_bay_orchestration_records_the_installed_file_not_only_the_input(self):
        with tempfile.TemporaryDirectory() as directory:
            args = arguments(Path(directory))
            process = SimpleNamespace(lines=[], wait_for=lambda *a: 0, abort=lambda: None)
            def case(run, name):
                run.start(name, 0, {})
                return dict(case=name, status="PASS")
            with patch.object(bay, "Path", side_effect=redirected_path(args.session_root)), \
                    patch.object(bay, "assert_pin"), patch.object(bay, "require_lock"), \
                    patch.object(bay.smoke, "CapturedProcess", return_value=process), \
                    patch.object(bay.smoke, "allocate_port", return_value=32000), \
                    patch.object(bay, "run_case", side_effect=case):
                report = bay.run(args)
            self.assertEqual("PASS", report["status"])
            self.assertEqual(SHA, report["candidate"]["installed_jar_sha256"])
            starts = json.loads((args.evidence_dir / "installed-starts.json").read_text())
            self.assertEqual(report["candidate"]["installed_jar"], starts[0]["installed_jar"])
            self.assertEqual(SHA, starts[0]["installed_jar_sha256"])

    def test_cargo_launch_boundaries_refuse_installed_tampering_after_a_valid_copy(self):
        for module in cargo.RUNNERS[:3]:
            with self.subTest(runner=module.__name__), tempfile.TemporaryDirectory() as directory:
                args = arguments(Path(directory))
                args.case = [next(iter(module.CASES))]
                def command(java):
                    (args.session_dir / "mods" / args.jar.name).write_bytes(REPLACEMENT)
                    return [java, "-Xms1G", "-Xmx4G", "server"]
                with ExitStack() as stack:
                    for owner in (cargo.fluid.native, cargo.settlement):
                        stack.enter_context(patch.object(owner, "assert_pin"))
                        stack.enter_context(patch.object(owner, "require_lock"))
                        stack.enter_context(patch.object(owner, "preflight", return_value={}))
                    stack.enter_context(patch.object(module, "_server_command", side_effect=command))
                    stack.enter_context(patch.object(bay.smoke, "allocate_port", return_value=32000))
                    process = stack.enter_context(patch.object(bay.smoke, "CapturedProcess"))
                    with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                        module.run(args)
                    process.assert_not_called()
                self.assertEqual(SHA, evidence.file_sha256(args.jar))

    def test_all_cargo_wrapper_copying_runners_refuse_the_library_copy_race(self):
        modules = (*cargo.RUNNERS, cargo.loader, cargo.loader_s6, cargo.loader_s1)
        for module in modules:
            with self.subTest(runner=module.__name__), tempfile.TemporaryDirectory() as directory:
                args = arguments(Path(directory))
                with ExitStack() as stack:
                    stack.enter_context(patch.object(cargo.fluid.native, "assert_pin"))
                    stack.enter_context(patch.object(cargo.fluid.native, "require_lock"))
                    stack.enter_context(patch.object(cargo.fluid.native, "preflight", return_value={}))
                    stack.enter_context(patch.object(cargo.settlement, "assert_pin"))
                    stack.enter_context(patch.object(cargo.settlement, "require_lock"))
                    stack.enter_context(patch.object(cargo.settlement, "preflight", return_value={}))
                    stack.enter_context(patch.object(bay.shutil, "copytree", side_effect=replace_during_libraries(args.jar)))
                    process = stack.enter_context(patch.object(bay.smoke, "CapturedProcess"))
                    if module in (cargo.loader, cargo.loader_s6):
                        cut = module.CASES[0] if module is cargo.loader_s6 else cargo.REUSE["B3-S1-REUSE"][1][0]
                        if module is cargo.loader_s6:
                            stack.enter_context(patch.object(module, "CapturedProcess", process))
                        row = module.run_cut(args, cut, args.evidence_dir, "owner", dict(jar_sha256=SHA))
                        self.assertEqual("FAILED", row["status"])
                        self.assertIn("Installed JAR SHA-256", json.dumps(row))
                    elif module is cargo.loader_s1:
                        args.old_jar = Path(directory) / "old.jar"; args.old_jar.write_bytes(b"old")
                        args.old_source_commit = HEAD
                        stack.enter_context(patch.object(module, "Path", side_effect=redirected_path(args.session_root)))
                        stack.enter_context(patch.object(module, "assert_pin"))
                        stack.enter_context(patch.object(module, "require_lock"))
                        stack.enter_context(patch.object(module, "preflight", return_value={}))
                        stack.enter_context(patch.object(module.oracle, "check_jar", side_effect=lambda jar, old:
                                                        dict(sha256=evidence.file_sha256(jar))))
                        with self.assertRaisesRegex(bay.smoke.SmokeError, "Candidate JAR changed"):
                            module.run(args)
                    else:
                        args.case = [next(iter(module.CASES))]
                        with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                            module.run(args)
                    process.assert_not_called()

    def test_w336_constructor_and_start_refuse_candidate_copy_races(self):
        for stage in ("constructor", "start"):
            with self.subTest(stage=stage), tempfile.TemporaryDirectory() as directory:
                args = arguments(Path(directory))
                with ExitStack() as stack:
                    stack.enter_context(patch.object(native, "TEMP", args.session_root))
                    stack.enter_context(patch.object(native, "verify_candidate", return_value=dict(jar_sha256=SHA)))
                    stack.enter_context(patch.object(native, "require_lock"))
                    stack.enter_context(patch.object(native.smoke, "allocate_port", return_value=32000))
                    process = stack.enter_context(patch.object(native.smoke, "CapturedProcess"))
                    if stage == "constructor":
                        stack.enter_context(patch.object(bay.shutil, "copytree", side_effect=replace_during_libraries(args.jar)))
                        with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                            native.Native(args)
                        self.assertFalse(args.session_dir.exists())
                    else:
                        run = native.Native(args)
                        real = evidence.shutil.copy2
                        def copying(source, target):
                            args.jar.write_bytes(REPLACEMENT)
                            real(source, target)
                            args.jar.write_bytes(ORIGINAL)
                        try:
                            with patch.object(evidence.shutil, "copy2", side_effect=copying):
                                with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                                    run.start("test")
                        finally:
                            run.close()
                    process.assert_not_called()

    def test_w336_historical_copy_uses_its_own_pin_and_records_the_installed_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            args = arguments(Path(directory))
            old = Path(directory) / "old.jar"; old.write_bytes(b"old")
            sha = evidence.file_sha256(old)
            with patch.object(native, "TEMP", args.session_root), patch.object(native, "require_lock"), \
                    patch.object(native, "verify_candidate", return_value=dict(jar_sha256=SHA)), \
                    patch.object(native.smoke, "allocate_port", return_value=32000), patch.object(native.smoke, "CapturedProcess") as process:
                run = native.Native(args)
                try:
                    with self.assertRaisesRegex(ValueError, "Historical JAR start requires"):
                        run.start("old", jar=old)
                    with self.assertRaisesRegex(evidence.CandidateError, "Installed JAR SHA-256"):
                        run.start("old", jar=old, jar_sha256=SHA)
                    process.assert_not_called()
                    run.start("old", jar=old, jar_sha256=sha)
                    self.assertEqual(sha, run.starts[-1]["installed_jar_sha256"])
                    self.assertEqual(sha, evidence.file_sha256(Path(run.starts[-1]["installed_jar"])))
                finally:
                    run.close()


if __name__ == "__main__":
    unittest.main()
