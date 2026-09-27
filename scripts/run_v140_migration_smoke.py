#!/usr/bin/env python3
"""Upgrade a retained v1.3 world COPY, remove planetary content and restore it.

Four finite clean processes; original history and all intermediate captures are
retained. This is not a real-player, process-kill or sustained-load campaign.
"""
from __future__ import annotations

import argparse
import json
import platform
import re
import shutil
import sys
import time
import uuid
from pathlib import Path

if __package__:
    from . import v140_migration_fixture as fixture
else:
    import v140_migration_fixture as fixture

worlds, legacy = fixture.worlds, fixture.legacy
support, server, schema = fixture.support, fixture.server, fixture.schema
require, write_json, HOST = fixture.require, fixture.write_json, fixture.HOST
PHASES = ("legacy-copy", "upgrade-remove", "removed-restore", "restored-restart")


class MigrationRun(worlds.Harness):
    def __init__(self, root, output, java, port, old, host, consumer):
        super().__init__(root, output, java, port)
        self.history, self.host, self.consumer = old, host, consumer
        self.pending = self.restored = None

    def assemble_witness(self, process):
        # Disjoint from the original terminal at 264,101,264.
        x, y, z = 520, 101, 520
        process.command("forceload add 518 519 522 522")
        support.wait_condition(process, "execute if loaded 520 100 520", "MIG02_WITNESS", timeout=30)
        self.condition(process, "if block 520 100 520 minecraft:air", "WITNESS_SPACE")
        for index, (dx, dy, dz) in enumerate(worlds.PARTS):
            self.condition(process, f"if block {x+dx} {y+dy} {z+dz} minecraft:air", f"WITNESS_PART_{index}")
        process.command(f"setblock {x} {y-1} {z} {HOST}:rocket_assembler")
        for (dx, dy, dz), block in worlds.PARTS.items():
            process.command(f"setblock {x+dx} {y+dy} {z+dz} {block}")
        process.command('data merge block 520 101 521 {Items:[{Slot:0b,id:"minecraft:diamond",Count:17b},'
                        '{Slot:1b,id:"minecraft:iron_ingot",Count:64b}]}')
        assembled = self.query(process, "arce rocket assemble 520 100 520", worlds.flight.ASSEMBLY_LOG, 45)
        require(assembled.group(1) == "6", "Witness structure differs")
        entity = assembled.group(3)
        support.rocket_smoke._wait_for_active_entity(process, entity)
        worlds.flight.FlightHarness.refuel(process, worlds.EARTH, entity)
        report = worlds.flight.FlightHarness.report(process, worlds.EARTH, entity)
        require(report["fuel"] == report["capacity"] == 2000 and report["state"] == "FUELED"
                and report["origin"] == [x, y, z] and report["snapshot"] == assembled.group(2), "Witness rocket differs")
        self.reports.append(report)

    def capture(self, directory, pending=None, *, closed_world=True):
        state = fixture.capture_state(self.root, directory / "state", closed_world=closed_world)
        fixture.preserve_history(self.history, state, closed_world=closed_world)
        worlds.validate_stations(state["stations"], self.station_requests)
        if self.stations is not None:
            require(all(old in state["stations"]["stations"] for old in self.stations["stations"]), "Old station changed")
        if state["bindings"] is not None:
            expected = [{"body_id": HOST + ":" + body, "level": level} for body, level in
                        (("earth", worlds.EARTH), ("moon", HOST + ":moon"), ("space", HOST + ":space"),
                         ("mars", HOST + ":mars"), ("venus", HOST + ":venus"))]
            expected.append({"body_id": HOST + ":gas_giant"})
            require(state["bindings"] == {"schema_version": 1, "bindings": sorted(expected, key=lambda row: row["body_id"])},
                    "Adoption/removal/restoration changed binding identities")
        if closed_world:
            data = worlds.capture_rocket(self.root, directory / "native", self.reports[-1], self.original, [])
            require(self.original is None or data == self.original, "Upgrade/removal/restart changed the saved rocket")
            self.original = data
        if pending is not None:
            fixture.check_claims(self.history, state, self.research, worlds.native.OWNER, pending, closed_world=closed_world)
        manifest = fixture.copy_world(self.root / fixture.PACK, directory / "probe-pack")
        write_json(directory / "probe-pack.json", manifest)
        return state

    def reload_content(self, process, removed, generation):
        routes = fixture.set_removal(self.root, self.host, removed)
        start = len(process.lines)
        self.query(process, "reload", rf"Accepted planetary catalog generation {generation} with {4 if removed else 6} bodies, {routes} routes", 60)
        process.wait_for(re.compile(rf"Accepted satellite catalog generation {generation} with 2 definitions"), 60, start_at=start)
        self.query(process, "arce celestial validate", rf"Celestial catalog generation {generation} is valid with {4 if removed else 6} bodies")

    def launch_research(self, process):
        start = len(process.lines)
        for body in ("mars", "venus"):
            match = self.query(process, f"arce satellite release-test launch {worlds.native.OWNER} {body}",
                    r"ARCE_RELEASE_TEST_SATELLITE_LAUNCH satellite=(\S+) mission=(\S+) owner=(\S+) target=(\S+) code=SUCCESS deadline=(\d+)")
            satellite, mission, owner, target, deadline = match.groups()
            require(owner == worlds.native.OWNER and target == HOST + ":" + body, "Mission launch identity differs")
            self.research.append({"satellite": str(uuid.UUID(satellite)), "mission": str(uuid.UUID(mission)),
                                  "owner": owner, "body": target, "deadline": int(deadline)})
        process.wait_for(re.compile(r"ARCE_SATELLITE_SCHEDULER completed=\d+ inspected=\d+ remaining=0"), 30, start_at=start)
        self.claim_research(process, self.research[0], "SUCCESS")

    def denied_content(self, process):
        report = self.reports[-1]
        self.query(process, f"arce rocket release-test launch-surface {report['entity']} {HOST}:mars",
                   "Release-test surface launch failed: INVALID_DESTINATION")
        self.query(process, f"arce rocket release-test launch-station {report['entity']} {self.station_requests[1]['station_id']}",
                   "Release-test station launch failed: INVALID_DESTINATION")
        require(worlds.flight.FlightHarness.report(process, worlds.EARTH, report["entity"]) == report, "Denied arrival changed rocket")
        self.query(process, f"arce station admin create {worlds.native.OWNER} {HOST}:mars Unavailable",
                   "Station creation failed: UNKNOWN_ORBIT_BODY")
        self.query(process, f"arce satellite release-test launch {worlds.native.OWNER} venus",
                   "Release-test satellite launch rejected: TARGET_NOT_ALLOWED")

    def phase_actions(self, process, phase, directory):
        if phase == "legacy-copy":
            self.create_station(process, "moon", "Legacy copy witness")
            self.assemble_witness(process)
            return
        previous = self.reports[-1]
        support.rocket_smoke._wait_for_active_entity(process, previous["entity"])
        require(worlds.flight.FlightHarness.report(process, worlds.EARTH, previous["entity"]) == previous
                and self.snbt(process) == self.previous_snbt, "Rocket changed across the process boundary")
        if phase == "upgrade-remove":
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            upgraded = self.capture(directory / "before-new-work", closed_world=False)
            fixture.same_authority(dict(self.legacy_saved, bindings=upgraded["bindings"]), upgraded, closed_world=False)
            self.query(process, "arce celestial list", re.escape(HOST + ":moon -> " + HOST + ":moon") + r" .*solar=1.0 radiation=0.0")
            self.launch_research(process)
            self.create_station(process, "mars", "Removed orbit witness")
            self.reload_content(process, True, 2)
            for _ in range(2):
                self.query(process, "arce satellite release-test claim " + self.research[1]["mission"],
                           "Release-test satellite claim rejected: CATALOG_UNAVAILABLE")
            self.denied_content(process)
        elif phase == "removed-restore":
            self.denied_content(process)
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            waiting = self.capture(directory / "before-restoration", True, closed_world=False)
            fixture.same_authority(self.pending, waiting, closed_world=False)
            self.reload_content(process, False, 2)
            # Observe production tick replay without sending another claim first.
            mission = self.research[1]["mission"]
            deadline = time.monotonic() + 10
            while True:
                observed = self.query(process, "arce satellite admin mission " + mission,
                                      rf"mission={mission} .* status=(\S+) deadline=")
                if observed.group(1) == "CLAIMED":
                    break
                require(observed.group(1) == "CLAIM_PENDING_DISCOVERY" and time.monotonic() < deadline,
                        "Automatic restored discovery did not finish within its observation bound")
                time.sleep(0.05)
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            automatic = self.capture(directory / "automatic-restoration", False, closed_world=False)
            fixture.check_completion(self.pending, automatic, self.research[1], closed_world=False)
            for mission in self.research:
                self.claim_research(process, mission, "ALREADY_CLAIMED")
            self.restored = automatic
        else:
            for mission in self.research:
                self.claim_research(process, mission, "ALREADY_CLAIMED")

    def cycle(self, phase, artifact):
        directory = self.output / phase
        directory.mkdir()
        command = support.rocket_smoke._server_command(self.java)
        write_json(directory / "launch.json", {"command": command, "cwd": str(self.root), "artifact": artifact})
        process, self.commands = None, []
        receipt = {"phase": phase, "result": "IN_PROGRESS"}
        try:
            self.check_installed(artifact)
            process = server.CapturedProcess(command, self.root, directory / "stdout.txt")
            original = process.command
            def recorded(value):
                self.commands.append(value); original(value)
            process.command = recorded
            process.wait_for(server.READY_MARKER, 240)
            status = server.wait_for_status(self.port)
            support.validate_status(status, True, artifact["version"])
            write_json(directory / "status.json", status)
            bodies = 3 if phase == "legacy-copy" else 4 if phase == "removed-restore" else 6
            self.query(process, "arce celestial validate", rf"Celestial catalog generation 1 is valid with {bodies} bodies")
            require(any("Accepted satellite catalog generation 1 with 2 definitions" in line for line in process.lines), "Satellite catalog not accepted")
            self.phase_actions(process, phase, directory)
            self.previous_snbt = self.snbt(process)
            (directory / "rocket-entity.snbt").write_text(self.previous_snbt, encoding="utf-8")
            self.query(process, "save-all flush", server.SAVE_MARKER, 60)
            process.command("stop"); require(process.finish() == 0, "Migration process did not exit cleanly")
            expected = []
            if phase == "removed-restore":
                marker = "ARCE_STATION_UNKNOWN_ORBIT_BODY station=" + self.station_requests[1]["station_id"]
                expected = [line.rstrip() for line in process.lines if line.rstrip().endswith(marker)]
                require(len(expected) == 1, "Missing-orbit startup diagnostic differs")
            schema.validate_log(process.lines, expected)
            self.check_installed(artifact)
            saved = self.capture(directory, None if phase == "legacy-copy" else phase == "upgrade-remove")
            self.stations = saved["stations"]
            if phase == "legacy-copy":
                require(saved["bindings"] is None, "v1.3 unexpectedly wrote a v1.4 binding ledger")
                self.legacy_saved = saved
                fixture.copy_world(self.root / "world", directory / "world-backup")
            elif phase == "upgrade-remove":
                self.pending = saved
            else:
                fixture.same_authority(self.restored, saved, closed_world=False)
                self.restored = saved
            configuration = worlds.native.configuration_identity("server.properties", support.regular(self.root / "server.properties", 65536))
            require(self.configuration is None or configuration == self.configuration, "Server configuration changed")
            self.configuration = configuration
            for relative in ("world/level.dat", "server.properties", "eula.txt"):
                target = directory / "inputs" / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(support.regular(self.root / relative, 4 * 1024**2))
            receipt.update(result="PASS", expected_errors=expected, log_counts=server.log_audit_counts(process.lines),
                           reports=self.reports, station_requests=self.station_requests, research=self.research,
                           configuration=configuration)
        except BaseException as exc:
            receipt.update(result="FAIL", error=f"{type(exc).__name__}: {exc}")
            if process is not None:
                process.abort()
            raise
        finally:
            receipt["exit_code"] = process.process.poll() if process else None
            write_json(directory / "observations.json", receipt)
            write_json(directory / "commands.json", self.commands)
            for name in ("latest.log", "debug.log"):
                source = self.root / "logs" / name
                if source.exists():
                    (directory / name).write_bytes(support.regular(source, 32 * 1024**2))

    def check_installed(self, artifact):
        expected = {entry["name"]: entry["sha256"] for entry in (artifact, self.consumer)}
        require({path.name for path in (self.root / "mods").iterdir()} == set(expected), "Installed mod set differs")
        for name, digest in expected.items():
            path = support.safe_path(self.root / "mods" / name)
            require(server.digest_file(path) == digest, "Installed artifact changed: " + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("server_dir", type=Path)
    for name in ("source-world", "source-evidence", "baseline-jar", "host-jar", "fixture-jar", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--accept-eula", action="store_true", required=True)
    args, output, original_manifest = parser.parse_args(), None, None
    try:
        root, source, history, baseline, host, consumer, evidence = map(support.safe_path,
                (args.server_dir, args.source_world, args.source_evidence, args.baseline_jar, args.host_jar, args.fixture_jar, args.evidence_dir))
        require(root.is_dir() and {path.name for path in root.iterdir()} == {"libraries"}, "Server must contain only libraries")
        require(not evidence.exists(), "Evidence directory must be new")
        for output_root in (root, evidence):
            for other in (source, history, baseline, host, consumer):
                require(output_root != other and output_root not in other.parents and other not in output_root.parents,
                        "Input and output paths must be disjoint")
        require(root != evidence and root not in evidence.parents and evidence not in root.parents, "Outputs must be disjoint")
        for path in (root / "libraries").rglob("*"):
            support.safe_path(path)
        artifacts = [support.artifact(baseline, HOST, "1.20.1-1.3.0-dev"),
                     support.artifact(consumer, support.FIXTURE, "1.0.0"), support.artifact(host, HOST, "1.20.1-1.4.0-dev")]
        provenance = fixture.verify_history(source, history, artifacts[:2])
        require(schema.packaged_counts(baseline) == (3, 4) and schema.packaged_counts(host) == (6, 9), "Artifact catalogs differ")
        java, version = server.resolve_java(args.java)
        evidence.mkdir(); output = evidence
        original_manifest = fixture.copy_world(source, root / "world")
        fixture.copy_world(source, evidence / "original-world")
        write_json(evidence / "source-provenance.json", provenance)
        write_json(evidence / "source-world-files.json", original_manifest)
        old = fixture.capture_state(root, evidence / "original-state")
        require(old["stations"] is None and old["bindings"] is None, "Selected historical fixture changed its scope")
        fixture.install_legacy_pack(root, baseline)
        port = server.allocate_port(); server.write_server_configuration(root, port, True)
        args_file = root / "libraries/net/minecraftforge/forge" / server.FORGE_COORDINATE / ("win_args.txt" if platform.system() == "Windows" else "unix_args.txt")
        summary = {"result": "IN_PROGRESS", "schema_version": 1, "artifacts": artifacts, "port": port, "java": version,
                   "scope": "Copied authentic v1.3 fixture; four clean processes, content removal/restoration; no clients or forced interruption",
                   "forge_args_sha256": server.digest_file(args_file), "server": str(root)}
        write_json(evidence / "summary.json", summary)
        (root / "mods").mkdir()
        for item in artifacts[:2]:
            shutil.copyfile(item["path"], root / "mods" / item["name"])
        run = MigrationRun(root, evidence, java, port, old, host, artifacts[1])
        run.cycle(PHASES[0], artifacts[0])
        installed = root / "mods" / artifacts[0]["name"]
        require(server.digest_file(installed) == artifacts[0]["sha256"], "Installed baseline changed")
        installed.unlink(); shutil.copyfile(host, root / "mods" / artifacts[2]["name"])
        for phase in PHASES[1:]:
            run.cycle(phase, artifacts[2])
        require(fixture.inventory(source) == original_manifest, "Original source world was modified")
        for item in artifacts:
            require(server.digest_file(Path(item["path"])) == item["sha256"], "Input artifact changed")
        summary.update(result="PASS", source_unchanged=True, reports=run.reports,
                       research=run.research, station_requests=run.station_requests)
        write_json(evidence / "summary.json", summary)
        print(f"[PASS] Four bounded v1.3 upgrade/removal/restoration processes; evidence: {output}")
        return 0
    except (OSError, ValueError, KeyError, TypeError, IndexError, support.SmokeError) as exc:
        if output is not None:
            write_json(output / "failure.json", {"error": str(exc), "type": type(exc).__name__})
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1
    finally:
        if output is not None:
            if original_manifest is not None:
                write_json(output / "original-unchanged.json", {"unchanged": fixture.inventory(args.source_world) == original_manifest})
            files = sorted(path for path in output.rglob("*") if path.is_file() and path.name != "SHA256SUMS")
            (output / "SHA256SUMS").write_text("".join(f"{server.digest_file(path)}  {path.relative_to(output).as_posix()}\n" for path in files), encoding="ascii", newline="\n")


if __name__ == "__main__":
    raise SystemExit(main())
