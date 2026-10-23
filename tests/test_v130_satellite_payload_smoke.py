import contextlib
import copy
import gzip
import io
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import Mock, patch
from scripts import run_v130_satellite_payload_smoke as runner


def nbt_uuid(value):
    raw = uuid.UUID(value).bytes
    return [int.from_bytes(raw[i:i + 4], "big", signed=True) for i in range(0, 16, 4)]


def fixture(phase):
    satellite = str(uuid.UUID(int=1))
    missions = [str(uuid.UUID(int=i)) for i in range(2, 4 if phase == "reinstalled" else 3)]
    owner = nbt_uuid(runner.OWNER)
    state = {"schema_version": 2, "satellite_id": nbt_uuid(satellite), "owner_id": owner,
             "definition_id": runner.DEFINITION, "status": "operational", "kind": "data", "kind_state": {},
             "blueprint": {"components": [], "legacy": 1, "data": 1000, "rating": 0,
                           "power": 4, "battery": 720, "cargo": 0}}
    if phase == "create": state["current_mission_id"] = nbt_uuid(missions[0])
    amount = {"create": 0, "mod-uninstalled": 126, "reinstalled": 337}[phase]
    earned = {"create": 0, "mod-uninstalled": 137, "reinstalled": 348}[phase]
    data = {"schema_version": 4, "format_epoch": "v1.8.0-ground-surveys", "save_epoch": 10,
         "instances": [], "ground_surveys": [], "satellites": [state], "missions": [
        {"schema_version": 2, "kind": "data", "seed": 0, "start_epoch": 1, "reward_version": "data-v1",
         "mission_id": nbt_uuid(identity), "satellite_id": nbt_uuid(satellite),
         "owner_id": owner, "definition_id": runner.DEFINITION, "target_body_id": runner.HOST + ":earth",
         "discovery_required": 1 if i == 0 else 0, "started_at": 100, "completes_at": 500 if i == 0 else 120,
         "research_yield": 137 if i == 0 else 211, "discovery_cost": 11,
         "status": "active" if phase == "create" else "claimed"} for i, identity in enumerate(missions)],
         "research_accounts": [{"schema_version": 1, "owner_id": owner, "balance": amount,
                                "lifetime_earned": earned, "lifetime_spent": 0 if phase == "create" else 11}]}
    terminal = {"schema_version": 2, "owner_id": owner, "energy": 9000, "selected_target": 0,
                "terminal_id": nbt_uuid(str(uuid.UUID(int=5))), "reward_buffer": [], "receipts": [],
                "last_result": 9 if phase == "reinstalled" else 0, "inventory": {"Size": 6, "Items": [
                    {"Slot": 2, "id": "minecraft:amethyst_shard", "Count": 1},
                    {"Slot": 3, "id": runner.HOST + ":satellite_control_chip", "Count": 1,
                     "tag": {"SatelliteIdentity": {"schema_version": 1, "owner_id": owner,
                              "satellite_id": nbt_uuid(satellite), "definition_id": runner.DEFINITION}}}]}}
    return data, terminal, satellite, missions


