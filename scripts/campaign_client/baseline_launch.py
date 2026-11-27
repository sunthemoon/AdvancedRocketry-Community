"""Construct an offline baseline command from verified local official runtime files."""
import hashlib
import os
import re
import shutil
import uuid
import zipfile

from scripts.v100_compatibility_runtime import child, expand, libraries, verify_file
from .baseline import VANILLA, baseline_metadata, baseline_path, load_baseline
from .baseline_pairing import load_pairing
from .baseline_world import WORLD, create_world
from .evidence import sha256


def prepare_baseline(cell, spec, game, java, paired_world=None):
    # Recheck both pinned inputs and empty directories immediately before building the command.
    checked = load_baseline(cell["baseline_path"])
    if checked["baseline_identity"] != cell["baseline_identity"]:
        raise ValueError("Baseline identity changed after session validation")
    pairing = load_pairing(game.parent, paired_world) if paired_world is not None else None
    if pairing is not None:
        source, target = pairing["path"].parent, game.resolve()
        if target.is_relative_to(source) or source.is_relative_to(target):
            raise ValueError("Baseline game directory must not overlap the paired source world")
    game.mkdir()
    for name in ("mods", "config", "defaultconfigs", "resourcepacks", "saves"):
        (game / name).mkdir()
    for entry in cell["baseline_identity"]["mods"]["entries"]:
        source = baseline_path(cell["client_game_dir"], "mods/" + entry["file"])
        target = game / "mods" / entry["file"]
        shutil.copy2(source, target)
        if sha256(target) != entry["sha256"]:
            raise ValueError("Baseline JEI changed during copy")
    pause = "true" if spec.get("pause_on_lost_focus", False) else "false"
    (game / "options.txt").write_text(
        f"lang:{spec['language']}\nguiScale:{spec['gui_scale']}\nfullscreen:false\n"
        f"pauseOnLostFocus:{pause}\ntutorialStep:none\nonboardAccessibility:false\n"
        "skipMultiplayerWarning:true\njoinedFirstServer:true\nnarrator:0\n", encoding="utf-8")
    create_world(game, pairing)
    vanilla, patched = baseline_metadata(cell)
    natives = game / "natives"
    natives.mkdir()
    classpath, native_bytes, native_count = [], 0, 0
    for record in libraries(vanilla, patched):
        path = child(cell["libraries_root"], record["path"])
        verify_file(path, record["sha1"], record["size"])
        classpath.append(str(path))
        if not path.name.endswith("-natives-windows.jar"):
            continue
        with zipfile.ZipFile(path) as archive:
            if len(archive.infolist()) > 256:
                raise ValueError("Native archive entry count exceeds diagnostic bound")
            for info in archive.infolist():
                if not info.filename.lower().endswith(".dll"):
                    continue
                native = re.fullmatch(r"(?:windows/x64/org/lwjgl/(?:[A-Za-z0-9_]+/)*)?([A-Za-z0-9_]+\.dll)", info.filename)
                if info.file_size > 32 * 1024 * 1024 or native is None:
                    raise ValueError("Unexpected native library entry")
                native_bytes += info.file_size
                native_count += 1
                if native_bytes > 128 * 1024 * 1024 or native_count > 64:
                    raise ValueError("Native extraction exceeds diagnostic bound")
                target, data = child(natives, native.group(1)), archive.read(info)
                if target.exists() and target.read_bytes() != data:
                    raise ValueError("Conflicting native libraries")
                target.write_bytes(data)
    client = cell["runtime_root"] / "versions/1.20.1/1.20.1.jar"
    verify_file(client, vanilla["downloads"]["client"]["sha1"], vanilla["downloads"]["client"]["size"])
    selected = game / (cell["version_id"] + ".jar")
    shutil.copy2(client, selected)
    classpath.append(str(selected))
    player = spec["player_name"]
    offline_id = str(uuid.UUID(bytes=hashlib.md5(f"OfflinePlayer:{player}".encode()).digest(), version=3))
    substitutions = {"auth_player_name": player, "version_name": cell["version_id"], "game_directory": str(game),
        "assets_root": str(cell["assets_root"]), "assets_index_name": vanilla["assetIndex"]["id"],
        "auth_uuid": offline_id, "auth_access_token": "0", "clientid": "0", "auth_xuid": "0",
        "user_type": "legacy", "version_type": "release", "natives_directory": str(natives),
        "launcher_name": "arce-disposable-test", "launcher_version": "1", "classpath": os.pathsep.join(classpath),
        "library_directory": str(cell["libraries_root"]), "classpath_separator": os.pathsep}
    jvm = expand([*vanilla["arguments"]["jvm"], *patched["arguments"]["jvm"]], substitutions)
    args = expand([*vanilla["arguments"]["game"], *patched["arguments"]["game"]], substitutions)
    main = vanilla["mainClass"] if cell["profile"] == VANILLA else patched["mainClass"]
    return [str(java), "-Xms512M", "-Xmx3G", *jvm, main, *args,
            "--width", str(spec["window_size"][0]), "--height", str(spec["window_size"][1]),
            "--quickPlaySingleplayer", WORLD]
