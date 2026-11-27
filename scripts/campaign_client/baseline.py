"""Account-free, hash-pinned Vanilla, empty Forge and JEI-only baselines; no installs."""
import hashlib
import json
import re
import zipfile
from pathlib import Path

from .cells import artifact, is_jei, path_at, read_object
from .evidence import sha256
from scripts.v100_compatibility_runtime import child, libraries, profile, read_json, safe_path, verify_file

VANILLA = "VANILLA_1_20_1"
FORGE = "FORGE_47_4_10_EMPTY"
FORGE_JEI = "FORGE_47_4_10_JEI_ONLY"
JEI_FILE = "jei-1.20.1-forge-15.56.0.205.jar"
JEI_SHA256 = "6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c"
VERSION_FILES = {
    "versions/1.20.1/1.20.1.json": "802d49b1ca907c9dad7ee0bb7c65678e452acf963962eff3b6fe8ecb4dbb23c8",
    "versions/1.20.1/1.20.1.jar": "56b71336d2b4fdffd197f56595b0da93e32a946f78f382a299b8f4b92758bb0f",
}
FORGE_VERSION = "versions/1.20.1-forge-47.4.10/1.20.1-forge-47.4.10.json"
FORGE_PROFILE_SHA256 = "de680055791048582f749d061dc3a2fdcfaff9b04ea3058017c8b91ae9d3feaa"
# Measured from the campaign runtime; the installer supplies no MC_SRG_SHA.
FORGE_SRG_CLIENT = "net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar"
FORGE_SRG_CLIENT_SHA256 = "e2e940fe7ca0a9fba5a5c11ad2cf3e2f7df91bbf51d12af22cdde7af18adc344"
EMPTY_MODS_SHA256 = hashlib.sha256(b"[]\n").hexdigest()
EMPTY_DIRS = {"mods", "config", "defaultconfigs", "resourcepacks", "saves"}


def baseline_path(base, value):
    path_at(base, value)  # Apply the shared string validation, but check links before resolving.
    path = Path(value)
    return safe_path(path if path.is_absolute() else base / path)


def check_empty_game(game):
    """Not just JARs: even a hidden file, subdirectory or link invalidates an empty input."""
    safe_path(game)
    if not game.is_dir() or {p.name for p in game.iterdir()} != EMPTY_DIRS:
        raise ValueError("Baseline template needs exactly the empty game directories")
    for name in EMPTY_DIRS:
        directory = safe_path(game / name)
        if not directory.is_dir() or next(directory.iterdir(), None) is not None:
            raise ValueError("Baseline game directories must be empty")


def jei_mods():
    entries = [{"file": JEI_FILE, "sha256": JEI_SHA256, "jei": True}]
    digest = hashlib.sha256((json.dumps(entries, sort_keys=True, separators=(",", ":")) + "\n").encode()).hexdigest()
    return entries, digest


def check_jei_game(game):
    """Exactly the campaign JEI JAR; no arbitrary config, pack, save, hidden entry or link."""
    safe_path(game)
    if not game.is_dir() or {p.name for p in game.iterdir()} != EMPTY_DIRS:
        raise ValueError("JEI baseline template needs exactly five game directories")
    for name in EMPTY_DIRS:
        directory = safe_path(game / name)
        expected = {JEI_FILE} if name == "mods" else set()
        if not directory.is_dir() or {p.name for p in directory.iterdir()} != expected:
            raise ValueError("JEI baseline needs exactly the pinned JEI JAR and otherwise empty directories")
    jar = safe_path(game / "mods" / JEI_FILE)
    if not jar.is_file() or sha256(jar) != JEI_SHA256 or not is_jei(jar):
        raise ValueError("Baseline JEI SHA-256 or mod identity mismatch")


