import hashlib
import io
import subprocess
import tempfile
import threading
import types
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import validate_bootstrap_provenance as validator


def object_id(content, object_type="blob"):
    return hashlib.sha1(f"{object_type} {len(content)}\0".encode() + content).hexdigest()


def frame(content=b"data", object_type="blob", oid=None):
    oid = oid or object_id(content, object_type)
    return f"{oid} {object_type} {len(content)}\n".encode() + content + b"\n"


class FakeInput(io.BytesIO):
    def __init__(self, process, failure=None):
        super().__init__()
        self.process = process
        self.failure = failure
        self.writes = []

    def write(self, data):
        if self.failure == "write":
            raise BrokenPipeError("injected write failure")
        self.writes.append(data)
        if self.failure == "short write":
            return len(data) - 1
        return super().write(data)

    def flush(self):
        if self.failure == "flush":
            raise OSError("injected flush failure")
        return super().flush()

    def close(self):
        if self.failure == "close":
            self.failure = None
            raise OSError("injected close failure")
        if self.process.exit_on_close:
            self.process.returncode = self.process.exit_code
        super().close()


class BlockingOutput(io.BytesIO):
    def __init__(self, data, process, block_header=False):
        super().__init__(data)
        self.process = process
        self.block_header = block_header

    def readline(self, size=-1):
        if self.block_header:
            if not self.process.killed.wait(2):
                raise OSError("test child did not receive its bounded termination")
            return b""
        return super().readline(size)

    def read(self, size=-1):
        if self.tell() == len(self.getvalue()):
            if not self.process.killed.wait(2):
                raise OSError("test EOF wait was not bounded")
            return b""
        return super().read(size)


class FakeProcess:
    def __init__(self, data, *, exit_code=0, input_failure=None, blocking=None,
                 wait_failure=False):
        self.returncode = None
        self.exit_code = exit_code
        self.exit_on_close = blocking is None
        self.killed = threading.Event()
        self.wait_failure = wait_failure
        self.stdin = FakeInput(self, input_failure)
        self.stdout = (BlockingOutput(data, self, block_header=blocking == "header")
                       if blocking else io.BytesIO(data))

    def poll(self):
        return self.returncode

    def kill(self):
        self.returncode = -9
        self.killed.set()

    def wait(self, timeout=None):
        if self.wait_failure:
            raise subprocess.TimeoutExpired("injected child", timeout)
        return self.returncode if self.returncode is not None else self.exit_code


