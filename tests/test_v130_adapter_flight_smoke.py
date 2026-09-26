import copy
import gzip
import json
import struct
import tempfile
import unittest
import uuid
import zlib
from pathlib import Path
from unittest.mock import Mock, patch

from scripts import run_v130_adapter_flight_smoke as runner


def uid(number):
    return str(uuid.UUID(int=number))


def nbt_uuid(value):
    return list(struct.unpack(">4i", uuid.UUID(value).bytes))


def reports():
    return [{"entity": uid(10 + index), "logical": uid(1), "snapshot": str(index + 1) * 64,
             "dimension": dimension, "origin": origin, "fuel": 1000 - 372 * index,
             "capacity": 1000, "state": "FUELED" if index == 0 else "LANDED",
             "passengers": 0, "blocks": 5, "transfer": "none"}
            for index, (dimension, origin) in enumerate(((runner.EARTH, [264, 101, 264]),
                                                        (runner.MOON, [8, 80, 8]),
                                                        (runner.EARTH, [0, 72, 0])))]


def snapshot(report, index):
    pairs = sorted(runner.RELATIVE.items())
    blocks = [{"position": list(pos), "palette": i} for i, (pos, _) in enumerate(pairs)]
    for block, (_, name) in zip(blocks, pairs):
        if name == runner.CARGO:
            block["block_entity"] = {"adapter": runner.recovery.ADAPTER,
                                     "data": {"payload_version": 1, "data": copy.deepcopy(runner.recovery.INVENTORY)}}
    return {"schema_version": 1, "snapshot_id": uid(20 + index), "source_dimension": report["dimension"],
            "source_origin": report["origin"], "content_hash": report["snapshot"], "created_at_game_time": index * 100,
            "bounding_box": [-1, 0, 0, 1, 2, 0], "relative_blocks": blocks,
            "block_palette": [{"id": name, "properties": {}} for _, name in pairs], "passenger_anchors": [[0, 1, 0]],
            "mass_inputs": {"block_count": 5, "mass": 210, "thrust": 1000, "fuel_capacity": 1000,
                            "engine_count": 1, "seat_count": 1, "guidance_count": 1, "block_entity_count": 1}}


def fuel(amount, ledger):
    return {"capacity": 1000, "amount": amount,
            "committed_debits": [{"transaction_id": nbt_uuid(value)} for value in ledger]}


def flight_data(report, state, ledger):
    return {"schema_version": 2, "logical_rocket_id": nbt_uuid(report["logical"]), "state": state,
            "fuel": fuel(report["fuel"], ledger), "current_dimension": report["dimension"], "current_origin": report["origin"],
            "current_body": runner.HOST + (":earth" if report["dimension"] == runner.EARTH else ":moon"),
            "passengers": {"seat_capacity": 1, "assignments": []}}


