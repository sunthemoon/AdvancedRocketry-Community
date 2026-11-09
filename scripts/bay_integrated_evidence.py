"""Test-side, bounded raw authority capture for the pinned bay native runners.

W331 adds the candidate pin: every matrix runner takes the candidate JAR, its SHA-256 and its production source commit,
checks them before Java starts and records them. ``python -B scripts/bay_integrated_evidence.py pin ...`` records the
same check for any driver invocation.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid
import zlib

import loader_cut_nbt as nbt

# The production-equality paths W325 checked at every invocation (W325.md, "Candidate and evidence inventory").
PRODUCTION_PATHS = ("src/main", "src/generated", "build.gradle", "settings.gradle", "gradle.properties")
REPOSITORY = Path(__file__).resolve().parents[1]


class CandidateError(ValueError):
    """The candidate JAR, its hash or its source commit does not match the arguments."""


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def candidate_pin(jar: Path, jar_sha256: str, candidate_commit: str, repository: Path = REPOSITORY,
                  run=subprocess.run) -> dict:
    """Refuse unless the JAR has the given SHA-256 and the harness HEAD has the candidate's production source.

    The diff is W325's production-equality check, now against the commit the caller names instead of a fixed C1 source.
    """
    if not re.fullmatch(r"[0-9a-f]{64}", jar_sha256 or ""):
        raise CandidateError(f"--jar-sha256 must be 64 lowercase hex digits: {jar_sha256!r}")
    if not re.fullmatch(r"[0-9a-f]{40}", candidate_commit or ""):
        raise CandidateError(f"--candidate-commit must be a full 40-digit commit: {candidate_commit!r}")
    jar = Path(jar)
    if not jar.is_file() or jar.is_symlink():
        raise CandidateError(f"Candidate JAR is missing or linked: {jar}")
    actual = file_sha256(jar)
    if actual != jar_sha256:
        raise CandidateError(f"Candidate JAR SHA-256 {actual} differs from --jar-sha256 {jar_sha256}")
    command = ["git", "diff", "--quiet", candidate_commit, "HEAD", "--", *PRODUCTION_PATHS]
    diff = run(command, cwd=repository, capture_output=True, text=True)
    if diff.returncode != 0:
        raise CandidateError(f"Production source differs from candidate {candidate_commit} (git diff exit "
                             f"{diff.returncode}): {(diff.stderr or '').strip()[:200]}")
    head = run(["git", "rev-parse", "HEAD"], cwd=repository, capture_output=True, text=True)
    if head.returncode != 0 or not re.fullmatch(r"[0-9a-f]{40}", head.stdout.strip()):
        raise CandidateError("The harness HEAD cannot be read")
    return dict(jar=str(jar), jar_sha256=actual, candidate_commit=candidate_commit, harness_commit=head.stdout.strip(),
                production_diff=command, production_diff_exit=diff.returncode)


def recheck_candidate(pin: dict) -> None:
    """At the end of an invocation: the same JAR bytes are still at the recorded path."""
    if file_sha256(Path(pin["jar"])) != pin["jar_sha256"]:
        raise CandidateError(f"Candidate JAR changed during the invocation: {pin['jar']}")


def unexpected_errors(lines: list[str], registry_injection: bool = False, crash_injection: bool = False,
                      journal_injection: bool = False) -> list[str]:
    """Audit the first start too; selected failure tests do not permit unrelated errors.

    W331's ``journal_injection`` allows only the transaction journal's one ERROR per failed or unknown write streak
    (R-040), which its selected transaction-writer FAIL or UNKNOWN cut produces.
    """
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
        if journal_injection and re.search(
                r"ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=(NOT_COMMITTED|FAILED|UNKNOWN) sequence=\d+ acknowledged=(none|\d+)$",
                line.rstrip()):
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


# W331 raw oracles: bounded reads of one stopped-world SavedData file.

def _payload(path: Path) -> bytes:
    raw = Path(path).read_bytes()
    if len(raw) > nbt.MAX_COMPRESSED:
        raise ValueError(f"SavedData exceeds its bound: {path}")
    decoder = zlib.decompressobj(16 + zlib.MAX_WBITS)
    payload = decoder.decompress(raw, nbt.MAX_DECODED + 1)
    if len(payload) > nbt.MAX_DECODED or not decoder.eof:
        raise ValueError(f"SavedData payload exceeds its bound or is truncated: {path}")
    return payload


def raw_names(path: Path, identity: str) -> bool:
    """Whether a gzip SavedData file names one UUID, as Minecraft's int-array UUID or as its canonical string."""
    if not Path(path).is_file():
        return False
    payload = _payload(path)
    return uuid.UUID(identity).bytes in payload or str(uuid.UUID(identity)).encode("ascii") in payload


def registry_satellites(path: Path) -> list[str]:
    """The satellite IDs of a stopped world's satellite registry (its ``satellites`` list), in file order."""
    if not Path(path).is_file():
        return []
    _, root = nbt.read(_payload(path))
    return [_uuid(entry.value["satellite_id"]) for entry in root.value["data"].value["satellites"].value[1]]


def _uuid(tag) -> str:
    if tag.type != nbt.INTS or len(tag.value) != 4:
        raise ValueError("Not an int-array UUID")
    return str(uuid.UUID(bytes=b"".join(int(part).to_bytes(4, "big", signed=True) for part in tag.value)))


def bay_receipts(path: Path) -> list[dict]:
    """The receipts of a stopped world's receipt file, in file order; [] when the file is absent."""
    if not Path(path).is_file():
        return []
    _, root = nbt.read(_payload(path))
    data = root.value["data"].value
    rows = []
    for receipt in data["receipts"].value[1]:
        fields = receipt.value
        rows.append(dict(op=_uuid(fields["op"]), rocket=_uuid(fields["rocket"]), satellite=_uuid(fields["satellite"]),
                         phase=fields["phase"].value.decode("ascii"), reason=fields["reason"].value.decode("ascii"),
                         src_before=bytes(fields["src_before"].value).hex(), dst_before=bytes(fields["dst_before"].value).hex(),
                         package_sig=bytes(fields["package_sig"].value).hex(), released=bool(fields["released"].value)))
    return rows


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="W331 candidate pin for one bay matrix invocation")
    commands = parser.add_subparsers(dest="command", required=True)
    pin = commands.add_parser("pin", help="Check and record the candidate JAR, its SHA-256 and its source commit")
    pin.add_argument("--jar", type=Path, required=True)
    pin.add_argument("--jar-sha256", required=True)
    pin.add_argument("--candidate-commit", required=True)
    pin.add_argument("--output", type=Path, required=True, help="A new JSON file for this invocation's record")
    args = parser.parse_args(argv)
    if args.output.exists():
        print(f"FAIL: {args.output} already exists", flush=True)
        return 1
    try:
        result = candidate_pin(args.jar, args.jar_sha256, args.candidate_commit)
    except CandidateError as failure:
        print(f"FAIL: {failure}", flush=True)
        return 1
    result.update(checked=datetime.now(timezone.utc).isoformat(), argv=list(sys.argv if argv is None else argv))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(f"PASS: {result['jar_sha256']} at {result['candidate_commit']}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
