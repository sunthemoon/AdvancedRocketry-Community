#!/usr/bin/env python3
"""Validate canonical v1.0-v2.0 plans; this does not run release gates."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path, PurePosixPath
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[1]
VERSION_DOCS = (
    "V1.0.0-COMMUNITY-MVP.md",
    "V1.1.0-EXPANSION-KERNEL.md",
    "V1.2.0-MACHINE-MULTIBLOCK-KERNEL.md",
    "V1.3.0-PUBLIC-API-COMPATIBILITY.md",
    "V1.4.0-PLANETARY-EXPANSION.md",
    "V1.5.0-ORBITAL-STATION-WARP.md",
    "V1.6.0-SATELLITE-RESOURCE-MISSIONS.md",
    "V1.7.0-ENDGAME-SYSTEMS.md",
    "V1.8.0-CLASSIC-CONTENT-COMPLETION.md",
    "V1.9.0-PARITY-BETA-HARDENING.md",
    "V2.0.0-CLASSIC-FEATURE-PARITY.md",
)
SOURCE_RECORD = "docs/provenance/v1.0.0-v1plus-planning-source.json"
STATES = {"PLANNED", "IN_PROGRESS", "BLOCKED", "READY_FOR_AUDIT", "PASSED", "RELEASED"}
GATES = {f"G{number}" for number in range(10)}
MAX_TEXT_BYTES = 512 * 1024


def read_text(path: Path) -> str:
    if not path.is_file() or path.stat().st_size > MAX_TEXT_BYTES:
        raise ValueError(f"missing or oversized file: {path}")
    return path.read_text(encoding="utf-8")


def relative_path(value: str) -> bool:
    path = PurePosixPath(value)
    return bool(value) and not path.is_absolute() and ".." not in path.parts and not any(
        char in value for char in "\\:"
    )


def outside_fences(text: str) -> str:
    result: list[str] = []
    fence = ""
    for line in text.splitlines():
        match = re.match(r"^\s{0,3}(`{3,}|~{3,})", line)
        if match:
            marker = match.group(1)
            if not fence:
                fence = marker
            elif marker[0] == fence[0] and len(marker) >= len(fence):
                fence = ""
        elif not fence:
            result.append(line)
    if fence:
        raise ValueError("unclosed Markdown fence")
    return "\n".join(result)


def validate(root: Path, package_root: Path | None = None) -> list[str]:
    errors: list[str] = []
    root = root.resolve()
    try:
        index = read_text(root / "DOCUMENT-INDEX.md")
        roadmap = read_text(root / "docs/04-VERSION-ROADMAP.md")
        manifest = json.loads(read_text(root / SOURCE_RECORD))
        inputs = manifest["inputs"]
        if manifest["schema_version"] != 1 or not isinstance(inputs, list) or len(inputs) != 33:
            raise ValueError("source inventory must contain the 33 original package files")
    except (OSError, ValueError, KeyError, TypeError) as exc:
        return [str(exc)]

    documents = {"AGENTS.md", "DOCUMENT-INDEX.md", "README.md", "PRODUCT.md",
                 "docs/04-VERSION-ROADMAP.md", "docs/10-CODEX-EXECUTION-RUNBOOK.md"}
    seen: set[str] = set()
    for row in inputs:
        try:
            source = row["source_path"]
            if not isinstance(source, str) or not relative_path(source) or source in seen:
                raise ValueError("invalid or duplicate source path")
            seen.add(source)
            if not re.fullmatch(r"[0-9a-f]{64}", row["source_sha256"]):
                raise ValueError("invalid source SHA-256")
            if not isinstance(row["source_bytes"], int) or not 0 < row["source_bytes"] <= MAX_TEXT_BYTES:
                raise ValueError("invalid source byte count")
            targets = row["targets"]
            if not isinstance(targets, list):
                raise ValueError("targets must be a list")
            if row["disposition"] not in {"adapted", "merged-into-canonical-document", "reference-only-package-metadata"}:
                raise ValueError("unknown source disposition")
            if (row["disposition"] == "reference-only-package-metadata") != (not targets):
                raise ValueError("source disposition/targets disagree")
            for target in targets:
                if not isinstance(target, str) or not relative_path(target) or not (root / target).is_file():
                    raise ValueError(f"missing or invalid target: {target}")
                if target.endswith(".md"):
                    documents.add(target)
                if target not in index and target != "DOCUMENT-INDEX.md":
                    raise ValueError(f"target missing from document index: {target}")
            if package_root is not None:
                source_file = package_root / source
                if not source_file.is_file() or source_file.stat().st_size != row["source_bytes"]:
                    raise ValueError(f"source size mismatch: {source}")
                if hashlib.sha256(source_file.read_bytes()).hexdigest() != row["source_sha256"]:
                    raise ValueError(f"source hash mismatch: {source}")
        except (OSError, ValueError, KeyError, TypeError) as exc:
            errors.append(f"source inventory: {exc}")
    if package_root is not None:
        actual = {path.relative_to(package_root).as_posix() for path in package_root.rglob("*") if path.is_file()}
        if actual != seen:
            errors.append("package file set differs from the source inventory")

    for name in VERSION_DOCS:
        relative = f"docs/versions/{name}"
        version = name.split("-", 1)[0].lower()
        try:
            text = read_text(root / relative)
            if relative not in index or f"| `{version}` |" not in roadmap:
                errors.append(f"{version}: missing canonical index/roadmap entry")
            blocks = re.findall(r"```yaml\s*\n(.*?)\n```", text, re.DOTALL)
            status_blocks = [block for block in blocks if re.search(r"^version:", block, re.MULTILINE)]
            if len(status_blocks) != 1:
                raise ValueError("expected one version status block")
            block = status_blocks[0]
            pairs = re.findall(r"^(\w+):\s*([^\n]*)", block, re.MULTILINE)
            fields = dict(pairs)
            if len(fields) != len(pairs):
                errors.append(f"{version}: duplicate status field")
            if fields.get("version") != version or fields.get("status") not in STATES:
                errors.append(f"{version}: invalid version identity/status")
            raw_gates = fields.get("required_gates", "")
            gates = re.fullmatch(r"\[([^\]]*)\]", raw_gates)
            entries = [item.strip() for item in gates.group(1).split(",")] if gates else []
            if len(entries) != 10 or set(entries) != GATES:
                errors.append(f"{version}: Required Gate must explicitly list G0-G9 once")
            if fields.get("status") in {"PASSED", "RELEASED"}:
                for field in ("human_approved_by", "human_approved_at"):
                    if not fields.get(field, "").strip('" '):
                        errors.append(f"{version}: accepted status without {field}")
            for command in ("./gradlew clean build", "./gradlew test", "./gradlew runData",
                            "git diff --exit-code", "./gradlew runGameTestServer"):
                if command not in text:
                    errors.append(f"{version}: missing required command {command}")
            documents.add(relative)
        except (OSError, ValueError) as exc:
            errors.append(f"{version}: {exc}")

    for relative in sorted(documents):
        try:
            text = outside_fences(read_text(root / relative))
            for link in re.findall(r"\[[^\]\n]*\]\(([^)\s]+)\)", text):
                parsed = urlsplit(link)
                if parsed.scheme or parsed.netloc or not parsed.path:
                    continue
                target = ((root / relative).parent / unquote(parsed.path)).resolve()
                if not target.is_relative_to(root) or not target.exists():
                    errors.append(f"{relative}: broken local link {link}")
        except (OSError, ValueError) as exc:
            errors.append(f"{relative}: {exc}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--package-root", type=Path, help="also verify the original local input, without editing it")
    args = parser.parse_args()
    errors = validate(args.root, args.package_root)
    for error in errors:
        print(f"FAIL: {error}")
    if not errors:
        print("PASS: 11 version plans, explicit G0-G9, entry points, links and 33-input source inventory")
        if args.package_root:
            print("PASS: all original package file sizes and SHA-256 match")
    print("Scope: planning validation only; no release Gate or human approval asserted.")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
