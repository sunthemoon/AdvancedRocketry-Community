"""Freshly verified packet payloads share transport, never validation results."""
import hashlib
import io
import os
import subprocess
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import prepare_v002_g0_review_packet as packet
from tests.packet_review_fixture import PacketReviewFixtureMixin


def oid(content):
    return hashlib.sha1(f"blob {len(content)}\0".encode() + content).hexdigest()


def frame(content=b"data", identity=None):
    return f"{identity or oid(content)} blob {len(content)}\n".encode() + content + b"\n"


class FakeInput(io.BytesIO):
    def __init__(self, owner, failure):
        super().__init__()
        self.owner, self.failure = owner, failure
        self.requests = []

    def fileno(self):
        return -100

    def write(self, data):
        self.requests.append(data)
        if self.failure == "write":
            raise BrokenPipeError("write failure")
        if self.failure == "short write":
            return len(data) - 1
        return super().write(data)

    def flush(self):
        if self.failure == "flush":
            raise OSError("flush failure")
        super().flush()

    def close(self):
        if self.failure == "input close":
            self.failure = None
            raise OSError("input close failure")
        if self.owner.blocking is None:
            self.owner.returncode = self.owner.exit_code
        super().close()


class FakeOutput(io.BytesIO):
    def __init__(self, owner, data, failure):
        super().__init__(data)
        self.owner, self.failure = owner, failure
        self.read_sizes = []

    def fileno(self):
        return -101

    def readline(self, size=-1):
        if self.failure == "header read":
            raise OSError("header failure")
        if self.owner.blocking == "header":
            self.wait_for_kill()
            return b""
        return super().readline(size)

    def wait_for_kill(self):
        if not self.owner.killed.wait(2):
            raise OSError("test timeout did not retire owned child")

    def read(self, size=-1):
        self.read_sizes.append(size)
        if self.failure == "header read" and self.tell() == 0:
            raise OSError("header failure")
        if self.owner.blocking == "header" and self.tell() == 0:
            self.wait_for_kill()
            return b""
        if self.failure == "content read":
            raise OSError("content failure")
        if self.owner.blocking == "eof" and self.tell() == len(self.getvalue()):
            self.wait_for_kill()
            return b""
        if self.owner.blocking == "dead eof" and self.tell() == len(self.getvalue()):
            time.sleep(0.1)
            return b""
        return super().read(size)

    def close(self):
        if self.failure == "output close":
            self.failure = None
            raise OSError("output close failure")
        super().close()


class FakeProcess:
    def __init__(self, data, *, failure=None, exit_code=0, blocking=None):
        self.failure, self.exit_code, self.blocking = failure, exit_code, blocking
        self.returncode = None
        self.killed = threading.Event()
        self.waits = 0
        self.stdin = FakeInput(self, failure)
        self.stdout = FakeOutput(self, data, failure)

    def poll(self):
        return self.returncode

    def kill(self):
        self.returncode = -9
        self.killed.set()

    def wait(self, timeout=None):
        self.waits += 1
        if self.failure == "wait":
            self.failure = None
            raise OSError("wait failure")
        if self.blocking == "exit" and self.returncode is None:
            if not self.killed.wait(2):
                raise OSError("test exit wait was not bounded")
        self.returncode = self.returncode if self.returncode is not None else self.exit_code
        return self.returncode


