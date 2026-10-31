"""R208 reader diagnostics and disposable-runner failure preservation."""
from contextlib import ExitStack, nullcontext
import hashlib
import json
from pathlib import Path
import struct
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_s6_named_pad as pad
import run_v180_loader_s6 as runner
from run_dedicated_server_smoke import SmokeError


def saved_data_files(world):
    (world / "data").mkdir(exist_ok=True)
    for name in (pad.LEDGER, pad.JOURNAL, "advancedrocketrycommunity_landing_pads.dat"):
        (world / "data" / name).write_bytes(b"broken")


class NamedPadReaderTest(unittest.TestCase):
    def test_unknown_keeps_file_length_header_and_reason_without_changing_bytes(self):
        with tempfile.TemporaryDirectory() as temp:
            world = Path(temp); path = world / "entities/r.-1.0.mca"
            path.parent.mkdir(); original = b"\x00\x00\x02\x01\xff"; path.write_bytes(original)
            image = pad.inspect(world)
            self.assertEqual("UNKNOWN", image["read_status"])
            self.assertIsNone(image["native"])
            diagnostic = image["failure"]
            self.assertEqual("entities/r.-1.0.mca", diagnostic["file"])
            self.assertEqual(len(original), diagnostic["length"])
            self.assertEqual(original.hex(), diagnostic["header_hex"])
            self.assertEqual(len(original), diagnostic["header_length"])
            self.assertIn("Truncated native region header", diagnostic["reason"])
            self.assertEqual(original, path.read_bytes())

    def test_empty_region_is_known_but_every_nonempty_short_header_is_unknown(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(pad, "ledger", return_value={}), \
                patch.object(pad.flight.legacy, "journal", return_value={}), \
                patch.object(pad, "payload", return_value={}), patch.object(pad, "record") as record:
            world = Path(temp); path = world / "entities/r.0.0.mca"; path.parent.mkdir()
            saved_data_files(world)
            for size in (0, 1, 4095, 4096, 8191, 8192):
                with self.subTest(size=size):
                    original = bytes(size); path.write_bytes(original)
                    image = pad.inspect(world)
                    self.assertEqual("KNOWN" if size in (0, 8192) else "UNKNOWN", image["read_status"])
                    self.assertEqual(original, path.read_bytes())
                    if size in (0, 8192):
                        self.assertEqual([], image["native"]["rockets"])
                        self.assertEqual(hashlib.sha256(original).hexdigest(), image["native"]["files"]["entities/r.0.0.mca"])
            record.assert_not_called()

    def test_truncated_allocated_record_is_unknown_with_slot_and_full_header(self):
        with tempfile.TemporaryDirectory() as temp:
            world = Path(temp); path = world / "region/r.0.0.mca"; path.parent.mkdir()
            header = bytearray(8192); struct.pack_into(">I", header, 12, (2 << 8) | 1)
            path.write_bytes(header)
            image = pad.inspect(world)
            self.assertEqual("UNKNOWN", image["read_status"])
            self.assertEqual(3, image["failure"]["slot"])
            self.assertEqual(8192, image["failure"]["length"])
            self.assertEqual(header.hex(), image["failure"]["header_hex"])

    def test_each_saved_data_failure_identifies_its_own_input(self):
        for target, name in (("ledger", "advancedrocketrycommunity_resource_escrow.dat"),
                             ("journal", "advancedrocketrycommunity_rocket_transfers.dat"),
                             ("payload", "advancedrocketrycommunity_landing_pads.dat")):
            with self.subTest(target=target), tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
                world = Path(temp); saved_data_files(world)
                for method in ("ledger", "journal", "payload"):
                    stack.enter_context(patch.object(pad.flight.legacy if method == "journal" else pad, method,
                        **({"side_effect": ValueError("invalid payload")} if method == target else {"return_value": {}})))
                image = pad.inspect(world)
                self.assertEqual("UNKNOWN", image["read_status"])
                self.assertEqual("data/" + name, image["failure"]["file"])
                self.assertEqual(6, image["failure"]["length"])
                self.assertEqual(b"broken".hex(), image["failure"]["header_hex"])
                self.assertIn("invalid payload", image["failure"]["reason"])


class NamedPadRunnerReadFailureTest(unittest.TestCase):
    def test_campaign_does_not_spend_another_attempt_after_unknown(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); sessions = root / "sessions"; sessions.mkdir()
            args = SimpleNamespace(source_commit="sha", jar=root / "mod.jar", session_parent=sessions,
                                   evidence_dir=root / "evidence", case=list(pad.CASES))
            row = dict(status="FAILED", raw_read_failed=True, starts=[dict(restart=0)])
            with patch.object(runner.shared, "assert_pin"), patch.object(runner.shared, "digest", return_value="hash"), \
                    patch.object(runner.shared, "heavy_lock", side_effect=lambda _: nullcontext()), \
                    patch.object(runner, "run_cut", return_value=row) as run, patch.object(runner.time, "sleep"):
                result = runner.run(args)
            self.assertEqual("STOPPED_RAW_READ_FAILURE", result["status"])
            self.assertEqual(1, run.call_count)
            self.assertEqual(1, result["starts"])
            self.assertEqual(0, result["restarts"])

    def run_fixture(self, root, *, unknown=True, copy_error=False):
        template = root / "template"; (template / "libraries").mkdir(parents=True)
        sessions = root / "sessions"; sessions.mkdir()
        jar = root / "fixture.jar"; jar.write_bytes(b"jar")
        evidence = root / "evidence"
        args = SimpleNamespace(session_parent=sessions, source_server=template, jar=jar,
            java="java", source_commit="sha", startup_timeout=240)
        original = b"truncated native region"

        def launch(command, session, log):
            path = session / "world/entities/r.0.0.mca"; path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(original)
            return Mock(lines=[], process=Mock(pid=123), finish=Mock(return_value=0))

        with ExitStack() as stack:
            for method in ("assert_pin", "require_lock"):
                stack.enter_context(patch.object(runner.shared, method))
            stack.enter_context(patch.object(runner.shared, "disk_space", return_value={}))
            stack.enter_context(patch.object(runner.shared, "physical_memory", return_value=0))
            process = stack.enter_context(patch.object(runner, "CapturedProcess", side_effect=launch))
            stack.enter_context(patch.object(runner, "check_log"))
            stack.enter_context(patch.object(pad, "witness", return_value=[]))
            stack.enter_context(patch.object(pad, "check", **({"side_effect": SmokeError("UNKNOWN read")}
                if unknown else {"return_value": {"identity": {}}})))
            if not unknown:
                stack.enter_context(patch.object(pad, "inspect", return_value=dict(read_status="KNOWN", native={})))
            if copy_error:
                stack.enter_context(patch.object(zipfile.ZipFile, "write", side_effect=OSError("evidence disk failure")))
            result = runner.run_cut(args, "LF_S6_PAD_PREPARED", evidence, "owner",
                                    {"jar_sha256": runner.shared.digest(jar)})
        return result, process.call_count, evidence, original

    def test_unknown_stops_restarts_and_keeps_exact_offending_file_before_cleanup(self):
        with tempfile.TemporaryDirectory() as temp:
            row, launches, evidence, original = self.run_fixture(Path(temp))
            self.assertEqual(1, launches)
            self.assertEqual("FAILED", row["status"])
            self.assertTrue(row["raw_read_failed"])
            self.assertTrue(row["cleanup"])
            self.assertFalse(Path(row["session"]).exists())
            inputs = row["starts"][0]["failed_read_inputs"]
            with zipfile.ZipFile(evidence / inputs["file"]) as archive:
                self.assertEqual(original, archive.read("entities/r.0.0.mca"))
            self.assertEqual(row, json.loads((evidence / "result.json").read_text()))

    def test_failed_evidence_copy_keeps_owned_session_and_does_not_restart(self):
        with tempfile.TemporaryDirectory() as temp:
            row, launches, _, original = self.run_fixture(Path(temp), copy_error=True)
            self.assertEqual(1, launches)
            self.assertFalse(row["cleanup"])
            self.assertEqual("FAILED", row["status"])
            self.assertIn("evidence disk failure", row["preservation_error"])
            self.assertEqual(original, (Path(row["session"]) / "world/entities/r.0.0.mca").read_bytes())

    def test_known_reads_still_run_both_restarts(self):
        with tempfile.TemporaryDirectory() as temp:
            row, launches, _, _ = self.run_fixture(Path(temp), unknown=False)
            self.assertEqual(3, launches)
            self.assertEqual("PASS", row["status"])
            self.assertTrue(row["cleanup"])


if __name__ == "__main__":
    unittest.main()
