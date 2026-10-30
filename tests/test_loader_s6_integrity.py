"""R197 mutations of retained native observations, never rewritten evidence."""
import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_s6_oracle as oracle
from run_dedicated_server_smoke import SmokeError

NATIVE = json.loads((Path(__file__).parent / "fixtures/loader_s6/integrity-native.json").read_text())


class LoaderS6IntegrityTest(unittest.TestCase):
    def reject(self, family, stage, mutate):
        check = oracle.check_e5 if family == "e5" else oracle.check_e3
        images = NATIVE[family]["images"]
        identity = check(images[0], 0)["identity"]
        check(images[stage], stage, identity)
        image = copy.deepcopy(images[stage])
        mutate(image["native"])
        with self.assertRaises(SmokeError):
            check(image, stage, identity if stage else None)

    def test_i1_snapshot_identity_and_every_immutable_snapshot_field(self):
        mutations = {
            "snapshot_id": "00000000-0000-0000-0000-000000000001",
            "relative_blocks": [], "block_palette": [], "passenger_anchors": [],
            "bounding_box": [0, 0, 0, 0, 2, 0], "source_origin": [64, 101, 64],
            "source_dimension": "minecraft:the_end", "content_hash": "0" * 64,
            "created_at_game_time": 999, "mass_inputs": {},
        }
        for key, value in mutations.items():
            with self.subTest(field=key):
                self.reject("e3", 1, lambda d: d["rocket"]["RocketEntityData"]["snapshot"].update({key: value}))
        self.reject("e5", 1, lambda d: d["pairs"][1]["rocket"]["RocketEntityData"]["snapshot"].update(
            snapshot_id="00000000-0000-0000-0000-000000000001"))

    def test_i1_typed_fuel_not_just_amounts(self):
        for key, value in (("fluid", "minecraft:water"), ("branch", "EMPTY")):
            with self.subTest(field=key):
                self.reject("e3", 1, lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"].update({key: value}))
        for key, value in (("amount", 9999), ("capacity", 9999)):
            with self.subTest(field=key):
                self.reject("e3", 1, lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"].update({key: value}))
        for key, value in (("revision", 99), ("schema", 2), ("descriptor", {})):
            with self.subTest(field=key):
                self.reject("e3", 1, lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"].update({key: value}))

    def test_i1_coherent_but_wrong_fixture_descriptor_is_rejected(self):
        def mutate(d):
            data = d["rocket"]["RocketEntityData"]
            data["snapshot"]["propulsion"]["hand"] = 0
            data["flight_data"]["fuel"]["propulsion"]["descriptor"]["hand"] = 0
        self.reject("e3", 1, mutate)

    def test_i2_record_epochs_owner_and_binding(self):
        for family in ("e3", "e5"):
            for endpoint in ("source", "destination"):
                for key, value in (("after_epoch", 99), ("before_epoch", 99), ("level", "minecraft:the_end"),
                                   ("before_signature", "0" * 64), ("after_signature", "1" * 64)):
                    with self.subTest(family=family, endpoint=endpoint, field=key):
                        self.reject(family, 0, lambda d: d["ledger"]["records"][0][endpoint].update({key: value}))
            with self.subTest(family=family, field="owner"):
                self.reject(family, 0, lambda d: d["ledger"]["records"][0].update(owner=[0, 0, 0, 1]))
            for key in ("snapshot", "owner", "physical", "logical"):
                with self.subTest(family=family, binding=key):
                    self.reject(family, 0, lambda d: d["ledger"]["records"][0]["native_binding"].update({key: [0, 0, 0, 1]}))
            for key in ("snapshot_hash", "before_frontier", "after_frontier", "src_ref", "dst_ref", "invariant",
                        "before_wrapper", "after_wrapper", "source_before", "source_after", "destination_before", "destination_after"):
                with self.subTest(family=family, digest=key):
                    self.reject(family, 0, lambda d: d["ledger"]["records"][0]["native_binding"].update({key: [0] * 32}))

    def test_i3_l2_generation_regression_and_advance(self):
        images = NATIVE["l2"]["images"]
        op = images[0]["native"]["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]["markers"][0]["op_id"]
        identity = oracle.check_g3_l2(images[0], 0, operation=op)["identity"]
        for stage in (0, 1, 2):
            oracle.check_g3_l2(images[stage], stage, identity, op)
            for generation in (1, 2, 4):
                image = copy.deepcopy(images[stage]); image["native"]["ledger"]["writer_generation"] = generation
                with self.subTest(stage=stage, generation=generation), self.assertRaises(SmokeError):
                    oracle.check_g3_l2(image, stage, identity if stage else None, op)

    def test_i5_reviewers_dropped_prefix_witness(self):
        lines = NATIVE["recovery_log"]["lines"]
        account = [l for l in lines if "ARCE_LOADER_S6_ACCOUNT " in l]
        mutated = [account[-1]] + [l.replace("observations=101 ", "observations=1 ")
                                  for l in lines if "ARCE_LOADER_S6_RESULT " in l]
        with self.assertRaises(SmokeError):
            oracle.check_recovery_observations(mutated)

    def test_i5_framed_interval_rejects_prefix_suffix_holes_and_result_order(self):
        # New protocol framing applied only to a private test copy, not a replay.
        rows = [l for l in NATIVE["recovery_log"]["lines"] if "ARCE_LOADER_S6_ACCOUNT " in l]
        result = next(l for l in NATIVE["recovery_log"]["lines"] if "ARCE_LOADER_S6_RESULT " in l)
        lines = ["ARCE_LOADER_S6_BEGIN tick=1"] + rows + [result + " start_tick=1 end_tick=101 total_observations=101"]
        oracle.check_recovery_observations(lines)
        for changed in (lines[1:], lines[:1] + lines[2:], lines[:-2] + lines[-1:],
                        lines[:50] + lines[51:], [lines[-1]] + lines[:-1], lines + [rows[-1]],
                        [l.replace("total_observations=101", "total_observations=100") for l in lines]):
            with self.subTest(change=changed[:1]), self.assertRaises(SmokeError):
                oracle.check_recovery_observations(changed)


if __name__ == "__main__":
    unittest.main()
