import copy
import hashlib
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import cargo_recovery_oracle as recovery
import cargo_contention_oracle as contention
import test_cargo_storage_runner as storage_test


class CargoRecoveryOracleTest(unittest.TestCase):
    def raw(self):
        raw = storage_test.CargoStorageOracleTest().raw()
        raw["ledger"].update(ledger_id="ledger", rocket_authorities=[], armed_flights=[], issued_launch_serial=0)
        device = raw["terrain"]["block_entities"][0]["arce_rocket_fluid_cargo"]
        rocket = raw["entities"]["Entities"][0]["RocketEntityData"]
        device.update(role="LOAD", schema_version=1)
        for section in (device["resource_escrow"], rocket["resource_escrow"]):
            section.update(resource_epoch=0, ledger_id="ledger")
        device["tank"]["Amount"] = 5000
        block = rocket["snapshot"]["relative_blocks"][0]; block["position"] = [0, 0, 0]
        block["block_entity"]["data"]["tank"]["fluid"] = {}
        return raw, rocket, device

    def inspect(self, raw, cut, restart=False):
        with patch.object(recovery.storage, "read", return_value=raw):
            return recovery.inspect(Path("world"), "FC_LOAD_" + cut, restart)

    def test_future_and_invalid_roots_are_preserved_not_normalized(self):
        for cut in ("G1_FUTURE", "G1_INVALID"):
            raw, rocket, device = self.raw(); raw["ledger"].update(records=[], issued_through=0)
            if cut == "G1_FUTURE":
                device.update(schema_version=2, extension="W138-preserve")
            else:
                device["tank"]["Amount"] = -1
            for restarted in (False, True):
                self.assertEqual(device, self.inspect(raw, cut, restarted)["preserved"]["device"])
            device["tank"]["Amount"] = 0
            with self.assertRaises(recovery.native.smoke.SmokeError):
                self.inspect(raw, cut, True)

    def test_missing_cursor_and_other_payload_require_repair_without_historical_fallback(self):
        for cut in ("R3_CURSOR", "R3_OTHER"):
            raw, rocket, device = self.raw()
            raw["ledger"]["records"] = [dict(op_id="op", seq=1, phase="REPAIR", reason="OTHER_STATE", payload=dict(cursor=dict(p=[0, 0, 0])))]
            if cut == "R3_CURSOR":
                rocket["snapshot"]["relative_blocks"][0]["position"] = [0, 1, 0]
            else:
                device["tank"]["FluidName"] = "minecraft:lava"
            self.assertEqual(5000, self.inspect(raw, cut, True)["raw_total"])
            raw["ledger"]["records"][0]["phase"] = "PREPARED"
            with self.assertRaises(recovery.native.smoke.SmokeError):
                self.inspect(raw, cut, True)

    def test_every_composite_floor_is_checked_independently(self):
        for count in (255, 256):
            floors = []
            for i in range(count):
                floors.append(dict(rocket_id=[0, 0x107C0, 0, i], entity_id=[0, 0x107C1, 0, i], schema_version=2,
                                   resource_epoch=1, highest_seq=i + 1, signature_kind="ROCKET_COMPOSITE_V1", journal_generation=1,
                                   **{k: hashlib.sha256(f"w107c-floor-{v}-{i}".encode()).hexdigest() for k, v in
                                      (("signature", "signature"), ("journal_digest", "journal"), ("invariant_digest", "invariant"))}))
            recovery.floors(dict(floors=floors), count)
            floors[-1]["signature_kind"] = "FUEL_VECTOR_V1"
            with self.assertRaises(recovery.native.smoke.SmokeError):
                recovery.floors(dict(floors=floors), count)

    def test_stalled_component_keeps_exact_markers_while_the_healthy_component_retires(self):
        raw = storage_test.CargoStorageOracleTest().raw()
        root = raw["ledger"]; root.update(ledger_id="ledger", issued_through=2, rocket_authorities=[], armed_flights=[], issued_launch_serial=0,
                                          records=[dict(op_id="op", seq=1, phase="STALLED", reason="BARRIER_IO")])
        device = raw["terrain"]["block_entities"][0]["arce_rocket_fluid_cargo"]
        rocket = raw["entities"]["Entities"][0]["RocketEntityData"]
        for section, role in ((device["resource_escrow"], "SOURCE"), (rocket["resource_escrow"], "DESTINATION")):
            section.update(ledger_id="ledger", markers=[dict(op_id="op", seq=1, role=role, epoch_after=1)])
        control = copy.deepcopy(raw)
        healthy_device = control["terrain"]["block_entities"][0]
        healthy_device.update(x=88, z=88)
        healthy_rocket = control["entities"]["Entities"][0]
        healthy_rocket.update(UUID="healthy", Pos=[92.5, 100.0, 88.5])
        healthy_device["arce_rocket_fluid_cargo"]["binding"]["physical_rocket"] = "healthy"
        healthy_device["arce_rocket_fluid_cargo"]["resource_escrow"]["markers"] = []
        healthy_rocket["RocketEntityData"]["resource_escrow"]["markers"] = []
        with patch.object(contention.storage, "read", return_value=raw), patch.object(contention, "healthy", return_value=control):
            self.assertEqual(10000, contention.inspect(Path("world"), False)["raw_total"])
            healthy_device["arce_rocket_fluid_cargo"]["tank"]["Amount"] = 5000
            with self.assertRaises(recovery.native.smoke.SmokeError):
                contention.inspect(Path("world"), False)


if __name__ == "__main__":
    unittest.main()
