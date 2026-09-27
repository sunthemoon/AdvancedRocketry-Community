import copy
import json
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import run_v140_planetary_worlds_smoke as runner


class PlanetaryWorldsSmokeTest(unittest.TestCase):
    @staticmethod
    def nbt_uuid(value):
        value = uuid.UUID(value).int
        return [(part if part < 2**31 else part - 2**32) for shift in (96, 64, 32, 0)
                for part in [(value >> shift) & 0xffffffff]]

    def transfer_fixture(self):
        logical, source_id, target_id, transfer = [str(uuid.uuid4()) for _ in range(4)]
        reports = [{"logical": logical, "entity": source_id, "snapshot": "a"*64, "dimension": runner.HOST + ":venus",
                    "origin": [0, 80, 0], "fuel": 1000, "state": "LANDED"},
                   {"logical": logical, "entity": target_id, "snapshot": "b"*64, "dimension": runner.EARTH,
                    "origin": [0, 70, 0], "fuel": 600, "state": "LANDED"}]
        legs = [{"transfer": transfer, "required": 400}]
        plan = {"schema_version": 3, "request_id": self.nbt_uuid(transfer), "source_dimension": reports[0]["dimension"],
                "destination_dimension": runner.EARTH, "source_body": runner.HOST + ":venus", "destination_body": runner.HOST + ":earth",
                "required_fuel": 400, "destination_target": runner.surface_target(reports[1])}
        snapshots, states = [], []
        for index, report in enumerate(reports):
            snapshots.append({"snapshot_id": str(uuid.uuid4()), "content_hash": report["snapshot"], "source_dimension": report["dimension"],
                              "source_origin": report["origin"], "created_at_game_time": 100+index, "relative_blocks": [{"data": "cargo"}]})
            states.append({"schema_version": 2, "state": "TRANSIT" if index == 0 else "DESCENT", "logical_rocket_id": self.nbt_uuid(logical),
                           "current_dimension": report["dimension"], "current_origin": report["origin"],
                           "current_body": runner.surface_target(report)["body_id"], "current_target": runner.surface_target(report),
                           "passengers": {"seat_capacity": 1, "assignments": []}, "plan": copy.deepcopy(plan),
                           "active_transfer_id": self.nbt_uuid(transfer), "fuel": {"amount": report["fuel"], "capacity": 2000,
                           "committed_debits": [] if index == 0 else [{"transaction_id": self.nbt_uuid(transfer)}]}})
        record = {"schema_version": 2, "phase": "COMMITTED", "transfer_id": self.nbt_uuid(transfer),
                  "logical_rocket_id": self.nbt_uuid(logical), "owner_id": self.nbt_uuid(runner.native.OWNER),
                  "source_entity_id": self.nbt_uuid(source_id), "destination_entity_id": self.nbt_uuid(target_id),
                  "required_fuel": 400, "checksum": "c"*64, "source_snapshot": snapshots[0], "destination_snapshot": snapshots[1],
                  "source_flight": states[0], "destination_flight": states[1]}
        return {"schema_version": 2, "transfers": [record]}, reports, legs, {"snapshot": snapshots[1]}

    def test_captured_transfer_binds_exact_authorities(self):
        journal, reports, legs, rocket = self.transfer_fixture()
        runner.validate_transfer(journal, reports, legs, rocket)
        for field in ("owner_id", "logical_rocket_id", "source_entity_id", "destination_entity_id", "transfer_id",
                      "required_fuel", "checksum", "phase", "schema_version"):
            with self.subTest(field=field):
                changed = copy.deepcopy(journal)
                value = self.nbt_uuid(str(uuid.uuid4())) if field.endswith("_id") else 0
                if field in ("checksum", "phase"):
                    value = "invalid"
                changed["transfers"][0][field] = value
                with self.assertRaises(runner.SmokeError):
                    runner.validate_transfer(changed, reports, legs, rocket)
        with self.assertRaises(runner.SmokeError):
            runner.validate_transfer({"schema_version": 2, "transfers": []}, reports, legs, rocket)

    def test_captured_transfer_rejects_changed_plan_or_payload(self):
        journal, reports, legs, rocket = self.transfer_fixture()
        for kind in ("plan", "cargo", "ledger"):
            changed = copy.deepcopy(journal)
            if kind == "plan":
                changed["transfers"][0]["destination_flight"]["plan"]["required_fuel"] = 399
            elif kind == "cargo":
                changed["transfers"][0]["source_snapshot"]["relative_blocks"][0]["data"] = "other"
            else:
                changed["transfers"][0]["destination_flight"]["fuel"]["committed_debits"] = []
            with self.assertRaises(runner.SmokeError):
                runner.validate_transfer(changed, reports, legs, rocket)

    def test_stationary_flight_checks_body_target_passengers_and_no_plan(self):
        journal, reports, legs, _ = self.transfer_fixture()
        state = copy.deepcopy(journal["transfers"][0]["destination_flight"])
        state.pop("plan")
        state.pop("active_transfer_id")
        state["state"] = "LANDED"
        runner.validate_flight(state, reports[-1], legs, "LANDED")
        for field, value in (("current_body", runner.HOST + ":mars"), ("current_target", runner.surface_target(reports[0])),
                             ("current_origin", [0, 71, 0]), ("logical_rocket_id", self.nbt_uuid(str(uuid.uuid4()))),
                             ("passengers", {"seat_capacity": 1, "assignments": [{}]}), ("plan", {}),
                             ("active_transfer_id", self.nbt_uuid(legs[0]["transfer"]))):
            with self.subTest(field=field):
                changed = copy.deepcopy(state)
                changed[field] = value
                with self.assertRaises(runner.SmokeError):
                    runner.validate_flight(changed, reports[-1], legs, "LANDED")

    def test_station_records_match_uuid_owner_body_and_name(self):
        request = {"station_id": str(uuid.uuid4()), "owner_id": str(uuid.uuid4()), "orbit_body": runner.HOST + ":mars", "name": "Planetary mars"}
        station = {**request, "station_id": self.nbt_uuid(request["station_id"]), "owner_id": self.nbt_uuid(request["owner_id"])}
        runner.validate_stations({"stations": [station]}, [request])
        for field in request:
            changed = copy.deepcopy(station)
            changed[field] = self.nbt_uuid(str(uuid.uuid4())) if field.endswith("_id") else "different"
            with self.assertRaises(runner.SmokeError):
                runner.validate_stations({"stations": [changed]}, [request])
        with self.assertRaises(runner.SmokeError):
            runner.validate_stations({"stations": [station, station]}, [request])

    def bindings(self):
        old = {"schema_version": 1, "bindings": [
            {"body_id": runner.HOST + ":" + name, "level": level}
            for name, level in (("earth", runner.EARTH), ("moon", runner.HOST + ":moon"), ("space", runner.HOST + ":space"))]}
        new = copy.deepcopy(old)
        new["bindings"] += [{"body_id": runner.HOST + ":mars", "level": runner.HOST + ":mars"},
                            {"body_id": runner.HOST + ":venus", "level": runner.HOST + ":venus"},
                            {"body_id": runner.HOST + ":gas_giant"}]
        return old, new

    def test_binding_upgrade_is_additive_and_order_independent(self):
        old, new = self.bindings()
        new["bindings"].reverse()
        before = copy.deepcopy((old, new))
        runner.check_bindings(old, new)
        self.assertEqual(before, (old, new))

    def test_binding_upgrade_rejects_remap_alias_gas_surface_and_schema_change(self):
        old, valid = self.bindings()
        for kind in ("remap", "alias", "gas", "schema", "missing", "duplicate"):
            with self.subTest(kind=kind):
                new = copy.deepcopy(valid)
                if kind == "remap":
                    new["bindings"][0]["level"] = runner.HOST + ":another_earth"
                elif kind == "alias":
                    new["bindings"][3]["level"] = runner.EARTH
                elif kind == "gas":
                    new["bindings"][-1]["level"] = runner.HOST + ":gas_giant"
                elif kind == "schema":
                    new["schema_version"] = 2
                elif kind == "missing":
                    new["bindings"].pop()
                else:
                    new["bindings"][3] = copy.deepcopy(new["bindings"][4])
                with self.assertRaises(runner.SmokeError):
                    runner.check_bindings(old, new)

    def test_native_directories_are_exact_and_bounded(self):
        self.assertEqual(Path("world"), runner.world_directory(runner.EARTH))
        self.assertEqual(Path("world/dimensions/advancedrocketrycommunity/mars"), runner.world_directory(runner.HOST + ":mars"))
        for value in (runner.HOST + ":gas_giant", "../world", "minecraft:the_end", runner.HOST + ":../../escape"):
            with self.assertRaises(runner.SmokeError):
                runner.world_directory(value)

    def test_structure_comparison_ignores_only_relocation_fields(self):
        original = {"snapshot_id": "old", "source_dimension": runner.EARTH, "source_origin": [1, 2, 3],
                    "created_at_game_time": 12, "content_hash": "old", "relative_blocks": [{"data": {"Count": 17}}]}
        moved = copy.deepcopy(original)
        for key in runner.native.RELOCATED:
            moved[key] = "moved"
        self.assertEqual(runner.structure(original), runner.structure(moved))
        moved["relative_blocks"][0]["data"]["Count"] = 18
        self.assertNotEqual(runner.structure(original), runner.structure(moved))

    def test_missing_native_rocket_is_not_a_passing_capture(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(runner.SmokeError, "Missing/duplicate native rocket"):
                runner.capture_rocket(Path(directory), Path(directory) / "output", {"dimension": runner.EARTH}, None, [])

    def test_empty_entity_region_does_not_bypass_required_rocket_and_partial_header_rejects(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "world/entities/r.0.0.mca"
            path.parent.mkdir(parents=True)
            path.write_bytes(b"")
            with self.assertRaisesRegex(runner.SmokeError, "Missing/duplicate native rocket"):
                runner.capture_rocket(root, root / "empty", {"dimension": runner.EARTH}, None, [])
            self.assertEqual(b"", (root / "empty/world/entities/r.0.0.mca").read_bytes())
            path.write_bytes(bytes(4096))
            with self.assertRaisesRegex(runner.SmokeError, "Truncated entity region"):
                runner.capture_rocket(root, root / "partial", {"dimension": runner.EARTH}, None, [])

    def test_material_and_empty_region_budgets_remain_separately_bounded(self):
        for size, count in ((8192, 17), (0, 33)):
            with self.subTest(size=size), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                parent = root / "world/entities"
                parent.mkdir(parents=True)
                for index in range(count):
                    (parent / f"r.{index}.0.mca").write_bytes(bytes(size))
                with self.assertRaisesRegex(runner.SmokeError, "Entity region file budget exceeded"):
                    runner.capture_rocket(root, root / "output", {"dimension": runner.EARTH}, None, [])

    def test_spawn_failure_is_archived_without_assuming_process_exists(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            harness = runner.Harness(output, output, "java", 1)
            with patch.object(runner.server, "CapturedProcess", side_effect=OSError("fixture spawn failure")):
                with self.assertRaisesRegex(OSError, "fixture spawn failure"):
                    harness.cycle("baseline", {"sha256": "fixture"})
            observed = json.loads((output / "baseline/observations.json").read_text())
            self.assertEqual("FAIL", observed["result"])
            self.assertIsNone(observed["exit_code"])
            self.assertEqual([], json.loads((output / "baseline/commands.json").read_text()))

    def test_started_process_failure_is_aborted_and_exit_retained(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            process = Mock()
            process.wait_for.side_effect = runner.SmokeError("fixture startup failure")
            process.process.poll.return_value = 17
            harness = runner.Harness(output, output, "java", 1)
            with patch.object(runner.server, "CapturedProcess", return_value=process):
                with self.assertRaisesRegex(runner.SmokeError, "fixture startup failure"):
                    harness.cycle("baseline", {"sha256": "fixture"})
            process.abort.assert_called_once()
            observed = json.loads((output / "baseline/observations.json").read_text())
            self.assertEqual("FAIL", observed["result"])
            self.assertEqual(17, observed["exit_code"])

    def test_research_capture_checks_private_totals_identity_and_restart_replay(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "world/data").mkdir(parents=True)
            for name in ("celestial", "satellite_missions"):
                (root / "world/data" / (runner.HOST + "_" + name + ".dat")).write_bytes(b"fixture")
            output = root / "output"
            output.mkdir()
            harness = runner.Harness(root, output, "java", 1, True)
            owners = [str(uuid.uuid4()), str(uuid.uuid4())]
            harness.research = [{"satellite": str(uuid.uuid4()), "mission": str(uuid.uuid4()),
                                 "owner": owners[0 if i < 2 else 1], "body": runner.HOST + ":" + body}
                                for i, body in enumerate(runner.BODIES)]
            progress = {"schema_version": 2, "bodies": [{"id": m["body"], "discovered_at": 20} for m in harness.research]}
            missions = {"schema_version": 2, "missions": [], "satellites": [], "research_accounts": []}
            for m in harness.research:
                missions["missions"].append({"mission_id": self.nbt_uuid(m["mission"]), "satellite_id": self.nbt_uuid(m["satellite"]),
                    "owner_id": self.nbt_uuid(m["owner"]), "target_body_id": m["body"], "status": "claimed",
                    "research_yield": 120, "discovery_cost": 100, "discovery_required": 1,
                    "schema_version": 1, "definition_id": runner.HOST + ":data_satellite", "started_at": 0, "completes_at": 200})
                missions["satellites"].append({"satellite_id": self.nbt_uuid(m["satellite"]),
                    "owner_id": self.nbt_uuid(m["owner"]), "status": "operational",
                    "schema_version": 1, "definition_id": runner.HOST + ":data_satellite"})
            for owner, count in zip(owners, (2, 1)):
                missions["research_accounts"].append({"schema_version": 1, "owner_id": self.nbt_uuid(owner), "balance": 20 * count,
                                                     "lifetime_earned": 120 * count, "lifetime_spent": 100 * count})
            def reader(path):
                return {"data": copy.deepcopy(missions if "satellite_missions" in path.name else progress)}
            with patch.object(runner.schema, "read_nbt", side_effect=reader):
                harness.capture_research(output, "upgrade")
                harness.capture_research(output, "restart")
                for field in ("balance", "lifetime_earned", "lifetime_spent"):
                    missions["research_accounts"][0][field] += 1
                    with self.assertRaises(runner.SmokeError):
                        harness.capture_research(output, "restart")
                    missions["research_accounts"][0][field] -= 1
                missions["missions"][0]["status"] = "ready"
                with self.assertRaises(runner.SmokeError):
                    harness.capture_research(output, "restart")
                missions["missions"][0]["status"] = "claimed"
                for collection, field, replacement in (("missions", "definition_id", "test:other"),
                        ("satellites", "definition_id", "test:other"), ("missions", "completes_at", 201),
                        ("missions", "schema_version", 2), ("satellites", "schema_version", 2), ("research_accounts", "schema_version", 2)):
                    original = missions[collection][0][field]
                    missions[collection][0][field] = replacement
                    with self.assertRaises(runner.SmokeError):
                        harness.capture_research(output, "upgrade")
                    missions[collection][0][field] = original
                missions["research_accounts"].append(copy.deepcopy(missions["research_accounts"][0]))
                with self.assertRaises(runner.SmokeError):
                    harness.capture_research(output, "restart")

    def test_research_launch_uses_the_commands_bounded_word_argument(self):
        harness = runner.Harness(Path("unused"), Path("unused-output"), "java", 1, True)
        before = {"entity": str(uuid.uuid4()), "dimension": runner.EARTH, "fuel": 2000}
        harness.reports = [before]
        other_owner = uuid.uuid4()
        receipts = [Mock()]
        for i, body in enumerate(runner.BODIES):
            owner = runner.native.OWNER if i < 2 else str(other_owner)
            receipts.append(Mock(groups=Mock(return_value=(str(uuid.uuid4()), str(uuid.uuid4()), owner,
                                                           runner.HOST + ":" + body, "200"))))
        harness.query = Mock(side_effect=receipts)
        harness.claim_research = Mock()
        process = Mock(lines=[])
        with patch.object(runner.flight.FlightHarness, "report", return_value=before), \
                patch.object(runner.uuid, "uuid4", return_value=other_owner):
            harness.unlock_planets(process)
        for call, body in zip(harness.query.call_args_list[1:], runner.BODIES):
            command = call.args[1]
            self.assertEqual(body, command.split()[-1])
            self.assertNotIn(":", command)
        self.assertEqual(6, harness.claim_research.call_count)


if __name__ == "__main__":
    unittest.main()
