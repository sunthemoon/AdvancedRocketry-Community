import copy
import hashlib
import json
import re
import tempfile
import unittest
import uuid
import zipfile
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import run_v140_migration_smoke as runner

fixture = runner.fixture
HOST = runner.HOST + ":"


def nbt_uuid(value):
    return [part if part < 2**31 else part - 2**32 for shift in (96, 64, 32, 0)
            for part in [(uuid.UUID(value).int >> shift) & 0xffffffff]]


class MigrationSmokeTest(unittest.TestCase):
    def states(self):
        owner = str(uuid.uuid4())
        old = {"terminal": {"energy": 9000}, "bindings": None, "stations": None,
               "celestial": {"schema_version": 2, "bodies": [{"id": HOST + "earth", "discovered_at": 461}]},
               "satellite_missions": {"schema_version": 2, "clock": {"logical_game_time": 20, "last_observed_game_time": 20},
                                      "missions": [], "satellites": [], "research_accounts": []}}
        pending, missions = copy.deepcopy(old), []
        registry = pending["satellite_missions"]
        for body in ("mars", "venus"):
            mission, satellite = str(uuid.uuid4()), str(uuid.uuid4())
            missions.append({"body": HOST + body, "mission": mission, "satellite": satellite, "deadline": 220})
            registry["missions"].append({"schema_version": 1, "mission_id": nbt_uuid(mission), "owner_id": nbt_uuid(owner),
                "satellite_id": nbt_uuid(satellite), "definition_id": HOST + "data_satellite", "target_body_id": HOST + body,
                "completes_at": 220, "started_at": 20, "ready_at": 220, "resolved_at": 221,
                "research_yield": 120, "discovery_cost": 100, "discovery_required": 1,
                "status": "claimed" if body == "mars" else "claim_pending_discovery"})
            registry["satellites"].append({"schema_version": 1, "owner_id": nbt_uuid(owner), "satellite_id": nbt_uuid(satellite),
                "status": "operational", "definition_id": HOST + "data_satellite",
                **({"current_mission_id": nbt_uuid(mission)} if body == "venus" else {})})
        registry["research_accounts"].append({"schema_version": 1, "owner_id": nbt_uuid(owner), "balance": 40,
                                             "lifetime_earned": 240, "lifetime_spent": 200})
        pending["celestial"]["bodies"].append({"id": HOST + "mars", "discovered_at": 221})
        restored = copy.deepcopy(pending)
        restored["satellite_missions"]["missions"][1]["status"] = "claimed"
        restored["satellite_missions"]["satellites"][1].pop("current_mission_id")
        restored["celestial"]["bodies"].append({"id": HOST + "venus", "discovered_at": 250})
        return old, pending, restored, missions, owner

    def test_exact_pending_and_completion_authority(self):
        old, pending, restored, missions, owner = self.states()
        fixture.check_claims(old, pending, missions, owner, True)
        fixture.check_claims(old, restored, missions, owner, False)
        fixture.check_completion(pending, restored, missions[1])

    def test_recovery_rejects_repayment_changed_timestamps_and_fabricated_visits(self):
        _, pending, valid, missions, _ = self.states()
        for kind in ("balance", "earned", "spent", "timestamp", "visit", "status", "binding", "discovery"):
            with self.subTest(kind=kind):
                state = copy.deepcopy(valid)
                registry = state["satellite_missions"]
                if kind in ("balance", "earned", "spent"):
                    field = kind if kind == "balance" else "lifetime_" + kind
                    registry["research_accounts"][0][field] += 1
                elif kind == "timestamp":
                    registry["missions"][1]["resolved_at"] += 1
                elif kind == "visit":
                    state["celestial"]["bodies"][-1]["first_visit_at"] = 250
                elif kind == "status":
                    registry["missions"][1]["status"] = "ready"
                elif kind == "binding":
                    registry["satellites"][1]["current_mission_id"] = nbt_uuid(missions[1]["mission"])
                else:
                    state["celestial"]["bodies"][0]["discovered_at"] += 1
                with self.assertRaises(runner.support.SmokeError):
                    fixture.check_completion(pending, state, missions[1])

    def test_claim_snapshot_and_identity_mutations_reject(self):
        old, pending, _, missions, owner = self.states()
        for field, value in (("definition_id", "test:other"), ("completes_at", 221), ("discovery_required", 0),
                             ("discovery_cost", 0), ("research_yield", 121), ("status", "claimed"),
                             ("owner_id", nbt_uuid(str(uuid.uuid4())))):
            with self.subTest(field=field):
                changed = copy.deepcopy(pending)
                changed["satellite_missions"]["missions"][1][field] = value
                with self.assertRaises(runner.support.SmokeError):
                    fixture.check_claims(old, changed, missions, owner, True)

    def test_clock_is_the_only_restart_exception(self):
        old, _, _, _, _ = self.states()
        changed = copy.deepcopy(old)
        changed["satellite_missions"]["clock"]["logical_game_time"] += 100
        fixture.same_authority(old, changed)
        for kind in ("negative", "extra", "float", "other"):
            state = copy.deepcopy(changed)
            if kind == "negative":
                state["satellite_missions"]["clock"]["logical_game_time"] = -1
            elif kind == "extra":
                state["satellite_missions"]["clock"]["hidden"] = 1
            elif kind == "float":
                state["satellite_missions"]["clock"]["logical_game_time"] = 1.0
            else:
                state["terminal"]["energy"] -= 1
            with self.assertRaises(runner.support.SmokeError):
                fixture.same_authority(old, state)

    def test_live_authority_omits_regions_but_closed_world_still_requires_terminal(self):
        old, _, _, _, _ = self.states()
        live = copy.deepcopy(old); live.pop("terminal")
        fixture.same_authority(old, live, closed_world=False)
        fixture.preserve_history(old, live, closed_world=False)
        with self.assertRaises(KeyError):
            fixture.preserve_history(old, live)
        live["celestial"]["bodies"][0]["discovered_at"] += 1
        with self.assertRaises(runner.support.SmokeError):
            fixture.same_authority(old, live, closed_world=False)

    def test_duplicate_identity_and_old_record_mutation_reject(self):
        old, _, _, _, _ = self.states()
        record = {"mission_id": nbt_uuid(str(uuid.uuid4())), "status": "claimed"}
        old["satellite_missions"]["missions"].append(record)
        for value in ([], [dict(record, status="ready")], [record, record]):
            state = copy.deepcopy(old)
            state["satellite_missions"]["missions"] = value
            with self.assertRaises(runner.support.SmokeError):
                fixture.preserve_history(old, state)

    def test_copy_is_exact_disjoint_and_does_not_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "original"
            source.mkdir(); (source / "level.dat").write_bytes(b"original")
            before = fixture.inventory(source)
            self.assertEqual(before, fixture.copy_world(source, root / "copy"))
            self.assertEqual(before, fixture.inventory(source))
            for destination in (source, source / "nested", root, root / "copy"):
                with self.assertRaises(runner.support.SmokeError):
                    fixture.copy_world(source, destination)

    def test_copy_rejects_changed_source_during_copy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); source = root / "source"; source.mkdir()
            (source / "file").write_bytes(b"original")
            real_copy = fixture.shutil.copytree
            def changed(a, b):
                real_copy(a, b); (a / "file").write_bytes(b"changed")
            with patch.object(fixture.shutil, "copytree", side_effect=changed):
                with self.assertRaisesRegex(runner.support.SmokeError, "Copy changed"):
                    fixture.copy_world(source, root / "copy")

    def test_inventory_has_empty_tree_file_and_directory_bounds(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(runner.support.SmokeError, "empty"):
                fixture.inventory(root)
            (root / "file").write_bytes(b"x")
            for index in range(512):
                (root / str(index)).mkdir()
            with self.assertRaisesRegex(runner.support.SmokeError, "directory budget"):
                fixture.inventory(root)
            with patch.object(fixture.os, "walk", return_value=[(root, [], ["file"] * 513)]):
                with self.assertRaisesRegex(runner.support.SmokeError, "copy budget"):
                    fixture.inventory(root)

    def test_history_requires_captured_bytes_completed_receipts_and_old_host(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); world = root / "world"; evidence = root / "evidence"
            world.mkdir(); evidence.mkdir()
            captures = {}
            for relative in (runner.legacy.SAVED, "world/level.dat", "world/region/r.0.0.mca",
                             runner.legacy.DEFINITION_FILE, runner.legacy.PACK + "/pack.mcmeta"):
                path = world / Path(relative).relative_to("world")
                path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(b"capture")
                captures[relative] = {"bytes": 7, "sha256": hashlib.sha256(b"capture").hexdigest()}
            artifacts = [{"sha256": fixture.OLD_HOST_SHA}, {"sha256": "consumer"}]
            summary = {"result": "PASS", "artifacts": artifacts, "cycles": [
                {"result": "PASS", "exit_code": 0, "phase": phase, "disk_evidence": captures}
                for phase in ("initial", "removed", "reinstalled")]}
            def write_summary():
                runner.write_json(evidence / "summary.json", summary)
                digest = runner.server.digest_file(evidence / "summary.json")
                (evidence / "SHA256SUMS").write_text(digest + "  summary.json\n", encoding="ascii")
            write_summary()
            self.assertEqual(1, fixture.verify_history(world, evidence, artifacts)["manifest_entries"])
            (world / "level.dat").write_bytes(b"changed")
            with self.assertRaisesRegex(runner.support.SmokeError, "historical capture"):
                fixture.verify_history(world, evidence, artifacts)
            (world / "level.dat").write_bytes(b"capture")
            summary["cycles"][0]["exit_code"] = 1; write_summary()
            with self.assertRaisesRegex(runner.support.SmokeError, "receipts"):
                fixture.verify_history(world, evidence, artifacts)
            with self.assertRaisesRegex(runner.support.SmokeError, "pinned"):
                fixture.verify_history(world, evidence, [{"sha256": "not-old"}, artifacts[1]])

    def resource_jar(self, path, child=False):
        with zipfile.ZipFile(path, "w") as jar:
            for body in ("earth", "moon", "space", "mars", "venus", "gas_giant"):
                data = {"id": HOST + body}
                if body == "moon":
                    data["parent"] = HOST + ("mars" if child else "earth")
                jar.writestr(f"data/{runner.HOST}/celestial_bodies/{body}.json", json.dumps(data))
            for body in ("moon", "mars", "venus"):
                route = {"from": {"body_id": HOST + "earth"}, "to": {"body_id": HOST + body}}
                jar.writestr(f"data/{runner.HOST}/travel_routes/earth_{body}.json", json.dumps(route))
            jar.writestr(f"data/{runner.HOST}/satellite_definitions/data_satellite.json",
                         json.dumps({"allowed_targets": [HOST + body for body in ("earth", "mars", "venus", "gas_giant")]}))

    def test_removal_filters_resources_routes_and_targets_coherently(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "host.jar"; self.resource_jar(path)
            metadata, satellite, count = fixture.removal_resources(path, True)
            filters = metadata["filter"]["block"]
            self.assertEqual(4, len(filters)); self.assertEqual(1, count)
            for kind, name in (("celestial_bodies", "mars"), ("celestial_bodies", "venus"),
                               ("travel_routes", "earth_mars"), ("travel_routes", "earth_venus")):
                self.assertTrue(any(re.fullmatch(row["namespace"], runner.HOST)
                    and re.fullmatch(row["path"], f"{kind}/{name}.json") for row in filters))
            self.assertFalse(any(re.fullmatch(row["path"], "celestial_bodies/marsXjson") for row in filters))
            self.assertEqual([HOST + "earth", HOST + "gas_giant"], satellite["allowed_targets"])
            restored, targets, count = fixture.removal_resources(path, False)
            self.assertNotIn("filter", restored); self.assertEqual(3, count)
            self.assertEqual(4, len(targets["allowed_targets"]))

    def test_removal_rejects_unhandled_child(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "host.jar"; self.resource_jar(path, True)
            with self.assertRaisesRegex(runner.support.SmokeError, "dependent child"):
                fixture.removal_resources(path, True)

    def test_legacy_pack_preserves_implicit_schema_one_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); path = root / "host.jar"; self.resource_jar(path)
            fixture.install_legacy_pack(root, path)
            with zipfile.ZipFile(path) as jar:
                for body in ("earth", "moon", "space"):
                    name = f"data/{runner.HOST}/celestial_bodies/{body}.json"
                    self.assertEqual(jar.read(name), (root / fixture.PACK / name).read_bytes())
            with self.assertRaises(runner.support.SmokeError):
                fixture.install_legacy_pack(root, path)

    def test_installed_mods_require_exact_identities(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root / "mods").mkdir()
            host = {"name": "host.jar", "sha256": hashlib.sha256(b"host").hexdigest()}
            consumer = {"name": "consumer.jar", "sha256": hashlib.sha256(b"consumer").hexdigest()}
            run = runner.MigrationRun(root, root, "java", 1, {}, root / "host.jar", consumer)
            (root / "mods/host.jar").write_bytes(b"host"); (root / "mods/consumer.jar").write_bytes(b"consumer")
            run.check_installed(host)
            (root / "mods/host.jar").write_bytes(b"changed")
            with self.assertRaises(runner.support.SmokeError):
                run.check_installed(host)
            (root / "mods/host.jar").write_bytes(b"host"); (root / "mods/other.jar").write_bytes(b"other")
            with self.assertRaises(runner.support.SmokeError):
                run.check_installed(host)

    def test_spawn_failure_retains_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run = runner.MigrationRun(root, root, "java", 1, {}, root / "host.jar", {})
            with patch.object(run, "check_installed"), patch.object(runner.server, "CapturedProcess", side_effect=OSError("spawn failure")):
                with self.assertRaisesRegex(OSError, "spawn failure"):
                    run.cycle("legacy-copy", {})
            receipt = json.loads((root / "legacy-copy/observations.json").read_text())
            self.assertEqual("FAIL", receipt["result"]); self.assertIsNone(receipt["exit_code"])

    def test_started_failure_aborts_only_owned_process_and_retains_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run = runner.MigrationRun(root, root, "java", 1, {}, root / "host.jar", {})
            process = Mock(); process.wait_for.side_effect = runner.support.SmokeError("startup failure")
            process.process.poll.return_value = 19
            with patch.object(run, "check_installed"), patch.object(runner.server, "CapturedProcess", return_value=process):
                with self.assertRaisesRegex(runner.support.SmokeError, "startup failure"):
                    run.cycle("legacy-copy", {})
            process.abort.assert_called_once()
            receipt = json.loads((root / "legacy-copy/observations.json").read_text())
            self.assertEqual("FAIL", receipt["result"]); self.assertEqual(19, receipt["exit_code"])

    def test_exact_diagnostic_allowlist_does_not_hide_other_errors(self):
        line = "[Server thread/ERROR] [advancedrocketrycommunity]: ARCE_STATION_UNKNOWN_ORBIT_BODY station=" + str(uuid.uuid4())
        runner.schema.validate_log([line], [line])
        with self.assertRaises(runner.support.SmokeError):
            runner.schema.validate_log([line, "[Server thread/ERROR]: unrelated"], [line])


if __name__ == "__main__":
    unittest.main()
