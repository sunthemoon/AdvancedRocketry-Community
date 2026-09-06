import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BASH = shutil.which("bash")
if not BASH and os.name == "nt":
    candidate = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe"
    if candidate.is_file():
        BASH = str(candidate)


@unittest.skipUnless(BASH, "Bash is required for isolated Linux-helper tests")
class LinuxHelperTests(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.calls = self.root / "calls.txt"
        self.stub("id", 'echo "${MOCK_UID:-1000}"')
        self.stub("tmux", 'printf "%s\\n" "$*" >> "$MOCK_CALLS"\n'
                  'if [[ "$1" == has-session ]]; then exit "${MOCK_SESSION_EXIT:-1}"; fi')

    def stub(self, name: str, body: str) -> None:
        target = self.bin / name
        target.write_text("#!/usr/bin/env bash\n" + body + "\n", encoding="utf-8", newline="\n")
        target.chmod(0o755)

    def run_helper(self, name: str, *args: str, **overrides: str) -> subprocess.CompletedProcess:
        env = dict(os.environ)
        env.update(STUB_BIN=self.bin.as_posix(), MOCK_CALLS=self.calls.as_posix(),
                   ARCE_ROOT=(self.root / "host").as_posix(), ARCE_LOG_DIR=(self.root / "logs").as_posix())
        env.update(overrides)
        prefix = 'export PATH="$STUB_BIN:$PATH"; '
        if os.name == "nt":
            prefix = 'export PATH="$(cygpath -u "$STUB_BIN"):$PATH"; '
        return subprocess.run(
            [BASH, "-c", prefix + 'exec bash "$@"', "helper-test",
             (ROOT / "scripts" / name).as_posix(), *args],
            env=env, cwd=self.root, capture_output=True, text=True, timeout=20,
        )

    def test_help_is_safe_without_tools_or_host_setup(self) -> None:
        for script in ("check-debian12-dev-host.sh", "tmux-arce-layout.sh", "start-xvfb-visual-smoke.sh"):
            with self.subTest(script=script):
                result = self.run_helper(script, "--help", MOCK_UID="0")
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("Usage:", result.stdout)
        self.assertFalse(self.calls.exists())
        self.assertFalse((self.root / "logs").exists())

    def test_tmux_rejects_session_metacharacters(self) -> None:
        result = self.run_helper("tmux-arce-layout.sh", "unsafe; touch injected")
        self.assertEqual(2, result.returncode)
        self.assertFalse(self.calls.exists())
        self.assertFalse((self.root / "injected").exists())

    def test_tmux_rejects_root(self) -> None:
        result = self.run_helper("tmux-arce-layout.sh", MOCK_UID="0")
        self.assertEqual(2, result.returncode)
        self.assertFalse(self.calls.exists())

    def test_tmux_rejects_missing_directories_before_creation(self) -> None:
        result = self.run_helper("tmux-arce-layout.sh")
        self.assertEqual(2, result.returncode)
        self.assertNotIn("new-session", self.calls.read_text())

    def test_tmux_attaches_exact_existing_session(self) -> None:
        result = self.run_helper("tmux-arce-layout.sh", "arce", MOCK_SESSION_EXIT="0")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["has-session -t =arce", "attach -t =arce"], self.calls.read_text().splitlines())

    def test_tmux_creates_layout_in_selected_root(self) -> None:
        host = self.root / "host with spaces"
        for folder in ("repo", "worktrees", "evidence", "logs"):
            (host / folder).mkdir(parents=True)
        result = self.run_helper("tmux-arce-layout.sh", "test-session", ARCE_ROOT=host.as_posix())
        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls.read_text()
        self.assertIn("new-session -d -s test-session", calls)
        self.assertEqual(6, calls.count("new-window"))
        self.assertNotIn("df -h /srv/arce", calls)

    def test_visual_rejects_root_before_starting_processes(self) -> None:
        result = self.run_helper("start-xvfb-visual-smoke.sh", MOCK_UID="0")
        self.assertEqual(2, result.returncode)
        self.assertFalse((self.root / "logs").exists())

    def test_visual_rejects_out_of_range_configuration(self) -> None:
        for overrides in ({"DISPLAY_NUM": "99;echo unsafe"}, {"VNC_PORT": "65536"},
                          {"VNC_PORT": "1023"}, {"RESOLUTION": "0x0x24"}, {"LP_NUM_THREADS": "100"}):
            with self.subTest(overrides=overrides):
                result = self.run_helper("start-xvfb-visual-smoke.sh", **overrides)
                self.assertEqual(2, result.returncode)
                self.assertFalse((self.root / "logs").exists())

    def test_visual_requires_vnc_password(self) -> None:
        for command in ("Xvfb", "fluxbox", "x11vnc", "glxinfo", "xauth", "mcookie"):
            self.stub(command, 'echo invoked >> "$MOCK_CALLS"')
        result = self.run_helper("start-xvfb-visual-smoke.sh", VNC_PASSWORD_FILE="")
        self.assertEqual(2, result.returncode)
        self.assertIn("VNC_PASSWORD_FILE", result.stderr)
        self.assertFalse(self.calls.exists())

    def visual_stubs(self) -> str:
        password = self.root / "vnc.pass"
        password.write_text("test-only", encoding="utf-8")
        for command in ("Xvfb", "fluxbox"):
            self.stub(command, 'printf "%s %s\\n" "${0##*/}" "$*" >> "$MOCK_CALLS"\nexec sleep 5')
        self.stub("x11vnc", 'printf "x11vnc %s\\n" "$*" >> "$MOCK_CALLS"\nexec sleep 2')
        self.stub("xauth", 'printf "xauth %s\\n" "$*" >> "$MOCK_CALLS"')
        self.stub("mcookie", "echo 0123456789abcdef0123456789abcdef")
        self.stub("glxinfo", "echo 'OpenGL renderer string: llvmpipe (test stub)'")
        return password.as_posix()

    def test_visual_child_exit_is_failure_and_private_auth_is_removed(self) -> None:
        password = self.visual_stubs()
        result = self.run_helper("start-xvfb-visual-smoke.sh", VNC_PASSWORD_FILE=password)
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("V0 software smoke only", result.stdout)
        calls = self.calls.read_text()
        self.assertIn("-nolisten tcp -auth", calls)
        self.assertIn("x11vnc -norc -display", calls)
        self.assertIn("-localhost -forever -shared -rfbauth", calls)
        self.assertNotIn(" -ac ", calls)
        self.assertFalse(list((self.root / "logs").rglob("Xauthority")))

    def test_visual_startup_failure_does_not_claim_ready(self) -> None:
        password = self.visual_stubs()
        self.stub("glxinfo", "echo 'renderer unavailable' >&2; exit 9")
        self.stub("Xvfb", "exit 8")
        result = self.run_helper("start-xvfb-visual-smoke.sh", VNC_PASSWORD_FILE=password)
        self.assertEqual(1, result.returncode)
        self.assertNotIn("V0 software smoke only", result.stdout)
        self.assertFalse(list((self.root / "logs").rglob("Xauthority")))

    def test_host_audit_propagates_failed_checks_without_running_wrapper(self) -> None:
        for command in ("whoami", "lscpu", "free", "swapon", "df", "javac", "codex", "python3",
                        "lspci", "glxinfo", "loginctl", "ss", "journalctl", "cat", "uname"):
            self.stub(command, "exit 0")
        self.stub("java", 'echo \'openjdk version "17.0.7"\'')
        self.stub("git", "exit 0")
        self.stub("tmux", "exit 0")
        wrapper = self.root / "gradlew"
        wrapper.write_text('#!/usr/bin/env bash\necho WRONG >> "$MOCK_CALLS"\n', newline="\n")
        wrapper.chmod(0o755)
        result = self.run_helper("check-debian12-dev-host.sh")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertFalse(self.calls.exists())
        self.stub("java", "exit 7")
        result = self.run_helper("check-debian12-dev-host.sh")
        self.assertEqual(1, result.returncode)
        self.assertIn("CHECK_FAILED: exit=7", result.stdout)
        self.assertFalse(self.calls.exists())


if __name__ == "__main__":
    unittest.main()
