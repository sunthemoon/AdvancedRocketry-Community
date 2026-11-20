#!/usr/bin/env python3
"""Copy a prepared official runtime into four campaign cells, without starting it.

Usage: prepare_campaign_cells.py RUNTIME CANDIDATE SHA256 OUTPUT [--accept-eula]
Use --replace-candidate with the same four cells to update only their candidate
copies and manifests. Stop all cell users before preparing or replacing files.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shutil
import stat
import sys

if __package__:
    from . import v100_compatibility_runtime as runtime
    from .run_dedicated_server_smoke import SmokeError, digest_file
else:
    import v100_compatibility_runtime as runtime
    from run_dedicated_server_smoke import SmokeError, digest_file


CELLS = tuple((forge, jei) for forge in runtime.FORGE_HASHES for jei in (False, True))
CLIENT_OPTIONS = ("lang:en_us\nguiScale:3\nfullscreen:false\n"
                  "overrideWidth:1280\noverrideHeight:720\nsoundCategory_master:1.0\n")
MAX_TREE_FILES = 100_000


def safe_path(path: Path) -> Path:
    """Check before resolving, including dangling links and Windows reparse points."""
    path = path.absolute()
    for part in (path, *path.parents):
        if runtime.is_link_or_junction(part):
            raise SmokeError(f"Linked campaign path is not allowed: {part}")
        try:
            attributes = part.lstat().st_file_attributes if os.name == "nt" else 0
        except FileNotFoundError:
            continue
        if attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT:
            raise SmokeError(f"Reparse campaign path is not allowed: {part}")
    return path.resolve()


def child(root: Path, relative: str) -> Path:
    path = safe_path(runtime.child(root, relative))
    if not path.is_relative_to(root) or path == root:
        raise SmokeError(f"Campaign path escapes its root: {relative}")
    return path


def check_tree(root: Path) -> None:
    """Do not follow even an unrelated link in a runtime or an existing output."""
    safe_path(root)
    if not root.is_dir():
        raise SmokeError(f"Missing campaign directory: {root}")
    count = 0
    for directory, dirs, files in os.walk(root, followlinks=False):
        for name in [*dirs, *files]:
            path = Path(directory) / name
            safe_path(path)
            if not (path.is_dir() or path.is_file()):
                raise SmokeError(f"Nonregular campaign entry: {path}")
            count += 1
            if count > MAX_TREE_FILES:
                raise SmokeError(f"Campaign tree exceeds {MAX_TREE_FILES} entries: {root}")


def sha256(value: str) -> str:
    if not isinstance(value, str) or re.fullmatch(r"[a-fA-F0-9]{64}", value) is None:
        raise SmokeError("Expected a 64-digit candidate SHA-256")
    return value.lower()


def jar_name(value: str) -> str:
    if (not isinstance(value, str)
            or re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*\.jar", value) is None
            or value.lower() == "jei.jar"):
        raise SmokeError("Candidate must have a portable JAR filename other than jei.jar")
    return value


def file_record(path: Path) -> dict:
    path = safe_path(path)
    if not path.is_file():
        raise SmokeError(f"Missing campaign file: {path}")
    return {"path": str(path), "sha256": digest_file(path)}


def checked_file(path: Path, expected: str) -> dict:
    record = file_record(path)
    if record["sha256"] != expected:
        raise SmokeError(f"SHA-256 mismatch: {path}")
    return record


def argument_name(forge: str) -> str:
    return f"libraries/net/minecraftforge/forge/1.20.1-{forge}/win_args.txt"


def runtime_inputs(root: Path) -> dict[str, str]:
    check_tree(root)
    prepared = runtime.read_json(root / "prepared.json")
    if (type(prepared.get("schema_version")) is not int or prepared["schema_version"] != 1
            or prepared.get("forge") != list(runtime.FORGE_HASHES)
            or prepared.get("jei") != runtime.JEI):
        raise SmokeError("Runtime does not describe the pinned Forge/JEI matrix")
    checked_file(root / "jei.jar", sha256(prepared.get("jei_sha256")))
    versions = {}
    for forge in runtime.FORGE_HASHES:
        version = f"1.20.1-forge-{forge}"
        profile = runtime.read_json(root / "versions" / version / f"{version}.json")
        if profile.get("id") != version or profile.get("inheritsFrom") != "1.20.1":
            raise SmokeError(f"Unexpected prepared Forge client profile: {forge}")
        source = child(root, f"servers/{forge}")
        for dirty in ("world", "mods", "server.properties", "eula.txt"):
            if (source / dirty).exists():
                raise SmokeError(f"Server template is not unused: {source / dirty}")
        file_record(child(source, argument_name(forge)))
        versions[forge] = profile["id"]
    return versions


def cell_name(forge: str, jei: bool) -> str:
    return f"{forge}-{'jei' if jei else 'nojei'}"


def manifest(root: Path, cell: Path, forge: str, jei: bool, version: str,
             name: str, expected: str) -> dict:
    server = child(cell, "server")
    client = child(cell, "client")
    candidate = checked_file(child(client, f"mods/{name}"), expected)
    checked_file(child(server, f"mods/{name}"), expected)
    dependency = file_record(child(client, "mods/jei.jar")) if jei else None
    if dependency:
        checked_file(Path(dependency["path"]), digest_file(root / "jei.jar"))
    mods = {}
    for side, game in (("server", server), ("client", client)):
        entries = sorted(child(game, "mods").iterdir())
        expected_names = {name, "jei.jar"} if side == "client" and jei else {name}
        if {path.name for path in entries} != expected_names:
            raise SmokeError(f"Unexpected mods in campaign cell: {game}")
        mods[side] = [file_record(path) for path in entries]
    args = file_record(child(server, argument_name(forge)))
    checked_file(Path(args["path"]), digest_file(child(root, f"servers/{forge}/{argument_name(forge)}")))
    return {"schema_version": 1, "forge": forge, "jei": jei, "runtime_root": str(root),
            "server_dir": str(server), "client_game_dir": str(client), "version_id": version,
            "candidate": candidate, "jei_jar": dependency, "mods": mods, "server_forge_args": args}


def copy_candidate(source: Path, target: Path, expected: str) -> None:
    safe_path(target)
    with source.open("rb") as incoming, target.open("xb") as outgoing:
        shutil.copyfileobj(incoming, outgoing)
    checked_file(target, expected)


def prepare_cells(runtime_root: Path, candidate: Path, expected_sha256: str, output_root: Path,
                  *, accept_eula: bool = False, replace_candidate: bool = False) -> list[Path]:
    root, candidate, output = map(safe_path, (runtime_root, candidate, output_root))
    expected = sha256(expected_sha256)
    name = jar_name(candidate.name)
    checked_file(candidate, expected)
    if (output == root or output.is_relative_to(root) or root.is_relative_to(output)
            or candidate.is_relative_to(output)):
        raise SmokeError("Runtime, candidate source and output must not overlap")
    versions = runtime_inputs(root)
    if output.exists():
        check_tree(output)
    cells = [(child(output, cell_name(forge, jei)), forge, jei) for forge, jei in CELLS]
    old = []
    # Validate all four before writing anything; never silently complete a partial set.
    for cell, forge, jei in cells:
        if not replace_candidate:
            if cell.exists():
                raise SmokeError(f"Refusing to overwrite existing cell: {cell}")
            continue
        value = runtime.read_json(child(cell, "cell.json"))
        entry = value.get("candidate")
        if not isinstance(entry, dict) or not isinstance(entry.get("path"), str):
            raise SmokeError(f"Missing candidate record: {cell}")
        old_name = jar_name(Path(entry["path"]).name)
        old_hash = sha256(entry.get("sha256"))
        actual = manifest(root, cell, forge, jei, versions[forge], old_name, old_hash)
        if (type(value.get("schema_version")) is not int or type(value.get("jei")) is not bool
                or value != actual):
            raise SmokeError(f"Cell manifest differs from the installed cell: {cell}")
        if name != old_name and name.casefold() == old_name.casefold():
            raise SmokeError("Case-only candidate renames are not supported")
        for staging in ("candidate-server.partial", "candidate-client.partial", "cell.json.partial"):
            if child(cell, staging).exists():
                raise SmokeError(f"Retained incomplete replacement: {cell / staging}")
        old.append((old_name, old_hash))
    if replace_candidate and len(set(old)) != 1:
        raise SmokeError("Existing cells do not share one candidate identity")
    output.mkdir(parents=True, exist_ok=True)
    for index, (cell, forge, jei) in enumerate(cells):
        if replace_candidate:
            old_name = old[index][0]
            for side in ("server", "client"):
                staged = child(cell, f"candidate-{side}.partial")
                copy_candidate(candidate, staged, expected)
                destination = child(cell, f"{side}/mods/{name}")
                os.replace(staged, destination)
                if old_name != name:
                    child(cell, f"{side}/mods/{old_name}").unlink()
        else:
            cell.mkdir(exist_ok=False)
            shutil.copytree(child(root, f"servers/{forge}"), child(cell, "server"))
            for side in ("server", "client"):
                mods = child(cell, f"{side}/mods")
                mods.mkdir(parents=True, exist_ok=False)
                copy_candidate(candidate, child(mods, name), expected)
            if jei:
                shutil.copy2(root / "jei.jar", child(cell, "client/mods/jei.jar"))
            child(cell, "client/options.txt").write_text(CLIENT_OPTIONS, encoding="utf-8", newline="\n")
            if accept_eula:
                child(cell, "server/eula.txt").write_text("eula=true\n", encoding="utf-8", newline="\n")
        value = manifest(root, cell, forge, jei, versions[forge], name, expected)
        target = child(cell, "cell.json")
        if replace_candidate:
            staged = child(cell, "cell.json.partial")
            runtime.write_json(staged, value)
            os.replace(staged, target)
        else:
            runtime.write_json(target, value)
    return [cell / "cell.json" for cell, _, _ in cells]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("runtime_root", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("expected_sha256")
    parser.add_argument("output_root", type=Path)
    parser.add_argument("--accept-eula", action="store_true", help="Write eula=true in NEW servers only")
    parser.add_argument("--replace-candidate", action="store_true",
                        help="Validate all cells, then replace only candidate copies and cell.json")
    args = parser.parse_args(argv)
    try:
        paths = prepare_cells(args.runtime_root, args.candidate, args.expected_sha256, args.output_root,
                              accept_eula=args.accept_eula, replace_candidate=args.replace_candidate)
        for path in paths:
            print(f"[PASS] {path} sha256={digest_file(path)}")
        print("[PASS] Four campaign cells prepared; no server or client started")
        return 0
    except (OSError, ValueError, KeyError, SmokeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
