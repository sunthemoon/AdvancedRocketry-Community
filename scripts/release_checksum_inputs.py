"""Bounded ordinary local/index inputs for release checksum validation.

Identity checks detect observed ordinary changes, not adversarial filesystem
ABA races. Deadlines are cooperative around local OS calls. Git output polling
is not an atomic disk quota or proof of descendant-process containment.
"""
from __future__ import annotations

import hashlib
import os
import shutil
import stat
import subprocess
import tempfile
import time
from pathlib import Path

MAX_GIT_BYTES = 4 * 1024**2
MAX_TRACKED_PATHS = 32768
GIT_TIMEOUT_SECONDS = 30
MAX_PATH_BYTES = 4096
MAX_PATH_DEPTH = 64
MAX_CHECKSUM_BYTES = 2 * 1024**2
MAX_CHECKSUM_ENTRIES = 8192
MAX_MANIFEST_BYTES = 8 * 1024**2
MAX_EVIDENCE_ENTRIES = 8192
MAX_EVIDENCE_FILES = 4096
MAX_EVIDENCE_DIRECTORIES = 512
MAX_EVIDENCE_FILE_BYTES = 50 * 1024**2
MAX_EVIDENCE_TOTAL_BYTES = 100 * 1024**2
MAX_ARTIFACT_BYTES = 256 * 1024**2
MAX_SESSION_BYTES = 512 * 1024**2
INPUT_TIMEOUT_SECONDS = 60
CHUNK_BYTES = 64 * 1024


class ChecksumInputError(ValueError):
    """An input cannot be admitted without weakening checksum validation."""


def _linked(status: os.stat_result) -> bool:
    return stat.S_ISLNK(status.st_mode) or bool(
        getattr(status, 'st_file_attributes', 0)
        & getattr(stat, 'FILE_ATTRIBUTE_REPARSE_POINT', 0x400)
    )


def _identity(status: os.stat_result) -> tuple[int, ...]:
    return (status.st_dev, status.st_ino, status.st_mode, status.st_size,
            status.st_mtime_ns, status.st_ctime_ns, status.st_nlink,
            getattr(status, 'st_file_attributes', 0),
            getattr(status, 'st_birthtime_ns', 0))


def _opened_identity(status: os.stat_result) -> tuple[int, ...]:
    # CPython Windows lstat/fstat can report different deprecated ctime values
    # for an unchanged file. Keep ctime in each API's own before/after checks,
    # but compare birth time plus file ID/size/mtime across the two APIs.
    identity = _identity(status)
    return identity[:5] + (0,) + identity[6:] if os.name == 'nt' else identity


