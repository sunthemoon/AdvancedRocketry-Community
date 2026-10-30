"""Native-shaped mutations for the separate C oracle."""
import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_s6_flight as flight
import loader_s6_oracle as s6
from run_dedicated_server_smoke import SmokeError


def seed():
    fixture = json.loads((Path(__file__).parent / "fixtures/loader_s6/integrity-native.json").read_text())
    disk = s6.normalize_ids(copy.deepcopy(fixture["e3"]["images"][1]["native"]))
    disk["source"] = disk.pop("rocket")
    disk["destination"] = None
    disk["journal"] = dict(schema_version=3, transfers=[], classic_first_moon_landing=dict(schema_version=1, state="EMPTY"),
                           format_epoch="v1.8.0-first-moon-landing")
    disk["source"]["RocketEntityData"]["flight_data"].pop("plan", None)
    return dict(read_status="KNOWN", native=disk)


class FlightOracleTests(unittest.TestCase):
    def test_complete_native_empty_journal_is_required(self):
        native = json.loads((Path(__file__).parent / "fixtures/loader_s6/flight-setup-native.json").read_text())
        image = native["image"]
        identity = flight.check(image, 0)["identity"]
        for restart in (1, 2):
            flight.check(image, restart, identity)
        for mutate in (
                lambda journal: journal.pop("format_epoch"),
                lambda journal: journal.update(format_epoch="other"),
                lambda journal: journal.update(extra=1),
                lambda journal: journal["classic_first_moon_landing"].update(state="CLAIMED"),
                lambda journal: journal["transfers"].append({})):
            changed = copy.deepcopy(image)
            mutate(changed["native"]["journal"])
            with self.assertRaises(SmokeError): flight.check(changed, 0)

    def test_prelaunch_digest_and_retired_markers_are_bound_to_the_observed_fill(self):
        image = seed(); disk = image["native"]; data = disk["source"]["RocketEntityData"]
        witness = dict(operation="00000000-0000-0000-0000-000000000001", source=disk["source"]["UUID"],
            logical=data["assembly_transaction_id"], owner=data["owner_id"], ledger=disk["ledger"]["ledger_id"],
            source_digest=flight.source_digest(disk["source"]))
        for wrapper, role in ((data["resource_escrow"], "DESTINATION"),
                (disk["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"], "SOURCE")):
            wrapper["markers"] = [dict(op_id=witness["operation"], seq=1, role=role, epoch_after=1)]
        flight.check(image, 0, witness=witness)
        changed = copy.deepcopy(image)
        changed["native"]["source"]["RocketEntityData"]["snapshot"]["created_at_game_time"] += 1
        with self.assertRaises(SmokeError): flight.check(changed, 0, witness=witness)
        changed = copy.deepcopy(image)
        changed["native"]["source"]["RocketEntityData"]["resource_escrow"]["markers"][0]["seq"] = 2
        with self.assertRaises(SmokeError): flight.check(changed, 0, witness=witness)
        with self.assertRaises(SmokeError): flight.check(image, 1, witness=witness)

    def test_log_selection_needs_exact_hook_one_baseline_and_no_errors(self):
        cut = flight.F1[0]
        baseline = "ARCE_LOADER_S6_FLIGHT_FILLED cut=" + cut + " operation=o source=s logical=l owner=u ledger=d source_digest=" + "a" * 64 + " tank=4000 bank=1000 epoch=1"
        pause = "ARCE_LOADER_S6_FLIGHT_PAUSED cut=" + cut + " hook=BARRIER_STORE_BEFORE"
        flight.check_logs([baseline, pause], 0, cut)
        for lines in ([pause], [baseline, baseline, pause], [baseline, pause.replace("BEFORE", "AFTER")],
                      [baseline, pause, "[Server thread/ERROR] fault"]):
            with self.assertRaises(SmokeError): flight.check_logs(lines, 0, cut)

    def test_pre_arm_prefix_and_both_restarts(self):
        image = seed()
        identity = flight.check(image, 0)["identity"]
        for restart in (1, 2):
            self.assertEqual(identity, flight.check(image, restart, identity)["identity"])

    def test_unknown_and_each_changed_resource_or_authority_are_rejected(self):
        image = seed()
        changes = [
            lambda d: d.update(read_status="UNKNOWN"),
            lambda d: d["native"]["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=3999),
            lambda d: d["native"]["source"]["RocketEntityData"]["flight_data"]["fuel"].update(amount=999),
            lambda d: d["native"]["source"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"].update(revision=2),
            lambda d: d["native"]["source"]["RocketEntityData"]["resource_escrow"].update(resource_epoch=2),
            lambda d: d["native"]["ledger"].update(issued_launch_serial=1),
            lambda d: d["native"]["ledger"].update(writer_generation=4),
            lambda d: d["native"].update(destination=copy.deepcopy(d["native"]["source"])),
        ]
        for mutate in changes:
            with self.subTest(mutate=changes.index(mutate)):
                altered = copy.deepcopy(image); mutate(altered)
                with self.assertRaises(SmokeError): flight.check(altered, 0)

    def test_full_restart_identity_includes_snapshot_binding_and_typed_fuel(self):
        image = seed(); identity = flight.check(image, 0)["identity"]
        for key in ("snapshot", "owner_id", "assembly_transaction_id"):
            altered = copy.deepcopy(image)
            data = altered["native"]["source"]["RocketEntityData"]
            if key == "snapshot": data[key]["created_at_game_time"] += 1
            else: data[key] = "00000000-0000-0000-0000-000000000099"
            with self.subTest(key=key), self.assertRaises(SmokeError): flight.check(altered, 1, identity)
        altered = copy.deepcopy(image)
        altered["native"]["loaders"][0]["arce_fluid_fuel_loader"]["binding"]["x"] += 1
        with self.assertRaises(SmokeError): flight.check(altered, 1, identity)


if __name__ == "__main__": unittest.main()
