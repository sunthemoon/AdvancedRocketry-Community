import copy
import hashlib
import json
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import run_v140_discovery_cut_smoke as runner
from tests.test_v140_migration_smoke import nbt_uuid

fixture, HOST = runner.fixture, runner.HOST + ":"


class DiscoveryCutSmokeTest(unittest.TestCase):
    def states(self):
        mission = {"mission": str(uuid.uuid4()), "satellite": str(uuid.uuid4()), "owner": str(uuid.uuid4()),
                   "body": HOST + "mars", "deadline": 220}
        old = {"satellite_missions": {"schema_version": 2, "format_epoch": "v0.9.0-beta",
                   "clock": {"logical_game_time": 20, "last_observed_game_time": 20},
                   "missions": [], "satellites": [], "research_accounts": []},
               "celestial": {"schema_version": 2, "bodies": [{"id": HOST + "earth", "discovered_at": 10}]}}
        ready = copy.deepcopy(old)
        registry = ready["satellite_missions"]
        registry["missions"].append({"schema_version": 1, "mission_id": nbt_uuid(mission["mission"]),
            "satellite_id": nbt_uuid(mission["satellite"]), "owner_id": nbt_uuid(mission["owner"]),
            "definition_id": HOST + "data_satellite", "target_body_id": mission["body"], "started_at": 20,
            "completes_at": 220, "ready_at": 225, "research_yield": 120, "discovery_cost": 100,
            "discovery_required": 1, "status": "ready"})
        registry["satellites"].append({"schema_version": 1, "satellite_id": nbt_uuid(mission["satellite"]),
            "owner_id": nbt_uuid(mission["owner"]), "definition_id": HOST + "data_satellite", "launched_at": 20,
            "status": "operational", "current_mission_id": nbt_uuid(mission["mission"])})
        registry["research_accounts"].append({"schema_version": 1, "owner_id": nbt_uuid(mission["owner"]),
                                             "balance": 0, "lifetime_earned": 0, "lifetime_spent": 0})
        paid = copy.deepcopy(ready)
        paid["satellite_missions"]["missions"][0].update(status="claim_pending_discovery", resolved_at=230)
        paid["satellite_missions"]["research_accounts"][0].update(balance=20, lifetime_earned=120, lifetime_spent=100)
        discovered = copy.deepcopy(paid)
        discovered["celestial"]["bodies"].append({"id": HOST + "mars", "discovered_at": 230})
        final = copy.deepcopy(discovered)
        final["satellite_missions"]["missions"][0]["status"] = "claimed"
        final["satellite_missions"]["satellites"][0].pop("current_mission_id")
        return old, ready, paid, discovered, final, mission

    def cuts(self):
        _, ready, paid, discovered, final, mission = self.states()
        cuts = [{"authority": ready, "pending": {"satellite_missions": paid["satellite_missions"]}},
                {"authority": paid, "pending": {"celestial": discovered["celestial"]}},
                {"authority": discovered, "pending": {"satellite_missions": final["satellite_missions"]}},
                {"authority": final, "pending": {}}]
        return ready, mission, cuts

    def test_prepared_copy_adds_only_the_selected_ready_mission(self):
        old, ready, _, _, _, mission = self.states()
        fixture.check_prepared(old, ready, mission)
        for field, value in (("completes_at", 221), ("research_yield", 121), ("discovery_cost", 99),
                             ("discovery_required", 0), ("ready_at", 219), ("definition_id", HOST + "other"),
                             ("owner_id", nbt_uuid(str(uuid.uuid4()))), ("status", "active")):
            with self.subTest(field=field):
                changed = copy.deepcopy(ready)
                changed["satellite_missions"]["missions"][0][field] = value
                with self.assertRaises(runner.support.SmokeError):
                    fixture.check_prepared(old, changed, mission)

    def test_all_four_authority_and_scratch_pairs(self):
        ready, mission, cuts = self.cuts()
        for boundary, state in enumerate(cuts, 1):
            fixture.check_cut(ready, state, mission, boundary)

    def test_wrong_missing_extra_or_changed_scratch_rejects(self):
        ready, mission, cuts = self.cuts()
        for boundary, valid in enumerate(cuts, 1):
            for mutation in ("empty", "other", "changed"):
                if boundary == 4 and mutation != "other":
                    continue
                with self.subTest(boundary=boundary, mutation=mutation):
                    state = copy.deepcopy(valid)
                    if mutation == "empty":
                        state["pending"] = {}
                    elif mutation == "other":
                        state["pending"]["unknown"] = {}
                    else:
                        pending = next(iter(state["pending"].values()))
                        pending["schema_version"] = 99
                    with self.assertRaises(runner.support.SmokeError):
                        fixture.check_cut(ready, state, mission, boundary)

    def test_ready_authority_cannot_be_promoted_from_its_paid_scratch(self):
        ready, mission, cuts = self.cuts()
        state = copy.deepcopy(cuts[0])
        state["authority"]["satellite_missions"] = state["pending"]["satellite_missions"]
        with self.assertRaises(runner.support.SmokeError):
            fixture.check_cut(ready, state, mission, 1)

    def test_unpaid_startup_may_leave_exact_scratch_but_cannot_repay_or_change_it(self):
        ready, mission, cuts = self.cuts()
        killed = cuts[0]
        killed["files"] = [{"path": "receipt.dat.arce-pending", "bytes": 100, "sha256": "same"}]
        fixture.check_automatic(ready, killed, mission, 1, killed)
        cleared = copy.deepcopy(killed); cleared["pending"] = {}; cleared["files"] = []
        fixture.check_automatic(ready, cleared, mission, 1, killed)
        for kind in ("payment", "scratch", "bytes"):
            changed = copy.deepcopy(killed)
            if kind == "payment":
                changed["authority"] = cuts[1]["authority"]
            elif kind == "scratch":
                changed["pending"]["satellite_missions"]["clock"]["logical_game_time"] += 1
            else:
                changed["files"][0]["sha256"] = "different"
            with self.assertRaises(runner.support.SmokeError):
                fixture.check_automatic(ready, changed, mission, 1, killed)
        with self.assertRaises(runner.support.SmokeError):
            fixture.check_automatic(ready, dict(cuts[3], pending=killed["pending"]), mission, 4, cuts[3])

    def test_repayment_changed_timestamps_and_fabricated_visit_reject(self):
        _, ready, _, _, final, mission = self.states()
        fixture.check_phase(ready, final, mission, "claimed", True, resolved_at=230, discovered_at=230)
        for field in ("balance", "lifetime_earned", "lifetime_spent", "resolved_at", "discovered_at", "visit", "binding", "old"):
            with self.subTest(field=field):
                changed = copy.deepcopy(final)
                registry = changed["satellite_missions"]
                if field in ("balance", "lifetime_earned", "lifetime_spent"):
                    registry["research_accounts"][0][field] += 1
                elif field == "resolved_at":
                    registry["missions"][0][field] += 1
                elif field == "discovered_at":
                    changed["celestial"]["bodies"][1][field] += 1
                elif field == "visit":
                    changed["celestial"]["bodies"][1]["first_visit_at"] = 230
                elif field == "binding":
                    registry["satellites"][0]["current_mission_id"] = nbt_uuid(mission["mission"])
                else:
                    changed["celestial"]["bodies"][0]["discovered_at"] += 1
                with self.assertRaises(runner.support.SmokeError):
                    fixture.check_phase(ready, changed, mission, "claimed", True, resolved_at=230, discovered_at=230)

    def test_recovery_retains_receipt_but_may_add_discovery_later(self):
        _, ready, paid, _, final, mission = self.states()
        times = fixture.check_phase(ready, paid, mission, "claim_pending_discovery", False)
        later = copy.deepcopy(final)
        later["celestial"]["bodies"][1]["discovered_at"] = 300
        fixture.check_phase(ready, later, mission, "claimed", True, **times)
        with self.assertRaises(runner.support.SmokeError):
            fixture.same_authority(final, later)

    def test_duplicate_identities_and_unvalidated_clock_reject(self):
        _, ready, _, _, _, _ = self.states()
        advanced = copy.deepcopy(ready)
        advanced["satellite_missions"]["clock"]["logical_game_time"] += 100
        fixture.same_authority(ready, advanced)
        for field in ("missions", "satellites", "research_accounts", "bodies", "clock"):
            with self.subTest(field=field):
                changed = copy.deepcopy(ready)
                if field == "clock":
                    changed["satellite_missions"]["clock"]["unexpected"] = 1
                else:
                    values = changed["celestial" if field == "bodies" else "satellite_missions"][field]
                    values.append(copy.deepcopy(values[0]))
                with self.assertRaises(runner.support.SmokeError):
                    fixture.same_authority(ready, changed)

    def test_capture_is_read_only_and_requires_both_authorities(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); world = root / "world"; data = world / "data"; data.mkdir(parents=True)
            for store in fixture.STORES:
                (data / (runner.HOST + "_" + store + ".dat")).write_bytes(b"observed")
            pending = data / (runner.HOST + "_satellite_missions.dat.arce-pending")
            pending.write_bytes(b"scratch")
            before = runner.migration.inventory(world)
            with patch.object(fixture.schema, "read_nbt", return_value={"data": {"schema_version": 2}}):
                result = fixture.capture(world, root / "capture")
                self.assertEqual(3, len(result["files"]))
                self.assertEqual({"satellite_missions"}, set(result["pending"]))
                self.assertEqual(before, runner.migration.inventory(world))
                (data / (runner.HOST + "_celestial.dat")).unlink()
                with self.assertRaisesRegex(runner.support.SmokeError, "Missing authority"):
                    fixture.capture(world, root / "missing")

    def test_kill_uses_only_owned_handle_with_live_observer(self):
        events = []
        process, probe = Mock(), Mock()
        process.process.poll.return_value = probe.process.poll.return_value = None
        process.process.pid = 123
        process.process.kill.side_effect = lambda: events.append("kill")
        process.finish.side_effect = lambda timeout: events.append("server-exit") or 1
        probe.finish.side_effect = lambda timeout: events.append("probe-exit") or 0
        probe.lines = ["MIG03_DISCONNECTED_AFTER_CUT\n"]
        self.assertEqual(1, runner.terminate_at_cut(process, probe)["exit_code"])
        self.assertEqual(["kill", "server-exit", "probe-exit"], events)
        process.command.assert_not_called()

    def test_detached_probe_or_clean_exit_cannot_pass_as_a_cut(self):
        for kind in ("detached", "clean", "probe-failure", "missing-marker"):
            with self.subTest(kind=kind):
                process, probe = Mock(), Mock()
                process.process.poll.return_value = None
                probe.process.poll.return_value = 0 if kind == "detached" else None
                process.finish.return_value = 0 if kind == "clean" else 1
                probe.finish.return_value = 1 if kind == "probe-failure" else 0
                probe.lines = [] if kind == "missing-marker" else ["MIG03_DISCONNECTED_AFTER_CUT\n"]
                with self.assertRaises(runner.support.SmokeError):
                    runner.terminate_at_cut(process, probe)
                if kind == "detached":
                    process.process.kill.assert_not_called()

    def test_phase_spawn_failure_retains_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run = runner.CutRun(root, root, "java", 1, [], root)
            with patch.object(runner, "installed"), patch.object(runner.server, "CapturedProcess", side_effect=OSError("spawn failed")):
                with self.assertRaises(OSError):
                    with run.phase("failure"):
                        self.fail("Spawn failure yielded a process")
            receipt = json.loads((root / "failure/observations.json").read_text())
            self.assertEqual("FAIL", receipt["result"])
            self.assertIsNone(receipt["exit_code"])

    def test_cut_observation_failure_kills_server_before_probe_disposal(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            run = runner.CutRun(root, root, "java", 1, [], root)
            process, probe, events = Mock(), Mock(), []
            process.process.poll.return_value = probe.process.poll.return_value = None
            process.process.kill.side_effect = lambda: events.append("server-kill")
            process.finish.side_effect = lambda timeout: events.append("server-exit")
            probe.abort.side_effect = lambda: events.append("probe-dispose")
            probe.wait_for.side_effect = runner.support.SmokeError("attach failed")
            context = Mock(); context.__enter__ = Mock(return_value=(process, root, {}))
            context.__exit__ = Mock(return_value=False)
            _, ready, _, _, _, mission = self.states()
            with patch.object(run, "phase", return_value=context), patch.object(run, "mission_status"), \
                    patch.object(run, "query"), patch.object(fixture, "capture", return_value={"authority": ready, "pending": {}}), \
                    patch.object(runner.server, "CapturedProcess", return_value=probe):
                with self.assertRaisesRegex(runner.support.SmokeError, "attach failed"):
                    run.cut(1, ready, mission)
            self.assertEqual(["server-kill", "server-exit", "probe-dispose"], events)

    def test_source_requires_manifest_inventory_and_unchanged_result(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "original-world").mkdir()
            (root / "original-world/level.dat").write_bytes(b"original")
            runner.write_json(root / "source-world-files.json", runner.migration.inventory(root / "original-world"))
            summary = {"result": "PASS", "source_unchanged": True,
                       "artifacts": [{"sha256": "old"}, {"sha256": "consumer"}, {"sha256": "host"}]}
            runner.write_json(root / "summary.json", summary)
            files = ("summary.json", "source-world-files.json", "original-world/level.dat")
            manifest = "".join(f"{hashlib.sha256((root / path).read_bytes()).hexdigest()}  {path}\n" for path in files)
            (root / "SHA256SUMS").write_text(manifest)
            artifacts = [{"sha256": "host"}, {"sha256": "consumer"}]
            self.assertEqual(3, fixture.verify_source(root, artifacts)["manifest_entries"])
            (root / "SHA256SUMS").write_text(manifest + manifest.splitlines()[0] + "\n")
            with self.assertRaisesRegex(runner.support.SmokeError, "duplicate"):
                fixture.verify_source(root, artifacts)
            (root / "SHA256SUMS").write_text(manifest)
            (root / "original-world/level.dat").write_bytes(b"changed")
            with self.assertRaisesRegex(runner.support.SmokeError, "checksum"):
                fixture.verify_source(root, artifacts)
            summary["source_unchanged"] = False
            runner.write_json(root / "summary.json", summary)
            with self.assertRaisesRegex(runner.support.SmokeError, "incomplete"):
                fixture.verify_source(root, artifacts)


if __name__ == "__main__":
    unittest.main()
