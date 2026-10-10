"""Sequential, leaf-owned file admission for offline validation evidence."""
import hashlib
import os
import stat
from pathlib import Path

if os.name == "nt":
    import ctypes
    from ctypes import wintypes

    class _StreamData(ctypes.Structure):
        _fields_ = [("size", ctypes.c_longlong), ("name", wintypes.WCHAR * 296)]

    _kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    _kernel.FindFirstStreamW.argtypes = (wintypes.LPCWSTR, ctypes.c_int,
                                        ctypes.POINTER(_StreamData), wintypes.DWORD)
    _kernel.FindFirstStreamW.restype = wintypes.HANDLE
    _kernel.FindNextStreamW.argtypes = (wintypes.HANDLE, ctypes.POINTER(_StreamData))
    _kernel.FindNextStreamW.restype = wintypes.BOOL
    _kernel.FindClose.argtypes = (wintypes.HANDLE,)
    _kernel.FindClose.restype = wintypes.BOOL


class ArtifactError(ValueError):
    """An input or proposed artifact violates its admitted boundary."""


def _positive(value):
    if type(value) is not int or value <= 0:
        raise ArtifactError("byte limits must be positive integers")
    return value


def _ordinary(value, directory=False):
    kind = stat.S_ISDIR if directory else stat.S_ISREG
    return (kind(value.st_mode) and not getattr(value, "st_file_attributes", 0) & 0x400
            and (directory or value.st_nlink == 1))


def _name(part):
    device = part.split(".", 1)[0].upper()
    reserved = {"CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$"}
    reserved.update(prefix + number for prefix in ("COM", "LPT")
                    for number in "123456789\u00b9\u00b2\u00b3")
    if (not part or part in (".", "..") or part.endswith((".", " "))
            or device in reserved or any(ord(c) < 32 or c in '<>:"|?*\\/' for c in part)):
        raise ArtifactError("nonordinary or aliased file name")


def _names(path):
    for part in path.parts:
        if part != path.anchor:
            _name(part)


def _streams(path):
    """Reject named $DATA streams without reading their contents (Windows)."""
    if os.name != "nt":
        return
    data = _StreamData()
    handle = _kernel.FindFirstStreamW(str(path), 0, ctypes.byref(data), 0)
    if handle == ctypes.c_void_p(-1).value:
        # 87 documents a filesystem with no stream support; 38 means no streams.
        if ctypes.get_last_error() in (38, 87):
            return
        raise ArtifactError("stream inventory unavailable")
    try:
        if data.name != "::$DATA":
            raise ArtifactError("named data stream rejected")
        if _kernel.FindNextStreamW(handle, ctypes.byref(data)):
            raise ArtifactError("additional data stream rejected")
        if ctypes.get_last_error() != 38:
            raise ArtifactError("stream inventory incomplete")
    finally:
        if not _kernel.FindClose(handle):
            raise ArtifactError("stream inventory handle close failed")


def _chain(path):
    for parent in (path, *path.parents):
        if not _ordinary(parent.lstat(), directory=True):
            raise ArtifactError("nonordinary or reparse ancestor")


def _stamp(value):
    return (value.st_dev, value.st_ino, value.st_mode, value.st_size,
            value.st_mtime_ns, value.st_nlink, getattr(value, "st_file_attributes", 0))


def _shared(value):
    # Windows path stat infers executable permission bits from the filename.
    return (value.st_dev, value.st_ino, stat.S_IFMT(value.st_mode),
            value.st_size, value.st_mtime_ns, value.st_nlink)


def read_file(path, limit, *, capture=True):
    """Return identity and optional bytes; actual reads never exceed limit + 1."""
    limit = _positive(limit)
    if type(capture) is not bool:
        raise ArtifactError("capture must be a boolean")
    path = Path(os.path.abspath(path))
    _names(path)
    _chain(path.parent)
    before = path.lstat()
    if not _ordinary(before) or before.st_size > limit:
        raise ArtifactError("nonordinary or oversized input")
    _streams(path)
    digest, size = hashlib.sha256(), 0
    chunks = [] if capture else None
    with path.open("rb", buffering=0) as source:
        opened = os.fstat(source.fileno())
        if not _ordinary(opened) or _shared(opened) != _shared(before):
            raise ArtifactError("input changed before open")
        while True:
            data = source.read(min(65536, limit + 1 - size))
            size += len(data)
            if size > limit:
                raise ArtifactError("input exceeds actual read bound")
            if not data:
                break
            digest.update(data)
            if capture:
                chunks.append(data)
        if _stamp(os.fstat(source.fileno())) != _stamp(opened):
            raise ArtifactError("input changed during read")
    if _stamp(path.lstat()) != _stamp(before):
        raise ArtifactError("input path changed during read")
    _streams(path)
    return {"bytes": size, "sha256": digest.hexdigest()}, b"".join(chunks) if capture else None


