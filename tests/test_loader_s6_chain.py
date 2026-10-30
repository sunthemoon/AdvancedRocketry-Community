"""Native-form two-loader mutations; model decisions are not native evidence."""
import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_s6_chain as chain
from loader_s6_oracle import normalize_ids
from run_dedicated_server_smoke import SmokeError
from run_v180_rocket_settlement_intent import ACTOR


def native():
    return normalize_ids(json.loads((Path(__file__).parent / "fixtures/loader_s6/chain-native.json").read_text())["images"][0])


def veto_log():
    return (Path(__file__).parent / "fixtures/loader_s6/veto-event.log").read_text().splitlines()


def veto_error():
    lines = veto_log(); start = next(n for n, line in enumerate(lines) if "/ERROR" in line)
    end = next(n for n in range(start + 1, len(lines)) if lines[n].startswith("["))
    return lines[start:end]


def decision(bi=False):
    image = native(); d = image["native"]; root = d["ledger"]; records = root["records"]
    if bi:
        data = d["rocket"]["RocketEntityData"]
        data["snapshot"]["propulsion"] = chain.descriptor(True)
        data["flight_data"]["fuel"].update(amount=1000, propulsion=chain.vector(True, 2))
        for index, op in enumerate(records):
            fluid = chain.legacy.HYDROGEN if index == 0 else chain.legacy.OXYGEN
            loader = d["loaders"][index]["arce_fluid_fuel_loader"]; loader["tank"]["FluidName"] = fluid
            op["payload"]["FluidName"] = fluid; op["payload_hash"] = chain.i.digest(chain.i.native_tag(op["payload"]))
            op["source"].update(before_signature=chain.tank_signature(loader["device"], fluid, 5000),
                                after_signature=chain.tank_signature(loader["device"], fluid, 4000))
            op["destination"].update(before_signature=chain.fuel_signature(True, index), after_signature=chain.fuel_signature(True, index + 1),
                                     unit="PRIMARY" if index == 0 else "OXIDIZER")
            op["native_binding"] = chain.expected_binding(d, op, records, True)
    intent = dict(schema_version=2, choice="ACCEPT_CURRENT", ledger_id=root["ledger_id"], actor=ACTOR, actor_name="Server",
        preview_tick=100, confirmed_tick=101, digest="a" * 64, members=[dict(op_id=op["op_id"], phase="REPAIR", reason="OTHER_STATE",
        digest="b" * 64) for op in records], observations=chain.expected_observations(d))
    for op in records: op.update(phase="SETTLING_ACCEPT", reason="OTHER_STATE", settlement=copy.deepcopy(intent))
    return image


def completed(image):
    image = copy.deepcopy(image); d = image["native"]; root = d["ledger"]; generation = root["writer_generation"] + 2
    root["tombstones"] = [dict(t, released=1, release_generation=generation) for t in chain.receipts(root)]
    root["rocket_authorities"] = [chain.head(root, generation)]; root["records"] = []; root["writer_generation"] = generation
    d["rocket"]["RocketEntityData"]["resource_escrow"]["markers"] = []
    for loader in d["loaders"]: loader["arce_fluid_fuel_loader"]["resource_escrow"]["markers"] = []
    return image


