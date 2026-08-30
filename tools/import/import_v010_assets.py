#!/usr/bin/env python3
"""Generate or verify the provenance-bound v0.1.0 minimal asset batch."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path, PurePosixPath
from typing import Any

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from tools.audit.audit_upstream import load_tracked_files  # noqa: E402


DEFAULT_PLAN = Path("tools/import/v010-content-plan.json")
DEFAULT_RECORD = Path("docs/provenance/v0.1.0-minimal-content.json")
ALLOWED_TARGET_PREFIXES = (
    "src/main/resources/assets/advancedrocketrycommunity/",
    "src/generated/resources/assets/advancedrocketrycommunity/",
)
MAX_PLAN_BYTES = 256 * 1024
MAX_ENTRIES = 64


def canonical_json(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def pretty_json(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode("utf-8")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def safe_relative(raw: str) -> str:
    path = PurePosixPath(raw)
    if (
        not raw
        or len(raw.encode("utf-8", errors="strict")) > 4096
        or path.is_absolute()
        or path.as_posix() != raw
        or "\\" in raw
        or ":" in raw
        or any(part in {"", ".", ".."} for part in path.parts)
        or any(ord(character) < 32 or ord(character) == 127 for character in raw)
    ):
        raise ValueError(f"unsafe repository-relative path: {raw!r}")
    return raw


def load_json(path: Path, maximum: int = MAX_PLAN_BYTES) -> Any:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"JSON input is not a regular file: {path}")
    data = path.read_bytes()
    if len(data) > maximum:
        raise ValueError(f"JSON input exceeds {maximum} bytes: {path}")
    return json.loads(data.decode("utf-8", errors="strict"))


def validate_plan(plan: object) -> dict[str, Any]:
    if not isinstance(plan, dict):
        raise ValueError("import plan must be a JSON object")
    required = {
        "schema_version",
        "batch_id",
        "target_version",
        "source_repository",
        "source_branch",
        "source_commit",
        "license",
        "copyright_notice",
        "entries",
    }
    if set(plan) != required:
        raise ValueError("import plan keys do not match the schema")
    if plan["schema_version"] != 1 or plan["target_version"] != "v0.1.0":
        raise ValueError("import plan schema/version mismatch")
    if plan["source_repository"] != "https://github.com/Advanced-Rocketry/AdvancedRocketry":
        raise ValueError("import plan uses an unapproved source repository")
    if plan["source_branch"] != "1.12" or plan["license"] != "MIT":
        raise ValueError("import plan branch/license mismatch")
    if plan["copyright_notice"] != "Copyright (c) 2017":
        raise ValueError("import plan does not preserve the upstream notice")
    if not re.fullmatch(r"[0-9a-f]{40}", plan["source_commit"]):
        raise ValueError("import plan source commit must be a full lowercase SHA-1")
    entries = plan["entries"]
    if not isinstance(entries, list) or not 1 <= len(entries) <= MAX_ENTRIES:
        raise ValueError("import plan entry count is outside bounds")
    sources: set[str] = set()
    targets: set[str] = set()
    target_casefold: set[str] = set()
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise ValueError(f"entry {index} is not an object")
        base_keys = {"source_path", "target_path", "mode", "transformation"}
        if entry.get("mode") == "lang":
            expected = base_keys | {"translations", "additions"}
        elif entry.get("mode") == "copy":
            expected = base_keys
        else:
            raise ValueError(f"entry {index} has unsupported mode")
        if set(entry) != expected:
            raise ValueError(f"entry {index} keys do not match mode {entry.get('mode')}")
        source = safe_relative(entry["source_path"])
        target = safe_relative(entry["target_path"])
        if not source.startswith("src/main/resources/assets/advancedrocketry/"):
            raise ValueError(f"entry {index} source is outside the approved namespace")
        if not target.startswith(ALLOWED_TARGET_PREFIXES):
            raise ValueError(f"entry {index} target is outside the project asset roots")
        if source in sources or target in targets or target.casefold() in target_casefold:
            raise ValueError(f"entry {index} duplicates a source or target")
        sources.add(source)
        targets.add(target)
        target_casefold.add(target.casefold())
        transformations = entry["transformation"]
        if not isinstance(transformations, list) or not transformations or not all(
            isinstance(value, str) and value.strip() for value in transformations
        ):
            raise ValueError(f"entry {index} transformation list is invalid")
        if entry["mode"] == "lang":
            for field in ("translations", "additions"):
                mapping = entry[field]
                if not isinstance(mapping, dict) or not mapping or not all(
                    isinstance(key, str) and key and isinstance(value, str) and value
                    for key, value in mapping.items()
                ):
                    raise ValueError(f"entry {index} {field} mapping is invalid")
    return plan


def parse_lang(data: bytes) -> dict[str, str]:
    text = data.decode("utf-8", errors="strict").replace("\r\n", "\n").replace("\r", "\n")
    values: dict[str, str] = {}
    for line_number, raw in enumerate(text.splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"legacy lang line {line_number} has no '=' separator")
        key, value = line.split("=", 1)
        if not key or key in values:
            raise ValueError(f"legacy lang line {line_number} has an empty/duplicate key")
        values[key] = value
    return values


def render_target(entry: dict[str, Any], source: bytes) -> bytes:
    if entry["mode"] == "copy":
        return source
    source_values = parse_lang(source)
    output: dict[str, str] = {}
    for old_key, new_key in sorted(entry["translations"].items()):
        if old_key not in source_values:
            raise ValueError(f"legacy language key is missing: {old_key}")
        if new_key in output:
            raise ValueError(f"duplicate target language key: {new_key}")
        output[new_key] = source_values[old_key]
    for key, value in sorted(entry["additions"].items()):
        if key in output:
            raise ValueError(f"new language key collides with an extracted key: {key}")
        output[key] = value
    # Forge's LanguageProvider uses Gson/DataProvider and intentionally writes
    # the JSON object without a trailing newline. Match those exact bytes so
    # the import transform and DataGen mutually verify one canonical target.
    return json.dumps(output, ensure_ascii=False, sort_keys=True, indent=2).encode("utf-8")


def build_core(plan: dict[str, Any], source_files: dict[str, bytes]) -> tuple[dict[str, Any], dict[str, bytes]]:
    rendered: dict[str, bytes] = {}
    entries: list[dict[str, Any]] = []
    for entry in sorted(plan["entries"], key=lambda value: value["target_path"]):
        source_path = entry["source_path"]
        if source_path not in source_files:
            raise ValueError(f"planned source is not tracked at the exact commit: {source_path}")
        source = source_files[source_path]
        target = render_target(entry, source)
        rendered[entry["target_path"]] = target
        entries.append(
            {
                "target_path": entry["target_path"],
                "status": "UPSTREAM_AR_MIT",
                "source_repository": plan["source_repository"],
                "source_branch": plan["source_branch"],
                "source_commit": plan["source_commit"],
                "source_path": source_path,
                "source_sha256": sha256(source),
                "target_sha256": sha256(target),
                "license": plan["license"],
                "copyright_notice": plan["copyright_notice"],
                "transformation": entry["transformation"],
            }
        )
    core = {
        "schema_version": 1,
        "batch_id": plan["batch_id"],
        "target_version": plan["target_version"],
        "source_repository": plan["source_repository"],
        "source_branch": plan["source_branch"],
        "source_commit": plan["source_commit"],
        "license": plan["license"],
        "copyright_notice": plan["copyright_notice"],
        "entries": entries,
    }
    return core, rendered


def pending_review(digest: str) -> dict[str, Any]:
    return {
        "status": "PENDING_HUMAN_REVIEW",
        "content_digest": digest,
        "reviewer": None,
        "reviewed_at": None,
        "findings": [],
    }


def validate_review(value: object, digest: str) -> dict[str, Any]:
    if not isinstance(value, dict) or set(value) != {
        "status",
        "content_digest",
        "reviewer",
        "reviewed_at",
        "findings",
    }:
        raise ValueError("provenance review object is malformed")
    if value["content_digest"] != digest:
        raise ValueError("provenance review digest does not bind the current record")
    if not isinstance(value["findings"], list) or not all(isinstance(item, str) for item in value["findings"]):
        raise ValueError("provenance findings must be a string list")
    if value["status"] == "PENDING_HUMAN_REVIEW":
        if value["reviewer"] is not None or value["reviewed_at"] is not None or value["findings"]:
            raise ValueError("pending provenance review must not prefill a decision")
    elif value["status"] in {"APPROVED", "CHANGES_REQUIRED"}:
        if not isinstance(value["reviewer"], str) or not value["reviewer"]:
            raise ValueError("completed provenance review requires a reviewer")
        if not isinstance(value["reviewed_at"], str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value["reviewed_at"]):
            raise ValueError("completed provenance review requires an ISO date")
        if value["status"] == "APPROVED" and value["findings"]:
            raise ValueError("approved provenance review cannot retain findings")
        if value["status"] == "CHANGES_REQUIRED" and not value["findings"]:
            raise ValueError("changes-required provenance review needs findings")
    else:
        raise ValueError("unsupported provenance review status")
    return value


def record_for(core: dict[str, Any], previous: object | None = None) -> dict[str, Any]:
    digest = sha256(canonical_json(core))
    review = pending_review(digest)
    if isinstance(previous, dict) and previous.get("review", {}).get("content_digest") == digest:
        review = validate_review(previous["review"], digest)
    return {**core, "review": review}


def _write_atomic(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + f".tmp-{os.getpid()}")
    temporary.write_bytes(data)
    os.replace(temporary, path)


def generate(repository_root: Path, upstream: Path, plan_path: Path, record_path: Path) -> dict[str, Any]:
    plan = validate_plan(load_json(plan_path))
    tracked = load_tracked_files(upstream, plan["source_commit"])
    core, rendered = build_core(plan, {item.path: item.data for item in tracked})
    previous = load_json(record_path) if record_path.exists() else None
    record = record_for(core, previous)
    for relative, data in rendered.items():
        target = repository_root.joinpath(*PurePosixPath(relative).parts)
        _write_atomic(target, data)
    _write_atomic(record_path, pretty_json(record))
    return record


def verify(repository_root: Path, upstream: Path, plan_path: Path, record_path: Path) -> list[str]:
    errors: list[str] = []
    plan = validate_plan(load_json(plan_path))
    tracked = load_tracked_files(upstream, plan["source_commit"])
    core, rendered = build_core(plan, {item.path: item.data for item in tracked})
    record = load_json(record_path)
    expected = record_for(core, record)
    if record != expected:
        errors.append("provenance record does not match the exact plan/upstream/targets")
    for relative, data in rendered.items():
        target = repository_root.joinpath(*PurePosixPath(relative).parts)
        if target.is_symlink() or not target.is_file():
            errors.append(f"target is missing or not a regular file: {relative}")
        elif target.read_bytes() != data:
            errors.append(f"target bytes differ from deterministic transform: {relative}")
    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("generate", "verify"))
    parser.add_argument("--upstream", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, default=REPOSITORY_ROOT)
    parser.add_argument("--plan", type=Path, default=DEFAULT_PLAN)
    parser.add_argument("--record", type=Path, default=DEFAULT_RECORD)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.repository_root.resolve()
    plan = args.plan if args.plan.is_absolute() else root / args.plan
    record = args.record if args.record.is_absolute() else root / args.record
    try:
        if args.mode == "generate":
            generated = generate(root, args.upstream, plan, record)
            print(
                f"[PASS] Generated {len(generated['entries'])} provenance-bound v0.1.0 targets; "
                f"review={generated['review']['status']}"
            )
            return 0
        errors = verify(root, args.upstream, plan, record)
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    loaded = load_json(record)
    print(f"[PASS] {len(loaded['entries'])} v0.1.0 imported targets match exact upstream bytes and transforms")
    print(f"[INFO] Provenance review status: {loaded['review']['status']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
