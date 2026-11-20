import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

from scripts.campaign_observe import jdk


def target(role="server", exited=False):
    handle = Mock(pid=1234)
    handle.poll.return_value = 0 if exited else None
    return jdk.CampaignJvm(handle, role)


class JdkTests(unittest.TestCase):
    def test_allowlist_refuses_argument_disclosure_and_injection(self):
        for command in ("VM.command_line", "VM.system_properties", "VM.flags", "help", "", "-l", "-f",
                        "GC.heap_info\nVM.command_line", "GC.heap_dump", "GC.run", "JFR.dump", "JFR.configure"):
            with self.subTest(command=command), self.assertRaises(ValueError):
                jdk.check_jcmd_command(command)
        self.assertEqual({"GC.heap_info", "GC.class_histogram", "JFR.start", "JFR.stop"}, jdk.JCMD_ALLOWLIST)
        runner = Mock()
        with self.assertRaises(ValueError):
            jdk._execute(target(), "VM.command_line", runner=runner)
        runner.assert_not_called()

    def test_target_must_be_live_and_not_pid_zero(self):
        runner = Mock(return_value=Mock(stdout="heap"))
        with self.assertRaises(ValueError):
            jdk.heap_info(target(exited=True), runner=runner)
        bad = target()
        bad.process.pid = 0
        with self.assertRaises(ValueError):
            jdk.heap_info(bad, runner=runner)
        runner.assert_not_called()

    def test_heap_and_client_histogram_commands_are_exact(self):
        runner = Mock(return_value=Mock(stdout="heap"))
        client = target("client")
        self.assertEqual("heap", jdk.heap_info(client, runner=runner, jcmd="/jdk/bin/jcmd"))
        self.assertEqual(["/jdk/bin/jcmd", "1234", "GC.heap_info"], runner.call_args.args[0])
        jdk.class_histogram(client, runner=runner)
        self.assertEqual(["jcmd", "1234", "GC.class_histogram", "-all"], runner.call_args.args[0])
        runner.return_value.stdout = "Could not start recording"
        with self.assertRaises(ValueError):
            jdk.heap_info(client, runner=runner)

    def test_jfr_start_stop_bounds_paths_and_no_client_attachment(self):
        runner = Mock(return_value=Mock(stdout="Started recording 1"))
        with tempfile.TemporaryDirectory(prefix="campaign space ") as temp:
            root = Path(temp).resolve()
            (root / "phase5-ref").mkdir()
            with patch.object(jdk, "check_free_space") as disk:
                jdk.start_jfr(target(), root, "phase5-ref/run.jfr", runner=runner)
                command = runner.call_args.args[0]
                self.assertEqual(["jcmd", "1234", "JFR.start"], command[:3])
                for arg in ("duration=600s", "maxsize=128M", "disk=true", "dumponexit=true", "settings=profile"):
                    self.assertIn(arg, command)
                self.assertIn(f'filename="{(root / "phase5-ref/run.jfr").as_posix()}"', command)
                disk.assert_called_once()
                runner.return_value.stdout = 'Stopped recording "campaign"'
                jdk.stop_jfr(target(), root, "phase5-ref/run.jfr", runner=runner)
                self.assertIn("JFR.stop", runner.call_args.args[0])
                for kwargs in ({"duration_s": 0}, {"duration_s": 14401}, {"max_mib": 0}, {"max_mib": 257}, {"name": "x\nJFR.stop"}):
                    with self.assertRaises(ValueError):
                        jdk.start_jfr(target(), root, "phase5-ref/run.jfr", runner=runner, **kwargs)
                for relative in ("../escape.jfr", "phase5-ref/run.txt", "missing/run.jfr"):
                    with self.assertRaises(ValueError):
                        jdk.start_jfr(target(), root, relative, runner=runner)
                (root / "phase5-ref/run.jfr").write_bytes(b"already exists")
                with self.assertRaises(ValueError):
                    jdk.stop_jfr(target(), root, "phase5-ref/run.jfr", runner=runner)
            with self.assertRaises(ValueError):
                jdk.start_jfr(target("client"), root, "phase5-ref/client.jfr", runner=runner)

    def test_hand_made_jfr_json_gc_and_tick_event_averages(self):
        fixture = {"recording": {"events": [
            {"type": "jdk.GCPhasePause", "values": {"duration": "PT0.002S"}},
            {"type": "jdk.GCPhasePause", "values": {"duration": "PT0.008S"}},
            {"type": "jdk.GCPhasePauseLevel1", "values": {"duration": "PT0.008S"}},
            {"type": "minecraft.ServerTickTime", "values": {"averageTickDuration": "PT0.02S"}},
            {"type": "minecraft.ServerTickTime", "values": {"averageTickDuration": "PT0.04S"}},
        ]}}
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "hand-made.json"
            path.write_text(json.dumps(fixture), encoding="utf-8")
            summary = jdk.summarize_jfr_json(json.loads(path.read_text(encoding="utf-8")))
        self.assertEqual({"count": 2, "total_ms": 10, "max_ms": 8}, summary["gc_pauses"])
        self.assertEqual(30, summary["minecraft_tick_events"]["mean_ms"])
        self.assertEqual(40, summary["minecraft_tick_events"]["p95_ms"])
        self.assertIsNone(jdk.summarize_jfr_json({"recording": {"events": []}})["minecraft_tick_events"]["mean_ms"])
        self.assertEqual(3723000, jdk.duration_ms("PT1H2M3S"))
        with self.assertRaises(ValueError):
            jdk.duration_ms("nan")

    def test_offline_jfr_print_uses_filtered_json_and_does_not_attach(self):
        with tempfile.TemporaryDirectory() as temp:
            recording = Path(temp).resolve() / "fake.jfr"
            recording.write_bytes(b"fake recording; never read by a real JDK")
            output = Path(temp) / "filtered.json"
            def runner(argv, **kwargs):
                self.assertEqual(["jfr", "print", "--json", "--events", "jdk.GCPhasePause,minecraft.ServerTickTime",
                                  str(recording)], argv)
                json.dump({"recording": {"events": []}}, kwargs["stdout"])
            self.assertEqual(0, jdk.summarize_recording(recording, output, runner=runner)["gc_pauses"]["count"])

    def test_reference_acceptance_bash_syntax(self):
        bash = shutil.which("bash")
        if bash is None:
            candidate = Path("C:/Program Files/Git/bin/bash.exe")
            bash = str(candidate) if candidate.is_file() else None
        if bash is None:
            self.skipTest("Bash is missing")
        script = Path(__file__).resolve().parents[1] / "scripts/campaign_observe/ref_acceptance.sh"
        result = subprocess.run([bash, "-n", str(script)], capture_output=True, text=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