class ChecksumInputs:
    """One operation's finite input budget and observed ordinary identities."""

    def __init__(self, root: Path):
        if '..' in root.parts:
            raise ChecksumInputError('Repository root must not contain traversal')
        self.root = root.absolute()
        if len(self.root.parts) > MAX_PATH_DEPTH:
            raise ChecksumInputError('Repository root exceeds absolute depth limit')
        self.deadline = time.monotonic() + INPUT_TIMEOUT_SECONDS
        self.total_bytes = 0
        self.directories: dict[Path, tuple[int, ...]] = {}
        self.files: dict[Path, tuple[int, ...]] = {}
        self.trees: dict[Path, tuple[tuple[str, tuple[int, ...]], ...]] = {}
        self.inspect(self.root, directory=True)

    def check_time(self) -> None:
        if time.monotonic() >= self.deadline:
            raise ChecksumInputError('Checksum input deadline exceeded')

    def target(self, path: Path) -> Path:
        self.check_time()
        target = path if path.is_absolute() else self.root / path
        if '..' in target.parts:
            raise ChecksumInputError('Input path must not contain traversal')
        try:
            relative = target.relative_to(self.root)
        except ValueError as exc:
            raise ChecksumInputError('Path must remain under the repository root') from exc
        text = relative.as_posix()
        if len(text.encode('utf-8')) > MAX_PATH_BYTES or len(relative.parts) > MAX_PATH_DEPTH:
            raise ChecksumInputError('Checksum input path exceeds byte/depth limit')
        if os.name == 'nt' and any(':' in part for part in relative.parts):
            raise ChecksumInputError('Alternate data streams are not ordinary input files')
        return target

    def relative(self, path: Path) -> str:
        return self.target(path).relative_to(self.root).as_posix()

    def inspect(self, path: Path, *, directory: bool = False,
                missing: bool = False) -> os.stat_result | None:
        target = self.target(path)
        current = Path(target.anchor)
        for index, part in enumerate((None, *target.parts[1:])):
            self.check_time()
            if part is not None:
                current /= part
            try:
                status = current.lstat()
            except FileNotFoundError:
                if missing:
                    return None
                raise
            if _linked(status):
                raise ChecksumInputError(
                    f'Input path must not be a symlink, junction, or reparse point: {current}'
                )
            final = index == len(target.parts) - 1
            if not final or directory:
                if not stat.S_ISDIR(status.st_mode):
                    raise ChecksumInputError(f'Input parent must be an ordinary directory: {current}')
                identity = (status.st_dev, status.st_ino, status.st_mode,
                            getattr(status, 'st_file_attributes', 0))
                previous = self.directories.setdefault(current, identity)
                if identity != previous:
                    raise ChecksumInputError(f'Input parent identity changed: {current}')
            elif not stat.S_ISREG(status.st_mode) or status.st_nlink != 1:
                raise ChecksumInputError(f'Input must be an ordinary non-hardlinked file: {current}')
        return status

    def _consume(self, path: Path, maximum: int, *, retain: bool) -> bytes | str:
        target = self.target(path)
        before = self.inspect(target)
        assert before is not None
        maximum = min(maximum, MAX_SESSION_BYTES - self.total_bytes)
        if maximum < 0 or before.st_size > maximum:
            raise ChecksumInputError(f'Input exceeds byte/aggregate limit: {target}')
        expected = _identity(before)
        previous = self.files.setdefault(target, expected)
        if expected != previous:
            raise ChecksumInputError(f'Input identity changed between reads: {target}')
        digest = hashlib.sha256()
        payload = bytearray()
        count = 0
        with target.open('rb') as stream:
            opened = os.fstat(stream.fileno())
            handle_identity = _identity(opened)
            if _opened_identity(opened) != _opened_identity(before):
                raise ChecksumInputError(f'Input identity changed before reading: {target}')
            while True:
                self.check_time()
                chunk = stream.read(min(CHUNK_BYTES, maximum - count + 1))
                if not chunk:
                    break
                count += len(chunk)
                self.total_bytes += len(chunk)
                if count > maximum:
                    raise ChecksumInputError(f'Input exceeds byte/aggregate limit: {target}')
                if retain:
                    payload.extend(chunk)
                else:
                    digest.update(chunk)
            if _identity(os.fstat(stream.fileno())) != handle_identity:
                raise ChecksumInputError(f'Input changed while reading: {target}')
        after = self.inspect(target)
        assert after is not None
        if count != before.st_size or _identity(after) != expected:
            raise ChecksumInputError(f'Input pathname/content changed while reading: {target}')
        return bytes(payload) if retain else digest.hexdigest()

    def read(self, path: Path, maximum: int) -> bytes:
        result = self._consume(path, maximum, retain=True)
        assert isinstance(result, bytes)
        return result

    def digest(self, path: Path, maximum: int = MAX_EVIDENCE_FILE_BYTES) -> str:
        result = self._consume(path, maximum, retain=False)
        assert isinstance(result, str)
        return result

    def scan(self, directory: Path) -> list[Path]:
        directory = self.target(directory)
        self.inspect(directory, directory=True)
        pending = [directory]
        files: list[Path] = []
        entries = directories = total = 0
        signature: list[tuple[str, tuple[int, ...]]] = []
        while pending:
            current = pending.pop()
            self.inspect(current, directory=True)
            directories += 1
            if directories > MAX_EVIDENCE_DIRECTORIES:
                raise ChecksumInputError('Evidence directory count exceeds limit')
            with os.scandir(current) as iterator:
                for entry in iterator:
                    self.check_time()
                    entries += 1
                    if entries > MAX_EVIDENCE_ENTRIES:
                        raise ChecksumInputError('Evidence filesystem entry count exceeds limit')
                    path = Path(entry.path)
                    status = path.lstat()
                    signature.append((self.relative(path), _identity(status)))
                    if _linked(status):
                        raise ChecksumInputError(f'Evidence path must not be a symlink/reparse point: {path}')
                    if stat.S_ISDIR(status.st_mode):
                        self.inspect(path, directory=True)
                        pending.append(path)
                        if directories + len(pending) > MAX_EVIDENCE_DIRECTORIES:
                            raise ChecksumInputError('Evidence directory count exceeds limit')
                    else:
                        observed = self.inspect(path)
                        assert observed is not None
                        if _identity(observed) != _identity(status):
                            raise ChecksumInputError(f'Evidence file changed during discovery: {path}')
                        previous_file = self.files.setdefault(path, _identity(observed))
                        if previous_file != _identity(observed):
                            raise ChecksumInputError(f'Evidence file identity changed: {path}')
                        files.append(path)
                        if len(files) > MAX_EVIDENCE_FILES:
                            raise ChecksumInputError('Evidence file count exceeds limit')
                        total += status.st_size
                        if status.st_size > MAX_EVIDENCE_FILE_BYTES or total > MAX_EVIDENCE_TOTAL_BYTES:
                            raise ChecksumInputError('Evidence file/aggregate byte limit exceeded')
        observed_tree = tuple(sorted(signature))
        previous = self.trees.setdefault(directory, observed_tree)
        if previous != observed_tree:
            raise ChecksumInputError(f'Evidence tree changed during validation: {directory}')
        return sorted(files, key=self.relative)

    def assert_stable(self) -> None:
        for directory in tuple(self.trees):
            self.scan(directory)
        # Ancestors of the assigned root are intentionally observed too, but
        # are not admitted as caller-selected targets outside that root.
        for directory, identity in self.directories.items():
            self.check_time()
            status = directory.lstat()
            observed = (status.st_dev, status.st_ino, status.st_mode,
                        getattr(status, 'st_file_attributes', 0))
            if _linked(status) or not stat.S_ISDIR(status.st_mode) or observed != identity:
                raise ChecksumInputError(f'Input parent identity changed: {directory}')
        for path, identity in self.files.items():
            status = self.inspect(path)
            assert status is not None
            if _identity(status) != identity:
                raise ChecksumInputError(f'Input identity changed during validation: {path}')

    def write(self, path: Path, payload: bytes) -> None:
        """Guard an explicit output before opening/truncating, not atomic publish."""
        target = self.target(path)
        if len(payload) > MAX_CHECKSUM_BYTES:
            raise ChecksumInputError('Rendered checksum list exceeds byte limit')
        self.assert_stable()
        parent = self.root
        for part in target.parent.relative_to(self.root).parts:
            self.inspect(parent, directory=True)
            parent /= part
            if self.inspect(parent, directory=True, missing=True) is None:
                parent.mkdir()
                created = self.inspect(parent, directory=True)
                assert created is not None
                self._record_output_change(parent, created)
            self.inspect(parent, directory=True)
        before = self.inspect(target, missing=True)
        self.assert_stable()
        with target.open('r+b' if before is not None else 'xb') as stream:
            opened = os.fstat(stream.fileno())
            if (_linked(opened) or not stat.S_ISREG(opened.st_mode)
                    or opened.st_nlink != 1
                    or (before is not None and _opened_identity(before) != _opened_identity(opened))):
                raise ChecksumInputError('Checksum output identity changed before writing')
            self.inspect(target.parent, directory=True)
            stream.write(payload)
            stream.truncate()
            stream.flush()
            final_handle = _opened_identity(os.fstat(stream.fileno()))
        final = self.inspect(target)
        assert final is not None
        if _opened_identity(final) != final_handle or final.st_size != len(payload):
            raise ChecksumInputError('Checksum output pathname changed while writing')
        # An existing checksum can also be an input to a caller's evidence tree.
        # The intentional output change must not retain its old input identity.
        self.files.pop(target, None)
        self._record_output_change(target, final)
        self.assert_stable()

    def _record_output_change(self, target: Path, status: os.stat_result) -> None:
        """Only explicit output/created parents change a prior tree snapshot."""
        for tree, signature in tuple(self.trees.items()):
            if target.is_relative_to(tree):
                expected = dict(signature)
                expected[self.relative(target)] = _identity(status)
                for parent in target.parents:
                    if parent == tree:
                        break
                    status = self.inspect(parent, directory=True)
                    assert status is not None
                    expected[self.relative(parent)] = _identity(status)
                self.trees[tree] = tuple(sorted(expected.items()))

    def git_paths(self) -> bytes:
        self.check_time()
        executable = shutil.which('git')
        if not executable:
            raise ChecksumInputError('Cannot locate Git on the runtime PATH')
        executable_path = Path(executable).absolute()
        if executable_path.is_relative_to(self.root):
            raise ChecksumInputError('Git executable must be an external ordinary file')
        tool_inputs = ChecksumInputs(executable_path.parent)
        tool_inputs.deadline = self.deadline
        # Installed Git for Windows legitimately hardlinks its cmd/bin launchers.
        # The trusted tool is not a checksum evidence input; guard its full
        # ancestor chain and regular leaf without imposing evidence link-count.
        executable_status = executable_path.lstat()
        if _linked(executable_status) or not stat.S_ISREG(executable_status.st_mode):
            raise ChecksumInputError('Git executable must be an external ordinary file')
        command = [str(executable_path), '--no-pager', '--no-replace-objects',
                   '-c', 'core.fsmonitor=false', '-c', 'core.untrackedCache=false',
                   '-c', f'safe.directory={self.root.as_posix()}', '-C', str(self.root),
                   'ls-files', '-z', '--cached']
        environment = {k: v for k, v in os.environ.items() if not k.upper().startswith('GIT_')}
        environment.update(GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_SYSTEM=os.devnull,
                           GIT_CONFIG_NOSYSTEM='1', GIT_NO_LAZY_FETCH='1',
                           GIT_NO_REPLACE_OBJECTS='1', GIT_OPTIONAL_LOCKS='0',
                           GIT_TERMINAL_PROMPT='0', LC_ALL='C')
        deadline = min(self.deadline, time.monotonic() + GIT_TIMEOUT_SECONDS)
        self.check_time()
        with tempfile.TemporaryFile() as output:
            process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=output,
                                       stderr=subprocess.DEVNULL, env=environment)
            try:
                while True:
                    if os.fstat(output.fileno()).st_size > MAX_GIT_BYTES:
                        raise ChecksumInputError('Git index inventory exceeds output byte limit')
                    if time.monotonic() >= deadline:
                        raise ChecksumInputError('Git index inventory timed out')
                    return_code = process.poll()
                    if return_code is not None:
                        break
                    time.sleep(0.01)
                if return_code:
                    raise ChecksumInputError(f'Git index inventory failed with exit {return_code}')
                output.seek(0)
                payload = output.read(MAX_GIT_BYTES + 1)
                if len(payload) > MAX_GIT_BYTES:
                    raise ChecksumInputError('Git index inventory exceeds output byte limit')
                if payload and not payload.endswith(b'\0'):
                    raise ChecksumInputError('Git index inventory is not NUL-terminated')
                tool_inputs.assert_stable()
                if _identity(executable_path.lstat()) != _identity(executable_status):
                    raise ChecksumInputError('Git executable identity changed during enumeration')
                return payload
            finally:
                if process.poll() is None:
                    process.kill()
                process.wait(timeout=5)
