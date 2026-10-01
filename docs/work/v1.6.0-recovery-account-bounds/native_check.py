"""Finite, disposable packaged-server checks for the F01 account-repair boundary."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import shutil
import socket
import struct
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO / "scripts"))
import inspect_celestial_saved_data as nbt  # noqa: E402
import run_dedicated_server_smoke as server  # noqa: E402
from run_v050_rocket_server_smoke import _server_command  # noqa: E402

HOST = "advancedrocketrycommunity"
REGISTRY = HOST + "_satellite_missions.dat"
JAVA = "C:/Program Files/Java/jdk-17.0.7/bin/java.exe"
# This probe's account root has more tags than a celestial-only fixture, but remains bounded.
nbt.MAX_TAGS = 40_000


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def named(kind: int, name: str, payload: bytes) -> bytes:
    text = name.encode("ascii")
    return bytes((kind,)) + struct.pack(">H", len(text)) + text + payload


def integer(name: str, value: int) -> bytes:
    return named(3, name, struct.pack(">i", value))


def long(name: str, value: int) -> bytes:
    return named(4, name, struct.pack(">q", value))


def text(name: str, value: str) -> bytes:
    raw = value.encode("ascii")
    return named(8, name, struct.pack(">H", len(raw)) + raw)


def compound(name: str, *fields: bytes) -> bytes:
    return named(10, name, b"".join(fields) + b"\0")


def uuid(name: str, high: int, low: int) -> bytes:
    return named(11, name, struct.pack(">i4i", 4, 0, high, 0, low))


def listing(name: str, entries: list[bytes], kind: int = 10) -> bytes:
    return named(9, name, bytes((kind,)) + struct.pack(">i", len(entries)) + b"".join(entries))


def seed(accounts: int) -> bytes:
    rows = [(integer("schema_version", 1) + uuid("owner_id", 1, index) + integer("balance", 11)
             + long("lifetime_earned", 19) + long("lifetime_spent", 8) + b"\0")
            for index in range(accounts)]
    satellite = (integer("schema_version", 2) + uuid("satellite_id", 2, 0)
                 + text("definition_id", HOST + ":data_satellite") + uuid("owner_id", 3, 0)
                 + long("launched_at", 0) + text("status", "operational") + text("kind", "data")
                 + compound("blueprint", named(1, "legacy", b"\1"), listing("components", [], 8),
                            integer("power", 4), integer("battery", 720), integer("data", 1000),
                            integer("cargo", 0), integer("rating", 0))
                 + compound("kind_state") + b"\0")
    payload = compound("data", integer("schema_version", 3), text("format_epoch", "v1.6.0-satellite-missions"),
                       long("save_epoch", 1), compound("clock", long("logical_game_time", 0),
                                                       long("last_observed_game_time", 0)),
                       listing("satellites", [satellite]), listing("missions", []),
                       listing("research_accounts", rows), listing("instances", []))
    return gzip.compress(compound("", payload, integer("DataVersion", 3465)), mtime=0)


def read(path: Path) -> dict:
    raw = path.read_bytes()
    require(len(raw) < 1024 * 1024, "Fixture exceeds compressed byte budget")
    expanded = gzip.decompress(raw)
    require(len(expanded) < 1024 * 1024, "Fixture exceeds expanded byte budget")
    return nbt.NbtReader(expanded).read_root()["data"]


def prepare(source: Path, destination: Path, jar: Path, accounts: int) -> tuple[int, Path]:
    require(source.is_dir() and not source.is_symlink(), "Installed server source is missing/linked")
    require(not destination.exists(), "Refusing to overwrite an existing server")
    require((source / "eula.txt").read_text().find("eula=true") >= 0, "Source has no accepted EULA")
    destination.mkdir()
    shutil.copytree(source / "libraries", destination / "libraries")
    shutil.copyfile(source / "eula.txt", destination / "eula.txt")
    (destination / "mods").mkdir()
    shutil.copyfile(jar, destination / "mods" / jar.name)
    require(digest(jar) == digest(destination / "mods" / jar.name), "Installed JAR differs")
    with socket.socket() as lease:
        lease.bind(("127.0.0.1", 0))
        port = lease.getsockname()[1]
    (destination / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "enable-rcon=false\nlevel-name=world\nlevel-type=minecraft:flat\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
        '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],'
        '"biome":"minecraft:plains","lakes":false,"features":false,"structure_overrides":[]}\n'
        "view-distance=2\nsimulation-distance=2\nspawn-protection=0\n", encoding="ascii")
    data = destination / "world" / "data"
    data.mkdir(parents=True)
    file = data / REGISTRY
    file.write_bytes(seed(accounts))
    return port, file


def accepted_cycle(directory: Path, evidence: Path, name: str) -> dict:
    out = evidence / name
    out.mkdir()
    process = server.CapturedProcess(_server_command(JAVA), directory, out / "stdout.txt")
    try:
        process.wait_for(server.READY_MARKER, 240)
        start = len(process.lines)
        process.command("arce satellite admin evidence")
        process.wait_for(re.compile("ARCE_SATELLITE_EVIDENCE satellites=1 missions=0"), 30, start_at=start)
        start = len(process.lines)
        process.command("save-all flush")
        process.wait_for(server.SAVE_MARKER, 120, start_at=start)
        process.command("stop")
        code = process.finish(120)
        require(code == 0, "Dedicated server did not stop cleanly")
        findings = server.scan_log(process.lines)
        # Pre-start validation and the runtime load may each emit this bounded repair diagnostic.
        restore = re.compile(r"ARCE_SATELLITE_RESTORE quarantined_missions=0 recovery_required=0 "
                             r"quarantined_instances=0 accounts_added=1$")
        expected = [line for line in findings if name == "repair" and restore.search(line)]
        unexpected = [line for line in findings if line not in expected]
        require(len(expected) <= 2 and not unexpected, "Strict log findings: " + repr(unexpected[:8]))
        return {"phase": name, "exit_code": code, "strict_log_findings": unexpected,
                "expected_repair_diagnostics": expected}
    finally:
        if process.process.poll() is None:
            process.abort()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--installed-server", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    require(not output.exists(), "Output must be a new directory")
    output.mkdir(parents=True)
    jar = REPO / "build/libs/advancedrocketry-community-1.20.1-1.6.0-dev.jar"
    summary = {"base_commit": "ab10fb53a580e53a9a1c24097a487f2a7c54ad52", "artifact_sha256": digest(jar),
               "environment": "packaged offline loopback, no real clients", "result": "IN_PROGRESS", "phases": []}
    try:
        _, accepted = prepare(args.installed_server.resolve(), output / "accepted-server", jar, 4095)
        summary["accepted_input_sha256"] = digest(accepted)
        summary["phases"].append(accepted_cycle(output / "accepted-server", output, "repair"))
        repaired = read(accepted)
        require(len(repaired["research_accounts"]) == 4096, "Repair did not keep the account count within 4096")
        owners = {tuple(row["owner_id"]): row for row in repaired["research_accounts"]}
        require(owners[(0, 3, 0, 0)]["balance"] == 0, "Missing owner's new account differs")
        require(owners[(0, 1, 0, 0)]["balance"] == 11, "Existing research account changed")
        summary["phases"].append(accepted_cycle(output / "accepted-server", output, "restart"))
        restarted = read(accepted)
        require(repaired["research_accounts"] == restarted["research_accounts"], "Restart changed accounts")
        require(repaired["satellites"] == restarted["satellites"], "Restart changed satellites")
        require(repaired["save_epoch"] == restarted["save_epoch"], "Repeated repair advanced save epoch")
        summary["accepted_final_sha256"] = digest(accepted)

        _, blocked = prepare(args.installed_server.resolve(), output / "blocked-server", jar, 4096)
        before = digest(blocked)
        process = server.CapturedProcess(_server_command(JAVA), output / "blocked-server", output / "blocked-stdout.txt")
        try:
            code = process.finish(180)
            # Forge may exit 0 after a pre-start exception; exit code alone is not the storage oracle.
            diagnostic = ("[ARCE-BETA-2000] Beta world data check blocked startup: "
                          + HOST + "_satellite_missions failed semantic validation.")
            require(any(diagnostic in line for line in process.lines),
                    "Startup did not reject this satellite registry in semantic validation")
            require(not any(server.READY_MARKER.search(line) for line in process.lines), "Blocked server became ready")
            require(digest(blocked) == before, "Blocked startup changed the original file bytes")
            summary["phases"].append({"phase": "blocked", "exit_code": code, "original_file_sha256": before,
                                      "strict_log_findings": server.scan_log(process.lines)})
        finally:
            if process.process.poll() is None:
                process.abort()
        summary["result"] = "PASS_SCOPED_S1"
        print(json.dumps(summary, indent=2))
        return 0
    except Exception as error:
        summary.update(result="FAIL", error=f"{type(error).__name__}: {error}")
        print(json.dumps(summary, indent=2))
        return 1
    finally:
        (output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