class PacketPayloadProtocolTests(unittest.TestCase):
    def setUp(self):
        self.root = Path("payload-unit-root").resolve()

    def read(self, process, identities=None, *, file_limit=1024, total_limit=4096):
        sink = {}
        with patch.object(packet.subprocess, "Popen", return_value=process) as launch, patch.object(packet.os, "set_blocking"):
            sizes = packet._read_verified_git_blobs_batch(
                self.root, identities or (oid(b"data"),),
                maximum_file_size=file_limit, maximum_aggregate_size=total_limit,
                contents=sink,
            )
        return sizes, sink, launch

    def reject(self, process, identities=None, **limits):
        sink = {}
        with patch.object(packet.subprocess, "Popen", return_value=process) as launch, patch.object(packet.os, "set_blocking"):
            with self.assertRaises(packet.PacketError):
                packet._read_verified_git_blobs_batch(
                    self.root, identities or (oid(b"data"),),
                    maximum_file_size=limits.get("file_limit", 1024),
                    maximum_aggregate_size=limits.get("total_limit", 4096),
                    contents=sink,
                )
        self.assertEqual({}, sink)
        self.assertEqual(1, launch.call_count)
        self.assertIsNotNone(process.returncode)
        self.assertGreater(process.waits, 0)
        self.assertTrue(process.stdin.closed)

    def test_multiple_empty_and_repeated_objects_are_freshly_verified(self):
        bodies = (b"data", b"", b"data")
        child = FakeProcess(b"".join(frame(body) for body in bodies))
        sizes, sink, launch = self.read(child, tuple(oid(body) for body in bodies))
        self.assertEqual({oid(b"data"): 4, oid(b""): 0}, sizes)
        self.assertEqual({oid(b"data"): b"data", oid(b""): b""}, sink)
        self.assertEqual([identity.encode() + b"\n" for identity in map(oid, bodies)], child.stdin.requests)
        self.assertEqual(1, launch.call_count)
        self.assertTrue(child.stdout.closed)
        self.assertTrue(all(0 < size <= packet.GIT_STREAM_CHUNK_BYTES for size in child.stdout.read_sizes))

    def test_protocol_rejections_do_not_publish_partial_content(self):
        identity = oid(b"data")
        responses = (
            b"", b"x" * 129 + b"\n", b"missing\n",
            b"\xff blob 4\ndata\n", f"{'0' * 40} blob 4\ndata\n".encode(),
            f"{identity} tree 4\ndata\n".encode(),
            f"{identity} blob -1\n".encode(), f"{identity} blob wrong\n".encode(),
            f"{identity} blob 4\nda".encode(), frame()[:-1] + b"x",
            frame(b"evil", identity), frame() + b"trailing",
        )
        for response in responses:
            with self.subTest(response=response):
                self.reject(FakeProcess(response))

    def test_repeated_oid_cannot_reuse_an_earlier_verified_result(self):
        child = FakeProcess(frame() + frame(b"evil", oid(b"data")))
        self.reject(child, (oid(b"data"), oid(b"data")))
        self.assertEqual(2, len(child.stdin.requests))

    def test_later_corruption_discards_earlier_content(self):
        self.reject(FakeProcess(frame() + b"missing\n"), (oid(b"data"), oid(b"other")))

    def test_size_boundaries_and_duplicate_byte_accounting(self):
        self.read(FakeProcess(frame()), file_limit=4, total_limit=4)
        self.reject(FakeProcess(frame()), file_limit=3)
        self.reject(FakeProcess(frame()), total_limit=3)
        self.reject(FakeProcess(frame() * 2), (oid(b"data"),) * 2, total_limit=7)
        self.read(FakeProcess(frame() * 2), (oid(b"data"),) * 2, total_limit=8)

    def test_io_and_nonzero_exit_fail_closed(self):
        for failure in ("write", "short write", "flush", "header read", "content read", "input close", "output close", "wait"):
            with self.subTest(failure=failure):
                self.reject(FakeProcess(frame(), failure=failure))
        self.reject(FakeProcess(frame(), exit_code=7))

    def test_admission_before_launch_and_empty_requests(self):
        with patch.object(packet.subprocess, "Popen") as launch:
            for identities, sink in ((('bad',), {}), ((oid(b"data"),) * 513, {}), ((oid(b"data"),), {"old": b"old"})):
                with self.subTest(identities=len(identities)), self.assertRaises(packet.PacketError):
                    packet._read_verified_git_blobs_batch(self.root, identities,
                        maximum_file_size=4, maximum_aggregate_size=4, contents=sink)
            self.assertEqual({}, packet._read_verified_git_blobs_batch(self.root, (),
                maximum_file_size=4, maximum_aggregate_size=4, contents={}))
            launch.assert_not_called()

    def test_owned_timeouts_cover_header_eof_and_exit(self):
        for blocking in ("header", "eof", "exit"):
            with self.subTest(blocking=blocking), patch.object(packet, "GIT_TIMEOUT_SECONDS", 0.03):
                child = FakeProcess(frame(), blocking=blocking)
                self.reject(child)
                self.assertTrue(child.killed.is_set())

    def test_per_request_and_aggregate_deadlines_are_not_enlarged(self):
        timers = []
        class Timer:
            def __init__(self, interval, callback):
                self.interval, self.cancelled = interval, False
                timers.append(self)
            def start(self):
                pass
            def cancel(self):
                self.cancelled = True
        with patch.object(packet.threading, "Timer", Timer):
            self.read(FakeProcess(frame() * 2), (oid(b"data"),) * 2)
        self.assertEqual([30, 60, 30], [timer.interval for timer in timers])
        self.assertTrue(all(timer.cancelled for timer in timers))

    def test_exited_child_delayed_eof_cannot_publish_over_deadline(self):
        class InertTimer:
            def __init__(self, *_):
                pass
            def start(self):
                pass
            def cancel(self):
                pass
        for timer in (threading.Timer, InertTimer):
            child = FakeProcess(frame(), blocking="dead eof")
            child.returncode = 0
            with self.subTest(timer=timer.__name__), patch.object(packet, "GIT_TIMEOUT_SECONDS", 0.03), patch.object(packet.threading, "Timer", timer):
                self.reject(child)

    def test_exceptional_cleanup_retires_child_before_input_close(self):
        child = FakeProcess(frame(), failure="content read")
        class CloseAfterExit(FakeInput):
            def close(self):
                if self.owner.returncode is None:
                    raise OSError("input close while owned child is still running")
                super().close()
        original_input = child.stdin
        child.stdin = CloseAfterExit(child, None)
        self.reject(child)
        self.assertTrue(child.killed.is_set())
        original_input.close()

    def test_real_pipe_held_open_after_child_exit_is_bounded_without_reader_thread(self):
        input_read, input_write = os.pipe()
        output_read, output_write = os.pipe()
        child = FakeProcess(b"")
        original_input, original_output = child.stdin, child.stdout
        child.stdin = os.fdopen(input_write, "wb", buffering=0)
        child.stdout = os.fdopen(output_read, "rb", buffering=0)
        child.returncode = 0
        sink = {}
        os.write(output_write, frame())
        started = time.monotonic()
        threads_before = set(threading.enumerate())
        try:
            with patch.object(packet.subprocess, "Popen", return_value=child), patch.object(packet, "GIT_TIMEOUT_SECONDS", 0.03):
                with self.assertRaisesRegex(packet.PacketError, "timed out"):
                    packet._read_verified_git_blobs_batch(self.root, (oid(b"data"),),
                        maximum_file_size=4, maximum_aggregate_size=4, contents=sink)
            self.assertLess(time.monotonic() - started, 0.5)
            self.assertEqual({}, sink)
            self.assertTrue(child.stdin.closed)
            self.assertTrue(child.stdout.closed)
            self.assertFalse(child.killed.is_set())
            # Timer cancellation may return before its timer thread ends, but
            # no newly created non-timer reader thread is allowed to survive.
            self.assertTrue(all(isinstance(thread, threading.Timer)
                for thread in set(threading.enumerate()) - threads_before))
        finally:
            child.stdin.close()
            child.stdout.close()
            os.close(input_read)
            os.close(output_write)
            original_input.close()
            original_output.close()

    def test_start_failure_preserves_empty_sink(self):
        sink = {}
        with patch.object(packet.subprocess, "Popen", side_effect=OSError("launch")), self.assertRaises(packet.PacketError):
            packet._read_verified_git_blobs_batch(self.root, (oid(b"data"),),
                maximum_file_size=4, maximum_aggregate_size=4, contents=sink)
        self.assertEqual({}, sink)

    def test_timer_start_failures_reap_owned_process_without_results(self):
        for failed_start in (1, 2, 3):
            starts = []
            class Timer:
                def __init__(self, interval, callback):
                    self.interval = interval
                def start(self):
                    starts.append(self)
                    if len(starts) == failed_start:
                        raise RuntimeError("timer thread start failure")
                def cancel(self):
                    pass
            with self.subTest(start=failed_start), patch.object(packet.threading, "Timer", Timer):
                self.reject(FakeProcess(frame() * 2), (oid(b"data"),) * 2)

    def test_path_modes_and_duplicates_are_rejected_before_content_launch(self):
        with patch.object(packet, "_read_verified_git_blobs_batch") as batch:
            with self.assertRaises(packet.PacketError):
                packet._git_payload_bindings(self.root, '1' * 40, ('a', 'a'), remaining_bytes=4)
            with patch.object(packet, "_verified_tree_entry", return_value=("120000", "blob", oid(b"data"))):
                with self.assertRaises(packet.PacketError):
                    packet._git_payload_bindings(self.root, '1' * 40, ('a',), remaining_bytes=4)
            batch.assert_not_called()

    def test_real_git_batch_calls_do_not_share_results(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            executable = packet._git_executable(root)
            subprocess.run([executable, "init", "--quiet", str(root)], check=True, capture_output=True, timeout=30)
            bodies = (b"data", b"")
            for body in bodies:
                result = subprocess.run([executable, "-C", str(root), "hash-object", "-w", "--stdin"], input=body, check=True, capture_output=True, timeout=30)
                self.assertEqual(oid(body), result.stdout.decode().strip())
            real_popen = subprocess.Popen
            with patch.object(packet.subprocess, "Popen", wraps=real_popen) as launch:
                for _ in range(2):
                    sink = {}
                    sizes = packet._read_verified_git_blobs_batch(root,
                        tuple(map(oid, bodies)) + (oid(b"data"),),
                        maximum_file_size=4, maximum_aggregate_size=8, contents=sink)
                    self.assertEqual({oid(body): body for body in bodies}, sink)
                    self.assertEqual({oid(body): len(body) for body in bodies}, sizes)
                self.assertEqual(2, launch.call_count)


class PacketPayloadIntegrationTests(PacketReviewFixtureMixin, unittest.TestCase):
    def test_same_commit_reference_packet_and_batch_packet_match(self):
        original_batch = packet._read_verified_git_blobs_batch
        with patch.object(packet, "_read_verified_git_blobs_batch", wraps=original_batch) as batch:
            generated, manifest = self.generate("batched")
        captures = [call for call in batch.call_args_list if call.kwargs.get("contents") is not None]
        self.assertEqual(1, len(captures))
        self.assertEqual(32, len(captures[0].args[1]))
        def reference(root, commit, paths, **kwargs):
            return {path: packet._git_blob(root, commit, path,
                root_tree_oid=kwargs["root_tree_oid"], tree_cache=kwargs["tree_cache"]) for path in paths}
        with patch.object(packet, "_git_payload_bindings", reference):
            expected, reference_manifest = self.generate("reference")
        self.assertEqual(reference_manifest, manifest)
        self.assertEqual(self.snapshot(expected), self.snapshot(generated))
        self.assertEqual([], self.verify_fast(generated))

    def test_failed_payload_finalization_blocks_publication_and_verification(self):
        original_batch = packet._read_verified_git_blobs_batch
        def fail_capture(*args, **kwargs):
            if kwargs.get("contents") is not None:
                raise packet.PacketError("injected payload finalization failure")
            return original_batch(*args, **kwargs)
        with patch.object(packet, "_read_verified_git_blobs_batch", fail_capture):
            with self.assertRaisesRegex(packet.PacketError, "finalization failure"):
                self.generate("rejected")
            self.assertFalse((self.build / "rejected").exists())
            self.assertTrue(any("finalization failure" in error for error in self.verify_fast()))


if __name__ == "__main__":
    unittest.main()