class EvidenceStore:
    """Exclusive writes plus maximum raw-capture reservations in one owned leaf.

    Not a concurrent writer or OS sandbox. Failed/unverifiable accounting blocks
    further writes. Successful partial capture files remain charged and retained.
    """

    def __init__(self, root, limit):
        self.root = Path(os.path.abspath(root))
        self.limit = _positive(limit)
        _names(self.root)
        _chain(self.root)
        _streams(self.root)
        self._sizes, self._held, self._targets = {}, 0, set()
        self._poisoned = False
        pending, count = [self.root], 0
        while pending:
            with os.scandir(pending.pop()) as entries:
                for entry in entries:
                    count += 1
                    if count > 5000:
                        raise ArtifactError("initial leaf entry limit")
                    _name(entry.name)
                    # Windows DirEntry.stat returns zero for st_nlink.
                    value = Path(entry.path).lstat()
                    if not (_ordinary(value, directory=True) or _ordinary(value)):
                        raise ArtifactError("nonordinary initial leaf entry")
                    _streams(Path(entry.path))
                    if _ordinary(value, directory=True):
                        pending.append(Path(entry.path))
                    elif _ordinary(value):
                        self._sizes[Path(entry.path)] = value.st_size
                    else:
                        raise ArtifactError("nonordinary initial leaf entry")
                    if sum(self._sizes.values()) > self.limit:
                        raise ArtifactError("initial leaf exceeds aggregate limit")

    @property
    def used(self):
        return sum(self._sizes.values())

    @property
    def reserved(self):
        return self._held

    def _target(self, name):
        path = Path(name)
        if not path.parts or path.is_absolute() or path.drive or ".." in path.parts:
            raise ArtifactError("artifact name must stay relative to leaf")
        # Validate before pathlib can discard dot segments or separators.
        for part in os.fspath(name).replace("\\", "/").split("/"):
            _name(part)
        _chain(self.root)
        path = self.root / path
        if path in self._targets or os.path.lexists(path):
            raise FileExistsError(path)
        parent = path.parent
        while not parent.exists():
            if os.path.lexists(parent):
                raise ArtifactError("nonordinary artifact ancestor")
            parent = parent.parent
        _chain(parent)
        return path

    def _admit(self, size):
        if self._poisoned or self.used + self._held + size > self.limit:
            raise ArtifactError("aggregate evidence admission rejected")
        self._held += size

    def _prepare(self, paths):
        for path in paths:
            path.parent.mkdir(parents=True, exist_ok=True)
            _chain(path.parent)

    def _finish(self, paths, maximum):
        try:
            sizes = {}
            for path in paths:
                if os.path.lexists(path):
                    info, _ = read_file(path, max(1, maximum[path]), capture=False)
                    if info["bytes"] > maximum[path]:
                        raise ArtifactError("retained artifact exceeds reservation")
                    sizes[path] = info["bytes"]
            self._sizes.update(sizes)
        except BaseException:
            self._poisoned = True
            raise
        else:
            self._held -= sum(maximum.values())
            self._targets.difference_update(paths)

    def write(self, name, data, limit=1048576):
        limit = _positive(limit)
        if type(data) is not bytes or len(data) > limit:
            raise ArtifactError("artifact bytes exceed individual limit")
        path = self._target(name)
        self._admit(len(data))
        self._targets.add(path)
        try:
            self._prepare((path,))
            with path.open("xb") as output:
                if output.write(data) != len(data):
                    raise OSError("short artifact write")
        finally:
            self._finish((path,), {path: len(data)})
        return path

    def reserve_capture(self, stdout, stderr, limit):
        """Admit both maximum streams before the external observer opens them."""
        limit = _positive(limit)
        paths = (self._target(stdout), self._target(stderr))
        if paths[0] == paths[1]:
            raise ArtifactError("capture outputs must be distinct")
        self._admit(2 * limit)
        self._targets.update(paths)
        try:
            self._prepare(paths)
        except BaseException:
            self._finish(paths, dict.fromkeys(paths, limit))
            raise
        return CaptureReservation(self, paths, limit)


class CaptureReservation:
    def __init__(self, store, paths, limit):
        self._store, self.paths, self.limit = store, paths, limit
        self._closed = False

    def close(self, *, quiescent):
        """Release unused bytes only after the caller proves both writers ended."""
        if type(quiescent) is not bool:
            raise ArtifactError("writer quiescence must be explicit")
        if not quiescent:
            return
        if not self._closed:
            self._closed = True
            self._store._finish(self.paths, dict.fromkeys(self.paths, self.limit))

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close(quiescent=False)
