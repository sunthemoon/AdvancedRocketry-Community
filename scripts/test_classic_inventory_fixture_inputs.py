"""Own-D platform and controlled-negative checks for quiescent bytes input."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import traceback
from types import SimpleNamespace
import unittest
from unittest import mock
import uuid

import classic_inventory_fixture_inputs as inputs


class _ObservedStream:
    """Test-only adapter around a real owned descriptor, not a runtime seam."""

    def __init__(self, stream, transform=None):
        self.stream = stream
        self.transform = transform
        self.requests = []

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.stream.close()

    def fileno(self):
        return self.stream.fileno()

    def read(self, size):
        self.requests.append(size)
        value = self.stream.read(size)
        return self.transform(value) if self.transform else value


class QuiescentPropertiesInputTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        value = os.environ.get("ARCE_C18C_INPUT_TEST_ROOT")
        if not value:
            raise RuntimeError("OWN_D_TEST_ROOT_REQUIRED")
        cls.fixture_parent = Path(value)
        prefix = Path("D:/GitHub/ARCE-Task-Evidence/v1.8.0")
        if (not cls.fixture_parent.is_absolute()
                or not cls.fixture_parent.is_relative_to(prefix)
                or ".." in cls.fixture_parent.parts):
            raise RuntimeError("OWN_D_TEST_ROOT_REQUIRED")
        cls.fixture_parent.mkdir(parents=True, exist_ok=False)
        cls.platform_observations = []

    @classmethod
    def tearDownClass(cls):
        # Literal checked fixture cleanup is a separate task-runner operation.
        report = {
            "fixture_root": cls.fixture_parent.as_posix(),
            "python": sys.version, "platform": os.name,
            "observations": cls.platform_observations,
            "cleanup": "not performed by tests; native literal runner cleanup required",
        }
        target = cls.fixture_parent.parent / (cls.fixture_parent.name + "-platform.json")
        target.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    def setUp(self):
        self.root = self.fixture_parent / ("case-" + uuid.uuid4().hex)
        self.root.mkdir()
        self.active = self.root / "server.properties"
        self.configured = self.root / ".classic-inventory-fixture" / "snapshot-server-properties-v1.txt"
        self.configured.parent.mkdir()
        self.active.write_bytes(b"active=opaque\n")
        self.configured.write_bytes(b"configured=opaque\n")

    def read(self, role="active", point="seed_preboot"):
        return inputs.read_quiescent_properties(self.root, role, point)

    def refuse(self, operation, code=None):
        with self.assertRaises(inputs.PropertiesFileInputError) as caught:
            operation()
        if code is not None:
            self.assertEqual(code, caught.exception.code)
        self.assertIn(str(caught.exception), inputs._CODES)
        self.assertTrue(caught.exception.__suppress_context__)
        return caught.exception

    def spy_open(self, transform=None, before_open=None):
        original = Path.open
        streams, calls = [], []
        def opened(path, mode="r", buffering=-1, **kwargs):
            calls.append((path, mode, buffering, kwargs))
            if before_open:
                before_open(path)
            stream = _ObservedStream(original(path, mode, buffering=buffering, **kwargs), transform)
            streams.append(stream)
            return stream
        return mock.patch.object(Path, "open", opened), streams, calls

    def changed_status(self, source, **changes):
        keys = ("st_dev", "st_ino", "st_mode", "st_size", "st_mtime_ns", "st_birthtime_ns",
                "st_nlink", "st_file_attributes", "st_reparse_tag")
        values = {key: getattr(source, key) for key in keys}
        values.update(changes)
        return SimpleNamespace(**values)

    def link(self, target, link, *, directory=False, junction=False):
        try:
            if junction:
                import _winapi
                if not hasattr(_winapi, "CreateJunction"):
                    self.skipTest("ACTUAL_JUNCTION_API_UNAVAILABLE")
                _winapi.CreateJunction(str(target), str(link))
            else:
                os.symlink(target, link, target_is_directory=directory)
        except OSError as error:
            self.skipTest("ACTUAL_REPARSE_CREATE_UNAVAILABLE:" + str(error.winerror))
        status = link.lstat()
        self.assertTrue(status.st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT)
        self.platform_observations.append({"kind": "actual-junction" if junction else "actual-symlink",
            "relative": link.relative_to(self.fixture_parent).as_posix(),
            "attributes": status.st_file_attributes, "reparse_tag": status.st_reparse_tag})

    def test_all_exact_roles_and_points_use_only_fixed_files(self):
        for role, expected in (("configured", b"configured=opaque\n"), ("active", b"active=opaque\n")):
            for point in ("seed_preboot", "reload_preboot", "stopped"):
                with self.subTest(role=role, point=point):
                    self.assertEqual(expected, self.read(role, point))

    def test_raw_bytes_are_owned_immutable_and_not_decoded(self):
        raw = b"\xff\x00\runsupported properties"
        self.active.write_bytes(raw)
        observed = self.read()
        self.assertIs(type(observed), bytes)
        self.assertEqual(raw, observed)
        with self.assertRaises(TypeError):
            observed[0] = 0
        self.assertEqual(hashlib.sha256(raw).digest(), hashlib.sha256(observed).digest())

    def test_exact_byte_limit_single_unbuffered_read(self):
        raw = b"x" * inputs.MAX_PROPERTIES_BYTES
        self.active.write_bytes(raw)
        patch, streams, calls = self.spy_open()
        with patch:
            self.assertEqual(raw, self.read())
        self.assertEqual([(self.active, "rb", 0, {})], calls)
        self.assertEqual([16_385], streams[0].requests)
        self.assertTrue(streams[0].stream.closed)
        self.platform_observations.append({"kind": "actual-exact-byte-read", "requested": 16_385,
                                           "returned": len(raw), "descriptor_closed": True})

    def test_overflow_byte_is_refused_after_one_bounded_read(self):
        self.active.write_bytes(b"x" * 16_385)
        patch, streams, calls = self.spy_open()
        with patch:
            self.refuse(self.read, "BYTES")
        self.assertEqual(1, len(calls))
        self.assertEqual([16_385], streams[0].requests)
        self.assertTrue(streams[0].stream.closed)

    def test_much_larger_file_still_has_one_bounded_request(self):
        self.active.write_bytes(b"x" * 65_536)
        patch, streams, calls = self.spy_open()
        with patch:
            self.refuse(self.read, "BYTES")
        self.assertEqual(1, len(calls))
        self.assertEqual([16_385], streams[0].requests)

    def test_empty_file_is_refused_and_closed(self):
        self.active.write_bytes(b"")
        patch, streams, _ = self.spy_open()
        with patch:
            self.refuse(self.read, "BYTES")
        self.assertTrue(streams[0].stream.closed)

    def test_invalid_exact_selectors_refuse_before_filesystem(self):
        class Text(str):
            pass
        with mock.patch.object(Path, "lstat", side_effect=AssertionError("UNEXPECTED_IO")):
            for role in (None, True, {}, "active-extra", Text("active")):
                with self.subTest(role=repr(role)):
                    self.refuse(lambda: inputs.read_quiescent_properties(self.root, role, "stopped"), "SELECTOR")
            for point in (None, 1, [], "live", Text("stopped")):
                with self.subTest(point=repr(point)):
                    self.refuse(lambda: inputs.read_quiescent_properties(self.root, "active", point), "SELECTOR")

    def test_invalid_root_shapes_refuse_before_filesystem(self):
        roots = (str(self.root), None, Path("relative"), Path("D:relative"), Path("C:/owned"),
                 Path("//server/share/owned"), Path("//?/D:/owned"), Path("D:/parent/../owned"),
                 Path("D:/owned:stream"), Path("D:/bad\x00name"), Path("D:/" + "x" * 1_024),
                 Path("D:/" + "\u00e9" * 600), Path("D:/\ud800"))
        with mock.patch.object(Path, "lstat", side_effect=AssertionError("UNEXPECTED_IO")):
            for root in roots:
                with self.subTest(root=repr(root)):
                    self.refuse(lambda: inputs.read_quiescent_properties(root, "active", "stopped"), "ROOT")

    def test_path_subclass_cannot_supply_callbacks(self):
        class PathWithCallback(type(self.root)):
            def lstat(self):
                raise AssertionError("CALLBACK_MUST_NOT_RUN")
        self.refuse(lambda: inputs.read_quiescent_properties(PathWithCallback(self.root), "active", "stopped"), "ROOT")

    def test_unsupported_platform_refuses_without_filesystem(self):
        with mock.patch.object(inputs.os, "name", "posix"), mock.patch.object(Path, "lstat", side_effect=AssertionError):
            self.refuse(self.read, "PLATFORM")

    def test_missing_required_files_are_not_absence_authority(self):
        missing = self.root / "missing-owned-root"
        missing.mkdir()
        self.refuse(lambda: inputs.read_quiescent_properties(missing, "active", "seed_preboot"), "IO")
        self.refuse(lambda: inputs.read_quiescent_properties(missing, "configured", "seed_preboot"), "IO")

    def test_file_cannot_be_root_or_intermediate_directory(self):
        self.refuse(lambda: inputs.read_quiescent_properties(self.active, "active", "stopped"), "FILE")
        root = self.root / "intermediate-case"
        root.mkdir()
        (root / ".classic-inventory-fixture").write_bytes(b"not-a-directory")
        self.refuse(lambda: inputs.read_quiescent_properties(root, "configured", "stopped"), "FILE")

    def test_leaf_directory_is_not_regular(self):
        root = self.root / "directory-leaf-case"
        root.mkdir()
        (root / "server.properties").mkdir()
        self.refuse(lambda: inputs.read_quiescent_properties(root, "active", "stopped"), "FILE")

    def test_actual_leaf_symlink_refuses_before_resolution(self):
        root = self.root / "leaf-link-case"
        root.mkdir()
        self.link(self.active, root / "server.properties")
        with mock.patch.object(Path, "resolve", side_effect=AssertionError("RESOLUTION_BEFORE_CHECK")):
            self.refuse(lambda: inputs.read_quiescent_properties(root, "active", "stopped"), "PATH")

    def test_actual_dangling_leaf_symlink_refuses(self):
        root = self.root / "dangling-link-case"
        root.mkdir()
        self.link(root / "absent-target", root / "server.properties")
        self.refuse(lambda: inputs.read_quiescent_properties(root, "active", "stopped"), "PATH")

    def test_actual_root_symlink_refuses_before_resolution(self):
        linked = self.root / "linked-root"
        self.link(self.root, linked, directory=True)
        with mock.patch.object(Path, "resolve", side_effect=AssertionError("RESOLUTION_BEFORE_CHECK")):
            self.refuse(lambda: inputs.read_quiescent_properties(linked, "active", "stopped"), "PATH")

    def test_actual_intermediate_junction_refuses_before_resolution(self):
        root = self.root / "junction-case"
        root.mkdir()
        self.link(self.configured.parent, root / ".classic-inventory-fixture", directory=True, junction=True)
        with mock.patch.object(Path, "resolve", side_effect=AssertionError("RESOLUTION_BEFORE_CHECK")):
            self.refuse(lambda: inputs.read_quiescent_properties(root, "configured", "stopped"), "PATH")

    def test_actual_ancestor_junction_refuses(self):
        target = self.root / "junction-target"
        target.mkdir()
        nested = target / "owned"
        nested.mkdir()
        (nested / "server.properties").write_bytes(b"owned-data")
        linked = self.root / "ancestor-junction"
        self.link(target, linked, directory=True, junction=True)
        self.refuse(lambda: inputs.read_quiescent_properties(linked / "owned", "active", "stopped"), "PATH")

    def test_actual_descriptor_observations_are_meaningful(self):
        with self.active.open("rb", buffering=0) as stream:
            opened = os.fstat(stream.fileno())
            literal = self.active.lstat()
            self.assertEqual(inputs._identity(literal), inputs._identity(opened))
            self.assertIs(type(opened.st_file_attributes), int)
            self.assertGreater(opened.st_ino, 0)
            self.assertNotEqual(0, opened.st_dev)
            self.platform_observations.append({"kind": "actual-descriptor-fields", "dev": opened.st_dev,
                "ino": opened.st_ino, "attributes": opened.st_file_attributes, "tag": opened.st_reparse_tag,
                "mode": opened.st_mode, "size": opened.st_size, "nlink": opened.st_nlink,
                "mtime_ns": opened.st_mtime_ns, "birthtime_ns": opened.st_birthtime_ns,
                "lstat_ctime_ns": literal.st_ctime_ns, "fstat_ctime_ns": opened.st_ctime_ns})

    def test_missing_attribute_or_zero_identity_capability_refuses(self):
        source = self.active.lstat()
        for values in ({"st_file_attributes": None}, {"st_reparse_tag": None},
                       {"st_birthtime_ns": None}, {"st_ino": 0}, {"st_dev": 0}):
            with self.subTest(values=values):
                self.refuse(lambda: inputs._identity(self.changed_status(source, **values)), "PLATFORM")

    def test_descriptor_replacement_before_read_closes_without_reading(self):
        real_fstat = os.fstat
        patch, streams, _ = self.spy_open()
        def changed(fd):
            source = real_fstat(fd)
            return self.changed_status(source, st_ino=source.st_ino + 1)
        with patch, mock.patch.object(inputs.os, "fstat", changed):
            self.refuse(self.read, "CHANGED")
        self.assertEqual([], streams[0].requests)
        self.assertTrue(streams[0].stream.closed)

    def test_actual_replacement_before_open_refuses_and_closes(self):
        replacement = self.root / "replacement.bin"
        replacement.write_bytes(b"replacement-data")
        def replace(path):
            self.assertEqual(self.active, path)
            os.replace(replacement, path)
        patch, streams, _ = self.spy_open(before_open=replace)
        with patch:
            self.refuse(self.read, "CHANGED")
        self.assertTrue(streams[0].stream.closed)
        self.assertEqual(b"replacement-data", self.active.read_bytes())

    def test_descriptor_metadata_change_after_read_is_not_refreshed(self):
        real_fstat = os.fstat
        calls = []
        patch, streams, _ = self.spy_open()
        def changed(fd):
            calls.append(fd)
            source = real_fstat(fd)
            return source if len(calls) == 1 else self.changed_status(source, st_mtime_ns=source.st_mtime_ns + 1)
        with patch, mock.patch.object(inputs.os, "fstat", changed):
            self.refuse(self.read, "CHANGED")
        self.assertEqual(2, len(calls))
        self.assertEqual([16_385], streams[0].requests)
        self.assertTrue(streams[0].stream.closed)

    def test_short_read_refuses_without_retry(self):
        patch, streams, calls = self.spy_open(transform=lambda raw: raw[:-1])
        with patch:
            self.refuse(self.read, "CHANGED")
        self.assertEqual(1, len(calls))
        self.assertEqual([16_385], streams[0].requests)
        self.assertTrue(streams[0].stream.closed)

    def test_nonbytes_read_result_refuses(self):
        patch, streams, _ = self.spy_open(transform=bytearray)
        with patch:
            self.refuse(self.read, "IO")
        self.assertTrue(streams[0].stream.closed)

    def test_postread_actual_file_change_refuses_without_repair(self):
        original = inputs._literal_observations
        calls = []
        def changed(root, relative):
            calls.append(root)
            if len(calls) == 2:
                self.active.write_bytes(b"after-read-change")
            return original(root, relative)
        with mock.patch.object(inputs, "_literal_observations", changed):
            self.refuse(self.read, "CHANGED")
        self.assertEqual(2, len(calls))
        self.assertEqual(b"after-read-change", self.active.read_bytes())

    def test_detected_ancestor_metadata_change_refuses(self):
        original = inputs._literal_observations
        calls = []
        def changed(root, relative):
            calls.append(root)
            values = original(root, relative)
            if len(calls) == 2:
                first = values[0]
                changed_identity = first[1][:4] + (first[1][4] + 1,) + first[1][5:]
                return ((first[0], changed_identity),) + values[1:]
            return values
        with mock.patch.object(inputs, "_literal_observations", changed):
            self.refuse(self.read, "CHANGED")

    def test_canonical_escape_and_changed_location_refuse(self):
        original = Path.resolve
        def escaped(path, *args, **kwargs):
            if path == self.active:
                return self.fixture_parent / "outside.properties"
            return original(path, *args, **kwargs)
        with mock.patch.object(Path, "resolve", escaped):
            self.refuse(self.read, "PATH")
        real_canonical = inputs._canonical_locations
        calls = []
        def changed(root, target):
            calls.append(target)
            result = real_canonical(root, target)
            return result if len(calls) == 1 else (result[0], result[0] / "changed.properties")
        with mock.patch.object(inputs, "_canonical_locations", changed):
            self.refuse(self.read, "CHANGED")

    def test_canonical_alternate_drive_refuses(self):
        original = Path.resolve
        def redirected(path, *args, **kwargs):
            if path == self.root:
                return Path("C:/redirected-owned-root")
            if path == self.active:
                return Path("C:/redirected-owned-root/server.properties")
            return original(path, *args, **kwargs)
        with mock.patch.object(Path, "resolve", redirected):
            self.refuse(self.read, "PATH")

    def test_postread_actual_reparse_refuses(self):
        replacement = self.root / "linked-data.bin"
        replacement.write_bytes(b"replacement")
        original = inputs._literal_observations
        calls = []
        def changed(root, relative):
            calls.append(root)
            if len(calls) == 2:
                self.active.unlink()
                self.link(replacement, self.active)
            return original(root, relative)
        with mock.patch.object(inputs, "_literal_observations", changed):
            self.refuse(self.read, "PATH")
        self.assertEqual(2, len(calls))

    def test_descriptor_io_failure_closes_without_reading(self):
        patch, streams, _ = self.spy_open()
        with patch, mock.patch.object(inputs.os, "fstat", side_effect=OSError("PRIVATE_FSTAT_SECRET")):
            error = self.refuse(self.read, "IO")
        self.assertEqual([], streams[0].requests)
        self.assertTrue(streams[0].stream.closed)
        self.assertNotIn("PRIVATE_FSTAT_SECRET", "".join(traceback.format_exception(error)))

    def test_permission_and_read_errors_do_not_echo_secret_or_chain(self):
        secret = "TOKEN=private-path-secret"
        with mock.patch.object(Path, "lstat", side_effect=PermissionError(secret)):
            error = self.refuse(self.read, "IO")
        self.assertNotIn(secret, str(error))
        self.assertNotIn(secret, "".join(traceback.format_exception(error)))
        def failing_read(raw):
            raise OSError(secret)
        patch, streams, calls = self.spy_open(transform=failing_read)
        with patch:
            error = self.refuse(self.read, "IO")
        self.assertNotIn(secret, "".join(traceback.format_exception(error)))
        self.assertEqual(1, len(calls))
        self.assertTrue(streams[0].stream.closed)

    def test_close_failure_is_fixed_error_without_public_os_text(self):
        original_open = Path.open
        streams = []
        class FailedClose(_ObservedStream):
            def __exit__(self, *args):
                self.stream.close()
                raise OSError("PRIVATE_CLOSE_SECRET")
        def opened(path, mode="r", buffering=-1, **kwargs):
            value = FailedClose(original_open(path, mode, buffering=buffering, **kwargs))
            streams.append(value)
            return value
        with mock.patch.object(Path, "open", opened):
            error = self.refuse(self.read, "IO")
        self.assertNotIn("PRIVATE_CLOSE_SECRET", "".join(traceback.format_exception(error)))
        self.assertTrue(streams[0].stream.closed)

    def test_helper_makes_no_write_or_decode_call(self):
        before = self.active.read_bytes()
        with mock.patch.object(Path, "write_bytes", side_effect=AssertionError("WRITE")), \
             mock.patch.object(Path, "write_text", side_effect=AssertionError("WRITE")), \
             mock.patch.object(Path, "mkdir", side_effect=AssertionError("WRITE")), \
             mock.patch.object(Path, "read_bytes", side_effect=AssertionError("UNBOUNDED_READ")):
            self.assertEqual(before, self.read())
        self.assertEqual(before, self.active.read_bytes())


if __name__ == "__main__":
    unittest.main()