class GitObjectSessionProtocolTests(unittest.TestCase):
    def setUp(self):
        self.root = Path("object-session-unit-root").resolve()

    def run_scope(self, process, operation):
        with patch.object(validator.subprocess, "Popen", return_value=process) as launch:
            result = validator._with_git_object_session(self.root, operation)
        return result, launch

    def read(self, errors, *, content=b"data", oid=None, object_type="blob", limit=1024):
        return validator._read_verified_git_object(
            self.root, oid or object_id(content, object_type), object_type,
            limit, "unit object", errors,
        )

    def test_multiple_and_repeated_objects_use_one_process_and_reverify_every_oid(self):
        process = FakeProcess(frame() + frame(b"other") + frame())
        snapshots = []

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            self.assertEqual(b"other", self.read(errors, content=b"other"))
            self.assertEqual(b"data", self.read(errors))
            session = validator._git_object_scope.session
            snapshots.append((session.requests, session.payload_bytes))
            return errors, {}

        with patch.object(validator, "_git_object_sha1", wraps=validator._git_object_sha1) as verify:
            (errors, _), launch = self.run_scope(process, operation)
        self.assertEqual([], errors)
        self.assertEqual([(3, 13)], snapshots)
        self.assertEqual(3, verify.call_count)
        self.assertEqual(1, launch.call_count)
        self.assertEqual([object_id(b"data").encode() + b"\n",
                          object_id(b"other").encode() + b"\n",
                          object_id(b"data").encode() + b"\n"], process.stdin.writes)
        self.assertTrue(process.stdin.closed and process.stdout.closed)
        self.assertIsNone(validator._git_object_scope.session)
        self.assertEqual(["cat-file", "--batch"], launch.call_args.args[0][-2:])
        self.assertEqual("1", launch.call_args.kwargs["env"]["GIT_NO_LAZY_FETCH"])
        self.assertEqual("1", launch.call_args.kwargs["env"]["GIT_NO_REPLACE_OBJECTS"])

    def test_malformed_frames_are_rejected_without_process_restart(self):
        oid = object_id(b"data")
        header = f"{oid} blob 4\n".encode()
        cases = {
            "empty": b"", "partial header": b"partial",
            "oversized header": b"x" * (validator.MAX_GIT_BATCH_HEADER_BYTES + 1),
            "missing": f"{oid} missing\n".encode(),
            "wrong oid": frame(oid="0" * 40),
            "wrong type": frame(object_type="tree", oid=oid),
            "invalid ascii": oid.encode() + b" blob \xff\n",
            "invalid size": f"{oid} blob nope\n".encode(),
            "negative size": f"{oid} blob -1\n".encode(),
            "oversized payload": f"{oid} blob 1025\n".encode(),
            "truncated payload": header + b"da",
            "missing terminator": header + b"data",
            "undeclared bytes": header + b"dataX\n",
            "forged content": header + b"evil\n",
        }
        for name, data in cases.items():
            with self.subTest(name=name):
                process = FakeProcess(data)

                def operation():
                    errors = []
                    self.assertIsNone(self.read(errors))
                    self.assertIsNone(self.read(errors))
                    return errors, {}

                (errors, _), launch = self.run_scope(process, operation)
                self.assertTrue(errors)
                self.assertEqual(1, launch.call_count)
                self.assertEqual(1, len(process.stdin.writes))
                self.assertTrue(process.killed.is_set())

    def test_repeated_oid_does_not_reuse_prior_success(self):
        oid = object_id(b"data")
        process = FakeProcess(frame() + frame(b"evil", oid=oid))

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            self.assertIsNone(self.read(errors))
            return errors, {}

        (errors, _), launch = self.run_scope(process, operation)
        self.assertTrue(any("identity mismatch" in error for error in errors), errors)
        self.assertEqual(1, launch.call_count)
        self.assertEqual(2, len(process.stdin.writes))

    def test_trailing_bytes_fail_the_final_public_result(self):
        process = FakeProcess(frame() + b"unrequested\n")

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            self.assertEqual([], errors)
            return errors, {"review_status": "computed-before-close"}

        (errors, details), _ = self.run_scope(process, operation)
        self.assertTrue(any("trailing bytes" in error for error in errors), errors)
        self.assertEqual("computed-before-close", details["review_status"])

    def test_nonzero_exit_fails_after_a_valid_response(self):
        process = FakeProcess(frame(), exit_code=7)

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            return errors, {}

        (errors, _), _ = self.run_scope(process, operation)
        self.assertTrue(any("exit 7" in error for error in errors), errors)

    def test_request_count_limit_is_checked_before_another_write(self):
        process = FakeProcess(frame())

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            self.assertIsNone(self.read(errors))
            return errors, {}

        with patch.object(validator, "MAX_GIT_OBJECT_SESSION_REQUESTS", 1):
            (errors, _), launch = self.run_scope(process, operation)
        self.assertTrue(any("request count limit" in error for error in errors), errors)
        self.assertEqual(1, launch.call_count)
        self.assertEqual(1, len(process.stdin.writes))

    def test_aggregate_limit_is_checked_before_reading_the_next_payload(self):
        process = FakeProcess(frame() + frame())
        positions = []

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            self.assertIsNone(self.read(errors))
            positions.append(process.stdout.tell())
            return errors, {}

        with patch.object(validator, "MAX_GIT_OBJECT_SESSION_BYTES", 7):
            (errors, _), _ = self.run_scope(process, operation)
        self.assertEqual([len(frame()) + len(frame()) - 5], positions)
        self.assertTrue(any("aggregate byte limit" in error for error in errors), errors)

    def test_invalid_oid_does_not_start_a_process(self):
        process = FakeProcess(b"")

        def operation():
            errors = []
            self.assertIsNone(self.read(errors, oid="HEAD"))
            return errors, {}

        (errors, _), launch = self.run_scope(process, operation)
        self.assertEqual(0, launch.call_count)
        self.assertTrue(any("invalid SHA-1" in error for error in errors), errors)

    def test_empty_object_is_verified_and_admitted(self):
        process = FakeProcess(frame(b""))

        def operation():
            errors = []
            self.assertEqual(b"", self.read(errors, content=b"", limit=0))
            return errors, {}

        (errors, _), _ = self.run_scope(process, operation)
        self.assertEqual([], errors)

    def test_write_and_flush_failures_poison_without_retry(self):
        for failure in ("write", "flush"):
            with self.subTest(failure=failure):
                process = FakeProcess(frame(), input_failure=failure)

                def operation():
                    errors = []
                    self.assertIsNone(self.read(errors))
                    self.assertIsNone(self.read(errors))
                    return errors, {}

                (errors, _), launch = self.run_scope(process, operation)
                self.assertTrue(any(failure + " failure" in error for error in errors), errors)
                self.assertEqual(1, launch.call_count)

    def test_short_request_write_is_rejected_before_reading_a_response(self):
        process = FakeProcess(frame(), input_failure="short write")

        def operation():
            errors = []
            self.assertIsNone(self.read(errors))
            self.assertEqual(0, process.stdout.tell())
            return errors, {}

        (errors, _), _ = self.run_scope(process, operation)
        self.assertTrue(any("not completely written" in error for error in errors), errors)

    def test_read_failure_poisoning_is_reported(self):
        process = FakeProcess(frame())

        def operation():
            errors = []
            self.assertIsNone(self.read(errors))
            self.assertIsNone(self.read(errors))
            return errors, {}

        with patch.object(process.stdout, "readline", side_effect=OSError("injected read failure")):
            (errors, _), launch = self.run_scope(process, operation)
        self.assertTrue(any("read failure" in error for error in errors), errors)
        self.assertEqual(1, launch.call_count)

    def test_closed_session_rejects_reuse_without_another_process(self):
        process = FakeProcess(frame())

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            validator._git_object_scope.session.close()
            self.assertIsNone(self.read(errors))
            return errors, {}

        (errors, _), launch = self.run_scope(process, operation)
        self.assertTrue(any("already closed" in error for error in errors), errors)
        self.assertEqual(1, launch.call_count)

    def test_start_failure_is_not_retried(self):
        def operation():
            errors = []
            self.assertIsNone(self.read(errors))
            self.assertIsNone(self.read(errors))
            return errors, {}

        with patch.object(validator.subprocess, "Popen", side_effect=OSError("start failure")) as launch:
            errors, _ = validator._with_git_object_session(self.root, operation)
        self.assertTrue(any("start failure" in error for error in errors), errors)
        self.assertEqual(1, launch.call_count)

    def test_request_timeout_poisoning_and_finalization_are_observed(self):
        process = FakeProcess(b"", blocking="header")

        def operation():
            errors = []
            self.assertIsNone(self.read(errors))
            self.assertIsNone(self.read(errors))
            return errors, {}

        with patch.object(validator, "GIT_TIMEOUT_SECONDS", 0.02):
            (errors, _), launch = self.run_scope(process, operation)
        self.assertTrue(any("read timed out" in error for error in errors), errors)
        self.assertTrue(process.killed.is_set())
        self.assertEqual(1, launch.call_count)

    def test_lifetime_expiration_without_an_object_still_fails(self):
        def operation():
            self.assertTrue(validator._git_object_scope.session.expired.wait(1))
            return [], {}

        with patch.object(validator, "GIT_OBJECT_SESSION_TIMEOUT_SECONDS", 0.02):
            (errors, _), launch = self.run_scope(FakeProcess(b""), operation)
        self.assertTrue(any("lifetime timed out" in error for error in errors), errors)
        self.assertEqual(0, launch.call_count)

    def test_final_eof_wait_is_bounded_and_errors_reach_the_result(self):
        process = FakeProcess(frame(), blocking="eof")

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            return errors, {}

        with patch.object(validator, "GIT_TIMEOUT_SECONDS", 0.02):
            (errors, _), _ = self.run_scope(process, operation)
        self.assertTrue(any("finalization timed out" in error for error in errors), errors)
        self.assertTrue(process.killed.is_set())

    def test_input_close_failure_is_not_silently_ignored(self):
        process = FakeProcess(frame(), input_failure="close")

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            return errors, {}

        (errors, _), _ = self.run_scope(process, operation)
        self.assertTrue(any("cannot close bounded Git object input" in error for error in errors), errors)

    def test_wait_failure_is_reported_and_state_is_restored(self):
        process = FakeProcess(frame(), wait_failure=True)

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            return errors, {}

        (errors, _), _ = self.run_scope(process, operation)
        self.assertTrue(any("cannot finalize" in error for error in errors), errors)
        self.assertTrue(any("cannot reap" in error for error in errors), errors)
        self.assertIsNone(validator._git_object_scope.session)

    def test_exception_restores_scope_and_closes_owned_process(self):
        process = FakeProcess(frame())

        def operation():
            self.assertEqual(b"data", self.read([]))
            raise RuntimeError("injected domain failure")

        with self.assertRaisesRegex(RuntimeError, "injected domain failure"):
            self.run_scope(process, operation)
        self.assertIsNone(validator._git_object_scope.session)
        self.assertTrue(process.stdin.closed and process.stdout.closed)

    def test_nested_scope_restores_outer_transport_without_sharing(self):
        outer, inner = FakeProcess(frame() + frame()), FakeProcess(frame())
        sessions = []

        def inner_operation():
            sessions.append(validator._git_object_scope.session)
            errors = []
            self.assertEqual(b"data", self.read(errors))
            return errors, {}

        def outer_operation():
            errors = []
            initial = validator._git_object_scope.session
            sessions.append(initial)
            self.assertEqual(b"data", self.read(errors))
            self.assertEqual([], validator._with_git_object_session(self.root, inner_operation)[0])
            self.assertIs(initial, validator._git_object_scope.session)
            self.assertEqual(b"data", self.read(errors))
            return errors, {}

        with patch.object(validator.subprocess, "Popen", side_effect=[outer, inner]) as launch:
            errors, _ = validator._with_git_object_session(self.root, outer_operation)
        self.assertEqual([], errors)
        self.assertEqual([2, 1], [session.requests for session in sessions])
        self.assertEqual(2, launch.call_count)

    def test_sequential_validations_never_inherit_a_transport(self):
        processes = [FakeProcess(frame()), FakeProcess(frame())]

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            return errors, {}

        with patch.object(validator.subprocess, "Popen", side_effect=processes) as launch:
            self.assertEqual([], validator._with_git_object_session(self.root, operation)[0])
            self.assertEqual([], validator._with_git_object_session(self.root, operation)[0])
        self.assertEqual(2, launch.call_count)

    def test_foreign_root_helper_keeps_isolated_original_eof_contract(self):
        outer, foreign = FakeProcess(frame() + frame()), FakeProcess(frame())

        def operation():
            errors = []
            self.assertEqual(b"data", self.read(errors))
            self.assertEqual(b"data", validator._read_verified_git_object(
                self.root.parent / "foreign", object_id(b"data"), "blob", 1024,
                "foreign root", errors,
            ))
            self.assertEqual(b"data", self.read(errors))
            return errors, {}

        with patch.object(validator.subprocess, "Popen", side_effect=[outer, foreign]) as launch:
            errors, _ = validator._with_git_object_session(self.root, operation)
        self.assertEqual([], errors)
        self.assertEqual(2, launch.call_count)
        self.assertEqual(2, len(outer.stdin.writes))
        self.assertEqual(1, len(foreign.stdin.writes))

    def test_thread_scopes_do_not_share_processes(self):
        barrier = threading.Barrier(2)
        processes, results, failures = [], [], []

        def launch(*args, **kwargs):
            process = FakeProcess(frame())
            processes.append(process)
            return process

        def target():
            try:
                def operation():
                    errors = []
                    self.assertEqual(b"data", self.read(errors))
                    barrier.wait(timeout=2)
                    self.assertEqual(1, validator._git_object_scope.session.requests)
                    return errors, {}
                results.append(validator._with_git_object_session(self.root, operation))
                self.assertIsNone(validator._git_object_scope.session)
            except BaseException as exc:
                failures.append(exc)

        with patch.object(validator.subprocess, "Popen", side_effect=launch):
            threads = [threading.Thread(target=target) for _ in range(2)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join(timeout=3)
            self.assertFalse(any(thread.is_alive() for thread in threads))
        self.assertEqual([], failures)
        self.assertEqual([[], []], [result[0] for result in results])
        self.assertEqual(2, len(processes))

    def test_unregistered_exact_source_module_is_independent_and_loadable(self):
        module = types.ModuleType("_object_session_bound_validator_test")
        module.__file__ = validator.__file__
        module.__package__ = ""
        content = Path(validator.__file__).read_bytes()
        exec(compile(content, "bound-commit:validator", "exec"), module.__dict__)
        self.assertEqual(module.__name__, module.validate_bootstrap_provenance_at_commit.__module__)
        self.assertIsNot(module._git_object_scope, validator._git_object_scope)
        errors, _ = module.validate_bootstrap_provenance_at_commit(self.root, "invalid")
        self.assertTrue(any("selected_commit must" in error for error in errors), errors)
        self.assertIsNone(module._git_object_scope.session)


class GitObjectSessionRealGitTests(unittest.TestCase):
    def test_real_git_multiple_types_and_repeated_objects_have_one_owned_transport(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()

            def git(*args, content=None):
                result = subprocess.run(["git", "-C", str(root), *args], input=content,
                                        check=True, capture_output=True, timeout=15)
                return result.stdout.strip().decode("ascii")

            git("init", "--quiet")
            git("config", "user.name", "Object Session Test")
            git("config", "user.email", "session-test@example.invalid")
            oid = git("hash-object", "-w", "--stdin", content=b"real content")
            tree = git("mktree", content=b"")
            commit = git("commit-tree", tree, "-m", "session fixture")

            def operation():
                errors = []
                for _ in range(2):
                    self.assertEqual(b"real content", validator._read_verified_git_object(
                        root, oid, "blob", 1024, "real repeated blob", errors))
                self.assertEqual(b"", validator._read_verified_git_object(
                    root, tree, "tree", 1024, "real empty tree", errors))
                content = validator._read_verified_git_object(
                    root, commit, "commit", 4096, "real commit", errors)
                self.assertIsNotNone(content)
                self.assertTrue(content.startswith(b"tree " + tree.encode()))
                self.assertEqual(4, validator._git_object_scope.session.requests)
                return errors, {}

            with patch.object(validator.subprocess, "Popen", wraps=subprocess.Popen) as launch:
                errors, _ = validator._with_git_object_session(root, operation)
            self.assertEqual([], errors)
            self.assertEqual(1, launch.call_count)
            self.assertIsNone(validator._git_object_scope.session)


if __name__ == "__main__":
    unittest.main()
