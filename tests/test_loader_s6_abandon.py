"""Native-form capture mutations; synthetic decisions are not native evidence."""
import copy
import unittest

from test_loader_s6_chain import decision, chain, SmokeError
import loader_s6_abandon as abandon


def abandoned(bi=False, before=False):
    image = decision(bi); disk = image["native"]; root = disk["ledger"]
    intent = root["records"][0]["settlement"]
    intent.update(choice="ABANDON", confirmed_tick=intent["preview_tick"] + 200)
    absent = intent["observations"][1]; absent.update(missing=1, bound=0, quantity=0); del absent["state"]
    for op in root["records"]:
        op.update(phase="SETTLING_ABANDON", settlement=copy.deepcopy(intent))
        if before: op.update(phase="REPAIR"); op.pop("settlement")
    disk["loaders"].pop(); disk["absent_slot"] = []
    return image


def completed(image):
    image = copy.deepcopy(image); disk = image["native"]; root = disk["ledger"]
    generation = root["writer_generation"] + 2
    root["tombstones"] = [dict(t, released=1, release_generation=generation) for t in abandon.receipts(root)]
    root["rocket_authorities"] = [chain.head(root, generation)]
    root["records"] = []; root["writer_generation"] = generation
    disk["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"]["markers"] = []
    disk["rocket"]["RocketEntityData"]["resource_escrow"]["markers"] = []
    return image


class AbandonOracleTest(unittest.TestCase):
    def test_unknown_and_missing_source_are_not_zero_inventory_or_loss(self):
        with self.assertRaises(SmokeError): abandon.check(dict(read_status="UNKNOWN", native=None), 0)
        result = abandon.check(abandoned(before=True), 0)
        self.assertEqual(result["raw_total"], 6000)
        for key in ("missing_amount", "available_total", "loss"): self.assertIsNone(result[key])

    def test_predecision_holds_twice_and_checks_every_native_resource(self):
        image = abandoned(before=True); initial = abandon.check(image, 0)
        first = abandon.check(image, 1, initial["identity"])
        abandon.check(image, 2, first["identity"])
        for mutate in (
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=3999),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["binding"].update(x=65),
            lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"].update(amount=2001),
            lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"]["markers"].pop(),
            lambda d: d["ledger"]["records"][1]["native_binding"].update(source_after=[0] * 32),
            lambda d: d["ledger"]["records"][1]["source"].update(after_epoch=2),
            lambda d: d["rocket"]["RocketEntityData"]["snapshot"]["block_palette"][0].update(id="minecraft:gold_block"),
            lambda d: d["absent_slot"].append(dict(id="minecraft:chest", x=67, y=100, z=64)),
            lambda d: d["loaders"].append(copy.deepcopy(d["loaders"][0])),
        ):
            changed = copy.deepcopy(image); mutate(changed["native"])
            with self.assertRaises(SmokeError): abandon.check(changed, 1, initial["identity"])

    def test_abandon_releases_only_chosen_survivors_twice(self):
        for bi in (False, True):
            for side in ("REPLACED", "ACK"):
                cut = "LF_S6_CHAIN_ABANDON_" + side + ("_BI" if bi else "")
                initial = abandoned(bi); first = abandon.check(initial, 0, cut=cut); final = completed(initial)
                second = abandon.check(final, 1, first["identity"], cut)
                third = abandon.check(final, 2, second["identity"], cut)
                self.assertEqual(third["raw_total"], 6000)
                self.assertIsNone(third["missing_amount"])
                self.assertEqual(sum(third["per_fluid"].values()), 6000)

    def test_absence_interval_frozen_members_and_missing_receipts_are_exact(self):
        cut = "LF_S6_CHAIN_ABANDON_ACK_BI"; image = abandoned(True)
        for mutate in (
            lambda i: i.update(confirmed_tick=i["preview_tick"] + 199),
            lambda i: i.update(choice="ACCEPT_CURRENT"),
            lambda i: i["members"].pop(),
            lambda i: i["observations"][1].update(quantity=4000),
            lambda i: i["observations"][1].update(bound=1),
            lambda i: i["observations"][2]["state"].update(epoch=1),
        ):
            changed = copy.deepcopy(image)
            for op in changed["native"]["ledger"]["records"]: mutate(op["settlement"])
            with self.assertRaises(SmokeError): abandon.check(changed, 0, cut=cut)
        first = abandon.check(image, 0, cut=cut); final = completed(image)
        for mutate in (
            lambda r: r["tombstones"][1]["missing"].clear(),
            lambda r: r["tombstones"][0].update(loss_known=1),
            lambda r: r["rocket_authorities"][0].update(e=1),
            lambda r: r["tombstones"][1]["before_choice"][0].update(missing=0),
        ):
            changed = copy.deepcopy(final); mutate(changed["native"]["ledger"])
            with self.assertRaises(SmokeError): abandon.check(changed, 1, first["identity"], cut)
