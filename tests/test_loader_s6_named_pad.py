import copy
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_s6_named_pad as pad
from run_dedicated_server_smoke import SmokeError


def sample():
    return dict(pads=dict(schema_version=1, next_epoch=1, pads=[dict(station="station", pad="pad", physical="physical",
        owner="owner", epoch=0, claim="transfer")]), source=dict(snapshot=dict(blocks=[1, 2]), fuel=dict(amount=1000)),
        journal=dict(transfers=[dict(transfer_id="transfer")]), absence=0, releases=0, label="PREPARED", seq=0)


class NamedPadIntervalTest(unittest.TestCase):
    def test_refusal_preserves_complete_objects(self):
        before = sample(); after = copy.deepcopy(before); after.update(seq=1, label="REFUSED", refused=1)
        pad.check_interval([before, after], "LF_S6_PAD_REFUSED", 0)
        for change in (lambda v: v["source"]["snapshot"]["blocks"].pop(),
                       lambda v: v["source"]["fuel"].update(amount=999),
                       lambda v: v["pads"]["pads"][0].update(epoch=1),
                       lambda v: v["pads"]["pads"][0].update(claim="other"),
                       lambda v: v["journal"].update(transfers=[]),
                       lambda v: v.update(refused=0)):
            value = copy.deepcopy(after); change(value)
            with self.subTest(value=value), self.assertRaises(SmokeError):
                pad.check_interval([before, value], "LF_S6_PAD_REFUSED", 0)

    def test_unknown_never_grants_absence_or_releases(self):
        before = sample(); after = copy.deepcopy(before)
        after.update(seq=1, label="UNKNOWN", outcome="UNKNOWN")
        pad.check_interval([before, after], "LF_S6_PAD_UNKNOWN", 0)
        for key, value in (("absence", 1), ("releases", 1), ("outcome", "COMMITTED")):
            bad = copy.deepcopy(after); bad[key] = value
            with self.subTest(key=key), self.assertRaises(SmokeError):
                pad.check_interval([before, bad], "LF_S6_PAD_UNKNOWN", 0)

    def test_release_requires_preceding_checked_absence(self):
        before = sample(); ack = copy.deepcopy(before); ack.update(seq=1, label="ACK", absence=1)
        released = copy.deepcopy(ack); released.update(seq=2, label="RELEASED", releases=1)
        released["pads"]["pads"][0].pop("claim")
        pad.check_interval([before, ack, released], "LF_S6_PAD_RELEASED", 0)
        for events in ([before, released], [before, released, ack], [before, ack, dict(released, releases=2)]):
            with self.subTest(events=events), self.assertRaises(SmokeError):
                pad.check_interval(events, "LF_S6_PAD_RELEASED", 0)

    def test_missing_prefix_and_middle_reject(self):
        before = sample(); after = copy.deepcopy(before); after.update(seq=2, label="REFUSED", refused=1)
        for events in ([], [after], [before, after]):
            with self.subTest(events=events), self.assertRaises(SmokeError):
                pad.check_interval(events, "LF_S6_PAD_REFUSED", 0)


def native_sample():
    fixture = json.loads((Path(__file__).parent / "fixtures/loader_s6/forward-replaced-native.json").read_text())
    disk = pad.normalize(fixture["images"][0]["native"])
    baseline = disk.pop("source"); disk.pop("destination")
    disk["rockets"] = [copy.deepcopy(baseline)]
    row = disk["journal"]["transfers"][0]
    entry = dict(station="00000000-0000-4000-8000-000000000001", pad="00000000-0000-4000-8000-000000000002",
                 physical="00000000-0000-4000-8000-000000000003", owner=row["owner_id"], name="W221 Dock", x=32, y=99, z=0,
                 epoch=0, claim=row["transfer_id"])
    disk["pads"] = dict(schema=1, next_epoch=1, pads=[entry])
    disk["physical_pads"] = [dict(id="advancedrocketrycommunity:landing_pad", x=32, y=99, z=0,
        landing_pad=dict(schema=1, physical=entry["physical"], station=entry["station"], pad=entry["pad"], epoch=0))]
    for state in (row["source_flight"], row["destination_flight"]):
        state["plan"].update(destination_station_id=entry["station"], landing_pad=dict(id=entry["pad"], epoch=0))
    loader = copy.deepcopy(disk["loaders"][0]); markers = loader["arce_fluid_fuel_loader"]["resource_escrow"].pop("markers")
    loader["arce_fluid_fuel_loader"]["resource_escrow"]["markers"] = []
    event = dict(seq=0, label="PREPARED", releases=0, absence=0, baseline=baseline,
                 source=baseline, loader=loader, retired_loader_markers=markers, ledger=copy.deepcopy(disk["ledger"]),
                 journal=copy.deepcopy(disk["journal"]), pads=copy.deepcopy(disk["pads"]))
    return dict(read_status="KNOWN", native=disk), [event]


class NamedPadNativeTest(unittest.TestCase):
    def test_complete_prepared_control(self):
        image, events = native_sample()
        result = pad.check(image, 0, None, "LF_S6_PAD_PREPARED", events)
        self.assertEqual(5000, result["raw_total"])

    def test_native_mutations_and_unknown_are_rejected(self):
        image, events = native_sample()
        mutations = (
            lambda d: d["rockets"][0]["RocketEntityData"]["snapshot"]["relative_blocks"].pop(),
            lambda d: d["rockets"][0]["RocketEntityData"]["flight_data"]["fuel"].update(amount=999),
            lambda d: d["rockets"][0]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"].update(amount=999),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["tank"].update(Amount=4001),
            lambda d: d["loaders"][0]["arce_fluid_fuel_loader"]["resource_escrow"].update(resource_epoch=2),
            lambda d: d["pads"]["pads"][0].update(epoch=1),
            lambda d: d["pads"]["pads"][0].pop("claim"),
            lambda d: d["pads"]["pads"][0].update(claim="other"),
            lambda d: d["physical_pads"][0]["landing_pad"].update(physical="other"),
            lambda d: d["ledger"].update(writer_generation=3),
            lambda d: d["journal"].update(transfers=[]))
        for mutation in mutations:
            bad = copy.deepcopy(image); mutation(bad["native"])
            with self.subTest(mutation=mutation), self.assertRaises(SmokeError):
                pad.check(bad, 0, None, "LF_S6_PAD_PREPARED", events)
        with self.assertRaises(SmokeError): pad.check(dict(read_status="UNKNOWN", native=None), 0, None, "LF_S6_PAD_PREPARED", events)


if __name__ == "__main__": unittest.main()