def load_baseline(path):
    path = safe_path(Path(path))
    raw = read_object(path)
    required = {"schema_version", "kind", "profile", "runtime_root", "libraries_root", "assets_root",
                "client_game_dir", "version_files", "mods"}
    if (set(raw) != required or type(raw["schema_version"]) is not int or raw["schema_version"] != 1
            or raw["kind"] != "baseline" or raw["profile"] not in (VANILLA, FORGE, FORGE_JEI)):
        raise ValueError("Unsupported baseline schema or profile")
    cell = dict(raw)
    for key in ("runtime_root", "libraries_root", "assets_root", "client_game_dir"):
        cell[key] = baseline_path(path.parent, raw[key])
        if not cell[key].is_dir():
            raise ValueError("Missing baseline directory")
    for key in ("runtime_root", "client_game_dir"):
        if not cell[key].is_relative_to(path.parent) or cell[key] == path.parent:
            raise ValueError("Baseline version and game directories must be inside the profile")
    jei = raw["profile"] == FORGE_JEI
    (check_jei_game if jei else check_empty_game)(cell["client_game_dir"])
    mods = raw["mods"]
    if not isinstance(mods, dict) or set(mods) != {"path", "entries", "sha256"}:
        raise ValueError("Baseline needs a mods identity")
    expected_mods, mods_sha256 = jei_mods() if jei else ([], EMPTY_MODS_SHA256)
    if (mods["entries"] != expected_mods or mods["sha256"] != mods_sha256
            or baseline_path(path.parent, mods["path"]) != cell["client_game_dir"] / "mods"):
        raise ValueError("Invalid baseline mods identity")
    expected = dict(VERSION_FILES)
    forge = raw["profile"] in (FORGE, FORGE_JEI)
    if forge:
        expected[FORGE_VERSION] = FORGE_PROFILE_SHA256
        # The official helper verifies this installer's pinned SHA-1 and embedded profile below.
        expected["forge-47.4.10-installer.jar"] = None
    entries = raw["version_files"]
    if not isinstance(entries, list) or len(entries) != len(expected):
        raise ValueError("Incorrect baseline version-file inventory")
    records = [artifact(path.parent, entry) for entry in entries]
    for entry in entries:
        baseline_path(path.parent, entry["path"])
    actual = {record["path"] for record in records}
    if len(actual) != len(records) or actual != {cell["runtime_root"] / name for name in expected}:
        raise ValueError("Unexpected baseline version file")
    for record in records:
        file = safe_path(record["path"])
        pin = expected[file.relative_to(cell["runtime_root"]).as_posix()]
        if (pin is not None and record["sha256"] != pin) or sha256(file) != record["sha256"]:
            raise ValueError("Baseline version-file hash mismatch")
    # No undeclared versions, launcher profiles, accounts or runtime additions.
    found = set()
    allowed_dirs = {parent for file in actual for parent in file.parents
                    if parent.is_relative_to(cell["runtime_root"])}
    for file in cell["runtime_root"].rglob("*"):
        safe_path(file)
        if file.is_file():
            found.add(file)
        elif file not in allowed_dirs:
            raise ValueError("Unexpected baseline runtime directory")
        if len(found) > len(expected):
            raise ValueError("Unexpected baseline runtime file")
    if found != actual:
        raise ValueError("Unexpected baseline runtime file")
    vanilla, patched = baseline_metadata(cell)
    for entry in libraries(vanilla, patched):
        verify_file(child(cell["libraries_root"], entry["path"]), entry["sha1"], entry["size"])
    index = vanilla["assetIndex"]
    verify_file(child(cell["assets_root"], f"indexes/{index['id']}.json"), index["sha1"], index["size"])
    runtime_files = check_forge_outputs(cell) if forge else []
    cell.update(forge="47.4.10" if forge else None, jei=jei, candidate=None,
                version_id="1.20.1-forge-47.4.10" if forge else "1.20.1",
                baseline_path=path, baseline_identity={"profile": raw["profile"],
                    "manifest_sha256": sha256(path), "version_files": records, "mods": mods,
                    "forge_runtime_files": runtime_files})
    return cell


def check_forge_outputs(cell):
    """Forge discovers these locally installed files outside version.json's ordinary classpath."""
    with zipfile.ZipFile(cell["runtime_root"] / "forge-47.4.10-installer.jar") as archive:
        info = archive.getinfo("install_profile.json")
        if info.file_size > 1024 * 1024:
            raise ValueError("Oversized Forge install profile")
        installed = json.loads(archive.read(info))
    entries = [entry["downloads"]["artifact"] for entry in installed["libraries"]
               if entry["name"].split(":")[:2] in [["net.minecraftforge", name] for name in
                   ("fmlcore", "javafmllanguage", "lowcodelanguage", "mclanguage", "forge")]]
    for relative, key in (
        ("net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-client.jar", "PATCHED_SHA"),
        ("net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-extra.jar", "MC_EXTRA_SHA")):
        digest = installed["data"][key]["client"]
        if not isinstance(digest, str) or not re.fullmatch(r"'[a-f0-9]{40}'", digest):
            raise ValueError("Invalid Forge output digest")
        entries.append({"path": relative, "sha1": digest[1:-1]})
    result = []
    for entry in entries:
        file = child(cell["libraries_root"], entry["path"])
        verify_file(file, entry["sha1"], entry.get("size"))
        result.append({"path": file, "sha256": sha256(file)})
    srg = child(cell["libraries_root"], FORGE_SRG_CLIENT)
    verify_file(srg)
    digest = sha256(srg)
    if digest != FORGE_SRG_CLIENT_SHA256:
        raise ValueError("Forge SRG client SHA-256 mismatch")
    result.append({"path": srg, "sha256": digest})
    return result


def baseline_metadata(cell):
    if cell["profile"] in (FORGE, FORGE_JEI):
        return profile(cell["runtime_root"], "47.4.10")
    vanilla = read_json(cell["runtime_root"] / "versions/1.20.1/1.20.1.json")
    if vanilla.get("id") != "1.20.1" or vanilla.get("mainClass") != "net.minecraft.client.main.Main":
        raise ValueError("Not the Vanilla 1.20.1 profile")
    return vanilla, {"libraries": [], "arguments": {"jvm": [], "game": []}}
