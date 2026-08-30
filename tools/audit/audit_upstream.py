#!/usr/bin/env python3
"""Generate or verify the deterministic v0.1.0 upstream audit manifest."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import struct
import subprocess
import sys
import tempfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Iterable, Sequence


UPSTREAM_REPOSITORY = "https://github.com/Advanced-Rocketry/AdvancedRocketry"
UPSTREAM_BRANCH = "1.12"
UPSTREAM_NAMESPACE = "advancedrocketry"
UPSTREAM_JAVA_ROOT = "src/main/java/zmaster587/advancedRocketry/"
UPSTREAM_ASSET_ROOT = "src/main/resources/assets/advancedrocketry/"
EXPECTED_OUTPUTS = (
    "UPSTREAM_COMMIT.txt",
    "java-files.csv",
    "java-packages.csv",
    "dependency-imports.csv",
    "libvulpes-usage.csv",
    "static-world-state.csv",
    "network-packets.csv",
    "entities.csv",
    "block-entities.csv",
    "registries.csv",
    "recipes.csv",
    "assets.csv",
    "asset-references.csv",
    "missing-asset-references.csv",
    "duplicate-case-paths.csv",
    "large-files.csv",
    "asm-and-coremod.csv",
    "audit-summary.md",
)
MAX_FILES = 20_000
MAX_FILE_BYTES = 32 * 1024 * 1024
MAX_TOTAL_BYTES = 1024 * 1024 * 1024
GIT_TIMEOUT_SECONDS = 60

JAVA_COLUMNS = (
    "path",
    "package",
    "lines",
    "bytes",
    "sha256",
    "primary_domain",
    "imports_libvulpes",
    "imports_client",
    "has_static_mutable_state",
    "has_nbt",
    "has_network",
    "has_dimension_logic",
    "notes",
)
ASSET_COLUMNS = (
    "source_path",
    "kind",
    "bytes",
    "width",
    "height",
    "color_mode",
    "sha256",
    "license_status",
    "source_commit",
    "target_version",
    "target_path",
    "transformation",
    "status",
    "notes",
)
REFERENCE_COLUMNS = (
    "source_path",
    "line",
    "reference",
    "reference_kind",
    "target_path",
    "status",
)

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([^;]+);", re.MULTILINE)
PACKAGE_RE = re.compile(r"^\s*package\s+([^;]+);", re.MULTILINE)
CLASS_RE = re.compile(
    r"\b(?:class|interface|enum)\s+([A-Za-z_$][\w$]*)"
    r"(?:\s+extends\s+([^\{\n]+?))?(?:\s+implements\s+([^\{\n]+?))?\s*\{"
)
STATIC_MUTABLE_RE = re.compile(
    r"\bstatic\b[^;\n]*(?:Map|List|Set|Collection|Queue|Deque|Cache|"
    r"HashMap|ArrayList|HashSet|ConcurrentHashMap)\s*(?:<[^;\n]+>)?\s+"
    r"[A-Za-z_$][\w$]*"
)
RESOURCE_LOCATION_RE = re.compile(
    r"(?<![A-Za-z0-9_.-])([a-z0-9_.-]+:[a-z0-9_./-]+)"
)


@dataclass(frozen=True)
class TrackedFile:
    path: str
    data: bytes

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.data).hexdigest()


def _run_git(repository: Path, *args: str) -> bytes:
    result = subprocess.run(
        ["git", "-C", str(repository), *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=GIT_TIMEOUT_SECONDS,
    )
    if result.returncode:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise ValueError(f"git {' '.join(args)} failed: {detail}")
    return result.stdout


def _safe_relative(raw: str) -> str:
    path = PurePosixPath(raw)
    encoded = raw.encode("utf-8", errors="strict")
    if (
        not raw
        or len(encoded) > 4096
        or path.is_absolute()
        or path.as_posix() != raw
        or "\\" in raw
        or ":" in raw
        or any(part in {"", ".", ".."} for part in path.parts)
        or any(ord(character) < 32 or ord(character) == 127 for character in raw)
    ):
        raise ValueError(f"unsafe tracked path: {raw!r}")
    return raw


def load_tracked_files(repository: Path, expected_commit: str) -> list[TrackedFile]:
    repository = repository.resolve()
    if not (repository / ".git").exists():
        raise ValueError(f"upstream path is not a Git checkout: {repository}")
    head = _run_git(repository, "rev-parse", "HEAD").decode().strip()
    if head != expected_commit:
        raise ValueError(f"upstream HEAD is {head}, expected {expected_commit}")
    dirty = _run_git(repository, "status", "--porcelain", "--untracked-files=all")
    if dirty:
        raise ValueError("upstream checkout is not clean")
    raw_paths = _run_git(repository, "ls-files", "-z").split(b"\0")
    paths = [
        _safe_relative(raw.decode("utf-8", errors="strict"))
        for raw in raw_paths
        if raw
    ]
    if not paths or len(paths) > MAX_FILES:
        raise ValueError(f"tracked file count outside bounds: {len(paths)}")
    if len(paths) != len(set(paths)) or len(paths) != len({p.casefold() for p in paths}):
        # Case collisions are useful audit output, so exact duplicates fail while
        # case-folded duplicates remain permitted and are reported below.
        if len(paths) != len(set(paths)):
            raise ValueError("duplicate tracked paths returned by Git")
    files: list[TrackedFile] = []
    total = 0
    for relative in sorted(paths):
        path = repository.joinpath(*PurePosixPath(relative).parts)
        if path.is_symlink() or not path.is_file():
            raise ValueError(f"tracked path is not a regular file: {relative}")
        size = path.stat().st_size
        if size > MAX_FILE_BYTES:
            raise ValueError(f"tracked file exceeds {MAX_FILE_BYTES} bytes: {relative}")
        total += size
        if total > MAX_TOTAL_BYTES:
            raise ValueError("tracked input exceeds aggregate audit byte limit")
        files.append(TrackedFile(relative, path.read_bytes()))
    license_file = next((item for item in files if item.path == "LICENSE"), None)
    if license_file is None:
        raise ValueError("upstream root LICENSE is missing")
    license_text = license_file.data.decode("utf-8", errors="strict")
    if "MIT License" not in license_text or "Copyright (c) 2017" not in license_text:
        raise ValueError("upstream LICENSE does not contain the recorded MIT notice")
    return files


def _text(data: bytes) -> str:
    return data.decode("utf-8", errors="replace").replace("\r\n", "\n").replace("\r", "\n")


def _line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def _domain(path: str) -> str:
    relative = path.removeprefix(UPSTREAM_JAVA_ROOT)
    parts = PurePosixPath(relative).parts
    return parts[0] if len(parts) > 1 else "root"


def _dependency_category(import_name: str) -> str:
    if import_name.startswith("zmaster587.libVulpes"):
        return "libvulpes"
    if import_name.startswith("zmaster587.advancedRocketry"):
        return "upstream_internal"
    if import_name.startswith(("net.minecraft.client", "net.minecraftforge.client")):
        return "client_api"
    if import_name.startswith("net.minecraft"):
        return "minecraft"
    if import_name.startswith("net.minecraftforge"):
        return "forge"
    if import_name.startswith(("java.", "javax.")):
        return "jdk"
    return "third_party"


def _base_types(match: re.Match[str]) -> str:
    values = [value.strip() for value in match.groups()[1:] if value and value.strip()]
    return " | ".join(values)


def scan_java(files: Sequence[TrackedFile]) -> dict[str, list[dict[str, object]]]:
    java_rows: list[dict[str, object]] = []
    imports_rows: list[dict[str, object]] = []
    libvulpes_rows: list[dict[str, object]] = []
    static_rows: list[dict[str, object]] = []
    network_rows: list[dict[str, object]] = []
    entity_rows: list[dict[str, object]] = []
    block_entity_rows: list[dict[str, object]] = []
    registry_rows: list[dict[str, object]] = []
    asm_rows: list[dict[str, object]] = []
    package_totals: dict[str, list[int]] = defaultdict(lambda: [0, 0, 0])

    java_files = [item for item in files if item.path.startswith(UPSTREAM_JAVA_ROOT) and item.path.endswith(".java")]
    for item in java_files:
        text = _text(item.data)
        package_match = PACKAGE_RE.search(text)
        package = package_match.group(1) if package_match else ""
        lines = 0 if not text else text.count("\n") + (0 if text.endswith("\n") else 1)
        imports = sorted(set(IMPORT_RE.findall(text)))
        has_libvulpes = any(name.startswith("zmaster587.libVulpes") for name in imports)
        has_client = any(name.startswith(("net.minecraft.client", "net.minecraftforge.client")) for name in imports)
        static_matches = list(STATIC_MUTABLE_RE.finditer(text))
        has_nbt = bool(re.search(r"\b(?:NBTTag|NBTBase|readFromNBT|writeToNBT|serializeNBT|deserializeNBT)\b", text))
        has_network = bool(re.search(r"\b(?:IMessage|Packet|ByteBuf|SimpleNetworkWrapper|writeDataToNetwork|readDataFromNetwork)\b", text))
        has_dimension = bool(re.search(r"\b(?:DimensionManager|WorldProvider|dimensionId|dimensionID|getDimension\s*\()", text))
        notes: list[str] = []
        if lines > 1500:
            notes.append("over_1500_lines")
        elif lines > 800:
            notes.append("over_800_lines")
        elif lines > 500:
            notes.append("over_500_lines")
        if has_client and _domain(item.path) not in {"client"}:
            notes.append("client_import_outside_client_domain")
        if static_matches:
            notes.append("static_mutable_candidate")
        java_rows.append(
            {
                "path": item.path,
                "package": package,
                "lines": lines,
                "bytes": len(item.data),
                "sha256": item.sha256,
                "primary_domain": _domain(item.path),
                "imports_libvulpes": str(has_libvulpes).lower(),
                "imports_client": str(has_client).lower(),
                "has_static_mutable_state": str(bool(static_matches)).lower(),
                "has_nbt": str(has_nbt).lower(),
                "has_network": str(has_network).lower(),
                "has_dimension_logic": str(has_dimension).lower(),
                "notes": ";".join(notes),
            }
        )
        totals = package_totals[package]
        totals[0] += 1
        totals[1] += lines
        totals[2] += len(item.data)

        for import_name in imports:
            root = ".".join(import_name.split(".")[:3])
            imports_rows.append(
                {
                    "path": item.path,
                    "import": import_name,
                    "root": root,
                    "category": _dependency_category(import_name),
                }
            )
        for match in re.finditer(r"zmaster587\.libVulpes(?:\.[A-Za-z_$][\w$]*)+", text):
            libvulpes_rows.append(
                {
                    "path": item.path,
                    "line": _line_number(text, match.start()),
                    "symbol": match.group(0),
                    "usage": "import" if text.rfind("import", 0, match.start()) > text.rfind("\n", 0, match.start()) else "reference",
                }
            )
        for match in static_matches:
            declaration = " ".join(match.group(0).split())[:300]
            static_rows.append(
                {
                    "path": item.path,
                    "line": _line_number(text, match.start()),
                    "declaration": declaration,
                    "risk": "mutable_static_collection_candidate",
                }
            )

        classes = list(CLASS_RE.finditer(text))
        for match in classes:
            class_name = match.group(1)
            bases = _base_types(match)
            combined = f"{class_name} {bases} {item.path}"
            if "network/" in item.path or re.search(r"\b(?:IMessage|Packet)\b", bases):
                network_rows.append(
                    {
                        "path": item.path,
                        "class_name": class_name,
                        "base_types": bases,
                        "has_nbt": str(has_nbt).lower(),
                        "has_position": str(bool(re.search(r"\b(?:BlockPos|xPos|yPos|zPos|posX|posY|posZ)\b", text))).lower(),
                        "has_player": str(bool(re.search(r"\b(?:EntityPlayer|EntityPlayerMP|player)\b", text))).lower(),
                        "has_size_limit": str(bool(re.search(r"\b(?:MAX_|maximum|limit|\.size\(\)\s*[<>]=?)", text, re.IGNORECASE))).lower(),
                        "notes": "packet_candidate",
                    }
                )
            if re.search(r"\bEntity(?:Living|LivingBase|Player)?\b", bases) or "/entity/" in item.path:
                entity_rows.append(
                    {"path": item.path, "class_name": class_name, "base_types": bases, "domain": _domain(item.path)}
                )
            if re.search(r"\b(?:TileEntity|TileMultiblockMachine|TileInventoriedForgePowerMachine)\b", bases) or "/tile/" in item.path:
                block_entity_rows.append(
                    {"path": item.path, "class_name": class_name, "base_types": bases, "domain": _domain(item.path)}
                )
            if re.search(r"\b(?:ClassTransformer|IClassTransformer|IFMLLoadingPlugin|ASM)\b", combined, re.IGNORECASE):
                asm_rows.append(
                    {"path": item.path, "line": _line_number(text, match.start()), "kind": "class_or_interface", "evidence": class_name, "migration_status": "REJECTED_NO_PORT"}
                )
        for line_number, line in enumerate(text.splitlines(), start=1):
            compact = " ".join(line.strip().split())
            if re.search(r"\b(?:GameRegistry|ForgeRegistries|RegistryEvent|registerBlock|registerItem|registerTileEntity|@ObjectHolder)\b", line):
                kind = "tile_entity" if "TileEntity" in line else "legacy_registry"
                registry_rows.append(
                    {"path": item.path, "line": line_number, "registry_kind": kind, "declaration": compact[:400]}
                )
            if re.search(r"\b(?:org\.objectweb\.asm|IClassTransformer|IFMLLoadingPlugin|coremod|ClassTransformer)\b", line, re.IGNORECASE):
                asm_rows.append(
                    {"path": item.path, "line": line_number, "kind": "code_reference", "evidence": compact[:400], "migration_status": "REJECTED_NO_PORT"}
                )

    package_rows = [
        {"package": package, "files": values[0], "lines": values[1], "bytes": values[2]}
        for package, values in sorted(package_totals.items())
    ]
    return {
        "java-files.csv": java_rows,
        "java-packages.csv": package_rows,
        "dependency-imports.csv": imports_rows,
        "libvulpes-usage.csv": libvulpes_rows,
        "static-world-state.csv": static_rows,
        "network-packets.csv": network_rows,
        "entities.csv": entity_rows,
        "block-entities.csv": block_entity_rows,
        "registries.csv": registry_rows,
        "asm-and-coremod.csv": asm_rows,
    }


def _asset_kind(path: str) -> str:
    relative = path.removeprefix(UPSTREAM_ASSET_ROOT)
    suffix = PurePosixPath(relative).suffix.lower()
    if relative.startswith("textures/blocks/") and suffix == ".png":
        return "texture_block"
    if relative.startswith("textures/items/") and suffix == ".png":
        return "texture_item"
    if relative.startswith("textures/gui/") and suffix == ".png":
        return "texture_gui"
    if relative.startswith("textures/entity/") and suffix == ".png":
        return "texture_entity"
    if relative.startswith("textures/") and suffix == ".png":
        return "texture_other"
    if relative.startswith("models/") and suffix == ".json":
        return "model_json"
    if suffix == ".obj":
        return "model_obj"
    if suffix == ".mtl":
        return "model_mtl"
    if relative.startswith("sounds/") and suffix == ".ogg":
        return "sound_ogg"
    if relative == "sounds.json":
        return "sound_definition"
    if relative.startswith("lang/"):
        return "lang"
    if relative.startswith("recipes/") and suffix == ".json":
        return "recipe"
    if relative.startswith("advancements/") and suffix == ".json":
        return "advancement"
    if relative.startswith("blockstates/") and suffix == ".json":
        return "blockstate"
    return "other"


def _png_metadata(data: bytes) -> tuple[str, str, str, str]:
    if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        return "", "", "", "invalid_png"
    width, height, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    modes = {0: "grayscale", 2: "rgb", 3: "indexed", 4: "grayscale_alpha", 6: "rgba"}
    return str(width), str(height), f"{modes.get(color_type, 'unknown')}_{bit_depth}bit", ""


def _resource_target(reference: str, kind: str) -> tuple[str, str]:
    namespace, _, value = reference.partition(":")
    if not value:
        # Model and texture ResourceLocations without an explicit namespace use
        # Minecraft's namespace. Sound definitions are authored inside one
        # namespace, so an unqualified sound name remains local to that pack.
        namespace, value = (
            (UPSTREAM_NAMESPACE, namespace)
            if kind == "sound"
            else ("minecraft", namespace)
        )
    if namespace != UPSTREAM_NAMESPACE:
        return "", "EXTERNAL_NAMESPACE"
    value = value.removeprefix("./")
    if kind == "texture":
        return f"{UPSTREAM_ASSET_ROOT}textures/{value}.png", "LOCAL"
    if kind in {"model", "parent"}:
        return f"{UPSTREAM_ASSET_ROOT}models/{value}.json", "LOCAL"
    if kind == "sound":
        return f"{UPSTREAM_ASSET_ROOT}sounds/{value}.ogg", "LOCAL"
    return "", "NON_FILE_REFERENCE"


def _walk_json(value: object, prefix: str = "") -> Iterable[tuple[str, str]]:
    if isinstance(value, dict):
        for key in sorted(value):
            child = f"{prefix}.{key}" if prefix else key
            yield from _walk_json(value[key], child)
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from _walk_json(item, f"{prefix}[{index}]")
    elif isinstance(value, str):
        yield prefix, value


def _json_references(item: TrackedFile, parsed: object) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    kind = _asset_kind(item.path)
    for key_path, value in _walk_json(parsed):
        reference_kind = ""
        if key_path.endswith("parent"):
            reference_kind = "parent"
        elif ".textures." in f".{key_path}." or key_path.startswith("textures."):
            if value.startswith("#"):
                continue
            reference_kind = "texture"
        elif key_path.endswith("model") or ".model" in key_path:
            reference_kind = "model"
        elif kind == "sound_definition" and (key_path.endswith(".name") or re.search(r"sounds\[\d+\]$", key_path)):
            reference_kind = "sound"
        elif RESOURCE_LOCATION_RE.fullmatch(value) and kind in {"model_json", "blockstate"}:
            reference_kind = "model"
        if not reference_kind:
            continue
        target, classification = _resource_target(value, reference_kind)
        rows.append(
            {
                "source_path": item.path,
                "line": 0,
                "reference": value,
                "reference_kind": reference_kind,
                "target_path": target,
                "status": classification,
            }
        )
    return rows


def scan_assets(files: Sequence[TrackedFile], commit: str) -> dict[str, list[dict[str, object]]]:
    tracked = {item.path for item in files}
    asset_rows: list[dict[str, object]] = []
    reference_rows: list[dict[str, object]] = []
    recipe_rows: list[dict[str, object]] = []
    large_rows: list[dict[str, object]] = []
    case_groups: dict[str, list[str]] = defaultdict(list)
    asset_files = [item for item in files if item.path.startswith(UPSTREAM_ASSET_ROOT)]
    for item in asset_files:
        kind = _asset_kind(item.path)
        width = height = color_mode = note = ""
        if item.path.lower().endswith(".png"):
            width, height, color_mode, note = _png_metadata(item.data)
        elif item.path.lower().endswith(".ogg") and not item.data.startswith(b"OggS"):
            note = "invalid_ogg_header"
        parsed: object | None = None
        if item.path.lower().endswith(".json"):
            try:
                parsed = json.loads(_text(item.data))
            except json.JSONDecodeError as exc:
                note = f"invalid_json_line_{exc.lineno}"
        asset_rows.append(
            {
                "source_path": item.path,
                "kind": kind,
                "bytes": len(item.data),
                "width": width,
                "height": height,
                "color_mode": color_mode,
                "sha256": item.sha256,
                "license_status": "UPSTREAM_AR_MIT",
                "source_commit": commit,
                "target_version": "",
                "target_path": "",
                "transformation": "",
                "status": "INVENTORIED",
                "notes": note,
            }
        )
        case_groups[item.path.casefold()].append(item.path)
        if len(item.data) >= 1024 * 1024:
            large_rows.append(
                {"path": item.path, "bytes": len(item.data), "kind": kind, "threshold": "1048576"}
            )
        if parsed is not None:
            reference_rows.extend(_json_references(item, parsed))
            if kind == "recipe":
                recipe_type = parsed.get("type", "") if isinstance(parsed, dict) else ""
                result = parsed.get("result", "") if isinstance(parsed, dict) else ""
                if isinstance(result, dict):
                    result = result.get("item", "")
                recipe_rows.append(
                    {"source_path": item.path, "recipe_type": result if not recipe_type else recipe_type, "output": result, "sha256": item.sha256, "status": "VALID_JSON"}
                )
        text = _text(item.data) if kind in {"model_obj", "model_mtl"} else ""
        if kind == "model_obj":
            for line_number, line in enumerate(text.splitlines(), start=1):
                if line.strip().lower().startswith("mtllib "):
                    reference = line.strip().split(None, 1)[1]
                    base = PurePosixPath(item.path).parent
                    target = (base / reference).as_posix()
                    reference_rows.append(
                        {"source_path": item.path, "line": line_number, "reference": reference, "reference_kind": "mtl", "target_path": target, "status": "LOCAL"}
                    )
        if kind == "model_mtl":
            for line_number, line in enumerate(text.splitlines(), start=1):
                if line.strip().lower().startswith(("map_kd ", "map_ka ", "map_bump ")):
                    reference = line.strip().split(None, 1)[1]
                    base = PurePosixPath(item.path).parent
                    target = (base / reference).as_posix()
                    reference_rows.append(
                        {"source_path": item.path, "line": line_number, "reference": reference, "reference_kind": "texture_path", "target_path": target, "status": "LOCAL"}
                    )

    resolved_rows: list[dict[str, object]] = []
    for row in reference_rows:
        updated = dict(row)
        if row["status"] == "LOCAL":
            updated["status"] = "PRESENT" if row["target_path"] in tracked else "MISSING"
        resolved_rows.append(updated)
    missing_rows = [row for row in resolved_rows if row["status"] == "MISSING"]
    collision_rows = [
        {"casefold_path": key, "paths": " | ".join(sorted(values))}
        for key, values in sorted(case_groups.items())
        if len(values) > 1
    ]
    return {
        "assets.csv": asset_rows,
        "asset-references.csv": resolved_rows,
        "missing-asset-references.csv": missing_rows,
        "duplicate-case-paths.csv": collision_rows,
        "large-files.csv": large_rows,
        "recipes.csv": recipe_rows,
    }


def _write_csv(path: Path, rows: Sequence[dict[str, object]], columns: Sequence[str]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns, lineterminator="\n")
        writer.writeheader()
        for row in sorted(rows, key=lambda value: tuple(str(value.get(column, "")) for column in columns)):
            writer.writerow({column: row.get(column, "") for column in columns})


def _summary(commit: str, rows: dict[str, list[dict[str, object]]]) -> str:
    java = rows["java-files.csv"]
    assets = rows["assets.csv"]
    domains = Counter(str(row["primary_domain"]) for row in java)
    kinds = Counter(str(row["kind"]) for row in assets)
    largest = sorted(java, key=lambda row: (-int(row["lines"]), str(row["path"])))[:15]
    lines = [
        "# Advanced Rocketry 1.12 audit summary",
        "",
        "```yaml",
        f"repository: {UPSTREAM_REPOSITORY}",
        f"branch: {UPSTREAM_BRANCH}",
        f"commit: {commit}",
        "license: MIT",
        "license_notice: Copyright (c) 2017",
        f"java_files: {len(java)}",
        f"asset_files: {len(assets)}",
        f"libvulpes_references: {len(rows['libvulpes-usage.csv'])}",
        f"static_mutable_candidates: {len(rows['static-world-state.csv'])}",
        f"network_packet_candidates: {len(rows['network-packets.csv'])}",
        f"asm_coremod_findings: {len(rows['asm-and-coremod.csv'])}",
        f"missing_asset_references: {len(rows['missing-asset-references.csv'])}",
        f"case_collisions: {len(rows['duplicate-case-paths.csv'])}",
        "generation: deterministic",
        "```",
        "",
        "## Java domains",
        "",
        "| Domain | Files |",
        "|---|---:|",
    ]
    lines.extend(f"| `{name}` | {count} |" for name, count in sorted(domains.items()))
    lines.extend(["", "## Asset kinds", "", "| Kind | Files |", "|---|---:|"])
    lines.extend(f"| `{name}` | {count} |" for name, count in sorted(kinds.items()))
    lines.extend(["", "## Largest Java files", "", "| Lines | Path |", "|---:|---|"])
    lines.extend(f"| {row['lines']} | `{row['path']}` |" for row in largest)
    lines.extend(
        [
            "",
            "## Architecture findings",
            "",
            "- LibVulpes usages are indexed rather than copied; v0.1.0 introduces no LibVulpes implementation.",
            "- ASM/coremod findings are indexed with `REJECTED_NO_PORT`; later work requires an ADR before changing that decision.",
            "- Mutable static collections, dimension APIs, NBT, packets, entities, block entities, and legacy registration points have dedicated indexes.",
            "- Missing upstream references and case collisions are historical audit findings, not permission to import broken paths.",
            "- v0.1.0 imports only individually reviewed targets named in its provenance ledger; the manifest is not an import allowlist.",
            "",
            "## v1.0 mapping",
            "",
            "Exact entry points are summarized in `docs/PORTING_MATRIX.md`. Audit status does not imply implementation or behavioral acceptance.",
            "",
        ]
    )
    return "\n".join(lines)


def build_manifest(repository: Path, expected_commit: str, output: Path) -> None:
    files = load_tracked_files(repository, expected_commit)
    rows = scan_java(files)
    rows.update(scan_assets(files, expected_commit))
    output.mkdir(parents=True, exist_ok=True)
    unexpected = sorted(path.name for path in output.iterdir() if path.name not in EXPECTED_OUTPUTS)
    if unexpected:
        raise ValueError("output directory contains unexpected files: " + ", ".join(unexpected))
    (output / "UPSTREAM_COMMIT.txt").write_text(expected_commit + "\n", encoding="utf-8", newline="\n")
    columns = {
        "java-files.csv": JAVA_COLUMNS,
        "java-packages.csv": ("package", "files", "lines", "bytes"),
        "dependency-imports.csv": ("path", "import", "root", "category"),
        "libvulpes-usage.csv": ("path", "line", "symbol", "usage"),
        "static-world-state.csv": ("path", "line", "declaration", "risk"),
        "network-packets.csv": ("path", "class_name", "base_types", "has_nbt", "has_position", "has_player", "has_size_limit", "notes"),
        "entities.csv": ("path", "class_name", "base_types", "domain"),
        "block-entities.csv": ("path", "class_name", "base_types", "domain"),
        "registries.csv": ("path", "line", "registry_kind", "declaration"),
        "recipes.csv": ("source_path", "recipe_type", "output", "sha256", "status"),
        "assets.csv": ASSET_COLUMNS,
        "asset-references.csv": REFERENCE_COLUMNS,
        "missing-asset-references.csv": REFERENCE_COLUMNS,
        "duplicate-case-paths.csv": ("casefold_path", "paths"),
        "large-files.csv": ("path", "bytes", "kind", "threshold"),
        "asm-and-coremod.csv": ("path", "line", "kind", "evidence", "migration_status"),
    }
    for name, fieldnames in columns.items():
        _write_csv(output / name, rows[name], fieldnames)
    (output / "audit-summary.md").write_text(
        _summary(expected_commit, rows), encoding="utf-8", newline="\n"
    )


def verify_manifest(repository: Path, expected_commit: str, output: Path) -> list[str]:
    errors: list[str] = []
    with tempfile.TemporaryDirectory(prefix="arce-audit-") as temporary:
        generated = Path(temporary) / "legacy-manifest"
        build_manifest(repository, expected_commit, generated)
        for name in EXPECTED_OUTPUTS:
            expected_path = output / name
            actual_path = generated / name
            if not expected_path.is_file():
                errors.append(f"missing committed manifest file: {name}")
                continue
            if expected_path.read_bytes() != actual_path.read_bytes():
                errors.append(f"manifest differs from exact upstream input: {name}")
        if output.is_dir():
            extras = sorted(path.name for path in output.iterdir() if path.name not in EXPECTED_OUTPUTS)
            errors.extend(f"unexpected committed manifest file: {name}" for name in extras)
    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("generate", "verify"))
    parser.add_argument("--upstream", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, default=Path("legacy-manifest"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.commit):
        print("[FAIL] --commit must be a lowercase full SHA-1", file=sys.stderr)
        return 2
    try:
        if args.mode == "generate":
            build_manifest(args.upstream, args.commit, args.output)
            print(f"[PASS] Generated {len(EXPECTED_OUTPUTS)} deterministic audit files in {args.output}")
            return 0
        errors = verify_manifest(args.upstream, args.commit, args.output)
    except (OSError, UnicodeError, ValueError, subprocess.TimeoutExpired) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    print(f"[PASS] {len(EXPECTED_OUTPUTS)} audit files match upstream commit {args.commit}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
