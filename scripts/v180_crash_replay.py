"""Bounded, offline X01 crash states (W368 sections 1--3).

Trace wire format (W369): zero-based seq/thread/store/op/path/result; write additionally carries
offset/length/sha256. Paths are world-relative; open names the pending file or
target. blobs/<sha256> contains write bytes; pre/<open-seq> contains the old
target, or pre/<open-seq>.absent is an empty absence marker. No server runs here.
"""
from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
import itertools
import json
from pathlib import Path, PurePosixPath
import shutil
from typing import Callable

MODEL = "W368-X01-R1-R8-v1"
PENDING = ".arce-pending"
MAX_OPS = 4096
MAX_BYTES = 256 * 1024 * 1024
MAX_STATES = 16
SEAM_NAMES = tuple("advancedrocketrycommunity_" + name + ".dat" for name in (
    "celestial", "rocket_transactions", "rocket_transfers", "stations", "satellite_missions",
    "endgame", "resource_escrow", "classic_progression", "rocket_rejoins", "bay_receipts"))

# Each entry retains the crash points even when short/zero payloads deduplicate.
CRASH_POINTS = {
    "S-OLD": ["before-open", "open:entry-lost", "write:entry-lost", "force:entry-lost",
              "close:entry-lost", "move:undone-entry-lost"],
    "S-EMPTY": ["open:empty", "write:all-lost"],
    "S-PREFIX": ["write:prefix"],
    "S-TORN": ["write:torn"],
    "S-STAGED": ["write:all-kept", "force:kept", "close:kept", "move:undone-staged"],
    "S-NEW": ["move:kept", "directory-flush:durable"],
}
UNCOVERED = [
    "Real OS page-cache ordering and filesystem journal recovery",
    "Disk/controller firmware and a real power cut",
    "Region/entity files and the escrow barrier's stores, force and readback (R7)",
    "Plain SavedData, station ordinary saves, planetary bindings and migration backups",
    "Player files, level.dat and configuration",
    "Intermediate late-loss generations and cross products of unfinished writes",
    "Simultaneous X02 faults in two stores",
]


class ReplayError(ValueError):
    pass


class OverBound(ReplayError):
    pass


def sha(data: bytes | None) -> str | None:
    return hashlib.sha256(data).hexdigest() if data is not None else None


def safe_path(root: Path, name: str, directory: bool = False) -> Path:
    """Never follow a trace/world junction, symlink, drive name or parent escape."""
    parts = PurePosixPath(name).parts
    if root.is_symlink() or (hasattr(root, "is_junction") and root.is_junction()):
        raise ReplayError("Linked input root")
    if not parts or "\\" in name or ":" in name or name.startswith("/") or any(p in ("..", ".") for p in parts):
        raise ReplayError(f"Unsafe relative path: {name!r}")
    path = root
    for part in parts:
        path = path / part
        if path.is_symlink() or (hasattr(path, "is_junction") and path.is_junction()):
            raise ReplayError(f"Linked path: {path}")
    if path.exists() and not (path.is_dir() if directory else path.is_file()):
        raise ReplayError(f"Not a regular file: {path}")
    return path


def read_optional(root: Path, name: str) -> bytes | None:
    path = safe_path(root, name)
    if path.exists() and path.stat().st_size > MAX_BYTES:
        raise ReplayError(f"Oversized input: {path}")
    return path.read_bytes() if path.exists() else None


@dataclass
class Write:
    seq: int
    thread: str
    store: str
    target: str
    old: bytes | None
    data: bytes = b""
    phase: str = "open"
    complete: bool = False


