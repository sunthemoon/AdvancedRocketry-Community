"""File and aggregate admission without Minecraft or external commands."""
import hashlib
import io
import os
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.bounded_validation_files import ArtifactError, EvidenceStore, read_file


class ValidationFileTests(unittest.TestCase):
    def setUp(self):
        self.parent = Path(tempfile.gettempdir()).resolve()
        self.root = Path(tempfile.mkdtemp(prefix="validation-admission-", dir=self.parent))
        self.assertEqual(self.root.resolve().parent, self.parent)
        self.addCleanup(self.cleanup)

    def cleanup(self):
        self.assertEqual(self.root.resolve().parent, self.parent)
        self.assertTrue(self.root.name.startswith("validation-admission-"))
        self.assertFalse(self.root.is_symlink() or self.root.is_junction())
        self.assertLessEqual(len(list(self.root.rglob("*"))), 1000)
        shutil.rmtree(self.root)

    def test_read_exact_limit_and_hash_only(self):
        path = self.root / "input"
        path.write_bytes(b"abcd")
        expected = {"bytes": 4, "sha256": hashlib.sha256(b"abcd").hexdigest()}
        self.assertEqual((expected, b"abcd"), read_file(path, 4))
        self.assertEqual((expected, None), read_file(path, 4, capture=False))

    def test_oversized_stat_rejected_before_open(self):
        path = self.root / "input"
        path.write_bytes(b"abcde")
        with patch.object(Path, "open", side_effect=AssertionError("must not open")):
            with self.assertRaises(ArtifactError):
                read_file(path, 4)

    def test_executable_filename_path_and_handle_modes(self):
        path = self.root / "fixture.exe"
        path.write_bytes(b"abc")
        self.assertEqual(b"abc", read_file(path, 3)[1])
        with path.open("rb") as source:
            self.assertEqual(path.stat().st_ino, os.fstat(source.fileno()).st_ino)
            if os.name == "nt":
                self.assertNotEqual(path.stat().st_mode, os.fstat(source.fileno()).st_mode)

    def test_growth_reads_at_most_cap_plus_one(self):
        path = self.root / "input"
        path.write_bytes(b"abcd")
        original, requests, consumed = Path.open, [], []
        class GrowingReader:
            def __init__(self, wrapped):
                self.wrapped = wrapped
            def __enter__(self):
                return self
            def __exit__(self, *args):
                self.wrapped.close()
            def fileno(self):
                return self.wrapped.fileno()
            def read(self, amount):
                requests.append(amount)
                data = self.wrapped.read(amount)
                consumed.append(len(data))
                if len(requests) == 1:
                    with original(path, "ab") as output:
                        output.write(b"0123456789")
                return data
        def opening(target, *args, **kwargs):
            return GrowingReader(original(target, *args, **kwargs))
        with patch.object(Path, "open", opening):
            with self.assertRaises(ArtifactError):
                read_file(path, 4)
        self.assertEqual([5, 1], requests)
        self.assertEqual(5, sum(consumed))

    def test_same_size_mutation_rejected(self):
        path = self.root / "input"
        path.write_bytes(b"abcd")
        original, count = os.fstat, 0
        def changing(fd):
            nonlocal count
            count += 1
            if count == 2:
                stamp = path.stat()
                os.utime(path, ns=(stamp.st_atime_ns, stamp.st_mtime_ns + 1000000000))
            return original(fd)
        with patch("scripts.bounded_validation_files.os.fstat", side_effect=changing):
            with self.assertRaises(ArtifactError):
                read_file(path, 4)

    def test_growth_bound_includes_underlying_raw_reads(self):
        path = self.root / "input"
        path.write_bytes(b"abcd")
        original, requests, consumed = Path.open, [], []
        class RawReader(io.RawIOBase):
            def __init__(self, wrapped):
                self.wrapped = wrapped
            def readable(self):
                return True
            def fileno(self):
                return self.wrapped.fileno()
            def readinto(self, buffer):
                requests.append(len(buffer))
                if len(requests) == 1:
                    with original(path, "ab", buffering=0) as output:
                        output.write(b"0123456789")
                amount = self.wrapped.readinto(buffer)
                consumed.append(amount)
                return amount
            def close(self):
                self.wrapped.close()
                super().close()
        def opening(target, mode, *args, **kwargs):
            raw = RawReader(original(target, mode, buffering=0))
            return raw if kwargs.get("buffering") == 0 else io.BufferedReader(raw)
        with patch.object(Path, "open", opening):
            with self.assertRaises(ArtifactError):
                read_file(path, 4)
        self.assertEqual([5], requests)
        self.assertEqual(5, sum(consumed))

    def test_hard_links_rejected_by_read_and_inventory(self):
        path = self.root / "input"
        path.write_bytes(b"abc")
        os.link(path, self.root / "alias")
        self.assertEqual(2, path.stat().st_nlink)
        with self.assertRaises(ArtifactError):
            read_file(path, 3)
        with self.assertRaises(ArtifactError):
            EvidenceStore(self.root, 6)

    def test_output_names_reject_ads_devices_and_normalized_aliases(self):
        store = EvidenceStore(self.root, 100)
        for name in ("host:payload", "nested/host:payload", "CON", "NUL.txt", "aux",
                     "COM1.log", "LPT9", "COM\u00b9", "LPT\u00b2", "CONIN$", "CONOUT$",
                     "trailing.", "trailing ", "a/./b", "a//b", "wild*", "a?b", "a\x01b"):
            with self.subTest(name=name), self.assertRaises(ArtifactError):
                store.write(name, b"abc")
        self.assertEqual((0, 0), (store.used, store.reserved))
        self.assertEqual([], list(self.root.iterdir()))

    def test_existing_file_and_directory_ads_rejected(self):
        if os.name != "nt":
            return  # ADS does not exist on the non-Windows branch.
        path = self.root / "input"
        path.write_bytes(b"abc")
        Path(str(path) + ":payload").write_bytes(b"hidden")
        with self.assertRaises(ArtifactError):
            read_file(path, 3)
        with self.assertRaises(ArtifactError):
            read_file(Path(str(path) + ":payload"), 6)
        with self.assertRaises(ArtifactError):
            EvidenceStore(self.root, 100)
        path.unlink()  # Remove this owned successful fixture, including its ADS.
        Path(str(self.root) + ":payload").write_bytes(b"hidden")
        with self.assertRaises(ArtifactError):
            EvidenceStore(self.root, 100)
        Path(str(self.root) + ":payload").unlink()

    def test_directory_and_ancestor_reparse_rejected(self):
        with self.assertRaises(ArtifactError):
            read_file(self.root, 4)
        path = self.root / "input"
        path.write_bytes(b"a")
        original = Path.lstat
        def reparse(target):
            value = original(target)
            if target == self.root:
                from types import SimpleNamespace
                return SimpleNamespace(st_mode=value.st_mode, st_file_attributes=0x400)
            return value
        with patch.object(Path, "lstat", reparse):
            with self.assertRaises(ArtifactError):
                read_file(path, 4)

    def test_initial_inventory_charged_and_oversize_rejected(self):
        (self.root / "existing").write_bytes(b"abc")
        self.assertEqual(3, EvidenceStore(self.root, 3).used)
        with self.assertRaises(ArtifactError):
            EvidenceStore(self.root, 2)

    def test_write_exhaustion_rejected_before_creation(self):
        store = EvidenceStore(self.root, 3)
        store.write("a", b"abc")
        with self.assertRaises(ArtifactError):
            store.write("new/deep/b", b"x")
        self.assertFalse((self.root / "new").exists())
        self.assertEqual((3, 0), (store.used, store.reserved))

    def test_empty_write_and_exclusive_output(self):
        store = EvidenceStore(self.root, 3)
        store.write("a", b"")
        self.assertEqual((0, 0), (store.used, store.reserved))
        with self.assertRaises(FileExistsError):
            store.write("a", b"x")
        self.assertEqual(b"", (self.root / "a").read_bytes())

    def test_capture_reserves_both_streams_before_creation(self):
        store = EvidenceStore(self.root, 9)
        with self.assertRaises(ArtifactError):
            store.reserve_capture("s/o", "s/e", 5)
        self.assertFalse((self.root / "s").exists())
        with store.reserve_capture("o", "e", 4) as reservation:
            self.assertEqual(8, store.reserved)
            with self.assertRaises(ArtifactError):
                store.write("third", b"ab")
            self.assertFalse((self.root / "third").exists())
            reservation.close(quiescent=True)
        self.assertEqual((0, 0), (store.used, store.reserved))

    def test_competing_reservations_and_targets(self):
        store = EvidenceStore(self.root, 8)
        first = store.reserve_capture("o", "e", 3)
        with self.assertRaises(ArtifactError):
            store.reserve_capture("x", "y", 2)
        with self.assertRaises(FileExistsError):
            store.write("o", b"x")
        first.close(quiescent=True)
        first.close(quiescent=True)
        self.assertEqual(0, store.reserved)

    def test_partial_capture_charged_after_exception(self):
        store = EvidenceStore(self.root, 8)
        with self.assertRaisesRegex(RuntimeError, "producer"):
            with store.reserve_capture("o", "e", 4) as reservation:
                (self.root / "o").write_bytes(b"abc")
                reservation.close(quiescent=True)
                raise RuntimeError("producer")
        self.assertEqual((3, 0), (store.used, store.reserved))
        self.assertEqual(b"abc", (self.root / "o").read_bytes())
        store.write("receipt", b"12345")
        with self.assertRaises(ArtifactError):
            store.write("extra", b"x")

    def test_oversized_capture_blocks_further_writes(self):
        store = EvidenceStore(self.root, 9)
        reservation = store.reserve_capture("o", "e", 4)
        (self.root / "o").write_bytes(b"abcde")
        with self.assertRaises(ArtifactError):
            reservation.close(quiescent=True)
        with self.assertRaises(ArtifactError):
            store.write("receipt", b"x")
        self.assertFalse((self.root / "receipt").exists())

    def test_live_writer_keeps_maximum_reservation(self):
        store = EvidenceStore(self.root, 10)
        with store.reserve_capture("o", "e", 4) as reservation:
            (self.root / "o").write_bytes(b"a")
            reservation.close(quiescent=False)
        self.assertEqual((0, 8), (store.used, store.reserved))
        with self.assertRaises(ArtifactError):
            store.write("receipt", b"abc")
        with self.assertRaises(ArtifactError):
            reservation.close(quiescent=1)
        reservation.close(quiescent=True)
        self.assertEqual((1, 0), (store.used, store.reserved))

    def test_partial_failed_write_remains_charged(self):
        store = EvidenceStore(self.root, 5)
        original = Path.open
        class BrokenWriter:
            def __init__(self, wrapped):
                self.wrapped = wrapped
            def __enter__(self):
                return self
            def __exit__(self, *args):
                self.wrapped.close()
            def write(self, data):
                self.wrapped.write(data[:2])
                raise OSError("disk failure")
        def opening(path, mode, *args, **kwargs):
            wrapped = original(path, mode, *args, **kwargs)
            return BrokenWriter(wrapped) if mode == "xb" else wrapped
        with patch.object(Path, "open", opening):
            with self.assertRaisesRegex(OSError, "disk failure"):
                store.write("partial", b"abcde")
        self.assertEqual((2, 0), (store.used, store.reserved))
        self.assertEqual(b"ab", (self.root / "partial").read_bytes())

    def test_invalid_names_and_individual_caps(self):
        store = EvidenceStore(self.root, 10)
        for name in ("", ".", "../escape", str(self.root / "absolute")):
            with self.subTest(name=name), self.assertRaises(ArtifactError):
                store.write(name, b"a")
        with self.assertRaises(ArtifactError):
            store.write("a", b"abc", limit=2)
        with self.assertRaises(ArtifactError):
            store.reserve_capture("a", "a", 1)
        self.assertEqual((0, 0), (store.used, store.reserved))

    def test_invalid_limits(self):
        path = self.root / "input"
        path.write_bytes(b"a")
        for value in (0, -1, True, 1.5, "4"):
            with self.subTest(limit=value):
                with self.assertRaises(ArtifactError):
                    read_file(path, value)
                with self.assertRaises(ArtifactError):
                    EvidenceStore(self.root, value)


if __name__ == "__main__":
    unittest.main()
