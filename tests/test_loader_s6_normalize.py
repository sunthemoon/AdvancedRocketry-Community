"""Native-form neutral-choice tests; synthetic decisions are not native evidence."""
import copy
import unittest

from test_loader_s6_chain import decision, chain, SmokeError
import loader_s6_normalize as normalize


def neutral(bi=False):
    image = decision(bi); disk = image["native"]; root = disk["ledger"]
    data = disk["rocket"]["RocketEntityData"]
    data["flight_data"]["fuel"].update(amount=0, propulsion=chain.vector(bi, 0))
    data["resource_escrow"] = chain.wrapper(None, [], 0)
    for op in root["records"]:
        chosen = op["settlement"]["observations"][2]
        chosen.update(bound=0, quantity=0, normalized_wrapper=chain.wrapper(root["ledger_id"], [], 0))
        chosen["state"].update(epoch=0, signature=chain.fuel_signature(bi, 0), markers=[])
    return image


def completed(image):
    image = copy.deepcopy(image); disk = image["native"]; root = disk["ledger"]
    generation = root["writer_generation"] + 2
    root["tombstones"] = [dict(t, released=1, release_generation=generation) for t in chain.receipts(root)]
    root["rocket_authorities"] = [dict(chain.head(root, generation), e=0)]
    root["records"] = []; root["writer_generation"] = generation
    for loader in disk["loaders"]: loader["arce_fluid_fuel_loader"]["resource_escrow"]["markers"] = []
    disk["rocket"]["RocketEntityData"]["resource_escrow"] = chain.wrapper(root["ledger_id"], [], 0)
    return image


class NormalizeOracleTest(unittest.TestCase):
    def test_chosen_empty_vector_normalizes_only_its_wrapper_on_both_restarts(self):
        for bi in (False, True):
            for side in ("BEFORE", "AFTER"):
                cut = "LF_S6_CHAIN_NORMALIZE_" + side + ("_BI" if bi else "")
                image = neutral(bi); first = normalize.check(image, 0, cut=cut); final = completed(image)
                second = normalize.check(final, 1, first["identity"], cut)
                third = normalize.check(final, 2, second["identity"], cut)
                self.assertEqual(third["raw_total"], 8000)
                self.assertEqual(sum(third["per_fluid"].values()), 8000)
                self.assertIsNone(third["available_total"])
                self.assertIsNone(third["loss"])

    def test_unknown_and_changed_native_resources_are_rejected(self):
        with self.assertRaises(SmokeError): normalize.check(dict(read_status="UNKNOWN", native=None), 0)
        for mutate in (
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=4001),
            lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"].update(amount=1),
            lambda d: d["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"].update(revision=2),
            lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"].update(ledger_id=d["ledger"]["ledger_id"]),
            lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"].update(resource_epoch=2),
            lambda d: d["ledger"]["records"][0]["native_binding"].update(destination_before=[0] * 32),
        ):
            changed = neutral(); mutate(changed["native"])
            with self.assertRaises(SmokeError): normalize.check(changed, 0)

    def test_prescribed_choice_receipts_and_epoch_zero_head_are_immutable(self):
        image = neutral(True); cut = "LF_S6_CHAIN_NORMALIZE_AFTER_BI"
        for mutate in (
            lambda i: i["observations"][2].update(bound=1),
            lambda i: i["observations"][2].update(quantity=2000),
            lambda i: i["observations"][2]["normalized_wrapper"].update(resource_epoch=1),
            lambda i: i["members"].pop(),
        ):
            changed = copy.deepcopy(image)
            for op in changed["native"]["ledger"]["records"]: mutate(op["settlement"])
            with self.assertRaises(SmokeError): normalize.check(changed, 0, cut=cut)
        first = normalize.check(image, 0, cut=cut); final = completed(image)
        for mutate in (
            lambda d: d["ledger"]["rocket_authorities"][0].update(e=2),
            lambda d: d["ledger"]["tombstones"][0].update(accounting_status="KNOWN"),
            lambda d: d["rocket"]["RocketEntityData"]["resource_escrow"].pop("ledger_id"),
            lambda d: d["rocket"]["RocketEntityData"]["snapshot"]["block_palette"][0].update(id="minecraft:gold_block"),
        ):
            changed = copy.deepcopy(final); mutate(changed["native"])
            with self.assertRaises(SmokeError): normalize.check(changed, 1, first["identity"], cut)
