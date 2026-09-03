#!/usr/bin/env python3
"""Audit merged Beta localization, asset references, and textual UI status."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
MOD_ID = "advancedrocketrycommunity"
MAX_RESOURCE_FILES = 10_000
MAX_RESOURCE_BYTES = 128 * 1024 * 1024
MAX_JSON_BYTES = 2 * 1024 * 1024
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[a-zA-Z%]")
TRANSLATION_LITERAL = re.compile(
    rf"(?:block|item|itemGroup|menu|screen|tooltip|message|status|station|body|hud|flight)\."
    rf"{MOD_ID}(?:\.[A-Za-z0-9_-]+)+"
)
STRING_LITERAL = re.compile(r'"([A-Za-z0-9_.-]+)"')
ACCESSIBILITY_CONTRACTS = {
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/ElectrolyzerScreen.java": (
        "Component.translatable(status.translationKey())",
    ),
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/RocketFlightScreen.java": (
        '"screen.advancedrocketrycommunity.rocket.state"',
        "Component.translatable(stateKey(menu.state()))",
    ),
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/SatelliteTerminalScreen.java": (
        "Component.translatable(status.translationKey())",
        '"screen.advancedrocketrycommunity.satellite.mission."',
    ),
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/client/LifeSupportHud.java": (
        '"hud.advancedrocketrycommunity.life_support.status"',
        '"hud.advancedrocketrycommunity.life_support.oxygen"',
    ),
}


def _resource_roots(repository_root: Path) -> list[Path]:
    roots = [repository_root / "src/main/resources", repository_root / "src/generated/resources"]
    roots.extend(sorted((repository_root / "src/generated").glob("v*/resources")))
    return [path for path in roots if path.is_dir()]


def _load_json(path: Path) -> Any:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_JSON_BYTES:
        raise ValueError(f"resource JSON is missing, linked, or oversized: {path}")
    return json.loads(path.read_text(encoding="utf-8", errors="strict"))


def _placeholder_contract(value: str) -> tuple[str, ...]:
    return tuple(item for item in PLACEHOLDER.findall(value) if item != "%%")


def audit_languages(paths: Iterable[Path]) -> tuple[dict[str, object], list[str]]:
    merged: dict[str, dict[str, str]] = {"en_us": {}, "zh_cn": {}}
    sources: dict[str, dict[str, str]] = {"en_us": {}, "zh_cn": {}}
    errors: list[str] = []
    files = 0
    for path in sorted(paths):
        locale = path.stem
        if locale not in merged:
            continue
        files += 1
        try:
            value = _load_json(path)
        except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
            errors.append(str(exc))
            continue
        if not isinstance(value, dict) or not all(
            isinstance(key, str) and isinstance(text, str) and text.strip()
            for key, text in value.items()
        ):
            errors.append(f"language file is not a non-empty string map: {path}")
            continue
        for key, text in value.items():
            if key in merged[locale]:
                errors.append(
                    f"duplicate {locale} translation key {key}: "
                    f"{sources[locale][key]} and {path.as_posix()}"
                )
                continue
            merged[locale][key] = text
            sources[locale][key] = path.as_posix()

    en_keys = set(merged["en_us"])
    zh_keys = set(merged["zh_cn"])
    for key in sorted(en_keys - zh_keys):
        errors.append(f"zh_cn is missing translation key {key}")
    for key in sorted(zh_keys - en_keys):
        errors.append(f"en_us is missing translation key {key}")
    for key in sorted(en_keys & zh_keys):
        if _placeholder_contract(merged["en_us"][key]) != _placeholder_contract(
            merged["zh_cn"][key]
        ):
            errors.append(f"translation placeholder contract differs for {key}")
    return {
        "language_files": files,
        "en_us_keys": len(en_keys),
        "zh_cn_keys": len(zh_keys),
        "parity": en_keys == zh_keys,
        "merged": merged,
    }, errors


def _walk_strings(value: Any, key_name: str) -> Iterable[tuple[str, str]]:
    if isinstance(value, dict):
        for key, child in value.items():
            if isinstance(child, str):
                yield str(key), child
            else:
                yield from _walk_strings(child, str(key))
    elif isinstance(value, list):
        for child in value:
            yield from _walk_strings(child, key_name)


def _resource_location(value: str, default_namespace: str) -> tuple[str, str]:
    if ":" in value:
        namespace, path = value.split(":", 1)
    else:
        namespace, path = default_namespace, value
    return namespace, path


def _required_asset(
    available: set[str],
    folded: dict[str, str],
    relative: str,
    source: str,
    errors: list[str],
) -> None:
    if relative in available:
        return
    existing = folded.get(relative.casefold())
    if existing is not None:
        errors.append(f"case-mismatched asset reference {relative} from {source}; found {existing}")
    else:
        errors.append(f"missing asset reference {relative} from {source}")


def audit_resources(repository_root: Path = ROOT) -> tuple[dict[str, object], list[str]]:
    roots = _resource_roots(repository_root)
    errors: list[str] = []
    resources: list[tuple[Path, str]] = []
    total_bytes = 0
    for root in roots:
        for path in sorted(root.rglob("*")):
            if path.is_symlink():
                errors.append(f"linked resource path is not allowed: {path}")
                continue
            if not path.is_file():
                continue
            relative = path.relative_to(root).as_posix()
            resources.append((path, relative))
            total_bytes += path.stat().st_size
            if len(resources) > MAX_RESOURCE_FILES or total_bytes > MAX_RESOURCE_BYTES:
                errors.append("resource inventory exceeds the bounded Beta audit limit")
                break

    available = {relative for _, relative in resources}
    folded: dict[str, str] = {}
    for relative in sorted(available):
        prior = folded.setdefault(relative.casefold(), relative)
        if prior != relative:
            errors.append(f"case-colliding resource paths: {prior} and {relative}")

    language_paths = [
        path
        for path, relative in resources
        if re.fullmatch(r"assets/[^/]+/lang/(?:en_us|zh_cn)\.json", relative)
    ]
    language, language_errors = audit_languages(language_paths)
    errors.extend(language_errors)
    merged = language.pop("merged")
    assert isinstance(merged, dict)

    json_files = 0
    references = 0
    for path, relative in resources:
        if not relative.startswith("assets/") or not relative.endswith(".json"):
            continue
        json_files += 1
        try:
            value = _load_json(path)
        except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
            errors.append(str(exc))
            continue
        parts = relative.split("/")
        if len(parts) < 3:
            continue
        namespace = parts[1]
        for key, target in _walk_strings(value, ""):
            required: str | None = None
            target_namespace = namespace
            target_path = target
            if "/models/" in relative and key == "parent" and target != "builtin/entity":
                target_namespace, target_path = _resource_location(target, "minecraft")
                if target_namespace != "minecraft":
                    required = f"assets/{target_namespace}/models/{target_path}.json"
            elif "/models/" in relative and key != "parent" and not target.startswith("#"):
                target_namespace, target_path = _resource_location(target, "minecraft")
                if target_namespace != "minecraft":
                    required = f"assets/{target_namespace}/textures/{target_path}.png"
            elif "/blockstates/" in relative and key == "model":
                target_namespace, target_path = _resource_location(target, namespace)
                if target_namespace != "minecraft":
                    required = f"assets/{target_namespace}/models/{target_path}.json"
            elif relative.endswith("/sounds.json") and key == "name":
                target_namespace, target_path = _resource_location(target, namespace)
                if target_namespace != "minecraft":
                    required = f"assets/{target_namespace}/sounds/{target_path}.ogg"
            if required is not None:
                references += 1
                _required_asset(available, folded, required, relative, errors)

    referenced_keys: set[str] = set()
    java_root = repository_root / "src/main/java"
    for path in sorted(java_root.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="strict")
        for literal in STRING_LITERAL.findall(text):
            if TRANSLATION_LITERAL.fullmatch(literal):
                referenced_keys.add(literal)
    en = merged.get("en_us", {})
    zh = merged.get("zh_cn", {})
    for key in sorted(referenced_keys):
        if key not in en or key not in zh:
            errors.append(f"referenced player-facing key is not localized in both languages: {key}")

    for relative, markers in ACCESSIBILITY_CONTRACTS.items():
        path = repository_root / relative
        try:
            text = path.read_text(encoding="utf-8", errors="strict")
        except (OSError, UnicodeError) as exc:
            errors.append(f"cannot inspect textual UI status contract {relative}: {exc}")
            continue
        for marker in markers:
            if marker not in text:
                errors.append(f"textual UI status contract missing {marker!r} from {relative}")

    summary = {
        "schema_version": 1,
        "version": "v0.9.0",
        "resource_roots": len(roots),
        "resource_files": len(resources),
        "resource_bytes": total_bytes,
        "json_files": json_files,
        "asset_references_checked": references,
        **language,
        "referenced_translation_keys": len(referenced_keys),
        "textual_status_surfaces": len(ACCESSIBILITY_CONTRACTS),
        "missing_or_case_mismatched_assets": sum(
            error.startswith(("missing asset", "case-mismatched asset")) for error in errors
        ),
        "errors": len(errors),
        "result": "PASS" if not errors else "FAIL",
    }
    return summary, errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        summary, errors = audit_resources(ROOT)
        if args.output is not None:
            output = args.output.resolve()
            output.parent.mkdir(parents=True, exist_ok=True)
            if output.is_symlink() or (output.exists() and not output.is_file()):
                raise ValueError(f"unsafe resource-audit output: {output}")
            output.write_text(
                json.dumps(summary, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
                newline="\n",
            )
        if errors:
            for error in errors:
                print(f"[FAIL] {error}", file=sys.stderr)
            return 1
        print(
            "[PASS] Beta resource audit: "
            f"{summary['resource_files']} files, {summary['json_files']} JSON, "
            f"{summary['en_us_keys']} bilingual keys, "
            f"{summary['asset_references_checked']} asset references"
        )
        return 0
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
