#!/usr/bin/env python3
"""Install and exercise a disposable Forge dedicated server with the built mod."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import platform
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import BinaryIO, Iterable


ROOT = Path(__file__).resolve().parents[1]
MINECRAFT_VERSION = "1.20.1"
MINECRAFT_PROTOCOL = 763
FORGE_VERSION = "47.4.10"
FORGE_COORDINATE = f"{MINECRAFT_VERSION}-{FORGE_VERSION}"
MOD_ID = "advancedrocketrycommunity"
MOD_VERSION = "1.20.1-0.0.2-dev"
ARTIFACT_NAME = f"advancedrocketry-community-{MOD_VERSION}.jar"
INSTALLER_NAME = f"forge-{FORGE_COORDINATE}-installer.jar"
INSTALLER_URL = (
    "https://maven.minecraftforge.net/net/minecraftforge/forge/"
    f"{FORGE_COORDINATE}/{INSTALLER_NAME}"
)
INSTALLER_SHA1 = "66bfea9963bfa60d88bab6b2750e74a958392715"
READY_MARKER = re.compile(r"Done \([^)]+\)! For help, type \"help\"")
SAVE_MARKER = re.compile(r"Saved the game")
ERROR_LINE = re.compile(r"\[[^\]]+/ERROR\]")
CLIENT_LINKAGE_MARKERS = (
    "Attempted to load class net/minecraft/client",
    "NoClassDefFoundError: net/minecraft/client",
    "NoClassDefFoundError: net.minecraft.client",
    "ClassNotFoundException: net.minecraft.client",
)


class SmokeError(RuntimeError):
    """A deterministic smoke-test failure."""


def digest_file(path: Path, algorithm: str = "sha256") -> str:
    digest = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def encode_varint(value: int) -> bytes:
    if not 0 <= value <= 0x7FFFFFFF:
        raise ValueError("VarInt value must be between 0 and 2^31 - 1")
    encoded = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        encoded.append(byte | (0x80 if value else 0))
        if not value:
            return bytes(encoded)


def read_varint(stream: BinaryIO) -> int:
    value = 0
    for offset in range(0, 35, 7):
        raw = stream.read(1)
        if not raw:
            raise SmokeError("Minecraft status response ended inside a VarInt")
        byte = raw[0]
        value |= (byte & 0x7F) << offset
        if not byte & 0x80:
            return value
    raise SmokeError("Minecraft status response contains an oversized VarInt")


def packet(payload: bytes) -> bytes:
    return encode_varint(len(payload)) + payload


def query_server_status(host: str, port: int, timeout: float = 5.0) -> dict:
    address = host.encode("utf-8")
    handshake = b"".join(
        (
            encode_varint(0),
            encode_varint(MINECRAFT_PROTOCOL),
            encode_varint(len(address)),
            address,
            port.to_bytes(2, "big"),
            encode_varint(1),
        )
    )
    request = packet(handshake) + packet(encode_varint(0))

    with socket.create_connection((host, port), timeout=timeout) as connection:
        connection.settimeout(timeout)
        connection.sendall(request)
        with connection.makefile("rb") as response:
            read_varint(response)
            if read_varint(response) != 0:
                raise SmokeError("Minecraft status response used an unexpected packet ID")
            length = read_varint(response)
            payload = response.read(length)
            if len(payload) != length:
                raise SmokeError("Minecraft status JSON was truncated")
    try:
        parsed = json.loads(payload.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise SmokeError(f"Minecraft status JSON is invalid: {exc}") from exc
    if not isinstance(parsed, dict):
        raise SmokeError("Minecraft status response is not an object")
    return parsed


def forge_mod_versions(status: dict) -> dict[str, str]:
    forge_data = status.get("forgeData", {})
    if not isinstance(forge_data, dict):
        return {}
    mods = forge_data.get("mods", [])
    if not isinstance(mods, list):
        return {}
    legacy = {
        str(mod.get("modId")): str(mod.get("modmarker", ""))
        for mod in mods
        if isinstance(mod, dict) and mod.get("modId")
    }
    optimized = forge_data.get("d")
    return decode_optimized_forge_data(optimized) if isinstance(optimized, str) else legacy


def read_utf(stream: BinaryIO) -> str:
    length = read_varint(stream)
    raw = stream.read(length)
    if len(raw) != length:
        raise SmokeError("Forge status data ended inside a UTF-8 string")
    try:
        return raw.decode("utf-8")
    except UnicodeError as exc:
        raise SmokeError(f"Forge status data contains invalid UTF-8: {exc}") from exc


def decode_optimized_forge_data(value: str) -> dict[str, str]:
    if len(value) < 2:
        raise SmokeError("Forge optimized status data is missing its size prefix")
    size = ord(value[0]) | (ord(value[1]) << 15)
    buffer = 0
    bits = 0
    decoded = bytearray()
    for character in value[2:]:
        buffer |= (ord(character) & 0x7FFF) << bits
        bits += 15
        while bits >= 8 and len(decoded) < size:
            decoded.append(buffer & 0xFF)
            buffer >>= 8
            bits -= 8
    while len(decoded) < size and bits > 0:
        decoded.append(buffer & 0xFF)
        buffer >>= 8
        bits -= 8
    if len(decoded) != size:
        raise SmokeError(
            f"Forge optimized status data decoded to {len(decoded)} bytes, expected {size}"
        )

    stream = io.BytesIO(decoded)
    if not stream.read(1):
        raise SmokeError("Forge optimized status data is empty")
    raw_mod_count = stream.read(2)
    if len(raw_mod_count) != 2:
        raise SmokeError("Forge optimized status data has no mod count")
    mod_count = int.from_bytes(raw_mod_count, "big")
    result: dict[str, str] = {}
    for _ in range(mod_count):
        channel_flags = read_varint(stream)
        channel_count = channel_flags >> 1
        ignores_server_only = bool(channel_flags & 1)
        mod_id = read_utf(stream)
        version = "IGNORE_SERVER_VERSION" if ignores_server_only else read_utf(stream)
        result[mod_id] = version
        for _ in range(channel_count):
            read_utf(stream)
            read_utf(stream)
            if not stream.read(1):
                raise SmokeError("Forge optimized status data ended inside channel metadata")
    non_mod_channels = read_varint(stream)
    for _ in range(non_mod_channels):
        read_utf(stream)
        read_utf(stream)
        if not stream.read(1):
            raise SmokeError("Forge optimized status data ended inside channel metadata")
    return result


def scan_log(lines: Iterable[str]) -> list[str]:
    findings: list[str] = []
    for line in lines:
        stripped = line.rstrip()
        if ERROR_LINE.search(stripped):
            findings.append(stripped)
        elif any(marker in stripped for marker in CLIENT_LINKAGE_MARKERS):
            findings.append(stripped)
    return findings


def evidence_lines(lines: Iterable[str]) -> list[str]:
    markers = (
        "Advanced Rocketry: Community Edition",
        "Starting minecraft server version",
        "Starting Minecraft server on",
        "Preparing level",
        "Done (",
        "There are ",
        "Saving the game",
        "Saved the game",
        "Stopping server",
        "Saving players",
        "Saving worlds",
    )
    selected = [line.rstrip() for line in lines if any(marker in line for marker in markers)]
    return selected or ["No evidence markers were selected from the captured log."]


def extract_java_version(output: str) -> str:
    match = re.search(r'(?:java|openjdk) version "([^"]+)"', output)
    if not match:
        raise SmokeError("Could not parse the Java runtime version")
    version = match.group(1)
    major = int(version.split(".", 1)[0])
    if major != 17:
        raise SmokeError(f"Java 17 is required; selected runtime is {version}")
    return version


def resolve_java(value: str) -> tuple[str, str]:
    candidate = Path(value)
    resolved = str(candidate.resolve()) if candidate.is_file() else shutil.which(value)
    if not resolved:
        raise SmokeError(f"Java executable was not found: {value}")
    completed = subprocess.run(
        [resolved, "-version"],
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=30,
    )
    output = (completed.stdout + completed.stderr).strip()
    if completed.returncode != 0:
        raise SmokeError(f"Java version check failed: {output}")
    return resolved, extract_java_version(output)


def download_installer(target: Path) -> Path:
    if target.is_file():
        actual = digest_file(target, "sha1")
        if actual != INSTALLER_SHA1:
            raise SmokeError(
                f"Cached Forge installer SHA-1 is {actual}, expected {INSTALLER_SHA1}: {target}"
            )
        return target

    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name(f"{target.name}.partial-{os.getpid()}")
    request = urllib.request.Request(
        INSTALLER_URL,
        headers={"User-Agent": "AdvancedRocketry-Community-v0.0.2-smoke"},
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response, partial.open("xb") as output:
            shutil.copyfileobj(response, output)
    except (OSError, urllib.error.URLError) as exc:
        raise SmokeError(f"Could not download the Forge installer: {exc}") from exc

    actual = digest_file(partial, "sha1")
    if actual != INSTALLER_SHA1:
        raise SmokeError(
            f"Downloaded Forge installer SHA-1 is {actual}, expected {INSTALLER_SHA1}"
        )
    partial.replace(target)
    return target


def allocate_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as candidate:
        candidate.bind(("127.0.0.1", 0))
        return int(candidate.getsockname()[1])


def create_session(work_root: Path, requested: Path | None) -> Path:
    if requested:
        session = requested.resolve()
    else:
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        session = (work_root / f"run-{stamp}-{os.getpid()}").resolve()
    if session.exists():
        raise SmokeError(f"Refusing to reuse an existing server session: {session}")
    session.mkdir(parents=True)
    return session


@dataclass
class ServerCycle:
    name: str
    lines: list[str]
    status: dict
    exit_code: int


class CapturedProcess:
    def __init__(self, command: list[str], cwd: Path, log_path: Path):
        self.process = subprocess.Popen(
            command,
            cwd=cwd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
        self.lines: list[str] = []
        self.condition = threading.Condition()
        self.log_stream = log_path.open("x", encoding="utf-8", newline="\n")
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()

    def _read(self) -> None:
        assert self.process.stdout is not None
        try:
            for line in self.process.stdout:
                self.log_stream.write(line)
                self.log_stream.flush()
                with self.condition:
                    self.lines.append(line)
                    self.condition.notify_all()
        finally:
            with self.condition:
                self.condition.notify_all()

    def wait_for(self, marker: re.Pattern[str], timeout: float) -> None:
        deadline = time.monotonic() + timeout
        checked = 0
        with self.condition:
            while True:
                for line in self.lines[checked:]:
                    if marker.search(line):
                        return
                checked = len(self.lines)
                code = self.process.poll()
                if code is not None:
                    tail = "".join(self.lines[-20:]).strip()
                    raise SmokeError(
                        f"Server exited with code {code} before {marker.pattern!r}\n{tail}"
                    )
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise SmokeError(f"Timed out waiting for server marker {marker.pattern!r}")
                self.condition.wait(min(remaining, 1.0))

    def command(self, value: str) -> None:
        if not self.process.stdin:
            raise SmokeError("Server stdin is unavailable")
        self.process.stdin.write(value + "\n")
        self.process.stdin.flush()

    def finish(self, timeout: float = 90.0) -> int:
        try:
            code = self.process.wait(timeout=timeout)
        except subprocess.TimeoutExpired as exc:
            self.process.terminate()
            try:
                self.process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=15)
            raise SmokeError("Server did not stop after the stop command") from exc
        finally:
            self.reader.join(timeout=5)
            self.log_stream.close()
        return code

    def abort(self) -> None:
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=15)
        self.reader.join(timeout=5)
        self.log_stream.close()


def wait_for_status(port: int, timeout: float = 30.0) -> dict:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            return query_server_status("127.0.0.1", port)
        except (OSError, SmokeError) as exc:
            last_error = exc
            time.sleep(0.25)
    raise SmokeError(f"Server status query did not succeed: {last_error}")


def run_cycle(
    name: str,
    java: str,
    server: Path,
    port: int,
    startup_timeout: float,
) -> ServerCycle:
    args_name = "win_args.txt" if platform.system() == "Windows" else "unix_args.txt"
    args_file = Path("libraries/net/minecraftforge/forge") / FORGE_COORDINATE / args_name
    command = [
        java,
        "-Xms512M",
        "-Xmx1024M",
        "-Djava.net.preferIPv4Stack=true",
        f"@{args_file.as_posix()}",
        "nogui",
    ]
    captured = CapturedProcess(command, server, server / f"{name}-full.txt")
    try:
        captured.wait_for(READY_MARKER, startup_timeout)
        status = wait_for_status(port)
        (server / f"{name}-status.json").write_text(
            json.dumps(status, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
            newline="\n",
        )
        mods = forge_mod_versions(status)
        if mods.get(MOD_ID) != MOD_VERSION:
            raise SmokeError(
                f"Status response mod marker is {mods.get(MOD_ID)!r}, expected {MOD_VERSION!r}"
            )
        captured.command("list")
        captured.command("save-all flush")
        captured.wait_for(SAVE_MARKER, 60)
        captured.command("stop")
        code = captured.finish()
    except BaseException:
        captured.abort()
        raise

    if code != 0:
        raise SmokeError(f"Server {name} exited with code {code}")
    findings = scan_log(captured.lines)
    if findings:
        raise SmokeError(f"Server {name} log contains errors: {findings[0]}")
    if not any("Advanced Rocketry: Community Edition" in line for line in captured.lines):
        raise SmokeError(f"Server {name} log does not contain the project initialization line")
    return ServerCycle(name, list(captured.lines), status, code)


def install_server(
    java: str,
    installer: Path,
    server: Path,
    timeout: float,
    max_attempts: int,
) -> tuple[Path, int]:
    if max_attempts < 1:
        raise SmokeError("Forge installer attempts must be at least 1")
    for attempt in range(1, max_attempts + 1):
        log_path = server / f"installer-attempt-{attempt}-full.txt"
        completed = subprocess.run(
            [java, "-jar", str(installer), "--installServer", str(server)],
            cwd=server,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
        )
        log_path.write_text(
            completed.stdout + completed.stderr,
            encoding="utf-8",
            newline="\n",
        )
        if completed.returncode == 0:
            break
        if attempt == max_attempts:
            raise SmokeError(
                f"Forge installer failed after {max_attempts} attempts; see {log_path}"
            )
        print(
            f"[WARN] Forge installer attempt {attempt} exited with code "
            f"{completed.returncode}; retrying with validated downloads retained",
            flush=True,
        )
    args_name = "win_args.txt" if platform.system() == "Windows" else "unix_args.txt"
    args_file = server / "libraries" / "net" / "minecraftforge" / "forge" / FORGE_COORDINATE / args_name
    if not args_file.is_file():
        raise SmokeError(f"Forge installer did not create {args_file}")
    return args_file, attempt


def write_server_configuration(server: Path, port: int, offline_mode: bool) -> None:
    (server / "eula.txt").write_text(
        "# Disposable automated test instance\neula=true\n",
        encoding="utf-8",
        newline="\n",
    )
    properties = {
        "difficulty": "peaceful",
        "enable-command-block": "false",
        "enforce-secure-profile": "false" if offline_mode else "true",
        "generate-structures": "false",
        "level-name": "world",
        "level-type": "minecraft:normal",
        "max-players": "2",
        "motd": "ARCE v0.0.2 dedicated smoke",
        "online-mode": "false" if offline_mode else "true",
        "server-ip": "127.0.0.1",
        "server-port": str(port),
        "simulation-distance": "2",
        "spawn-protection": "0",
        "sync-chunk-writes": "true",
        "view-distance": "2",
    }
    content = "".join(f"{key}={value}\n" for key, value in sorted(properties.items()))
    (server / "server.properties").write_text(content, encoding="utf-8", newline="\n")


def write_evidence(directory: Path, summary: dict, cycles: list[ServerCycle]) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    targets = [directory / "summary.json"] + [directory / f"{cycle.name}.txt" for cycle in cycles]
    existing = [path for path in targets if path.exists()]
    if existing:
        raise SmokeError(f"Refusing to overwrite existing evidence: {existing[0]}")
    (directory / "summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    for cycle in cycles:
        lines = [
            f"cycle={cycle.name}",
            f"exit_code={cycle.exit_code}",
            f"status_version={cycle.status.get('version', {}).get('name', '')}",
            f"status_protocol={cycle.status.get('version', {}).get('protocol', '')}",
            f"mod_marker={forge_mod_versions(cycle.status).get(MOD_ID, '')}",
            "",
            *evidence_lines(cycle.lines),
        ]
        (directory / f"{cycle.name}.txt").write_text(
            "\n".join(lines) + "\n",
            encoding="utf-8",
            newline="\n",
        )


def parse_args() -> argparse.Namespace:
    java_home = os.environ.get("JAVA_HOME")
    default_java = str(Path(java_home) / "bin" / "java") if java_home else "java"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "artifact",
        nargs="?",
        type=Path,
        default=ROOT / "build" / "libs" / ARTIFACT_NAME,
    )
    parser.add_argument("--java", default=default_java)
    parser.add_argument("--work-root", type=Path, default=ROOT / "build" / "dedicated-server-smoke")
    parser.add_argument("--session-dir", type=Path)
    parser.add_argument("--installer", type=Path)
    parser.add_argument("--evidence-dir", type=Path)
    parser.add_argument("--port", type=int)
    parser.add_argument("--offline-mode", action="store_true")
    parser.add_argument("--install-attempts", type=int, default=3)
    parser.add_argument("--install-timeout", type=float, default=600)
    parser.add_argument("--startup-timeout", type=float, default=240)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    session: Path | None = None
    started_at = datetime.now(timezone.utc)
    try:
        java, java_version = resolve_java(args.java)
        artifact = args.artifact.resolve()
        if not artifact.is_file() or artifact.name != ARTIFACT_NAME:
            raise SmokeError(f"Expected built artifact {ARTIFACT_NAME}: {artifact}")
        artifact_sha256 = digest_file(artifact)
        session = create_session(args.work_root.resolve(), args.session_dir)
        installer = args.installer.resolve() if args.installer else args.work_root.resolve() / "cache" / INSTALLER_NAME
        installer = download_installer(installer)
        _, installer_attempts = install_server(
            java,
            installer,
            session,
            args.install_timeout,
            args.install_attempts,
        )
        mods = session / "mods"
        mods.mkdir()
        shutil.copy2(artifact, mods / artifact.name)
        port = args.port or allocate_port()
        if not 1 <= port <= 65535:
            raise SmokeError(f"Server port is outside 1-65535: {port}")
        write_server_configuration(session, port, args.offline_mode)

        first = run_cycle("first-start", java, session, port, args.startup_timeout)
        level_data = session / "world" / "level.dat"
        if not level_data.is_file():
            raise SmokeError("First server cycle did not create world/level.dat")
        restart = run_cycle("restart", java, session, port, args.startup_timeout)
        cycles = [first, restart]

        summary = {
            "artifact": artifact.name,
            "artifact_sha256": artifact_sha256,
            "completed_at": datetime.now(timezone.utc).isoformat(),
            "cycles": [
                {
                    "exit_code": cycle.exit_code,
                    "mod_marker": forge_mod_versions(cycle.status).get(MOD_ID),
                    "name": cycle.name,
                    "status_protocol": cycle.status.get("version", {}).get("protocol"),
                    "status_version": cycle.status.get("version", {}).get("name"),
                }
                for cycle in cycles
            ],
            "forge": FORGE_VERSION,
            "installer_sha1": digest_file(installer, "sha1"),
            "installer_sha256": digest_file(installer),
            "installer_attempts": installer_attempts,
            "java": java_version,
            "minecraft": MINECRAFT_VERSION,
            "offline_mode": args.offline_mode,
            "platform": platform.platform(),
            "started_at": started_at.isoformat(),
            "world_level_dat": True,
        }
        evidence_dir = args.evidence_dir.resolve() if args.evidence_dir else session / "evidence"
        write_evidence(evidence_dir, summary, cycles)
        print(f"[PASS] Forge installer SHA-1: {summary['installer_sha1']}")
        print(f"[PASS] Artifact SHA-256: {artifact_sha256}")
        print(f"[PASS] First start, status query, save, and clean stop on port {port}")
        print("[PASS] Same-world restart, status query, save, and clean stop")
        print(f"[PASS] Evidence: {evidence_dir}")
        print(f"[INFO] Full disposable server session: {session}")
        return 0
    except (OSError, SmokeError, subprocess.SubprocessError, ValueError) as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        if session:
            print(f"[INFO] Failed disposable server session: {session}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
