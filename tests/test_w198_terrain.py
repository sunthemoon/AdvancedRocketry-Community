"""Integrated cut mapping and independent native-oracle regression tests."""
import copy
import json
from pathlib import Path
import sys
import struct
import tempfile
import unittest
import zlib
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import w198_terrain as terrain
import loader_cut_nbt as nbt
from run_w152_native import native_case_plan


def tag(value):
    """Encode the small synthetic native fixture without retaining campaign archives."""
    if isinstance(value, dict):
        return nbt.Tag(nbt.COMPOUND, {key: tag(child) for key, child in value.items()})
    if isinstance(value, list):
        children = [tag(child) for child in value]
        return nbt.Tag(nbt.LIST, (children[0].type if children else nbt.COMPOUND, children))
    if isinstance(value, str):
        return nbt.Tag(nbt.STRING, value.encode())
    if isinstance(value, int):
        return nbt.Tag(nbt.INT, value)
    raise TypeError(value)


class W198NativeOracleTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="w198-oracle-")
        self.addCleanup(temporary.cleanup)
        self.world = Path(temporary.name)
        self.item = dict(id="minecraft:diamond", Count=7, tag={"W152": "original"})
        payloads = [None, None,
                    dict(adapter="mod:vanilla_container_v1", data=dict(Items=[self.item])),
                    dict(adapter="mod:vanilla_container_v1", data=dict(Items=[self.item])),
                    dict(adapter="mod:pressurized_tank_v1", data=dict(tank=dict(schema=1, fluid=dict(
                        FluidName="minecraft:water", Amount=1000, Tag={"W152": "original-fluid"})))),
                    dict(adapter="mod:satellite_bay_v1", data=dict(package=dict(
                        id="mod:package", Count=1, tag={"W152": "original-package", "identity": 9})))]
        names = ["minecraft:iron_block", "minecraft:stone", "minecraft:chest", "minecraft:chest",
                 "mod:tank", "mod:bay"]
        blocks = [dict(position=[i // 2, 0, i % 2], palette=i,
                       **({"block_entity": payload} if payload else {})) for i, payload in enumerate(payloads)]
        snapshot = dict(source_origin=[15, 100, 15], source_dimension="minecraft:overworld",
                        snapshot_id=terrain.uuid_of([0, 0, 0, 7]), content_hash="fixture-hash",
                        block_palette=[dict(id=name, properties={}) for name in names], relative_blocks=blocks)
        source_snapshot = copy.deepcopy(snapshot); source_snapshot["source_origin"] = [40, 100, 8]
        destination = dict(id="mod:rocket", UUID=[0, 0, 0, 2], Pos=[15, 100, 15], Passengers=[],
                           RocketEntityData=dict(owner_id=[0, 0, 0, 3], snapshot=snapshot,
                                                 flight_data=dict(fuel=dict(amount=1000, debit_history=[500]))))
        source = copy.deepcopy(destination); source["UUID"] = [0, 0, 0, 1]
        source["RocketEntityData"]["snapshot"] = source_snapshot
        old = dict(source_snapshot=source_snapshot, destination_snapshot=snapshot)
        self.fixture = dict(source=source, destination=destination, transfer_root=dict(transfers=[old]))
        transfer = dict(schema_version=3, format_epoch="v1.8.0-first-moon-landing", transfers=[],
                        classic_first_moon_landing=dict(schema_version=1, state="EMPTY"))
        (self.world / "data").mkdir()
        nbt.write_gzip(self.world / terrain.TRANSFER, b"", tag(dict(data=transfer)))
        self.snapshot = snapshot
        self.regions = {}
        self.entities = []
        self.transaction = None
        self.populate()

    def populate(self, *, sealed=False, members=6):
        self.regions = {}
        for index, block in enumerate(self.snapshot["relative_blocks"][:members]):
            at = [a + b for a, b in zip(self.snapshot["source_origin"], block["position"])]
            name = self.snapshot["block_palette"][index]["id"]
            be = None
            if sealed:
                name = "advancedrocketrycommunity:sealed_rocket_block"
                member = dict(schema=1, snapshot_id=[0, 0, 0, 7], rocket_entity_id=[0, 0, 0, 2],
                              content_hash="fixture-hash", dimension="minecraft:overworld", position=at,
                              state=self.snapshot["block_palette"][index], member_index=index,
                              transaction_id=[0, 0, 0, 8])
                if "block_entity" in block: member["block_entity"] = block["block_entity"]
                be = dict(arce_sealed_rocket_member=member)
            elif "block_entity" in block:
                payload = block["block_entity"]; data = payload["data"]
                if payload["adapter"].endswith("vanilla_container_v1"): be = copy.deepcopy(data)
                elif payload["adapter"].endswith("pressurized_tank_v1"): be = dict(arce_pressurized_tank=data["tank"])
                else: be = dict(arce_satellite_bay=data)
            self.put_block(at, name, be)

    def put_block(self, at, name, be=None, *, chunk=None):
        x, y, z = at
        key = chunk if chunk is not None else (x >> 4, z >> 4)
        root = self.regions.setdefault(key, dict(states={}, block_entities=[]))
        root["states"][tuple(at)] = name
        if be is not None: root["block_entities"].append(dict(id=name, x=x, y=y, z=z, **be))

    def retain(self, phase):
        self.transaction = dict(type="SEALED_DISASSEMBLY", phase=phase, snapshot=self.snapshot,
                                rocket_entity_id=[0, 0, 0, 2], owner_id=[0, 0, 0, 3],
                                snapshot_id=[0, 0, 0, 7], content_hash="fixture-hash", transaction_id=[0, 0, 0, 8])

    def write_region(self, folder, x, z, root):
        directory = self.world / folder; directory.mkdir(exist_ok=True)
        path = directory / f"r.{x >> 5}.{z >> 5}.mca"
        data = bytearray(path.read_bytes() if path.exists() else bytes(8192))
        encoded = zlib.compress(nbt.write(b"", root))
        record = struct.pack(">IB", len(encoded) + 1, 2) + encoded
        sectors = (len(record) + 4095) // 4096
        struct.pack_into(">I", data, ((x & 31) + (z & 31) * 32) * 4, (len(data) // 4096 << 8) | sectors)
        data.extend(record + bytes(sectors * 4096 - len(record))); path.write_bytes(data)

    def inspect(self, stage="recover1", cut="NONE"):
        for (x, z), image in self.regions.items():
            names = ["minecraft:air"] + sorted(set(image["states"].values()) - {"minecraft:air"})
            # The native reader supports one palette entry; mutations need at least two.
            if len(names) == 1: names.append("minecraft:stone")
            root = tag(dict(block_entities=image["block_entities"], sections=[dict(Y=6, block_states=dict(
                palette=[dict(Name=name) for name in names]))]))
            states = nbt.compound(root, "sections").value[1][0].value["block_states"]
            states.value["data"] = nbt.Tag(nbt.LONGS, [0] * 256)
            for at, name in image["states"].items(): nbt.set_block(root, *at, name)
            self.write_region("region", x, z, root)
        self.write_region("entities", 0, 0, tag(dict(Entities=self.entities)))
        nbt.write_gzip(self.world / terrain.TRANSACTION, b"", tag(dict(data=dict(
            transactions=[self.transaction] if self.transaction else []))))
        return terrain.inspect(self.world, self.fixture, dict(oracle="U", input=dict(cut=cut)), stage)

    def test_extra_container_is_rejected_on_both_stable_restarts(self):
        for stage in ("recover1", "recover2"):
            with self.subTest(stage=stage):
                self.populate(); self.assertEqual("BLOCKS", self.inspect(stage)["authority"])
                self.put_block([18, 100, 15], "minecraft:chest", dict(Items=[self.item]))
                with self.assertRaisesRegex(ValueError, "Unaccounted census"):
                    self.inspect(stage)

    def test_extra_resources_and_sealed_identities_are_rejected_through_nested_inventories(self):
        cases = [dict(Items=[self.item]),
                 dict(arce_pressurized_tank=dict(FluidName="minecraft:water", Amount=1000)),
                 dict(arce_satellite_bay=dict(package=dict(id="mod:package", Count=1))),
                 dict(arce_sealed_rocket_member=dict(snapshot_id=[0, 0, 0, 7], member_index=0)),
                 dict(Items=[dict(id="minecraft:shulker_box", Count=1, tag=dict(BlockEntityTag=dict(Items=[self.item])))])]
        for payload in cases:
            for entity in (False, True):
                with self.subTest(payload=payload, entity=entity):
                    self.populate(); self.entities = []; self.inspect()
                    if entity:
                        self.entities = [dict(id="minecraft:boat", Passengers=[dict(id="minecraft:chest_minecart", **payload)])]
                    else:
                        self.put_block([18, 100, 15], "minecraft:chest", dict(nested=payload))
                    with self.assertRaisesRegex(ValueError, "Unaccounted census"):
                        self.inspect()

    def test_extra_payload_on_an_expected_host_is_not_exempted(self):
        self.inspect()
        tank = next(be for image in self.regions.values() for be in image["block_entities"] if be["id"] == "mod:tank")
        tank["Items"] = [dict(id="minecraft:diamond", Count=7)]
        with self.assertRaisesRegex(ValueError, "Unaccounted census"):
            self.inspect()

    def test_empty_unrelated_hosts_are_allowed_and_census_stays_bounded(self):
        before = self.inspect()
        self.put_block([-1, 100, -1], "minecraft:chest", dict(Items=[]))
        self.entities = [dict(id="minecraft:chest_minecart", Items=[])]
        # Written to the same region file, but outside the original sixteen chunk records.
        self.put_block([48, 100, 15], "minecraft:chest", dict(Items=[self.item]))
        self.assertEqual(terrain.comparable(before), terrain.comparable(self.inspect()))

    def test_valid_sealed_identity_does_not_exempt_unaccounted_member_fields(self):
        self.populate(sealed=True)
        self.assertEqual("BLOCKS", self.inspect("recovery-cut")["authority"])
        member = self.regions[0, 0]["block_entities"][0]["arce_sealed_rocket_member"]
        member["Items"] = [dict(id="minecraft:diamond", Count=7)]
        with self.assertRaisesRegex(ValueError, "Unaccounted census"):
            self.inspect("recovery-cut")

    def test_pre_restored_missing_current_d1_cannot_elect_snapshot_or_dispose_fuel(self):
        self.populate(members=0); self.retain("LOCKED")
        for stage in ("start", "recovery-cut"):
            with self.subTest(stage=stage):
                self.entities = [copy.deepcopy(self.fixture["destination"])]
                before = self.inspect(stage, "C1_MOVE_AFTER")
                self.assertEqual("ENTITY", before["authority"])
                self.assertEqual(dict(amount=1000, debit_history=[500]), before["fuel"])
                self.entities = []
                with self.assertRaisesRegex(ValueError, "Missing durable D1"):
                    self.inspect(stage, "C1_MOVE_AFTER")

    def test_current_d1_requires_exact_fuel_and_debit_history_with_partial_shadows(self):
        self.populate(sealed=True, members=1); self.retain("LOCKED")
        for field, replacement in (("amount", 0), ("debit_history", [])):
            with self.subTest(field=field):
                self.entities = [copy.deepcopy(self.fixture["destination"])]
                self.assertEqual("ENTITY", self.inspect("recovery-cut")["authority"])
                self.entities[0]["RocketEntityData"]["flight_data"]["fuel"][field] = replacement
                with self.assertRaisesRegex(ValueError, "Changed full D1"):
                    self.inspect("recovery-cut")


class W198TerrainTest(unittest.TestCase):
    def test_inventory_is_not_folded_and_every_row_has_four_starts(self):
        plan = terrain.plan()
        self.assertEqual(89, len(plan))
        self.assertEqual(native_case_plan(), [row["input"] for row in plan])
        self.assertEqual(356, sum(row["starts"] for row in plan))
        self.assertEqual([60, 60, 60, 60, 60, 56],
                         [sum(row["starts"] for row in plan if row["tranche"] == n) for n in range(1, 7)])
        self.assertEqual(89, len({row["id"] for row in plan}))

    def test_tranche_counts_attempts_before_launch_and_never_retries_twice_failed_row(self):
        terrain.check_launch([], "001", 1, "start")
        with self.assertRaisesRegex(ValueError, "60"):
            terrain.check_launch([{}] * 60, "001", 1, "start")
        with self.assertRaisesRegex(ValueError, "two"):
            terrain.check_attempt([dict(row="001", status="FAIL")] * 2, "001")

    def test_exact_predecessor_not_any_transfer_with_the_same_id(self):
        old = {"transfer_id": [1, 2, 3, 4], "snapshot": {"full": "image"}}
        root = dict(schema_version=3, format_epoch="v1.8.0-first-moon-landing",
                    transfers=[old], classic_first_moon_landing=dict(schema_version=1, state="EMPTY"))
        terrain.check_transfer(root, old, True)
        changed = copy.deepcopy(root); changed["transfers"][0]["snapshot"]["full"] = "other"
        with self.assertRaisesRegex(ValueError, "exact T"):
            terrain.check_transfer(changed, old, True)
        with self.assertRaisesRegex(ValueError, "exact T"):
            terrain.check_transfer(root, old, False)

    def test_nested_source_and_foreign_player_are_not_invisible(self):
        nested = {"id": "minecraft:pig", "Passengers": [{"id": "rocket", "RocketEntityData": {"owner": 1}}]}
        with self.assertRaisesRegex(ValueError, "Nested rocket"):
            terrain.rocket_census([nested])
        with self.assertRaisesRegex(ValueError, "player"):
            terrain.rocket_census([dict(id="minecraft:player")])

    def test_entity_oracle_compares_complete_data_including_fuel_and_manifest(self):
        entity = dict(id="rocket", UUID=[1, 2, 3, 4], Pos=[15.5, 100., 15.5],
                      RocketEntityData=dict(snapshot={"content": ["all"]},
                                           flight_data=dict(fuel={"debits": ["paid"], "amount": 1000}, passengers={})))
        terrain.check_entity(entity, entity)
        for field in ("snapshot", "flight_data"):
            changed = copy.deepcopy(entity); changed["RocketEntityData"][field] = {}
            with self.assertRaisesRegex(ValueError, "D1"):
                terrain.check_entity(changed, entity)

    def test_shared_fixture_integrated_mode_uses_installed_disassembly_not_direct_b2(self):
        source = Path(__file__).resolve().parents[1] / "src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server"
        helper = (source / "W152NativeI6.java").read_text()
        self.assertIn("manager.disassembleForReleaseTest", helper)
        self.assertNotIn("beginSealedForReleaseTest", helper)
        self.assertIn("w198Integrated", (source / "W152NativeProbe.java").read_text())

    def test_commands_keep_integrated_path_and_two_unarmed_stable_restarts(self):
        from run_w198_terrain import command_for
        row = terrain.plan()[7]; args = SimpleNamespace(java="java", source_commit="commit")
        for stage, cut in (("start", row["input"]["cut"]), ("recovery-cut", row["input"]["recovery"]),
                           ("recover1", "NONE"), ("recover2", "NONE")):
            command, actual = command_for(args, row, stage)
            self.assertEqual(cut, actual)
            self.assertIn("-Dadvancedrocketrycommunity.w198Integrated=true", command)
            self.assertIn("-Dadvancedrocketrycommunity.w152Cut=" + cut, command)

    def test_phase_is_file_election_not_callback_acknowledgement(self):
        for cut in ("C1_RETAIN_AFTER", "C1_MOVE_BEFORE", "PREPARE_PROOF_CURRENT"):
            self.assertIsNone(terrain.phase_at_cut(cut))
        for cut in ("C1_MOVE_AFTER", "C1_UNKNOWN", "C2_STAGE_AFTER", "C3_MOVE_BEFORE"):
            self.assertEqual("LOCKED", terrain.phase_at_cut(cut))
        for cut in ("C3_MOVE_AFTER", "C3_UNKNOWN", "C5_MOVE_BEFORE"):
            self.assertEqual("RESTORED", terrain.phase_at_cut(cut))
        for cut in ("C5_MOVE_AFTER", "C5_UNKNOWN", "UNSEAL_PREPARE_FAILED"):
            self.assertIsNone(terrain.phase_at_cut(cut))

    def test_native_host_projection_keeps_all_payload_fields(self):
        cases = [("vanilla_container_v1", {"Items": [{"Count": 7}], "CustomName": "name"},
                  {"Items": [{"Count": 7}], "CustomName": "name"}),
                 ("pressurized_tank_v1", {"arce_pressurized_tank": {"schema": 1, "fluid": {"Amount": 1000}}},
                  {"tank": {"schema": 1, "fluid": {"Amount": 1000}}}),
                 ("satellite_bay_v1", {"arce_satellite_bay": {"owner": "owner", "package": {"identity": 9}}},
                  {"owner": "owner", "package": {"identity": 9}})]
        for adapter, native, payload in cases:
            self.assertEqual(payload, terrain.native_payload(native, {"adapter": "mod:" + adapter}))
        with self.assertRaisesRegex(ValueError, "Unmapped"):
            terrain.native_payload({}, {"adapter": "mod:unknown"})

    def test_unknown_injection_requires_one_exact_error_and_matching_writer_observation(self):
        from run_w198_terrain import check_errors
        line = "[22:22:11] [Server thread/ERROR] [io.gi.su.ad.ro.pe.RocketTransactionSavedData/]: " \
               "ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=UNKNOWN sequence=2 acknowledged=none"
        for cut in ("C1_UNKNOWN", "C3_UNKNOWN", "C5_UNKNOWN"):
            events = [{"label": cut.replace("_UNKNOWN", "_WRITE_UNKNOWN")}]
            check_errors([line], cut, events)
            for lines, observed in (([], events), ([line, line], events), ([line + " extra"], events),
                                    ([line.replace("UNKNOWN", "NOT_COMMITTED")], events), ([line], [])):
                with self.assertRaises(ValueError): check_errors(lines, cut, observed)
        for cut in ("NONE", "C1_MOVE_AFTER", "RECOVERY_LOADED"):
            with self.assertRaises(ValueError): check_errors([line], cut, [])
        with self.assertRaises(ValueError): check_errors([line.replace("/ERROR]", "/FATAL]")], "C1_UNKNOWN", [])


if __name__ == "__main__":
    unittest.main()
