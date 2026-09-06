#!/usr/bin/env python3
"""Prepare isolated official Windows runtimes for the pinned packaged matrix.

This diagnostic launcher uses no existing launcher profile or account. Runtime
downloads are not project assets and must remain outside the repository.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import uuid
import zipfile
from pathlib import Path, PurePosixPath
from urllib.parse import urlsplit

if __package__:
    from .run_dedicated_server_smoke import SmokeError, digest_file, is_link_or_junction, resolve_java
else:
    from run_dedicated_server_smoke import SmokeError, digest_file, is_link_or_junction, resolve_java


ROOT = Path(__file__).resolve().parents[1]
FORGE_HASHES = {
    "47.4.10": "66bfea9963bfa60d88bab6b2750e74a958392715",
    "47.4.23": "ed31ce02ac69176f34353235cb2508d5a0f1e088",
}
JEI = "15.56.0.205"
JEI_SHA1 = "7f64b7f8fde7f001ef054dabe3618a8780ba2650"
MINECRAFT_PROFILE_SHA1 = "19f5ae58f9c31bd3b0923cb822e99e3162bd62ab"
HOSTS = {"maven.minecraftforge.net", "libraries.minecraft.net", "piston-meta.mojang.com",
         "piston-data.mojang.com", "launchermeta.mojang.com", "launcher.mojang.com",
         "resources.download.minecraft.net", "maven.blamejared.com"}
MAX_FILE = 80 * 1024 * 1024


def safe_path(path: Path) -> Path:
    if any(is_link_or_junction(part) for part in (path, *path.parents)):
        raise SmokeError(f"Linked runtime path is not allowed: {path}")
    return path.resolve()


def child(root: Path, relative: str) -> Path:
    parts = PurePosixPath(relative)
    if not relative or "\\" in relative or ":" in relative or parts.is_absolute() or ".." in parts.parts:
        raise SmokeError(f"Unsafe runtime-relative name: {relative}")
    return safe_path(root / parts)


def read_json(path: Path) -> dict:
    safe_path(path)
    if not path.is_file() or not 0 < path.stat().st_size <= 4 * 1024 * 1024:
        raise SmokeError(f"Runtime JSON is missing or oversized: {path}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise SmokeError(f"Runtime JSON must be an object: {path}")
    return value


def write_json(path: Path, value: dict) -> None:
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(value, sort_keys=True, indent=2) + "\n")


def download(url: str, target: Path, sha1: str | None = None, size: int | None = None) -> Path:
    parsed = urlsplit(url)
    if (parsed.scheme != "https" or parsed.hostname not in HOSTS or parsed.username
            or parsed.password or parsed.port not in (None, 443) or parsed.query or parsed.fragment):
        raise SmokeError(f"Runtime download is not an allowed official HTTPS URL: {url}")
    safe_path(target)
    if sha1 is not None and re.fullmatch(r"[a-f0-9]{40}", sha1) is None:
        raise SmokeError("Invalid runtime SHA-1")
    if size is not None and (type(size) is not int or not 0 < size <= MAX_FILE):
        raise SmokeError("Invalid runtime download size")
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = target.with_name(target.name + ".partial")
        if partial.exists():
            raise SmokeError(f"Retained incomplete download must be inspected: {partial}")
        subprocess.run(["curl.exe" if os.name == "nt" else "curl", "--fail", "--silent", "--show-error",
                        "--location", "--proto", "=https", "--proto-redir", "=https",
                        "--connect-timeout", "30", "--max-time", "180", "--max-filesize", str(MAX_FILE),
                        "--output", str(partial), url], check=True, timeout=190)
        verify_file(partial, sha1, size)
        partial.rename(target)
    verify_file(target, sha1, size)
    return target


def verify_file(path: Path, sha1: str | None = None, size: int | None = None) -> None:
    safe_path(path)
    if not path.is_file() or not 0 < path.stat().st_size <= MAX_FILE:
        raise SmokeError(f"Runtime file is missing or oversized: {path}")
    if size is not None and path.stat().st_size != size:
        raise SmokeError(f"Runtime file size mismatch: {path}")
    if sha1 is not None and digest_file(path, "sha1") != sha1:
        raise SmokeError(f"Runtime file SHA-1 mismatch: {path}")


def allowed(rules: list[dict] | None) -> bool:
    if rules is None:
        return True
    result = False
    for rule in rules:
        if set(rule) - {"action", "os", "features"} or rule.get("action") not in ("allow", "disallow"):
            raise SmokeError("Unsupported launcher rule")
        operating = rule.get("os", {})
        if set(operating) - {"name", "arch", "version"}:
            raise SmokeError("Unsupported launcher OS rule")
        applies = operating.get("name", "windows") == "windows"
        if "arch" in operating:
            applies = applies and operating["arch"] in ("x86_64", "amd64")
        if "version" in operating:
            applies = applies and re.search(operating["version"], "10.0") is not None
        applies = applies and all(value is False for value in rule.get("features", {}).values())
        if applies:
            result = rule["action"] == "allow"
    return result


def libraries(vanilla: dict, forge: dict) -> list[dict]:
    records = {}
    for item in [*vanilla["libraries"], *forge["libraries"]]:
        if allowed(item.get("rules")):
            coordinate = item["name"].split(":")
            key = tuple(coordinate[:2] + coordinate[3:])
            records[key] = item["downloads"]["artifact"]
    if not 1 <= len(records) <= 256:
        raise SmokeError("Runtime library count is outside the diagnostic bound")
    return list(records.values())


def profile(root: Path, forge: str) -> tuple[dict, dict]:
    if forge not in FORGE_HASHES:
        raise SmokeError("Forge version is not in the pinned compatibility matrix")
    vanilla_path = root / "versions/1.20.1/1.20.1.json"
    verify_file(vanilla_path, MINECRAFT_PROFILE_SHA1)
    vanilla = read_json(vanilla_path)
    version = f"1.20.1-forge-{forge}"
    patched = read_json(root / "versions" / version / f"{version}.json")
    installer = root / f"forge-{forge}-installer.jar"
    verify_file(installer, FORGE_HASHES[forge])
    with zipfile.ZipFile(installer) as archive:
        info = archive.getinfo("version.json")
        if info.file_size > 1024 * 1024 or json.loads(archive.read(info)) != patched:
            raise SmokeError("Installed Forge profile differs from its hash-verified official installer")
    if (vanilla.get("id") != "1.20.1" or patched.get("inheritsFrom") != "1.20.1"
            or patched.get("id") != version or patched.get("mainClass") != "cpw.mods.bootstraplauncher.BootstrapLauncher"
            or patched["arguments"]["game"][:2] != ["--launchTarget", "forgeclient"]):
        raise SmokeError("Profiles do not describe the pinned production client")
    return vanilla, patched


def prepare(root: Path, java: str, cache: Path | None) -> None:
    if os.name != "nt":
        raise SmokeError("This diagnostic runtime preparation currently targets Windows x64")
    root = safe_path(root)
    if root == ROOT or root.is_relative_to(ROOT) or ROOT.is_relative_to(root):
        raise SmokeError("Runtime must be disjoint from the repository")
    root.mkdir(parents=True, exist_ok=False)
    write_json(root / "arce-runtime.json", {"schema_version": 1, "purpose": "disposable_v100_compatibility"})
    write_json(root / "launcher_profiles.json", {"profiles": {}})
    url = f"https://piston-meta.mojang.com/v1/packages/{MINECRAFT_PROFILE_SHA1}/1.20.1.json"
    vanilla = read_json(download(url, root / "versions/1.20.1/1.20.1.json", MINECRAFT_PROFILE_SHA1))
    client = vanilla["downloads"]["client"]
    download(client["url"], root / "versions/1.20.1/1.20.1.jar", client["sha1"], client["size"])
    for forge, sha1 in FORGE_HASHES.items():
        installer = download(f"https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-{forge}/forge-1.20.1-{forge}-installer.jar",
                             root / f"forge-{forge}-installer.jar", sha1)
        for side in ("client", "server"):
            target = root if side == "client" else root / "servers" / forge
            target.mkdir(parents=True, exist_ok=True)
            log = root / f"install-{forge}-{side}.txt"
            with log.open("x", encoding="utf-8") as stream:
                result = subprocess.run([java, "-Xmx1G", "-jar", str(installer), f"--install{side.title()}", str(target)],
                                        cwd=target, stdout=stream, stderr=subprocess.STDOUT, timeout=600)
            if result.returncode:
                raise SmokeError(f"Official installer exited {result.returncode}; see {log}")
            print(f"[PASS] Official Forge {forge} {side} installer", flush=True)
        _, patched = profile(root, forge)
        for record in libraries(vanilla, patched):
            download(record["url"], child(root / "libraries", record["path"]), record["sha1"], record["size"])
    index = vanilla["assetIndex"]
    assets = root / "assets"
    objects = read_json(download(index["url"], child(assets, f"indexes/{index['id']}.json"), index["sha1"], index["size"]))["objects"]
    if len(objects) > 10000:
        raise SmokeError("Asset index exceeds diagnostic bound")
    for record in objects.values():
        digest = record["hash"]
        if re.fullmatch(r"[a-f0-9]{40}", digest) is None:
            raise SmokeError("Invalid asset hash")
        relative = f"objects/{digest[:2]}/{digest}"
        target = child(assets, relative)
        cached = child(cache, relative) if cache else None
        if not target.exists() and cached and cached.is_file():
            verify_file(cached, digest, record["size"])
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(cached, target)
        download(f"https://resources.download.minecraft.net/{digest[:2]}/{digest}", target, digest, record["size"])
    url = f"https://maven.blamejared.com/mezz/jei/jei-1.20.1-forge/{JEI}/jei-1.20.1-forge-{JEI}.jar"
    download(url, root / "jei.jar", JEI_SHA1)
    write_json(root / "prepared.json", {"schema_version": 1, "forge": list(FORGE_HASHES), "jei": JEI,
                                       "jei_sha256": digest_file(root / "jei.jar"), "asset_count": len(objects)})


def expand(values: list, substitutions: dict[str, str]) -> list[str]:
    output = []
    for value in values:
        if isinstance(value, dict):
            if not allowed(value.get("rules")):
                continue
            value = value["value"]
        for item in value if isinstance(value, list) else [value]:
            for key, replacement in substitutions.items():
                item = item.replace("${" + key + "}", replacement)
            if "${" in item or any(char in item for char in "\r\n\0"):
                raise SmokeError("Unresolved or unsafe launcher argument")
            output.append(item)
    return output


def client_command(root: Path, game: Path, forge: str, username: str, port: int, java: str) -> list[str]:
    if re.fullmatch(r"[A-Za-z0-9_]{3,16}", username) is None or not 1 <= port <= 65535:
        raise SmokeError("Invalid diagnostic player or loopback port")
    vanilla, patched = profile(root, forge)
    classpath = []
    natives = game / "natives"
    natives.mkdir(exist_ok=False)
    native_bytes = 0
    native_count = 0
    for record in libraries(vanilla, patched):
        path = child(root / "libraries", record["path"])
        verify_file(path, record["sha1"], record["size"])
        classpath.append(str(path))
        if not path.name.endswith("-natives-windows.jar"):
            continue
        with zipfile.ZipFile(path) as archive:
            if len(archive.infolist()) > 256:
                raise SmokeError("Native archive entry count exceeds diagnostic bound")
            for info in archive.infolist():
                if not info.filename.lower().endswith(".dll"):
                    continue
                native = re.fullmatch(r"(?:windows/x64/org/lwjgl/(?:[A-Za-z0-9_]+/)*)?([A-Za-z0-9_]+\.dll)", info.filename)
                if info.file_size > 32 * 1024 * 1024 or native is None:
                    raise SmokeError("Unexpected native library entry")
                native_bytes += info.file_size
                native_count += 1
                if native_bytes > 128 * 1024 * 1024 or native_count > 64:
                    raise SmokeError("Native extraction exceeds diagnostic bound")
                target = child(natives, native.group(1))
                data = archive.read(info)
                if target.exists() and target.read_bytes() != data:
                    raise SmokeError("Conflicting native libraries")
                target.write_bytes(data)
    client = root / "versions/1.20.1/1.20.1.jar"
    verify_file(client, vanilla["downloads"]["client"]["sha1"], vanilla["downloads"]["client"]["size"])
    classpath.append(str(client))
    offline_id = str(uuid.UUID(bytes=hashlib.md5(f"OfflinePlayer:{username}".encode()).digest(), version=3))
    substitutions = {"auth_player_name": username, "version_name": patched["id"], "game_directory": str(game),
                     "assets_root": str(root / "assets"), "assets_index_name": vanilla["assetIndex"]["id"],
                     "auth_uuid": offline_id, "auth_access_token": "0", "clientid": "0", "auth_xuid": "0",
                     "user_type": "legacy", "version_type": "release", "natives_directory": str(natives),
                     "launcher_name": "arce-disposable-test", "launcher_version": "1", "classpath": os.pathsep.join(classpath),
                     "library_directory": str(root / "libraries"), "classpath_separator": os.pathsep}
    jvm = expand([*vanilla["arguments"]["jvm"], *patched["arguments"]["jvm"]], substitutions)
    args = expand([*vanilla["arguments"]["game"], *patched["arguments"]["game"]], substitutions)
    return [java, "-Xms512M", "-Xmx3G", *jvm, patched["mainClass"], *args,
            "--width", "960", "--height", "540", "--quickPlayMultiplayer", f"127.0.0.1:{port}"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("--java", required=True)
    parser.add_argument("--asset-cache", type=Path)
    args = parser.parse_args()
    try:
        java, _ = resolve_java(args.java)
        prepare(args.root, java, args.asset_cache)
        print("[PASS] Isolated packaged runtimes prepared; no account profile read")
        return 0
    except (OSError, ValueError, KeyError, StopIteration, subprocess.SubprocessError, SmokeError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
