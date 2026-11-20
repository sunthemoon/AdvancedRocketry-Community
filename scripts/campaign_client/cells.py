"""Validate a prepared W400 cell without downloading or running anything."""
import json
import os
import re
import shutil
import tomllib
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
    # Dependency modId entries and filenames do not declare installed mods.
    with zipfile.ZipFile(path) as archive:
        try:
            info = archive.getinfo("META-INF/mods.toml")
        except KeyError:
            return False
        if info.file_size > 1024 * 1024:
            raise ValueError("Oversized mod metadata")
        metadata = tomllib.loads(archive.read(info).decode("utf-8"))
    mods = metadata.get("mods")
    if not isinstance(mods, list) or not 1 <= len(mods) <= 256:
        raise ValueError("Missing or oversized [[mods]] declarations")
    if any(not isinstance(mod, dict) or not isinstance(mod.get("modId"), str)
           or not mod["modId"] for mod in mods):
        raise ValueError("Invalid [[mods]] modId")
    return any(mod["modId"] == "jei" for mod in mods)


def artifact(base, spec):
    if not isinstance(spec, dict) or set(spec) != {"path", "sha256"}:
        raise ValueError("Artifact needs path and sha256")
    if not isinstance(spec["sha256"], str) or not re.fullmatch("[a-fA-F0-9]{64}", spec["sha256"]):
        raise ValueError("Invalid SHA-256")
    return {"path": path_at(base, spec["path"]), "sha256": spec["sha256"].lower()}


def checked_cell_artifact(base, spec):
    record = artifact(base, spec)
    if not record["path"].is_relative_to(base) or not record["path"].is_file():
        raise ValueError("Declared artifact must be a file inside the cell")
    if sha256(record["path"]) != record["sha256"]:
        raise ValueError("Declared artifact hash mismatch")
    return record


def validate_cell_files(base, cell):
    """Validate extended manifest inventories before checking installed mod IDs."""
    mods = cell["mods"]
    if not isinstance(mods, dict) or set(mods) != {"server", "client"}:
        raise ValueError("Cell mods must contain server and client lists")
    for side, key in (("server", "server_dir"), ("client", "client_game_dir")):
        game = cell[key]
        if not game.is_relative_to(base) or not game.is_dir():
            raise ValueError("Prepared game directory must be inside the cell")
        entries = mods[side]
        if not isinstance(entries, list) or not 1 <= len(entries) <= 256:
            raise ValueError("Missing or oversized cell mod list")
        records = [checked_cell_artifact(base, spec) for spec in entries]
        directory = (game / "mods").resolve()
        if not directory.is_dir() or not directory.is_relative_to(base):
            raise ValueError("Mods directory must be inside the cell")
        paths = {record["path"] for record in records}
        if len(paths) != len(records) or any(p.parent != directory or p.suffix.lower() != ".jar"
                                             for p in paths):
            raise ValueError("Duplicate or misplaced cell mod artifact")
        actual = [p.resolve() for p in directory.iterdir() if p.suffix.lower() == ".jar"]
        if len(actual) != len(paths) or paths != set(actual):
            raise ValueError("Declared mod list does not match installed JARs")
        mods[side] = records
    for key in (("candidate", "jei_jar") if cell["jei"] else ("candidate",)):
        cell[key] = checked_cell_artifact(base, cell[key])
        if cell[key]["path"] not in {record["path"] for record in mods["client"]}:
            raise ValueError("Declared client artifact is missing from cell mods")
    args = checked_cell_artifact(base, cell["server_forge_args"])
    expected = cell["server_dir"] / f"libraries/net/minecraftforge/forge/1.20.1-{cell['forge']}/win_args.txt"
    if args["path"] != expected.resolve():
        raise ValueError("Declared Forge arguments do not match the selected server/Forge")
    cell["server_forge_args"] = args


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
    extended = required | {"mods", "server_forge_args"}
    if set(cell) not in (required, extended) or type(cell["schema_version"]) is not int or cell["schema_version"] != 1:
        raise ValueError("Unsupported cell schema")
    if cell["forge"] not in ("47.4.10", "47.4.23") or type(cell["jei"]) is not bool:
        raise ValueError("Unsupported Forge/JEI cell")
    if cell["version_id"] != f"1.20.1-forge-{cell['forge']}":
        raise ValueError("Cell version_id does not match Forge")
    for key in ("runtime_root", "server_dir", "client_game_dir"):
        cell[key] = path_at(path.parent, cell[key])
    if not cell["jei"] and cell["jei_jar"] is not None:
        raise ValueError("No-JEI cell must declare jei_jar null")
    if "mods" in cell:
        validate_cell_files(path.parent, cell)
    else:
        cell["candidate"] = artifact(path.parent, cell["candidate"])
        cell["jei_jar"] = artifact(path.parent, cell["jei_jar"]) if cell["jei"] else None
        for spec in (cell["candidate"], cell["jei_jar"]):
            if spec and sha256(spec["path"]) != spec["sha256"]:
                raise ValueError("Declared artifact hash mismatch")
    server_jars = inventory(cell["server_dir"])
    if sum(j["sha256"] == cell["candidate"]["sha256"] for j in server_jars) != 1:
        raise ValueError("Server candidate SHA-256 missing, wrong or duplicated")
    check_game(cell, cell["client_game_dir"])
    return cell
