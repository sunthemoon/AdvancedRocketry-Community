"""Torn SavedData must fail its oracle without preventing the second native start."""
from contextlib import ExitStack
import gzip
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_dedicated_server_smoke as smoke
import run_v180_cargo_cuts as fluid
import run_v180_cargo_s4_s5_cuts as settlement
import run_v180_escrow_forced_stop as escrow
import run_v180_escrow_pre_f2 as pre_f2


class DamagedRestartTests(unittest.TestCase):
    def test_failed_compressed_observations_keep_both_starts_failed_and_clean_up(self):
        for module, case, decoder in ((fluid, "FC_LOAD_L2_REPLACED", lambda world: escrow.ledger(world / "data" / escrow.LEDGER)),
                                      (settlement, "FS_O3J_AFTER", pre_f2.journal)):
            for damage in ("truncated", "zeroed_tail", "bad_deflate"):
                with self.subTest(runner=module.__name__, damage=damage), tempfile.TemporaryDirectory() as temporary:
                    root = Path(temporary)
                    template = root / "template"
                    (template / "libraries").mkdir(parents=True)
                    jar = root / "candidate.jar"
                    jar.write_bytes(b"test candidate")
                    args = SimpleNamespace(source_commit="a" * 40, lock_owner="owner", source_server=template,
                                           session_dir=root / "server", evidence_dir=root / "evidence", jar=jar,
                                           java="not-java", case=[case], dev=False, startup_timeout=240)
                    original = gzip.compress(b"\x0a\x00\x00\x00", mtime=0)
                    content = (original[:len(original) // 2] if damage == "truncated" else
                               original[:-4] + bytes(4) if damage == "zeroed_tail" else
                               original[:10] + b"\x07" + original[11:])
                    started = []

                    def process(command, cwd, log):
                        world = Path(cwd) / ("world-" + case)
                        (world / "data").mkdir(parents=True, exist_ok=True)
                        name = escrow.LEDGER if module is fluid else pre_f2.JOURNAL
                        path = world / "data" / name
                        if not path.exists():
                            path.write_bytes(content)
                        self.assertEqual(path.read_bytes(), content)
                        started.append(Path(log).name)
                        return SimpleNamespace(wait_for=Mock(side_effect=smoke.SmokeError("startup refused")),
                                               abort=Mock(), lines=[], process=SimpleNamespace(poll=lambda: 1))

                    with ExitStack() as stack:
                        native = module.native if module is fluid else module
                        for name in ("assert_pin", "require_lock", "preflight"):
                            stack.enter_context(patch.object(native, name, return_value={}))
                        stack.enter_context(patch.object(module.evidence_tools, "install_candidate", return_value={}))
                        stack.enter_context(patch.object(module.evidence_tools, "recheck_installed"))
                        stack.enter_context(patch.object(module, "_server_command", return_value=["not-java", "a", "b"]))
                        stack.enter_context(patch.object(smoke, "CapturedProcess", side_effect=process))
                        stack.enter_context(patch.object(module.replay, "replay_starts", return_value=(1, 2)))
                        stack.enter_context(patch.object(module, "raw_snapshot", side_effect=decoder))
                        inspect = stack.enter_context(patch.object(module, "inspect"))
                        with self.assertRaises(smoke.SmokeError):
                            module.run(args)
                        inspect.assert_not_called()
                    report = json.loads((args.evidence_dir / "results.json").read_text())
                    starts = report["cases"][0]["starts"]
                    self.assertEqual([start["restart"] for start in starts], [1, 2])
                    self.assertEqual([start["status"] for start in starts], ["FAILED", "FAILED"])
                    for start in starts:
                        self.assertTrue(start.get("raw_error") or start.get("raw", {}).get("error"))
                    self.assertEqual(len(started), 2)
                    self.assertTrue(report["cleanup"])
                    self.assertFalse(args.session_dir.exists())


if __name__ == "__main__":
    unittest.main()