class ChainOracleTest(unittest.TestCase):
    def test_checkpointed_veto_retains_complete_frozen_authority_twice(self):
        for bi in (False, True):
            cut = "LF_S6_CHAIN_VETO" + ("_BI" if bi else "")
            image = decision(bi)
            initial = chain.check(image, 0, cut=cut)
            self.assertEqual(initial["state"], "HELD")
            first = chain.check(image, 1, initial["identity"], cut)
            chain.check(image, 2, first["identity"], cut)
            for mutate in (
                lambda d: d["ledger"].update(writer_generation=d["ledger"]["writer_generation"] + 1),
                lambda d: d["ledger"]["records"].pop(),
                lambda d: d["ledger"]["records"][0]["settlement"].update(confirmed_tick=102),
                lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]["markers"].clear(),
            ):
                changed = copy.deepcopy(image); mutate(changed["native"])
                with self.assertRaises(SmokeError): chain.check(changed, 1, initial["identity"], cut)
            with self.assertRaises(SmokeError): chain.check(completed(image), 1, initial["identity"], cut)

    def test_checkpointed_veto_requires_the_actual_diagnostic(self):
        cut = "LF_S6_CHAIN_VETO"
        marker = f"ARCE_LOADER_S6_CHAIN_PAUSED cut={cut} hook=O_VETO"
        diagnostic = f"ARCE_LOADER_S6_CHAIN_VETO cut={cut} reason=BARRIER_VETO records=2 checkpointed=true recovery=false"
        actual = "ARCE_ESCROW_SETTLEMENT_HELD reason=BARRIER_VETO records=retained"
        chain.check_logs(veto_error() + [marker, diagnostic, actual], 0, cut)
        for lines in ([marker], [marker, diagnostic], [marker, actual], [marker, diagnostic, diagnostic, actual],
                      [marker, diagnostic.replace("BARRIER_VETO", "Frozen settlement endpoint changed"), actual]):
            with self.assertRaises(SmokeError): chain.check_logs(lines, 0, cut)

    def test_retained_veto_own_store_error_is_exact_and_bounded(self):
        original = veto_log(); cut = "LF_S6_CHAIN_VETO"
        chain.check_logs(original, 1, cut)
        start = next(n for n, line in enumerate(original) if "/ERROR" in line)
        for changed in (
            original[:start] + original[start + 1:],
            original + [original[start]],
            [line.replace("/ERROR]", "/FATAL]") for line in original],
            [line.replace("Release-test S6 chain O-veto source chunk", "another failure") for line in original],
            [line for line in original if "GuardedChunkSaves.fail(" not in line],
            [line for line in original if "EscrowChunkStore.terrain(" not in line],
            [line for line in original if "EscrowSettlementRecovery.begin(" not in line],
            original[:start + 1] + ["Caused by: an unrelated exception"] + original[start + 1:],
            original[:start + 1] + ["stack padding"] * 97 + original[start + 1:],
        ):
            with self.assertRaises(SmokeError): chain.check_logs(changed, 1, cut)

    def test_frozen_mono_and_bipropellant_components_release_twice(self):
        for bi in (False, True):
            for side in ("REPLACED", "ACK"):
                cut = "LF_S6_CHAIN_" + side + ("_BI" if bi else "")
                initial = decision(bi); image = completed(initial)
                first = chain.check(initial, 0, cut=cut)
                second = chain.check(image, 1, first["identity"], cut)
                third = chain.check(image, 2, second["identity"], cut)
                self.assertEqual(third["raw_total"], 10000)
                self.assertIsNone(third["loss"])
                self.assertEqual(len(third["per_fluid"]), 2 if bi else 1)

    def test_frozen_and_released_authority_mutations_are_rejected(self):
        for bi in (False, True):
            cut = "LF_S6_CHAIN_REPLACED" + ("_BI" if bi else "")
            original = decision(bi)
            for mutate in (
                lambda r: r["records"][1]["settlement"].update(actor="foreign"),
                lambda r: r["records"][0]["settlement"]["members"].pop(),
                lambda r: r["records"][0]["settlement"]["observations"][2]["state"].update(epoch=1),
                lambda r: r["records"][0]["settlement"]["observations"][0].update(quantity=3999),
            ):
                image = copy.deepcopy(original); mutate(image["native"]["ledger"])
                with self.assertRaises(SmokeError): chain.check(image, 0, cut=cut)
            first = chain.check(original, 0, cut=cut); final = completed(original)
            for mutate in (
                lambda r: r["tombstones"][0]["survivors"][0].update(quantity=7),
                lambda r: r["tombstones"][0].update(loss_known=1),
                lambda r: r["tombstones"][1].update(anchor_op=r["tombstones"][0]["actor"]),
                lambda r: r["rocket_authorities"][0].update(e=1),
                lambda r: r["tombstones"].pop(),
            ):
                image = copy.deepcopy(final); mutate(image["native"]["ledger"])
                with self.assertRaises(SmokeError): chain.check(image, 1, first["identity"], cut)

    def test_log_boundaries_require_one_exact_marker_and_no_errors(self):
        for cut in chain.CASES:
            if "_VETO" in cut: continue  # The dedicated diagnostic test also checks the held reason.
            hook = "K1" if "_BEFORE" in cut else "K2" if "_REPLACED" in cut else "O1_ACK"
            line = f"ARCE_LOADER_S6_CHAIN_PAUSED cut={cut} hook={hook}\n"
            chain.check_logs([line], 0, cut)
            for lines in ([], [line, line], [line, "[Server/ERROR] test"]):
                with self.assertRaises(SmokeError): chain.check_logs(lines, 0, cut)

    def test_native_chain_control_and_all_resource_mutations(self):
        original = native()["native"]
        chain.check_resources(original, False, False)
        mutations = [
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=3999),
            lambda d: d["loaders"][1]["arce_fluid_fuel_loader"]["tank"].update(Amount=4001),
            lambda d: d["loaders"][1]["arce_fluid_fuel_loader"]["binding"].update(x=65),
            lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"].update(amount=1999),
            lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"].update(revision=1),
            lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"].update(resource_epoch=1),
            lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"]["markers"][0].update(seq=2),
            lambda d: d["ledger"]["records"][1]["destination"].update(before_epoch=0),
            lambda d: d["ledger"]["records"][1]["native_binding"].update(source_after=[0] * 32),
            lambda d: d["ledger"]["records"][0]["native_binding"].update(destination_before=[0] * 32),
            lambda d: d["ledger"]["records"][1]["native_binding"].update(before_wrapper=[0] * 32),
            lambda d: d["ledger"]["records"][1].update(owner=d["ledger"]["records"][1]["op_id"]),
            lambda d: d["rocket"]["RocketEntityData"]["snapshot"]["propulsion"].update(primary=3000),
        ]
        for index, mutate in enumerate(mutations):
            with self.subTest(index=index):
                image = copy.deepcopy(original); mutate(image)
                with self.assertRaises(SmokeError): chain.check_resources(image, False, False)

    def test_unknown_and_incomplete_component_are_not_zero_loss(self):
        for image in (dict(read_status="UNKNOWN", native=None), native()):
            with self.subTest(image=image["read_status"]):
                with self.assertRaises(SmokeError): chain.check(image, 0)

    def test_predecision_identity_covers_complete_snapshot_and_each_loader(self):
        image = native()
        for op in image["native"]["ledger"]["records"]:
            op.update(phase="REPAIR", reason="OTHER_STATE")
        initial = chain.check(image, 0)
        self.assertIsNone(initial["available_total"])
        self.assertIsNone(initial["loss"])
        for mutate in (
            lambda d: d["rocket"]["RocketEntityData"]["snapshot"]["block_palette"][0].update(id="minecraft:gold_block"),
            lambda d: d["loaders"][1]["arce_fluid_fuel_loader"].update(device=d["loaders"][0]["arce_fluid_fuel_loader"]["device"]),
        ):
            changed = copy.deepcopy(image); mutate(changed["native"])
            with self.assertRaises(SmokeError): chain.check(changed, 1, initial["identity"])


if __name__ == "__main__":
    unittest.main()
