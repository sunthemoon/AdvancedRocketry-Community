import contextlib
import copy
import gzip
import io
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch
from scripts import run_v130_satellite_payload_smoke as runner


def nbt_uuid(value):
    raw = uuid.UUID(value).bytes
    return [int.from_bytes(raw[i:i + 4], "big", signed=True) for i in range(0, 16, 4)]


def fixture(phase):
    satellite = str(uuid.UUID(int=1))
    missions = [str(uuid.UUID(int=i)) for i in range(2, 4 if phase == "reinstalled" else 3)]
    owner = nbt_uuid(runner.OWNER)
    state = {"schema_version": 1, "satellite_id": nbt_uuid(satellite), "owner_id": owner,
             "definition_id": runner.DEFINITION, "status": "operational"}
    if phase == "create": state["current_mission_id"] = nbt_uuid(missions[0])
    amount = {"create": 0, "mod-uninstalled": 126, "reinstalled": 337}[phase]
    earned = {"create": 0, "mod-uninstalled": 137, "reinstalled": 348}[phase]
    data = {"schema_version": 2, "satellites": [state], "missions": [
        {"schema_version": 1, "mission_id": nbt_uuid(identity), "satellite_id": nbt_uuid(satellite),
         "owner_id": owner, "definition_id": runner.DEFINITION, "target_body_id": runner.HOST + ":earth",
         "discovery_required": 1 if i == 0 else 0, "started_at": 100, "completes_at": 500 if i == 0 else 120,
         "research_yield": 137 if i == 0 else 211, "discovery_cost": 11,
         "status": "active" if phase == "create" else "claimed"} for i, identity in enumerate(missions)],
         "research_accounts": [{"schema_version": 1, "owner_id": owner, "balance": amount,
                                "lifetime_earned": earned, "lifetime_spent": 0 if phase == "create" else 11}]}
    terminal = {"schema_version": 1, "owner_id": owner, "energy": 9000, "selected_target": 0,
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
        for lines, installed in (([], True), ([runner.REGISTERED] * 2, True), ([runner.REGISTERED], False),
                                 ([runner.REGISTERED.replace("1.7", "1.6")], True)):
            with self.assertRaises(runner.SmokeError): runner.validate_registration(lines, installed)

    def test_probe_preserves_identity_consumption_and_authoritative_result(self):
        satellite = str(uuid.UUID(int=1))
        value = {"action": "create", "energy": 9000, "result": 0, "payload_count": 1, "package_empty": True,
                 "owner": runner.OWNER, "definition": runner.DEFINITION, "satellite": satellite}
        self.assertEqual(satellite, runner.validate_probe(value, "create", None, 0))
        for key, changed in (("action", "claim"), ("energy", 10000), ("result", 2), ("payload_count", 2),
                             ("package_empty", False), ("owner", str(uuid.UUID(int=9))), ("definition", "missing:id")):
            with self.assertRaises(runner.SmokeError): runner.validate_probe(dict(value, **{key: changed}), "create", satellite, 0)
        with self.assertRaises(runner.SmokeError): runner.validate_probe(value, "create", str(uuid.UUID(int=8)), 0)

    def test_native_mission_snapshots_rewards_and_terminal_survive_three_phases(self):
        previous = None
        for phase in runner.PHASES:
            data, terminal, satellite, missions = fixture(phase)
            runner.validate_disk(data, terminal, phase, satellite, missions, previous)
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
        for key, value in (("energy", 10000), ("selected_target", 1), ("last_result", 0)):
            with self.assertRaises(runner.SmokeError): runner.validate_disk(data, dict(terminal, **{key: value}), "reinstalled", satellite, missions, None)
        bad = copy.deepcopy(terminal); bad["inventory"]["Items"][0]["Count"] = 2
        with self.assertRaises(runner.SmokeError): runner.validate_disk(data, bad, "reinstalled", satellite, missions, None)
        previous = {missions[0]: dict(data["missions"][0], started_at=99, completes_at=499)}
        with self.assertRaises(runner.SmokeError): runner.validate_disk(data, terminal, "reinstalled", satellite, missions, previous)

    def test_decoder_checks_native_schema_and_record_limits(self):
        data, _, _, _ = fixture("create")
        with patch.object(runner.recovery, "NbtReader") as reader:
            reader.return_value.read_root.return_value = {"data": data}
            self.assertEqual(data, runner.decode_saved(gzip.compress(b"fixture")))
            for bad in ({}, {"data": dict(data, schema_version=9)}, {"data": dict(data, satellites=[])},
                        {"data": dict(data, missions=data["missions"] * 3)}):
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
