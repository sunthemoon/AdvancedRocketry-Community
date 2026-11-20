"""Validate a prepared W400 cell without downloading or running anything."""
import json
import os
import re
import shutil
import zipfile
from pathlib import Path

from .evidence import sha256


def copy_tree(source, target):
    """Copy a prepared input without traversing symlinks or Windows junctions."""
    for root, dirs, files in os.walk(source, followlinks=False):
        for path in (Path(root), *(Path(root) / n for n in dirs + files)):
            if path.is_symlink() or (getattr(path.stat(), "st_file_attributes", 0) & 0x400):
                raise ValueError("Linked template entries are not allowed")
    shutil.copytree(source, target)


def read_object(path, limit=256 * 1024):
    with Path(path).open("rb") as stream:
        raw = stream.read(limit + 1)
    if len(raw) > limit:
        raise ValueError("JSON file exceeds size bound")
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("Duplicate JSON key")
            result[key] = value
        return result
    value = json.loads(raw.decode("utf-8-sig"), object_pairs_hook=pairs,
                       parse_constant=lambda _: (_ for _ in ()).throw(ValueError("Nonfinite JSON")))
    if not isinstance(value, dict):
        raise ValueError("Expected a JSON object")
    return value


def path_at(base, value):
    if not isinstance(value, str) or not value or any(c in value for c in "\r\n\0"):
        raise ValueError("Invalid path")
    path = Path(value)
    return (path if path.is_absolute() else base / path).resolve()


def inventory(game):
    mods = Path(game) / "mods"
    if not mods.is_dir():
        raise ValueError("Missing mods directory")
    jars = sorted(p for p in mods.iterdir() if p.suffix.lower() == ".jar")
    if len(jars) > 256:
        raise ValueError("More than 256 mod JARs")
    return [{"file": p.name, "sha256": sha256(p), "jei": is_jei(p)} for p in jars]


def is_jei(path):
    # Also detect renamed JEI. Filename alone is not an adequate absence check.
    with zipfile.ZipFile(path) as archive:
        try:
            info = archive.getinfo("META-INF/mods.toml")
        except KeyError:
            return path.name.lower().startswith("jei")
        if info.file_size > 1024 * 1024:
            raise ValueError("Oversized mod metadata")
        metadata = archive.read(info).decode("utf-8")
    return bool(re.search(r'''(?m)^\s*modId\s*=\s*["']jei["']''', metadata)) or path.name.lower().startswith("jei")


def artifact(base, spec):
    if not isinstance(spec, dict) or set(spec) != {"path", "sha256"}:
        raise ValueError("Artifact needs path and sha256")
    if not isinstance(spec["sha256"], str) or not re.fullmatch("[a-fA-F0-9]{64}", spec["sha256"]):
        raise ValueError("Invalid SHA-256")
    return {"path": path_at(base, spec["path"]), "sha256": spec["sha256"].lower()}


def check_game(cell, game):
    jars = inventory(game)
    if sum(j["sha256"] == cell["candidate"]["sha256"] for j in jars) != 1:
        raise ValueError("Client candidate SHA-256 missing, wrong or duplicated")
    jei = [j for j in jars if j["jei"]]
    if cell["jei"]:
        if len(jei) != 1 or jei[0]["sha256"] != cell["jei_jar"]["sha256"]:
            raise ValueError("Client JEI SHA-256 missing, wrong or duplicated")
    elif jei:
        raise ValueError("JEI present in a no-JEI cell")
    return jars


def load_cell(path):
    path = Path(path).resolve()
    cell = read_object(path)
    required = {"schema_version", "forge", "jei", "runtime_root", "server_dir", "client_game_dir",
                "version_id", "candidate", "jei_jar"}
    if set(cell) != required or type(cell["schema_version"]) is not int or cell["schema_version"] != 1:
        raise ValueError("Unsupported cell schema")
    if cell["forge"] not in ("47.4.10", "47.4.23") or type(cell["jei"]) is not bool:
        raise ValueError("Unsupported Forge/JEI cell")
    if cell["version_id"] != f"1.20.1-forge-{cell['forge']}":
        raise ValueError("Cell version_id does not match Forge")
    for key in ("runtime_root", "server_dir", "client_game_dir"):
        cell[key] = path_at(path.parent, cell[key])
    cell["candidate"] = artifact(path.parent, cell["candidate"])
    cell["jei_jar"] = artifact(path.parent, cell["jei_jar"]) if cell["jei"] else cell["jei_jar"]
    if not cell["jei"] and cell["jei_jar"] is not None:
        raise ValueError("No-JEI cell must declare jei_jar null")
    for spec in (cell["candidate"], cell["jei_jar"]):
        if spec and sha256(spec["path"]) != spec["sha256"]:
            raise ValueError("Declared artifact hash mismatch")
    server_jars = inventory(cell["server_dir"])
    if sum(j["sha256"] == cell["candidate"]["sha256"] for j in server_jars) != 1:
        raise ValueError("Server candidate SHA-256 missing, wrong or duplicated")
    check_game(cell, cell["client_game_dir"])
    return cell