def fixture(count=1, restored=False):
    observations = reports()[:count + 1]
    legs = [{"transfer": uid(30 + i), "required": 372, "fuel_before": 1000 - i * 372,
             "fuel_after": 628 - i * 372} for i in range(count)]
    source, final = observations[-2:]
    ledger = [leg["transfer"] for leg in legs]
    entity_data = {"schema_version": 2, "assembly_transaction_id": nbt_uuid(final["logical"]),
                   "owner_id": nbt_uuid(runner.OWNER), "snapshot": snapshot(final, count),
                   "flight_data": flight_data(final, "LANDED", ledger)}
    entry = {"schema_version": 2, "phase": "COMMITTED", "transfer_id": nbt_uuid(ledger[-1]),
             "logical_rocket_id": nbt_uuid(final["logical"]), "owner_id": nbt_uuid(runner.OWNER),
             "source_entity_id": nbt_uuid(source["entity"]), "destination_entity_id": nbt_uuid(final["entity"]),
             "source_snapshot": snapshot(source, count - 1), "destination_snapshot": snapshot(final, count),
             "required_fuel": 372, "checksum": "a" * 64, "created_at_game_time": count * 100}
    plan = {"schema_version": 3, "request_id": nbt_uuid(ledger[-1]), "required_fuel": 372,
            "source_dimension": source["dimension"], "destination_dimension": final["dimension"]}
    for key, report, state, debits in (("source_flight", source, "TRANSIT", ledger[:-1]),
                                       ("destination_flight", final, "DESCENT", ledger)):
        entry[key] = flight_data(report, state, debits)
        entry[key].update(plan=copy.deepcopy(plan), active_transfer_id=nbt_uuid(ledger[-1]))
    locations = [runner.site(report["dimension"], report["origin"]) for report in observations]
    chunks = []
    for dimension, x, z in sorted(runner.watched_chunks(locations)):
        selected = {pos for loc in locations if loc["dimension"] == dimension for pos in runner.positions(loc)
                    if (pos[0] // 16, pos[2] // 16) == (x, z)}
        restored_positions = runner.positions(locations[-1]) if restored and dimension == final["dimension"] else {}
        chunk = {"dimension": dimension, "position": [x, z], "entities": [], "block_entities": [],
                 "blocks": [{"position": list(pos), "state": {"Name": restored_positions.get(pos, "minecraft:air")}}
                            for pos in sorted(selected)]}
        if dimension == final["dimension"] and [final["origin"][0] // 16, final["origin"][2] // 16] == [x, z] and not restored:
            px, py, pz = final["origin"]
            chunk["entities"] = [{"id": runner.ROCKET, "UUID": nbt_uuid(final["entity"]),
                                  "Pos": [px + .5, float(py), pz + .5], "RocketEntityData": entity_data}]
        if restored and dimension == final["dimension"]:
            px, py, pz = final["origin"]
            if ((px + 1) // 16, pz // 16) == (x, z):
                chunk["block_entities"] = [{"id": runner.CARGO, "x": px + 1, "y": py, "z": pz,
                                            "FixtureInventory": {"schema_version": 1, **copy.deepcopy(runner.recovery.INVENTORY)}}]
        chunks.append(chunk)
    disk = {"chunks": chunks, "transactions": {"schema_version": 2, "transactions": []},
            "transfers": {"schema_version": 2, "transfers": [] if restored else [entry]}}
    return disk, observations, legs, entity_data


class OracleTests(unittest.TestCase):
    def verify(self, disk, observed, legs, restored=False, previous=None):
        return runner.validate_disk(disk, observed, legs, restored, previous)

    def test_four_native_disk_boundaries(self):
        first, observed, legs, _ = fixture()
        moon = self.verify(first, observed, legs)
        second, observed, legs, _ = fixture(2)
        self.verify(second, observed, legs, previous=moon)
        final, observed, legs, _ = fixture(2, True)
        self.assertEqual(self.verify(final, observed, legs, True), self.verify(copy.deepcopy(final), observed, legs, True))

    def test_all_outer_transfer_bindings_are_required(self):
        for field, value in {"schema_version": 1, "phase": "PREPARED", "transfer_id": nbt_uuid(uid(99)),
                             "logical_rocket_id": nbt_uuid(uid(99)), "owner_id": nbt_uuid(uid(99)),
                             "source_entity_id": nbt_uuid(uid(99)), "destination_entity_id": nbt_uuid(uid(99)),
                             "required_fuel": 0, "checksum": "bad"}.items():
            disk, observed, legs, _ = fixture()
            disk["transfers"]["transfers"][0][field] = value
            with self.subTest(field=field), self.assertRaises(runner.SmokeError):
                self.verify(disk, observed, legs)

    def test_return_replaces_old_record(self):
        disk, observed, legs, _ = fixture(2)
        old = fixture()[0]["transfers"]["transfers"][0]
        for entries in ([], [old], disk["transfers"]["transfers"] + [old]):
            changed = copy.deepcopy(disk)
            changed["transfers"]["transfers"] = entries
            with self.assertRaises(runner.SmokeError):
                self.verify(changed, observed, legs)

    def test_ledger_rejects_topup_missing_duplicate_and_wrong_debit(self):
        for amount, ledger in ((628, [uid(30), uid(31)]), (256, [uid(31)]),
                               (256, [uid(30), uid(30)]), (256, [uid(30), uid(99)])):
            with self.subTest(amount=amount, ledger=ledger), self.assertRaises(runner.SmokeError):
                runner.check_fuel(fuel(amount, ledger), 256, [uid(30), uid(31)])

    def test_flight_receipts_and_plan_are_not_landed_entity_data(self):
        for key, field, value in (("source_flight", "state", "LANDED"),
                                  ("destination_flight", "active_transfer_id", nbt_uuid(uid(99))),
                                  ("destination_flight", "current_origin", [99, 99, 99]),
                                  ("source_flight", "plan", {})):
            disk, observed, legs, _ = fixture()
            disk["transfers"]["transfers"][0][key][field] = value
            with self.subTest(key=key, field=field), self.assertRaises(runner.SmokeError):
                self.verify(disk, observed, legs)

    def test_relocation_changes_only_allowed_fields(self):
        observed = reports()
        source, destination = snapshot(observed[0], 0), snapshot(observed[1], 1)
        runner.validate_relocation(source, destination)
        for field, value in (("snapshot_id", source["snapshot_id"]), ("content_hash", source["content_hash"]),
                             ("created_at_game_time", -1), ("passenger_anchors", []), ("mass_inputs", {})):
            altered = copy.deepcopy(destination)
            altered[field] = value
            with self.subTest(field=field), self.assertRaises(runner.SmokeError):
                runner.validate_relocation(source, altered)

    def test_exact_payload_counts_slots_metadata_and_adapter_are_required(self):
        for change in (lambda p: p.update(adapter="other:adapter"),
                       lambda p: p["data"].update(payload_version=2),
                       lambda p: p["data"]["data"]["Items"][0].update(Count=18),
                       lambda p: p["data"]["data"]["Items"][1].update(Slot=26),
                       lambda p: p["data"]["data"]["Items"][0].pop("tag")):
            report = reports()[0]
            value = snapshot(report, 0)
            change(next(b["block_entity"] for b in value["relative_blocks"] if "block_entity" in b))
            with self.assertRaises(runner.SmokeError):
                runner.validate_snapshot(value, report)

    def test_snapshot_receipt_position_bounds_and_metrics_checked(self):
        report = reports()[0]
        for key, value in (("source_dimension", runner.MOON), ("source_origin", [0, 1, 2]),
                           ("bounding_box", [0] * 6), ("content_hash", "f" * 64), ("mass_inputs", {})):
            changed = snapshot(report, 0)
            changed[key] = value
            with self.subTest(key=key), self.assertRaises(runner.SmokeError):
                runner.validate_snapshot(changed, report)

    def test_disk_rejects_drops_duplicate_rocket_missing_chunks_and_observations(self):
        for mutate in (lambda d: d["chunks"].pop(),
                       lambda d: d["chunks"].append(copy.deepcopy(d["chunks"][0])),
                       lambda d: next(c for c in d["chunks"] if c["blocks"])["blocks"].pop(),
                       lambda d: next(c for c in d["chunks"] if c["blocks"])["blocks"].append(
                           copy.deepcopy(next(c for c in d["chunks"] if c["blocks"])["blocks"][0])),
                       lambda d: d["chunks"][0]["entities"].append({"id": "minecraft:item"}),
                       lambda d: d["chunks"][0]["entities"].append({"id": runner.ROCKET}),
                       lambda d: d["transactions"]["transactions"].append({})):
            disk, observed, legs, _ = fixture()
            mutate(disk)
            with self.assertRaises(runner.SmokeError):
                self.verify(disk, observed, legs)

    def test_final_disk_requires_native_inventory_and_both_empty_journals(self):
        for mutate in (lambda d: d["transfers"]["transfers"].append({}),
                       lambda d: d["transactions"]["transactions"].append({}),
                       lambda d: next(c for c in d["chunks"] if c["block_entities"])["block_entities"][0]["FixtureInventory"].update(schema_version=2),
                       lambda d: next(c for c in d["chunks"] if c["block_entities"])["block_entities"][0]["FixtureInventory"]["Items"][0].pop("tag")):
            disk, observed, legs, _ = fixture(2, True)
            mutate(disk)
            with self.assertRaises(runner.SmokeError):
                self.verify(disk, observed, legs, True)


class BoundsAndDriverTests(unittest.TestCase):
    def test_disassembly_rejects_implicit_and_wrong_amount_before_explicit_disposal(self):
        run = runner.FlightRun(Path("unused"), Path("unused"), [], "unused", 1, 1)
        run.reports = reports()
        run.legs = [{"transfer": uid(31)}]
        run.saved_snbt = "unchanged"
        process = Mock()
        process.lines = []
        receipt = Mock()
        receipt.groups.return_value = (uid(12), uid(1), "SUCCESS", "5", "0")
        with patch.object(run, "query", return_value=receipt) as query, \
                patch.object(run, "entity_snbt", return_value="unchanged"), \
                patch.object(run, "inspect_saved_transfer"), patch.object(run, "check_live"):
            run.disassemble(process)
        commands = [call.args[1] for call in query.call_args_list]
        base = f"execute in {runner.EARTH} run arce rocket release-test disassemble {uid(12)}"
        self.assertEqual([base, base + " discard-fuel 257", base + " discard-fuel 256"], commands)
        self.assertIn("amount=256 reason=confirmed_disassembly", process.wait_for.call_args_list[0].args[0].pattern)

    def test_only_the_validated_generated_properties_timestamp_may_change(self):
        original = (b"#Minecraft server properties\r\n#Sun Sep 27 00:24:52 CST 2026\r\n"
                    b"server-ip=127.0.0.1\r\nserver-port=50046\r\nunknown-setting=value\r\n")
        identity = runner.configuration_identity("server.properties", original)
        later = original.replace(b"00:24:52", b"00:25:37")
        self.assertNotEqual(original, later)
        self.assertEqual(identity, runner.configuration_identity("server.properties", later))
        for changed in (original.replace(b"127.0.0.1", b"0.0.0.0"),
                        original.replace(b"50046", b"50047"),
                        original.replace(b"unknown-setting=value", b"unknown-setting=other"),
                        original + b"#new comment\r\n", original + b"new-setting=true\r\n",
                        original.replace(b"\r\n", b"\n")):
            with self.subTest(changed=changed):
                self.assertNotEqual(identity, runner.configuration_identity("server.properties", changed))
        for malformed in (original.replace(b"#Minecraft server properties", b"#Other header"),
                          original.replace(b"#Sun Sep 27 00:24:52 CST 2026\r\n", b""),
                          original.replace(b"00:24:52", b"99:99:99"),
                          original.replace(b"CST 2026", b"CST 2026 extra")):
            with self.subTest(malformed=malformed), self.assertRaises(runner.SmokeError):
                runner.configuration_identity("server.properties", malformed)
        self.assertNotEqual(runner.configuration_identity("config/fml.toml", original),
                            runner.configuration_identity("config/fml.toml", later))

    def test_dimension_region_coordinates_include_negative_and_boundary_chunks(self):
        self.assertEqual(Path("world/dimensions/advancedrocketrycommunity/moon/entities/r.-1.0.mca"),
                         runner.region_path(runner.MOON, "entities", -1, 0))
        chunks = runner.watched_chunks([runner.site(runner.EARTH, [0, 80, 0])])
        self.assertEqual({(runner.EARTH, x, z) for x in (-1, 0) for z in (-1, 0)}, chunks)
        for dimension, origin in (("other:moon", [0, 80, 0]), (runner.EARTH, [99999, 80, 0]),
                                   (runner.EARTH, [0, 319, 0]), (runner.EARTH, [0, 80.5, 0])):
            with self.assertRaises(runner.SmokeError):
                runner.site(dimension, origin)
        with self.assertRaises(runner.SmokeError):
            runner.watched_chunks([runner.site(runner.EARTH, [0, 80, 0])] * 4)

    def test_entity_nbt_coordinates_bounds_and_trailing_data(self):
        raw = b"\x0a\x00\x00\x0b\x00\x08Position" + struct.pack(">iii", 2, -1, 0)
        raw += b"\x09\x00\x08Entities\x0a\x00\x00\x00\x00\x00"
        encoded = zlib.compress(raw)
        self.assertEqual([], runner.decode_entities(encoded, -1, 0)["Entities"])
        with self.assertRaises(runner.SmokeError):
            runner.decode_entities(encoded, 0, 0)
        for value in (encoded + b"extra", encoded[:-2], zlib.compress(raw + b"extra"), zlib.compress(b"x" * (1024**2 + 1))):
            with self.assertRaises((runner.SmokeError, ValueError)):
                runner.decode_entities(value, -1, 0)

    def test_transfer_reader_checks_real_root_and_expansion_bound(self):
        raw = b"\x0a\x00\x00\x0a\x00\x04data\x03\x00\x0eschema_version\x00\x00\x00\x02"
        raw += b"\x09\x00\x09transfers\x0a\x00\x00\x00\x00\x00\x00"
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "transfers.dat"
            path.write_bytes(gzip.compress(raw))
            self.assertEqual({"schema_version": 2, "transfers": []}, runner.read_transfers(path))
            for bad in (raw.replace(b"transfers", b"wronglist"), b"x" * (4 * 1024**2 + 1)):
                path.write_bytes(gzip.compress(bad))
                with self.assertRaises((runner.SmokeError, ValueError)):
                    runner.read_transfers(path)

    def test_flight_phase_has_no_missing_mod_error_exemption(self):
        line = "[main/ERROR] [ne.mi.re.GameData/REGISTRIES]: " + runner.recovery.MISSING_MAPPING_ERROR
        for phase in runner.PHASES:
            with self.assertRaises(runner.SmokeError):
                runner.recovery.audit_log([line], phase)

    def test_setup_waits_for_the_one_chunk_covering_entire_air_shell(self):
        run = runner.FlightRun(Path("unused"), Path("unused"), [], "unused", 1, 1)
        process = Mock()
        observed = []
        process.command.side_effect = observed.append
        with patch.object(runner.recovery, "wait_condition", side_effect=lambda p, c, *a, **k: observed.append(c)), \
                patch.object(run, "query", side_effect=runner.SmokeError("stop before assembly")):
            with self.assertRaises(runner.SmokeError):
                run.assemble(process)
        self.assertLess(observed.index("execute if loaded 264 100 264"), observed.index("fill 262 100 263 266 104 265 minecraft:air"))
        self.assertEqual({(x // 16, z // 16) for x in range(262, 267) for z in range(263, 266)}, {(16, 16)})
        self.assertIn("setblock 263 101 264 advancedrocketrycommunity:rocket_fuel_tank", observed)

    def test_return_launch_never_calls_refuel(self):
        run = runner.FlightRun(Path("unused"), Path("unused"), [], "unused", 1, 1)
        run.reports = reports()[:2]
        run.legs = [{"transfer": uid(30)}]
        process = Mock()
        process.lines = []
        with patch.object(run, "query", side_effect=runner.SmokeError("stop at launch")) as query, \
                patch.object(runner.flight.FlightHarness, "refuel") as refill:
            with self.assertRaises(runner.SmokeError):
                run.launch(process, runner.EARTH)
        refill.assert_not_called()
        self.assertEqual(f"execute in {runner.MOON} run arce rocket release-test launch {uid(11)} earth", query.call_args.args[1])

    def test_unbounded_entity_selector_is_enumerated_once_across_dimensions(self):
        run = runner.FlightRun(Path("unused"), Path("unused"), [], "unused", 1, 1)
        run.reports = reports()[:2]
        process = Mock()
        with patch.object(run, "query"):
            run.check_live(process, False, "TEST")
        selectors = [call.args[0] for call in process.command.call_args_list if " as @e[" in call.args[0]]
        self.assertEqual([f"execute as @e[type={runner.ROCKET}] run scoreboard players add #rockets arce_v130_flight 1"], selectors)

    def test_failure_retains_exit_result_commands_and_only_aborts_owned_process(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            evidence = root / "evidence"
            evidence.mkdir()
            run = runner.FlightRun(root / "server", evidence, [], "unused", 1, 1)
            process = Mock()
            process.wait_for.side_effect = runner.SmokeError("startup failed")
            process.process.poll.return_value = -15
            with patch.object(run, "mods", return_value={}), patch.object(runner.server_smoke, "CapturedProcess", return_value=process):
                with self.assertRaises(runner.SmokeError):
                    run.run_phase(runner.PHASES[0])
            process.abort.assert_called_once()
            result = json.loads((evidence / runner.PHASES[0] / "result.json").read_text())
            self.assertEqual(("FAIL", -15), (result["result"], result["exit_code"]))
            self.assertTrue((evidence / runner.PHASES[0] / "commands.json").exists())

    def test_disposable_validation_rejects_preexisting_world_and_nested_evidence(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            server = root / "server"
            (server / "libraries").mkdir(parents=True)
            (server / "world").mkdir()
            with self.assertRaises(runner.SmokeError):
                runner.recovery.validate_inputs(server, root / "new", root / "host.jar", root / "fixture.jar", "unused")
            (server / "world").rmdir()
            with self.assertRaises(runner.SmokeError):
                runner.recovery.validate_inputs(server, server / "evidence", root / "host.jar", root / "fixture.jar", "unused")
            with self.assertRaises(runner.SmokeError):
                runner.recovery.safe_path(server / ".." / "other")


if __name__ == "__main__":
    unittest.main()