class SatellitePayloadSmokeTest(unittest.TestCase):
    def test_registration_requires_one_correct_delivery_or_actual_absence(self):
        runner.validate_registration([runner.REGISTERED], True)
        runner.validate_registration([], False)
        older = runner.REGISTERED.replace(f"API {runner.recovery.API}", "API 1.6")
        self.assertNotEqual(runner.REGISTERED, older)
        for lines, installed in (([], True), ([runner.REGISTERED] * 2, True), ([runner.REGISTERED], False),
                                 ([older], True)):
            with self.assertRaises(runner.SmokeError): runner.validate_registration(lines, installed)

    def test_probe_preserves_identity_consumption_and_authoritative_result(self):
        satellite = str(uuid.UUID(int=1))
        value = {"action": "create", "energy": 9000, "result": 0, "payload_count": 1, "package_empty": False,
                 "owner": runner.OWNER, "definition": runner.DEFINITION, "satellite": satellite}
        self.assertEqual(satellite, runner.validate_probe(value, "create", None, 0))
        for key, changed in (("action", "claim"), ("energy", 10000), ("result", 2), ("payload_count", 2),
                             ("package_empty", True), ("owner", str(uuid.UUID(int=9))), ("definition", "missing:id")):
            with self.assertRaises(runner.SmokeError): runner.validate_probe(dict(value, **{key: changed}), "create", satellite, 0)
        with self.assertRaises(runner.SmokeError): runner.validate_probe(value, "create", str(uuid.UUID(int=8)), 0)
        for action, code in (("launch", 0), ("probe", 0), ("claim", 0), ("claim", 9)):
            receipt = dict(value, action=action, result=code, package_empty=True)
            self.assertEqual(satellite, runner.validate_probe(receipt, action, satellite, code))
            with self.assertRaises(runner.SmokeError):
                runner.validate_probe(dict(receipt, package_empty=False), action, satellite, code)
            with self.assertRaises(runner.SmokeError):
                runner.validate_probe(dict(receipt, result=28), action, satellite, code)

    def make_run(self, directory):
        return runner.SatelliteRun(Path(directory), Path(directory), [{"version": "1.20.1-1.8.0-dev"}],
                                   "java", 12345, 240)

    def test_inspect_and_scheduler_require_exact_data_mission_receipts(self):
        run = self.make_run("unused")
        run.satellite, mission = str(uuid.UUID(int=1)), str(uuid.UUID(int=2))
        receipt = (f"satellite={run.satellite} owner={runner.OWNER} type={runner.DEFINITION} "
                   f"kind=data status=OPERATIONAL mission={mission}")
        run.query = Mock(side_effect=lambda process, command, marker: marker.search(receipt))
        self.assertEqual(mission, run.inspect_mission(object()))
        marker = run.query.call_args.args[2]
        for before, after in (("kind=data ", ""), ("kind=data", "kind=solar"), ("OPERATIONAL", "FAILED"),
                              (runner.OWNER, str(uuid.UUID(int=9))), (runner.DEFINITION, "missing:id"),
                              (f"satellite={run.satellite}", f"satellite={mission}"), (mission, "none")):
            self.assertIsNone(marker.search(receipt.replace(before, after)))
        scheduled = "ARCE_SATELLITE_SCHEDULER completed=1 pruned=0 inspected=1 remaining=0"
        self.assertIsNotNone(runner.SCHEDULED.search(scheduled))
        for before, after in (("completed=1", "completed=0"), ("pruned=0", "pruned=1"), ("pruned=0 ", ""),
                              ("inspected=1", "inspected=2"), ("remaining=0", "remaining=1")):
            self.assertIsNone(runner.SCHEDULED.search(scheduled.replace(before, after)))

    def test_interval_uses_observed_ticks_and_accepts_exact_boundary(self):
        run = self.make_run("unused")
        ticks = iter((100, 100, 109, 110))
        run.query = Mock(side_effect=lambda *args: runner.GAME_TIME.search(f"The time is {next(ticks)}"))
        process = object()
        with patch.object(runner.time, "sleep") as sleep:
            self.assertEqual({"after_action": 100, "before_next_action": 110, "elapsed_ticks": 10},
                             run.wait_intent_interval(process))
        self.assertEqual(3, sleep.call_count)
        self.assertEqual(4, run.query.call_count)
        for call in run.query.call_args_list:
            self.assertEqual((process, "time query gametime", runner.GAME_TIME), call.args[:3])
            self.assertGreater(call.args[3], 0)
            self.assertLessEqual(call.args[3], 10)

    def test_interval_rejects_stalled_or_reversed_clock_and_missing_receipt(self):
        run = self.make_run("unused")
        run.query = Mock(return_value=runner.GAME_TIME.search("The time is 100"))
        with patch.object(runner.time, "sleep"), patch.object(runner.time, "monotonic", side_effect=(0, 0, 1, 10)):
            with self.assertRaisesRegex(runner.SmokeError, "did not advance"):
                run.wait_intent_interval(object())
        self.assertEqual(2, run.query.call_count)
        ticks = iter((100, 109, 108))
        run.query = Mock(side_effect=lambda *args: runner.GAME_TIME.search(f"The time is {next(ticks)}"))
        with patch.object(runner.time, "sleep"):
            with self.assertRaisesRegex(runner.SmokeError, "moved backwards"):
                run.wait_intent_interval(object())
        run.query = Mock(side_effect=runner.SmokeError("Missing tick receipt"))
        with self.assertRaisesRegex(runner.SmokeError, "Missing tick receipt"):
            run.wait_intent_interval(object())

    def test_phase_spaces_create_launch_and_claim_replay_without_changing_results(self):
        class StopAtSave(Exception):
            pass

        for phase in ("create", "reinstalled"):
            with self.subTest(phase=phase), tempfile.TemporaryDirectory() as directory:
                run = self.make_run(directory)
                process = Mock(lines=[])
                process.process.poll.return_value = None
                events, ticks, document = [], iter((100, 109, 110)), {}
                run.mods = Mock(return_value={})
                run.inspect_mission = Mock(return_value=str(uuid.UUID(int=2)))
                run.write_override = Mock()
                run.missions = [str(uuid.UUID(int=1))]

                def fixture(process, action, code):
                    events.append((action, code))
                    return {"action": action, "result": code}

                def query(process, command, marker, timeout=30):
                    if command == "save-all flush":
                        raise StopAtSave()
                    if command == "time query gametime":
                        tick = next(ticks)
                        events.append(("tick", tick))
                        return marker.search(f"The time is {tick}")
                    self.assertEqual("reload", command)

                run.fixture, run.query = fixture, query
                with patch.object(runner.server, "CapturedProcess", return_value=process), \
                        patch.object(runner.server, "wait_for_status", return_value={}), \
                        patch.object(runner.recovery, "validate_status", return_value={}), \
                        patch.object(runner, "validate_registration"), \
                        patch.object(runner.recovery, "wait_condition"), patch.object(runner.time, "sleep"):
                    with self.assertRaises(StopAtSave):
                        run._run_phase(phase, Path(directory), document)
                gap = [("tick", 100), ("tick", 109), ("tick", 110)]
                expected = ([("create", 0)] + gap + [("launch", 0), ("probe", 0)] if phase == "create"
                            else [("probe", 0), ("launch", 0), ("claim", 0)] + gap + [("claim", 9)])
                self.assertEqual(expected, events)
                key = "create_to_launch_ticks" if phase == "create" else "claim_to_replay_ticks"
                self.assertEqual(10, document[key]["elapsed_ticks"])

    def test_native_mission_snapshots_rewards_and_terminal_survive_three_phases(self):
        previous = previous_terminal = None
        for phase in runner.PHASES:
            data, terminal, satellite, missions = fixture(phase)
            previous_terminal = runner.validate_disk(data, terminal, phase, satellite, missions, previous, previous_terminal)
            previous = {runner.recovery.uuid_from_nbt(value["mission_id"]): copy.deepcopy(value) for value in data["missions"]}

    def test_corrupt_or_rewritten_native_state_is_not_accepted(self):
        data, terminal, satellite, missions = fixture("reinstalled")
        for key, value in (("research_yield", 211), ("completes_at", 120), ("definition_id", "foreign:changed"),
                           ("status", "ready"), ("discovery_required", 0), ("owner_id", nbt_uuid(str(uuid.UUID(int=9))))):
            bad = copy.deepcopy(data); bad["missions"][0][key] = value
            with self.assertRaises(runner.SmokeError): runner.validate_disk(bad, terminal, "reinstalled", satellite, missions, None)
        bad = copy.deepcopy(data); bad["research_accounts"][0]["balance"] = 485
        with self.assertRaises(runner.SmokeError): runner.validate_disk(bad, terminal, "reinstalled", satellite, missions, None)
        for key, value in (("balance", 348), ("lifetime_earned", 337), ("lifetime_spent", 0)):
            bad = copy.deepcopy(data); bad["research_accounts"][0][key] = value
            with self.assertRaises(runner.SmokeError): runner.validate_disk(bad, terminal, "reinstalled", satellite, missions, None)
        for key, value in (("energy", 10000), ("selected_target", 1), ("last_result", 0), ("schema_version", 1),
                           ("reward_buffer", [{"item": "minecraft:diamond", "count": 1}]), ("receipts", [nbt_uuid(missions[0])])):
            with self.assertRaises(runner.SmokeError): runner.validate_disk(data, dict(terminal, **{key: value}), "reinstalled", satellite, missions, None)
        with self.assertRaises(runner.SmokeError):
            runner.validate_disk(data, terminal, "reinstalled", satellite, missions, None, str(uuid.UUID(int=6)))
        bad = copy.deepcopy(terminal); bad["inventory"]["Items"][0]["Count"] = 2
        with self.assertRaises(runner.SmokeError): runner.validate_disk(data, bad, "reinstalled", satellite, missions, None)
        previous = {missions[0]: dict(data["missions"][0], started_at=99, completes_at=499)}
        with self.assertRaises(runner.SmokeError): runner.validate_disk(data, terminal, "reinstalled", satellite, missions, previous)

    def test_native_data_kind_and_blueprint_metadata_are_not_relaxed(self):
        data, terminal, satellite, missions = fixture("create")
        for section, changes in (
                ("satellites", (("schema_version", 1), ("kind", "solar"), ("kind_state", {"charge": 1}),
                                ("orbit_body", runner.HOST + ":earth"), ("blueprint", {}))),
                ("missions", (("schema_version", 1), ("kind", "gas_harvester"), ("seed", 1),
                              ("start_epoch", 0), ("start_epoch", 11), ("reward_version", "data-v2"),
                              ("instance_id", nbt_uuid(str(uuid.UUID(int=6)))), ("quarantine", {})))):
            for key, value in changes:
                bad = copy.deepcopy(data); bad[section][0][key] = value
                with self.subTest(section=section, key=key, value=value), self.assertRaises(runner.SmokeError):
                    runner.validate_disk(bad, terminal, "create", satellite, missions, None)

    def test_decoder_checks_native_schema_and_record_limits(self):
        data, _, _, _ = fixture("create")
        with patch.object(runner.recovery, "NbtReader") as reader:
            reader.return_value.read_root.return_value = {"data": data}
            self.assertEqual(data, runner.decode_saved(gzip.compress(b"fixture")))
            for bad in ({}, {"data": dict(data, schema_version=9)}, {"data": dict(data, schema_version=2)},
                        {"data": dict(data, schema_version=3)}, {"data": dict(data, satellites=[])},
                        {"data": dict(data, missions=data["missions"] * 3)}, {"data": dict(data, format_epoch="wrong")},
                        {"data": dict(data, instances=[{}])}, {"data": dict(data, ground_surveys=[{}])},
                        {"data": dict(data, save_epoch=0)}):
                reader.return_value.read_root.return_value = bad
                with self.assertRaises(runner.SmokeError): runner.decode_saved(gzip.compress(b"fixture"))

    def test_invalid_java_fails_without_creating_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "new-evidence"
            argv = ["smoke", directory, "--host-jar", "host", "--fixture-jar", "fixture",
                    "--evidence-dir", str(output), "--java", "invalid", "--accept-eula"]
            error = io.StringIO()
            with patch("sys.argv", argv), patch.object(runner.recovery, "validate_inputs",
                    return_value=(Path(directory), output, [], Path("args"))), patch.object(runner.server,
                    "resolve_java", side_effect=runner.SmokeError("invalid Java selection")), contextlib.redirect_stderr(error):
                self.assertEqual(1, runner.main())
            self.assertIn("invalid Java selection", error.getvalue()); self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
