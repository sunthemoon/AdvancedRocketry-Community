import hashlib
import json
import os
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from scripts import release_checksum_inputs as bounded
from scripts.validate_build_artifact import build_content_manifest
from scripts.validate_release_checksums import (
    render_release_checksums,
    load_artifact_metadata,
    parse_checksum_text,
    read_tracked_files,
    update_release_checksums,
    validate_release_checksums,
)


class ReleaseChecksumValidationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.release_dir = self.root / "docs/releases/v0.0.2"
        self.evidence_dir = self.release_dir / "evidence"
        self.manifest = self.evidence_dir / "artifact/jar-content-manifest.json"
        self.lifecycle = self.evidence_dir / "dedicated-server/first-start.txt"
        self.checksums = self.release_dir / "checksums.txt"
        self.artifact_name = (
            "advancedrocketry-community-1.20.1-0.0.2-dev.jar"
        )
        self.artifact = self.root / "build/libs" / self.artifact_name

        self.manifest.parent.mkdir(parents=True)
        self.lifecycle.parent.mkdir(parents=True)
        self.artifact.parent.mkdir(parents=True)
        self.lifecycle.write_text("server lifecycle evidence\n", encoding="utf-8")
        with zipfile.ZipFile(
            self.artifact, "w", compression=zipfile.ZIP_DEFLATED
        ) as archive:
            archive.writestr("pack.mcmeta", b"{}")
        self.write_manifest()

        self.tracked_files = {
            "docs/releases/v0.0.2/checksums.txt",
            "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json",
            "docs/releases/v0.0.2/evidence/dedicated-server/first-start.txt",
        }

    @staticmethod
    def digest(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def write_manifest(
        self,
        *,
        include_artifact_sha256: bool = True,
        transform=None,
    ) -> None:
        document: dict[str, object] = build_content_manifest(self.artifact)
        if include_artifact_sha256:
            pass
        else:
            document.pop("artifact_sha256")
        if transform is not None:
            transform(document)
        self.manifest.write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

    def write_checksums(
        self,
        *,
        include_lifecycle: bool = True,
        extra_lines: tuple[str, ...] = (),
        artifact_entry_path: str | None = None,
    ) -> None:
        lines = [
            "# v0.0.2 release evidence",
            "",
            f"{self.digest(self.manifest)}  "
            "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json",
        ]
        if include_lifecycle:
            lines.append(
                f"{self.digest(self.lifecycle)}  "
                "docs/releases/v0.0.2/evidence/dedicated-server/first-start.txt"
            )
        lines.extend(extra_lines)
        lines.append(
            f"{self.digest(self.artifact)}  "
            f"{artifact_entry_path or f'build/libs/{self.artifact_name}'}"
        )
        self.checksums.parent.mkdir(parents=True, exist_ok=True)
        self.checksums.write_text("\n".join(lines) + "\n", encoding="utf-8")

    def validate(self, *, artifact: Path | None = None) -> list[str]:
        errors, _ = validate_release_checksums(
            repository_root=self.root,
            artifact_path=artifact,
            tracked_files=self.tracked_files,
        )
        return errors

    def test_happy_path_supports_governance_and_real_artifact_modes(self) -> None:
        self.write_checksums()

        governance_errors, governance_details = validate_release_checksums(
            repository_root=self.root,
            tracked_files=self.tracked_files,
        )
        artifact_errors, artifact_details = validate_release_checksums(
            repository_root=self.root,
            artifact_path=self.artifact,
            tracked_files=self.tracked_files,
        )

        self.assertEqual([], governance_errors)
        self.assertFalse(governance_details["artifact_verified"])
        self.assertEqual([], artifact_errors)
        self.assertTrue(artifact_details["artifact_verified"])
        self.assertEqual(3, artifact_details["entries"])

    def test_tampered_committed_evidence_is_rejected(self) -> None:
        self.write_checksums()
        self.lifecycle.write_text("tampered evidence\n", encoding="utf-8")

        errors = self.validate()

        self.assertTrue(
            any("SHA-256 mismatch for committed file" in error for error in errors),
            errors,
        )

    def test_evidence_omitted_from_checksum_list_is_rejected(self) -> None:
        self.write_checksums(include_lifecycle=False)

        errors = self.validate()

        self.assertTrue(
            any("Evidence files omitted from checksum list" in error for error in errors),
            errors,
        )
        self.assertTrue(any("first-start.txt" in error for error in errors), errors)

    def test_unsafe_traversal_path_is_rejected(self) -> None:
        unsafe_line = f"{hashlib.sha256(b'outside').hexdigest()}  ../outside.txt"
        self.write_checksums(extra_lines=(unsafe_line,))

        errors = self.validate()

        self.assertTrue(any("unsafe path" in error for error in errors), errors)
        self.assertTrue(any("traversal" in error for error in errors), errors)

    def test_duplicate_path_is_rejected(self) -> None:
        duplicate = (
            f"{self.digest(self.lifecycle)}  "
            "docs/releases/v0.0.2/evidence/dedicated-server/first-start.txt"
        )
        self.write_checksums(extra_lines=(duplicate,))

        errors = self.validate()

        self.assertTrue(any("duplicate path" in error for error in errors), errors)

    def test_real_artifact_hash_mismatch_is_rejected(self) -> None:
        self.write_checksums()
        self.artifact.write_bytes(b"different distributable")

        errors = self.validate(artifact=self.artifact)

        self.assertTrue(
            any("Artifact SHA-256 does not match" in error for error in errors),
            errors,
        )

    def test_missing_artifact_metadata_is_rejected(self) -> None:
        self.write_manifest(include_artifact_sha256=False)
        self.write_checksums()

        errors = self.validate()

        self.assertTrue(
            any("missing lowercase artifact_sha256 metadata" in error for error in errors),
            errors,
        )

    def test_complete_content_manifest_schema_is_required(self) -> None:
        def change_schema(document: dict[str, object]) -> None:
            document["schema_version"] = 2
            document["unexpected"] = True

        self.write_manifest(transform=change_schema)
        self.write_checksums()

        errors = self.validate()

        self.assertTrue(any("schema_version must be 1" in error for error in errors), errors)
        self.assertTrue(any("unexpected keys" in error for error in errors), errors)

    def test_manifest_entry_count_and_entry_schema_are_validated(self) -> None:
        def corrupt_entries(document: dict[str, object]) -> None:
            document["entry_count"] = 2
            entries = document["entries"]
            assert isinstance(entries, list)
            assert isinstance(entries[0], dict)
            entries[0]["extra"] = "not part of schema"

        self.write_manifest(transform=corrupt_entries)
        self.write_checksums()

        errors = self.validate()

        self.assertTrue(any("entry_count does not match" in error for error in errors), errors)
        self.assertTrue(any("exactly path, size, and sha256" in error for error in errors), errors)

    def test_built_artifact_entries_must_match_committed_manifest(self) -> None:
        def tamper_entry(document: dict[str, object]) -> None:
            entries = document["entries"]
            assert isinstance(entries, list)
            assert isinstance(entries[0], dict)
            entries[0]["sha256"] = "0" * 64

        self.write_manifest(transform=tamper_entry)
        self.write_checksums()

        errors = self.validate(artifact=self.artifact)

        self.assertTrue(
            any("Regenerated built artifact content manifest" in error for error in errors),
            errors,
        )
        self.assertTrue(any("entries" in error for error in errors), errors)

    def test_external_artifact_checksum_path_must_be_canonical(self) -> None:
        self.write_checksums(artifact_entry_path=self.artifact_name)

        errors = self.validate()

        self.assertTrue(
            any("build/libs/<filename>.jar" in error for error in errors), errors
        )
        self.assertTrue(any("canonical" in error for error in errors), errors)

    def test_supplied_artifact_path_must_be_canonical(self) -> None:
        self.write_checksums()
        outside = self.root / self.artifact_name
        outside.write_bytes(self.artifact.read_bytes())

        errors = self.validate(artifact=outside)

        self.assertTrue(
            any("canonical repository path" in error for error in errors), errors
        )

    def test_deterministic_update_covers_the_complete_evidence_tree(self) -> None:
        extra = self.evidence_dir / "client/manual-evidence.json"
        extra.parent.mkdir(parents=True)
        extra.write_text("{}\n", encoding="utf-8")
        self.tracked_files.add(
            "docs/releases/v0.0.2/evidence/client/manual-evidence.json"
        )

        errors = update_release_checksums(repository_root=self.root)

        self.assertEqual([], errors)
        first = self.checksums.read_bytes()
        second_text, render_errors = render_release_checksums(
            repository_root=self.root
        )
        self.assertEqual([], render_errors)
        self.assertEqual(first, second_text.encode("utf-8"))
        self.assertIn(self.digest(extra).encode("ascii"), first)
        self.assertEqual([], self.validate(artifact=self.artifact))

    def test_update_rejects_symlinked_evidence(self) -> None:
        link = self.evidence_dir / "linked.txt"
        try:
            link.symlink_to(self.lifecycle)
        except OSError as exc:
            self.skipTest(f"symlinks are unavailable: {exc}")

        errors = update_release_checksums(repository_root=self.root)

        self.assertTrue(any("must not be a symlink" in error for error in errors))


class ChecksumInputBoundaryTests(unittest.TestCase):
    def setUp(self) -> None:
        # Reuse the original fixture without inheriting/rerunning its methods.
        self.fixture = ReleaseChecksumValidationTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.fixture.write_checksums()
        self.root = self.fixture.root

    def test_checksum_byte_and_record_boundaries(self) -> None:
        line = '0' * 64 + '  ordinary.txt\n'
        with patch.object(bounded, 'MAX_CHECKSUM_BYTES', len(line.encode())):
            self.assertEqual([], parse_checksum_text(line)[1])
            self.assertIn('byte limit', parse_checksum_text(line + '\n')[1][0])
        with patch.object(bounded, 'MAX_CHECKSUM_ENTRIES', 1):
            self.assertEqual([], parse_checksum_text('# comment\n\n' + line)[1])
            self.assertIn('record count', parse_checksum_text(line + 'invalid\n')[1][-1])
            self.assertIn('record count', parse_checksum_text('invalid\n' * 2)[1][-1])

    def test_path_byte_and_depth_boundaries(self) -> None:
        session = bounded.ChecksumInputs(self.root)
        with patch.object(bounded, 'MAX_PATH_BYTES', 4):
            self.assertEqual(self.root / 'test', session.target(Path('test')))
            with self.assertRaisesRegex(bounded.ChecksumInputError, 'byte/depth'):
                session.target(Path('tests'))
        with patch.object(bounded, 'MAX_PATH_DEPTH', 2):
            session.target(Path('a/b'))
            with self.assertRaisesRegex(bounded.ChecksumInputError, 'byte/depth'):
                session.target(Path('a/b/c'))
        with self.assertRaisesRegex(bounded.ChecksumInputError, 'traversal'):
            session.target(self.root / 'a/../ordinary.txt')
        with self.assertRaisesRegex(bounded.ChecksumInputError, 'repository root'):
            session.target(self.root.parent / 'outside.txt')

    def test_read_byte_and_session_boundaries(self) -> None:
        path = self.root / 'ordinary.txt'
        path.write_bytes(b'1234')
        session = bounded.ChecksumInputs(self.root)
        with patch.object(bounded, 'MAX_SESSION_BYTES', 8):
            self.assertEqual(b'1234', session.read(path, 4))
            self.assertEqual(hashlib.sha256(b'1234').hexdigest(), session.digest(path, 4))
            with self.assertRaisesRegex(bounded.ChecksumInputError, 'aggregate limit'):
                session.read(path, 4)
        with self.assertRaisesRegex(bounded.ChecksumInputError, 'byte/aggregate'):
            bounded.ChecksumInputs(self.root).read(path, 3)

    @unittest.skipUnless(os.name == 'nt', 'Windows cross-API timestamp semantics')
    def test_windows_path_and_handle_creation_time_identity(self) -> None:
        path = self.root / 'timestamp.txt'
        path.write_bytes(b'before')
        # Separate creation and content-change timestamps on the real fixture.
        import time
        time.sleep(0.02)
        path.write_bytes(b'after')
        selected = path.lstat()
        with path.open('rb') as stream:
            opened = os.fstat(stream.fileno())
        self.assertEqual(selected.st_birthtime_ns, opened.st_birthtime_ns)
        self.assertEqual(bounded._opened_identity(selected), bounded._opened_identity(opened))
        self.assertEqual(b'after', bounded.ChecksumInputs(self.root).read(path, 5))

    def test_empty_file_at_exhausted_byte_budget(self) -> None:
        path = self.root / 'empty.txt'
        path.write_bytes(b'')
        with patch.object(bounded, 'MAX_SESSION_BYTES', 0):
            self.assertEqual(b'', bounded.ChecksumInputs(self.root).read(path, 0))

    def test_discovery_count_and_aggregate_limits(self) -> None:
        tree = self.root / 'bounded-tree'
        tree.mkdir()
        (tree / 'one.txt').write_bytes(b'12')
        (tree / 'two.txt').write_bytes(b'34')
        (tree / 'empty-dir').mkdir()
        limits = [('MAX_EVIDENCE_ENTRIES', 3, 2), ('MAX_EVIDENCE_FILES', 2, 1),
                  ('MAX_EVIDENCE_DIRECTORIES', 2, 1), ('MAX_EVIDENCE_FILE_BYTES', 2, 1),
                  ('MAX_EVIDENCE_TOTAL_BYTES', 4, 3)]
        for name, accepted, rejected in limits:
            with self.subTest(limit=name):
                with patch.object(bounded, name, accepted):
                    self.assertEqual(2, len(bounded.ChecksumInputs(self.root).scan(tree)))
                with patch.object(bounded, name, rejected):
                    with self.assertRaises(bounded.ChecksumInputError):
                        bounded.ChecksumInputs(self.root).scan(tree)

    def test_discovery_retains_file_selection_identity(self) -> None:
        tree = self.root / 'tree'
        tree.mkdir()
        path = tree / 'file.txt'
        path.write_bytes(b'before')
        session = bounded.ChecksumInputs(self.root)
        session.scan(tree)
        path.write_bytes(b'after and larger')
        with self.assertRaisesRegex(bounded.ChecksumInputError, 'between reads'):
            session.digest(path)

    def test_final_discovery_rejects_added_removed_and_empty_directory(self) -> None:
        for change in ('add', 'remove', 'directory'):
            with self.subTest(change=change):
                tree = self.root / change
                tree.mkdir()
                path = tree / 'file.txt'
                path.write_bytes(b'original')
                session = bounded.ChecksumInputs(self.root)
                session.scan(tree)
                if change == 'add':
                    (tree / 'added.txt').write_bytes(b'new')
                elif change == 'remove':
                    path.unlink()
                else:
                    (tree / 'empty').mkdir()
                with self.assertRaises((bounded.ChecksumInputError, OSError)):
                    session.assert_stable()

    def test_final_path_identity_rejects_ordinary_replacement(self) -> None:
        path = self.root / 'selected.txt'
        replacement = self.root / 'replacement.txt'
        path.write_bytes(b'ordinary')
        replacement.write_bytes(b'ordinary')
        session = bounded.ChecksumInputs(self.root)
        original_inspect = session.inspect
        calls = 0

        def inspect(candidate, **kwargs):
            nonlocal calls
            if candidate == path:
                calls += 1
                if calls == 2:
                    replacement.replace(path)
            return original_inspect(candidate, **kwargs)

        with patch.object(session, 'inspect', side_effect=inspect):
            with self.assertRaisesRegex(bounded.ChecksumInputError, 'pathname/content'):
                session.read(path, 8)

    def test_open_handle_identity_is_checked(self) -> None:
        path = self.root / 'selected.txt'
        other = self.root / 'other.txt'
        path.write_bytes(b'selected')
        other.write_bytes(b'different')
        session = bounded.ChecksumInputs(self.root)
        with other.open('rb') as stream:
            with patch.object(Path, 'open', return_value=stream):
                with self.assertRaisesRegex(bounded.ChecksumInputError, 'before reading'):
                    session.read(path, 20)

    def test_mutation_during_read_is_rejected(self) -> None:
        path = self.root / 'selected.txt'
        path.write_bytes(b'ordinary')
        session = bounded.ChecksumInputs(self.root)
        original_check = session.check_time
        original_read = Path.open

        class MutatingReader:
            def __enter__(self):
                self.stream = original_read(path, 'rb')
                return self

            def __exit__(self, *args):
                self.stream.close()

            def fileno(self):
                return self.stream.fileno()

            def read(self, size):
                result = self.stream.read(size)
                if result:
                    with original_read(path, 'ab') as writer:
                        writer.write(b'changed')
                return result

        with patch.object(Path, 'open', return_value=MutatingReader()):
            with self.assertRaisesRegex(bounded.ChecksumInputError, 'limit|changed'):
                session.read(path, 8)
        original_check()

    def test_shared_metadata_reopen_rejects_change(self) -> None:
        session = bounded.ChecksumInputs(self.root)
        session.digest(self.fixture.manifest)
        self.fixture.manifest.write_text('{}', encoding='utf-8')
        metadata, errors = load_artifact_metadata(self.fixture.manifest, _inputs=session)
        self.assertIsNone(metadata)
        self.assertTrue(any('identity changed' in error for error in errors), errors)

    def test_metadata_byte_bound_and_entry_deadline_return_errors(self) -> None:
        size = self.fixture.manifest.stat().st_size
        with patch.object(bounded, 'MAX_MANIFEST_BYTES', size):
            self.assertEqual([], load_artifact_metadata(self.fixture.manifest)[1])
        with patch.object(bounded, 'MAX_MANIFEST_BYTES', size - 1):
            self.assertIsNone(load_artifact_metadata(self.fixture.manifest)[0])
        session = bounded.ChecksumInputs(self.root)
        original = session.check_time

        def expire_in_schema():
            # Exercise the public catch at the actual entry-validation stage.
            import inspect
            if any(frame.function == '_load_artifact_metadata' and 'entry' in frame.frame.f_locals
                   for frame in inspect.stack()):
                raise bounded.ChecksumInputError('entry deadline test')
            original()

        with patch.object(session, 'check_time', side_effect=expire_in_schema):
            metadata, errors = load_artifact_metadata(self.fixture.manifest, _inputs=session)
        self.assertIsNone(metadata)
        self.assertTrue(any('entry deadline test' in error for error in errors), errors)

    def test_session_deadline_returns_validation_failure(self) -> None:
        with patch.object(bounded, 'INPUT_TIMEOUT_SECONDS', 0):
            errors, details = validate_release_checksums(
                self.root, tracked_files=self.fixture.tracked_files)
        self.assertTrue(any('deadline' in error for error in errors), errors)
        self.assertFalse(details['artifact_verified'])

    def test_index_and_local_coverage_include_missing_and_untracked(self) -> None:
        missing = 'docs/releases/v0.0.2/evidence/missing.txt'
        untracked = self.fixture.evidence_dir / 'untracked.txt'
        untracked.write_bytes(b'untracked')
        tracked = self.fixture.tracked_files | {missing}
        errors, details = validate_release_checksums(self.root, tracked_files=tracked)
        self.assertEqual(4, details['evidence_files'])
        self.assertTrue(any(missing in error and 'untracked.txt' in error for error in errors), errors)
        with patch.object(bounded, 'MAX_EVIDENCE_FILES', 3):
            errors, _ = validate_release_checksums(self.root, tracked_files=tracked)
        self.assertTrue(any('union exceeds' in error for error in errors), errors)

    def test_injected_tracking_count_bytes_and_unsafe_paths_fail_closed(self) -> None:
        for patch_name, limit in [('MAX_TRACKED_PATHS', 2), ('MAX_GIT_BYTES', 2)]:
            with self.subTest(limit=patch_name), patch.object(bounded, patch_name, limit):
                errors, _ = validate_release_checksums(self.root, tracked_files=self.fixture.tracked_files)
                self.assertTrue(any('count/byte' in error for error in errors), errors)
        errors, _ = validate_release_checksums(self.root, tracked_files={'../unsafe.txt'})
        self.assertTrue(any('Unsafe tracked path' in error for error in errors), errors)

    def test_git_inventory_framing_utf8_and_count(self) -> None:
        cases = [(b'a.txt\0\0', 'empty path'), (b'\xff\0', 'utf-8'),
                 (b'../unsafe\0', 'Unsafe tracked path')]
        for payload, expected in cases:
            with self.subTest(payload=payload):
                with patch.object(bounded.ChecksumInputs, 'git_paths', return_value=payload):
                    paths, error = read_tracked_files(self.root)
                self.assertEqual(set(), paths)
                self.assertIn(expected, error)
        with patch.object(bounded, 'MAX_TRACKED_PATHS', 1):
            with patch.object(bounded.ChecksumInputs, 'git_paths', return_value=b'a\0b\0'):
                self.assertIn('path count', read_tracked_files(self.root)[1])

    def test_git_locator_failure_does_not_launch(self) -> None:
        for executable in (None, str(self.root / 'git.exe')):
            with self.subTest(executable=executable), patch.object(
                bounded.shutil, 'which', return_value=executable), patch.object(
                bounded.subprocess, 'Popen') as launch:
                paths, error = read_tracked_files(self.root)
                self.assertEqual(set(), paths)
                self.assertIsNotNone(error)
                launch.assert_not_called()

    def test_git_output_framing_limit_exit_and_timeout(self) -> None:
        class Process:
            def __init__(self, payload, running=False, exit_code=0):
                self.payload, self.running, self.exit_code = payload, running, exit_code
                self.killed = self.waited = False

            def start(self, command, **kwargs):
                kwargs['stdout'].write(self.payload)
                kwargs['stdout'].flush()
                self.command, self.environment = command, kwargs['env']
                return self

            def poll(self):
                return None if self.running else self.exit_code

            def kill(self):
                self.killed, self.running = True, False

            def wait(self, timeout):
                self.waited = True
                return self.exit_code

        cases = [(b'a\0', 2, 0, False, 30, None),
                 (b'ab\0', 2, 0, False, 30, 'byte limit'),
                 (b'a', 2, 0, False, 30, 'NUL-terminated'),
                 (b'', 2, 7, False, 30, 'exit 7'),
                 (b'', 2, 0, True, 0, 'timed out')]
        for payload, cap, code, running, seconds, expected in cases:
            with self.subTest(case=expected):
                process = Process(payload, running, code)
                with patch.object(bounded, 'MAX_GIT_BYTES', cap), patch.object(
                    bounded, 'GIT_TIMEOUT_SECONDS', seconds), patch.object(
                    bounded.subprocess, 'Popen', side_effect=process.start), patch.dict(
                    os.environ, {'GIT_DIR': 'untrusted', 'GIT_CONFIG_COUNT': '5'}):
                    session = bounded.ChecksumInputs(self.root)
                    if expected:
                        with self.assertRaisesRegex(bounded.ChecksumInputError, expected):
                            session.git_paths()
                    else:
                        self.assertEqual(payload, session.git_paths())
                self.assertTrue(process.waited)
                self.assertEqual(running, process.killed)
                self.assertNotIn('GIT_DIR', process.environment)
                self.assertNotIn('GIT_CONFIG_COUNT', process.environment)
                self.assertIn('core.fsmonitor=false', process.command)

    def test_real_git_uses_index_membership_and_local_bytes(self) -> None:
        def git(*args):
            result = subprocess.run(['git', '-C', str(self.root), *args],
                                    capture_output=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stderr)
            return result

        git('init', '--quiet')
        git('add', '--', *(sorted(self.fixture.tracked_files)))
        paths, error = read_tracked_files(self.root)
        self.assertIsNone(error)
        self.assertEqual(self.fixture.tracked_files, paths)
        self.assertEqual([], validate_release_checksums(self.root)[0])
        self.fixture.lifecycle.write_text('new local bytes\n', encoding='utf-8')
        self.assertTrue(any('SHA-256 mismatch' in error
                            for error in validate_release_checksums(self.root)[0]))

    def test_reparse_parent_root_ancestor_and_update_are_rejected(self) -> None:
        target = self.root / 'real-parent'
        target.mkdir()
        (target / 'ordinary.txt').write_bytes(b'ordinary')
        link = self.root / 'linked-parent'
        try:
            link.symlink_to(target, target_is_directory=True)
        except OSError as exc:
            self.skipTest(f'directory symlinks are unavailable: {exc}')
        try:
            with self.assertRaisesRegex(bounded.ChecksumInputError, 'reparse point'):
                bounded.ChecksumInputs(self.root).read(link / 'ordinary.txt', 8)
            (target / 'nested-root').mkdir()
            with self.assertRaisesRegex(bounded.ChecksumInputError, 'reparse point'):
                bounded.ChecksumInputs(link / 'nested-root')
            errors = update_release_checksums(self.root, checksums_path=link / 'checksums.txt')
            self.assertTrue(any('reparse point' in error for error in errors), errors)
            self.assertFalse((target / 'checksums.txt').exists())
        finally:
            link.unlink()

    @unittest.skipUnless(os.name == 'nt', 'Windows junction observation')
    def test_actual_windows_parent_junction_is_rejected(self) -> None:
        target = self.root / 'junction-target'
        target.mkdir()
        (target / 'ordinary.txt').write_bytes(b'ordinary')
        link = self.root / 'junction-parent'
        result = subprocess.run([str(Path(os.environ['SystemRoot']) / 'System32/cmd.exe'),
                                 '/d', '/c', 'mklink', '/J', str(link), str(target)],
                                capture_output=True, timeout=30)
        self.assertEqual(0, result.returncode, result.stderr)
        try:
            self.assertTrue(link.lstat().st_file_attributes & 0x400)
            with self.assertRaisesRegex(bounded.ChecksumInputError, 'reparse point'):
                bounded.ChecksumInputs(self.root).read(link / 'ordinary.txt', 8)
        finally:
            # Remove only this owned junction, never its target contents.
            link.rmdir()
        self.assertEqual(b'ordinary', (target / 'ordinary.txt').read_bytes())

    def test_hardlinked_input_is_rejected(self) -> None:
        path = self.root / 'hardlinked.txt'
        try:
            path.hardlink_to(self.fixture.lifecycle)
        except OSError as exc:
            self.skipTest(f'hardlinks are unavailable: {exc}')
        with self.assertRaisesRegex(bounded.ChecksumInputError, 'non-hardlinked'):
            bounded.ChecksumInputs(self.root).digest(path)

    def test_update_checks_output_before_truncating_and_keeps_determinism(self) -> None:
        before = self.fixture.checksums.read_bytes()
        with patch.object(bounded, 'MAX_CHECKSUM_BYTES', 1):
            self.assertTrue(update_release_checksums(self.root))
        self.assertEqual(before, self.fixture.checksums.read_bytes())
        output = Path('new-parent/checksums.txt')
        self.assertEqual([], update_release_checksums(self.root, checksums_path=output))
        first = (self.root / output).read_bytes()
        self.assertEqual([], update_release_checksums(self.root, checksums_path=output))
        self.assertEqual(first, (self.root / output).read_bytes())

    def test_render_uses_case_sensitive_posix_path_order(self) -> None:
        for name in ('README.md', 'a-note.txt', 'Z-last.txt'):
            (self.fixture.evidence_dir / name).write_bytes(name.encode('ascii'))
        text, errors = render_release_checksums(self.root)
        self.assertEqual([], errors)
        self.assertIsNotNone(text)
        entries, errors = parse_checksum_text(text)
        self.assertEqual([], errors)
        evidence_paths = [entry.path for entry in entries[1:]]
        self.assertEqual(sorted(evidence_paths), evidence_paths)
        self.assertLess(evidence_paths.index('docs/releases/v0.0.2/evidence/README.md'),
                        evidence_paths.index('docs/releases/v0.0.2/evidence/a-note.txt'))

    def test_intentional_output_inside_evidence_keeps_updater_return(self) -> None:
        for preexisting in (False, True):
            with self.subTest(preexisting=preexisting):
                output = self.fixture.evidence_dir / f'nested-{preexisting}/checksums.txt'
                if preexisting:
                    output.parent.mkdir()
                    output.write_bytes(b'old checksum list\n')
                text, errors = render_release_checksums(self.root)
                self.assertEqual([], errors)
                self.assertEqual([], update_release_checksums(self.root, checksums_path=output))
                self.assertEqual(text.encode('utf-8'), output.read_bytes())
                # An updater side effect does not approve a self-referential list.
                tracked = self.fixture.tracked_files | {output.relative_to(self.root).as_posix()}
                validation_errors, _ = validate_release_checksums(
                    self.root, checksums_path=output, tracked_files=tracked)
                self.assertTrue(validation_errors)

    def test_intentional_output_does_not_mask_other_tree_additions(self) -> None:
        session = bounded.ChecksumInputs(self.root)
        session.scan(self.fixture.evidence_dir)
        output = self.fixture.evidence_dir / 'new/output.txt'
        original = session._record_output_change

        def record(target, status):
            original(target, status)
            if target == output:
                (self.fixture.evidence_dir / 'unrelated.txt').write_bytes(b'unrelated')

        with patch.object(session, '_record_output_change', side_effect=record):
            with self.assertRaisesRegex(bounded.ChecksumInputError, 'tree changed'):
                session.write(output, b'explicit output')

    def test_artifact_physical_limit_and_delegated_mutation_fail(self) -> None:
        with patch.object(bounded, 'MAX_ARTIFACT_BYTES', self.fixture.artifact.stat().st_size - 1):
            errors, details = validate_release_checksums(
                self.root, artifact_path=self.fixture.artifact,
                tracked_files=self.fixture.tracked_files)
        self.assertTrue(any('byte/aggregate' in error for error in errors), errors)
        self.assertFalse(details['artifact_verified'])
        original = build_content_manifest

        def regenerate(path):
            result = original(path)
            with path.open('ab') as stream:
                stream.write(b'changed after expansion')
            return result

        with patch('scripts.validate_release_checksums.build_content_manifest', side_effect=regenerate):
            errors, details = validate_release_checksums(
                self.root, artifact_path=self.fixture.artifact,
                tracked_files=self.fixture.tracked_files)
        self.assertTrue(errors)
        self.assertFalse(details['artifact_verified'])


if __name__ == "__main__":
    unittest.main()
