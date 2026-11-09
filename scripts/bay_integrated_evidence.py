"""Test-side, bounded raw authority capture for the pinned bay native runners."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import shutil
import uuid

import loader_cut_nbt as nbt


def unexpected_errors(lines: list[str], registry_injection: bool = False, crash_injection: bool = False) -> list[str]:
    """Audit the first start too; selected failure tests do not permit unrelated errors."""
    injected = crash_injection and any("W129a injected tick exception" in line for line in lines)
    crash_messages = ("Exception caught during firing event: W129a injected tick exception",
                      "Encountered an unexpected exception", "Preparing crash report with UUID ",
                      "This crash report has been saved to: ")
    unexpected = []
    for line in lines:
        if "/ERROR]" not in line and "/FATAL]" not in line:
            continue
        if registry_injection and re.search(
                r"ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED outcome=(FAILED|UNKNOWN) sequence=\d+ epoch=\d+ durable=\d+; retained dirty state", line):
            continue
        if injected and any(message in line for message in crash_messages):
            continue
        unexpected.append(line.rstrip())
    return unexpected


def snapshot_world(world: Path, destination: Path, max_bytes: int = 8 * 1024**2) -> dict:
    """Keep journal/registry/player/entity bytes after stop, not a disposable server copy."""
    if destination.exists():
        raise FileExistsError(destination)
    files = []
    for path in sorted(world.rglob("*")):
        if path.is_symlink() or getattr(path, "is_junction", lambda: False)():
            raise ValueError(f"Linked world entry: {path}")
        if not path.is_file():
            continue
        relative = path.relative_to(world)
        if (relative.parts[0] == "data" or relative.name in ("level.dat", "level.dat_old")
                or "entities" in relative.parts and path.suffix == ".mca"
                or relative.parts[0] == "playerdata" and path.suffix == ".dat"):
            files.append((path, relative, path.stat().st_size))
    size = sum(row[2] for row in files)
    if size > max_bytes:
        raise ValueError(f"Raw authority snapshot exceeds {max_bytes} bytes: {size}")
    destination.mkdir(parents=True)
    rows = []
    for source, relative, length in files:
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        rows.append(dict(path=relative.as_posix(), bytes=length,
                         sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
    result = dict(bytes=size, files=rows)
    (destination / "MANIFEST.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    return result


def observed_ticks(lines: list[str]) -> dict:
    """Preserve explicit tick observations without converting wall time into server ticks."""
    markers = []
    for line in lines:
        if "ARCE_" not in line:
            continue
        ticks = re.findall(r"\b(?:tick|ticks|deadline|firstTick)=(-?\d+)\b", line)
        if ticks:
            markers.append(dict(ticks=[int(value) for value in ticks], line=line.rstrip()))
    return dict(d_step_ticks="NOT_OBSERVABLE", tick_markers=markers,
                reason="C1 claim/proof transition logs do not include step start/end ticks; wall time is not ticks.")


def case_observations(directory: Path, case: str) -> dict:
    return {path.name: observed_ticks(path.read_text(encoding="utf-8", errors="replace").splitlines())
            for path in sorted(directory.glob(f"{case}-*.log"))}


def seed_capacity_world(world: Path, count: int, owner: str | None = None,
                        phase: str = "INTENT", released: bool = False) -> dict:
    """Create bounded inert historical receipts for pending or independent retained limits.

    C1's older live fill command always uses one owner and therefore cannot fill 64 after W238.
    This fixture exercises loading and pending refusal, not the admission that created these inert rows.
    """
    if phase not in ("INTENT", "REPAIR", "REJECTED"):
        raise ValueError("Unsupported capacity fixture phase")
    pending = phase != "REJECTED" or released
    owner_cap = 16 if pending else 128
    if not 1 <= count <= (64 if pending else 4096) or owner is not None and count > owner_cap:
        raise ValueError("Capacity fixture exceeds its unchanged pending or retained caps")
    file = world / "data/advancedrocketrycommunity_bay_receipts.dat"
    if file.exists():
        raise FileExistsError(file)
    namespace = uuid.uuid4()
    def identity(kind: str, index: int):
        return nbt.uuid_tag(uuid.uuid5(namespace, f"{kind}-{index}"))
    def string(value: str):
        return nbt.Tag(nbt.STRING, value.encode("ascii"))
    hashed = hashlib.sha256(b"W325 inert capacity fixture").digest()
    receipts = []
    for index in range(count):
        receipts.append(nbt.Tag(nbt.COMPOUND, dict(
            schema_version=nbt.Tag(nbt.INT, 1), op=identity("op", index), seq=nbt.Tag(nbt.LONG, index + 1),
            rocket=identity("rocket", index // 8), bay=nbt.Tag(nbt.INTS, [0, index % 8, 0]),
            satellite=identity("satellite", index), owner=nbt.uuid_tag(uuid.UUID(owner)) if owner else identity("owner", index // owner_cap),
            definition=string("advancedrocketrycommunity:data_satellite"), kind=string("data"),
            orbit=string("advancedrocketrycommunity:earth"), package_sig=nbt.Tag(nbt.BYTES, hashed),
            transfer=identity("transfer", index // 8), src_before=nbt.Tag(nbt.BYTES, hashed), dst_before=nbt.Tag(nbt.BYTES, hashed),
            phase=string(phase), reason=string("TRANSFER_AUTHORITY_CONFLICT" if phase == "REPAIR" else "NONE"),
            preexisting=nbt.Tag(nbt.BYTE, 0), released=nbt.Tag(nbt.BYTE, int(released)),
            registry_epoch=nbt.Tag(nbt.LONG, 0), created_tick=nbt.Tag(nbt.LONG, 0), updated_tick=nbt.Tag(nbt.LONG, 0),
            attempts=nbt.Tag(nbt.INT, 0))))
    payload = nbt.Tag(nbt.COMPOUND, dict(schema_version=nbt.Tag(nbt.INT, 1),
        format_epoch=string("v1.8.0-bay-receipts"), store_id=nbt.uuid_tag(namespace),
        issued_through=nbt.Tag(nbt.LONG, count), writer_generation=nbt.Tag(nbt.LONG, 1),
        receipts=nbt.Tag(nbt.LIST, (nbt.COMPOUND, receipts))))
    root = nbt.Tag(nbt.COMPOUND, dict(data=payload, DataVersion=nbt.Tag(nbt.INT, 3465)))
    file.parent.mkdir(parents=True, exist_ok=True)
    nbt.write_gzip(file, b"", root)
    if nbt.read_gzip(file)[1] != root:
        raise ValueError("Capacity fixture failed typed readback")
    return dict(count=count, pending=count if pending else 0, phase=phase, released=released,
                owner=owner, owners=1 if owner else (count + owner_cap - 1) // owner_cap,
                rockets=(count + 7) // 8, sha256=hashlib.sha256(file.read_bytes()).hexdigest())