def read_trace(trace: Path, kill: Path) -> list[Write]:
    ops = read_optional(trace, "ops.jsonl")
    if not ops or not ops.endswith(b"\n"):
        raise ReplayError("Missing, empty or partial ops.jsonl")
    if ops.count(b"\n") > MAX_OPS:
        raise OverBound("OVER_BOUND: trace exceeds 4096 operations")
    lines = ops.splitlines()
    writes, active, targets, blobs = [], {}, {}, {}
    stored_bytes = 0
    for expected, line in enumerate(lines):
        try:
            op = json.loads(line)
            seq, thread, store, kind, path, result = (op[k] for k in ("seq", "thread", "store", "op", "path", "result"))
            if type(seq) is not int or seq != expected or not all(isinstance(v, str) and v for v in (thread, kind, path)):
                raise ReplayError("Invalid sequence or operation fields")
            if result != "ok":
                raise ReplayError(f"X01 requires successful recorded operations: {result}")
            safe_path(kill, path, directory=kind == "forceDirectory")
            if kind == "forceDirectory":
                if store is not None:
                    raise ReplayError("Directory force must have a null store (W369)")
                candidates = [w for w in writes if w.thread == thread and not w.complete
                              and w.phase == "move" and str(PurePosixPath(w.target).parent) == path]
                if len(candidates) != 1:
                    raise ReplayError("Ambiguous directory force")
                candidates[0].complete = True; candidates[0].phase = kind
                continue
            if not isinstance(store, str) or not store:
                raise ReplayError("Missing store")
            key = (thread, store)
            if kind == "open":
                target = path.removesuffix(PENDING)
                if op["target"] != target:
                    raise ReplayError("Open target differs from its pending file")
                if key in active and not active[key].complete:
                    raise ReplayError("Overlapping writes on a thread/store")
                previous = targets.get(target)
                if previous is not None and not previous.complete:
                    raise ReplayError("Overlapping writes to one target")
                old = read_optional(trace, f"pre/{seq}")
                absent = read_optional(trace, f"pre/{seq}.absent")
                if (old is None) == (absent is None) or absent not in (None, b""):
                    raise ReplayError("Each open needs exactly one pre-image or empty absence marker")
                if old is None:
                    if op["pre"] != "absent":
                        raise ReplayError("Absence marker differs from trace")
                elif op["pre"] != sha(old) or type(op["preLength"]) is not int or op["preLength"] != len(old):
                    raise ReplayError("Damaged pre-image")
                stored_bytes += len(old) if old is not None else 0
                if stored_bytes > MAX_BYTES:
                    raise OverBound("OVER_BOUND: trace exceeds 256 MiB")
                if previous is not None and old != previous.data:
                    raise ReplayError("Pre-image does not match the previous generation")
                w = Write(seq, thread, store, target, old)
                writes.append(w); active[key] = w; targets[target] = w
                continue
            w = active.get(key)
            # Directory flush paths name the parent rather than the pending/target.
            if w is None or w.complete:
                raise ReplayError(f"Operation without an open: {kind}")
            allowed = {w.target, w.target + PENDING} if kind != "forceDirectory" else {str(PurePosixPath(w.target).parent)}
            if path not in allowed:
                raise ReplayError("Operation changed its target")
            if kind == "write" and w.phase in ("open", "write"):
                offset, length, digest = op["offset"], op["length"], op["sha256"]
                if type(offset) is not int or type(length) is not int or offset != len(w.data) or length < 0:
                    raise ReplayError("Non-contiguous or empty write")
                if not isinstance(digest, str) or len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
                    raise ReplayError("Invalid blob SHA-256")
                if digest not in blobs:
                    blob = read_optional(trace, "blobs/" + digest)
                    if blob is None or sha(blob) != digest:
                        raise ReplayError("Missing or damaged blob")
                    blobs[digest] = blob
                    stored_bytes += len(blob)
                    if stored_bytes > MAX_BYTES:
                        raise OverBound("OVER_BOUND: trace exceeds 256 MiB of blobs")
                blob = blobs[digest]
                if len(blob) != length or len(w.data) + length > MAX_BYTES:
                    raise ReplayError("Blob length mismatch or oversized candidate")
                w.data += blob
            elif kind == "force" and w.phase == "write":
                pass
            elif kind == "close" and w.phase == "force":
                pass
            elif kind == "readStaged" and w.phase == "close":
                pass
            elif kind == "move" and w.phase == "readStaged":
                if op["target"] != w.target:
                    raise ReplayError("Move changed its target")
            else:
                raise ReplayError(f"Invalid operation order: {w.phase} -> {kind}")
            w.phase = kind
        except (KeyError, TypeError, json.JSONDecodeError) as error:
            raise ReplayError(f"Damaged trace line {expected}: {error}") from error
    for w in targets.values():
        actual = read_optional(kill, w.target)
        if actual != (w.data if w.phase in ("move", "forceDirectory") else w.old):
            raise ReplayError("Kill image target differs from the recorded operations")
        pending = read_optional(kill, w.target + PENDING)
        expected_pending = None if w.phase in ("move", "forceDirectory") else w.data
        if pending != expected_pending:
            raise ReplayError("Kill image staging file differs from the recorded operations")
    if not writes:
        raise ReplayError("Trace has no writes")
    return writes


@dataclass
class State:
    id: str
    changes: dict[str, bytes | None]
    derivations: list[dict] = field(default_factory=list)
    files: dict = field(default_factory=dict)


def plan(trace: Path, kill: Path, late_stores: set[str] | None = None, window_start: int = 0) -> list[State]:
    """Pure derivation; deduplicate by every seam file, not just the edited pair."""
    writes = read_trace(trace, kill)
    names = {p for w in writes for p in (w.target, w.target + PENDING)}
    # Roots loaded before the recorder was installed still belong in the seam
    # hash table, even when this recording never wrote them.
    for name in SEAM_NAMES:
        target = "data/" + name
        if safe_path(kill, target).exists() or safe_path(kill, target + PENDING).exists():
            names.update((target, target + PENDING))
    names = sorted(names)
    baseline = {name: read_optional(kill, name) for name in names}
    states, seen = [], {}

    def add(kind, changes, derivation):
        content = baseline | changes
        hashes = {p: sha(v) for p, v in content.items()}
        key = tuple(hashes.items())
        if key in seen:
            seen[key].derivations.append(derivation)
            return
        state = State(f"{len(states) + 1:02d}-{kind}", changes, [derivation], hashes)
        seen[key] = state; states.append(state)

    for w in writes:
        if w.complete:
            continue
        # A killed write before the full candidate was recorded cannot be replayed faithfully.
        if w.phase not in ("force", "close", "readStaged", "move"):
            raise ReplayError("Incomplete candidate at kill; discard this recording")
        for kind, target, pending in (
            ("S-OLD", w.old, None), ("S-EMPTY", w.old, b""),
            ("S-PREFIX", w.old, w.data[:len(w.data) // 2]),
            ("S-TORN", w.old, w.data[:-4096] + bytes(min(4096, len(w.data)))),
            ("S-STAGED", w.old, w.data), ("S-NEW", w.data, None),
        ):
            add(kind, {w.target: target, w.target + PENDING: pending},
                dict(kind=kind, open_seq=w.seq, store=w.store, target=w.target, crash_points=CRASH_POINTS[kind]))
    earliest, completed = {}, set()
    for w in writes:
        if w.seq < window_start:
            continue
        earliest.setdefault(w.target, w)
        if w.complete and (late_stores is None or w.store in late_stores):
            completed.add(w.target)
    if len(completed) > 3:
        raise OverBound("OVER_BOUND: more than seven late-loss combinations")
    for size in range(1, len(completed) + 1):
        for group in itertools.combinations(sorted(completed), size):
            changes = {p: earliest[p].old for p in group}
            changes.update({p + PENDING: None for p in group})
            add("LATE-LOSS", changes, dict(kind="LATE-LOSS", rule="R6", targets=list(group),
                 open_seqs=[earliest[p].seq for p in group], window_start=window_start, intermediate_generations=False))
    if len(states) > MAX_STATES:
        raise OverBound(f"OVER_BOUND: {len(states)} distinct states exceed {MAX_STATES}")
    if not states:
        raise ReplayError("No crash states derived")
    return states


def write_table(path: Path, states: list[State], case: str, outcomes: dict | Callable) -> None:
    value = dict(model=MODEL, case=case, status="PLANNED", uncovered=UNCOVERED,
                 states=[dict(id=s.id, derivations=s.derivations, files=s.files,
                              allowed_outcomes=outcomes(s) if callable(outcomes) else outcomes) for s in states])
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def materialize(kill: Path, destination: Path, state: State) -> None:
    """Copy one image at a time. Caller owns deletion; never mutate the kill image."""
    if destination.exists() or destination.resolve() == kill.resolve() or kill.resolve() in destination.resolve().parents:
        raise ReplayError("Occupied or overlapping replay destination")
    for path in [kill, *kill.rglob("*")]:
        if path.is_symlink() or (hasattr(path, "is_junction") and path.is_junction()):
            raise ReplayError("Linked kill image")
    shutil.copytree(kill, destination)
    for name, content in state.changes.items():
        path = safe_path(destination, name)
        if content is None:
            path.unlink(missing_ok=True)
        else:
            path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(content)
    if any(sha(read_optional(destination, name)) != digest for name, digest in state.files.items()):
        raise ReplayError("Replay file hash mismatch")


def trace_command(args, command: list[str], restart: int) -> None:
    trace = getattr(args, "storage_trace", None)
    if trace is not None and restart == 0:
        command.insert(1, "-Dadvancedrocketrycommunity.storageTrace=" + str(trace.resolve()))
    if hasattr(args, "replay_commands"):
        args.replay_commands.append(dict(restart=restart, command=command))


def replay_starts(args, row: dict, world: Path, evidence: Path, case: str):
    """Opt-in Python runner API; the ordinary three-start flow is untouched."""
    if getattr(args, "replay_world", None) is None:
        return range(3)
    materialize(args.replay_world, world, args.replay_state)
    context = args.replay_context
    row["starts"].append(context["starts"][0])
    if "raw0" in context:
        (evidence / f"{case}-0-raw.json").write_text(json.dumps(context["raw0"]), encoding="utf-8")
    return (1, 2)


def capture_replay(args, world: Path, row: dict, evidence: Path, case: str) -> bool:
    capture = getattr(args, "replay_capture", None)
    if capture is None:
        return False
    raw = evidence / f"{case}-0-raw.json"
    context = dict(row)
    if raw.is_file():
        context["raw0"] = json.loads(raw.read_text(encoding="utf-8"))
    capture(world, context)
    return True
