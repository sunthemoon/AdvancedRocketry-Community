import copy
import gzip
import json
import math
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_s6_named_pad as pad
from run_dedicated_server_smoke import SmokeError


def location(dimension, storage, pos):
    x, z = math.floor(pos[0]) // 16, math.floor(pos[2]) // 16
    directory = {"minecraft:overworld": "", "minecraft:the_nether": "DIM-1/",
                 "minecraft:the_end": "DIM1/"}.get(dimension, "dimensions/" + dimension.replace(":", "/") + "/")
    return dict(dimension=dimension, region=f"{directory}{storage}/r.{x // 32}.{z // 32}.mca",
                slot=(x % 32) + 32 * (z % 32), chunk=[x, z], record_chunk=[x, z])


def locations(disk, events):
    plan = events[0]["journal"]["transfers"][0]["source_flight"]["plan"]
    return dict(rockets=[location(r["RocketEntityData"]["flight_data"]["current_dimension"], "entities", r["Pos"])
                         for r in disk["rockets"]],
                loaders=[location(plan["source_dimension"], "region", [r[k] for k in ("x", "y", "z")])
                         for r in disk["loaders"]],
                physical_pads=[location(plan["destination_dimension"], "region", [r[k] for k in ("x", "y", "z")])
                               for r in disk["physical_pads"]])


def released_sample():
    fixture = json.loads((Path(__file__).parent / "fixtures/loader_s6/named-pad-released.json").read_text())
    for start in fixture["starts"]:
        start["image"]["native"]["locations"] = locations(start["image"]["native"], fixture["starts"][0]["events"])
    return fixture["starts"]


def tag(value):
    """Encode small unit worlds from captured values, without mocking the native reader."""
    nbt = pad.nbt
    if isinstance(value, dict): return nbt.Tag(nbt.COMPOUND, {k: tag(v) for k, v in value.items()})
    if isinstance(value, str): return nbt.Tag(nbt.STRING, value.encode())
    if isinstance(value, list):
        items = [tag(v) for v in value]
        kind = max((v.type for v in items), default=nbt.COMPOUND)
        return nbt.Tag(nbt.LIST, (kind, [nbt.Tag(kind, v.value) for v in items]))
    return nbt.Tag(nbt.DOUBLE if isinstance(value, float) else nbt.LONG if abs(value) >= 2**31 else nbt.INT, value)


def write_world(world, image):
    disk = image["native"]
    (world / "data").mkdir()
    for key, name in (("ledger", pad.LEDGER), ("journal", pad.JOURNAL),
                      ("pads", "advancedrocketrycommunity_landing_pads.dat")):
        (world / "data" / name).write_bytes(gzip.compress(pad.nbt.write(b"", tag(dict(data=disk[key])))))
    for key in ("rockets", "loaders", "physical_pads"):
        for entity, where in zip(disk[key], disk["locations"][key]):
            path = world / where["region"]; path.parent.mkdir(parents=True, exist_ok=True)
            x, z = where["chunk"]
            root = dict(Position=[x, z], Entities=[entity]) if key == "rockets" else dict(xPos=x, zPos=z, block_entities=[entity])
            compressed = zlib.compress(pad.nbt.write(b"", tag(root)))
            record = struct.pack(">IB", len(compressed) + 1, 2) + compressed
            sectors = (len(record) + 4095) // 4096
            raw = bytearray(path.read_bytes()) if path.exists() else bytearray(8192)
            struct.pack_into(">I", raw, where["slot"] * 4, ((len(raw) // 4096) << 8) | sectors)
            path.write_bytes(raw + record + bytes(sectors * 4096 - len(record)))


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
    # LevelChunk adds this byte to serialized, instantiated block entities.
    disk["loaders"][0]["keepPacked"] = 0
    for state in (row["source_flight"], row["destination_flight"]):
        state["plan"].update(destination_station_id=entry["station"], landing_pad=dict(id=entry["pad"], epoch=0))
    loader = copy.deepcopy(disk["loaders"][0]); markers = loader["arce_fluid_fuel_loader"]["resource_escrow"].pop("markers")
    loader.pop("keepPacked")  # The live saveWithFullMetadata capture is not chunk serialization.
    loader["arce_fluid_fuel_loader"]["resource_escrow"]["markers"] = []
    event = dict(seq=0, label="PREPARED", releases=0, absence=0, baseline=baseline,
                 source=baseline, loader=loader, retired_loader_markers=markers, ledger=copy.deepcopy(disk["ledger"]),
                 journal=copy.deepcopy(disk["journal"]), pads=copy.deepcopy(disk["pads"]))
    disk["locations"] = locations(disk, [event])
    return dict(read_status="KNOWN", native=disk), [event]


class NamedPadNativeTest(unittest.TestCase):
    def test_chunk_packing_flag_is_exact_not_discarded_or_optional(self):
        image, events = native_sample()
        pad.check(image, 0, None, "LF_S6_PAD_PREPARED", events)
        for flag in (None, 1, "0"):
            bad = copy.deepcopy(image)
            if flag is None: bad["native"]["loaders"][0].pop("keepPacked")
            else: bad["native"]["loaders"][0]["keepPacked"] = flag
            with self.subTest(flag=flag), self.assertRaises(SmokeError):
                pad.check(bad, 0, None, "LF_S6_PAD_PREPARED", events)

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


class NamedPadReviewCorrectionsTest(unittest.TestCase):
    def test_terminal_claim_with_zero_release_rejects_with_intact_sequence(self):
        starts = released_sample(); first = starts[0]
        pad.check(first["image"], 0, None, "LF_S6_PAD_RELEASED", first["events"])
        bad = copy.deepcopy(first["events"])
        bad[-1]["pads"] = copy.deepcopy(bad[0]["pads"]); bad[-1]["releases"] = 0
        self.assertEqual([0, 1, 2], [e["seq"] for e in bad])
        self.assertEqual(["PREPARED", "ACK", "RELEASED"], [e["label"] for e in bad])
        with self.assertRaisesRegex(SmokeError, "terminal.*pad|release"):
            pad.check(first["image"], 0, None, "LF_S6_PAD_RELEASED", bad)

    def test_every_completed_restart_requires_exact_release(self):
        before = sample(); before["label"] = "RESTART"
        ack = copy.deepcopy(before); ack.update(seq=1, label="ACK", absence=1)
        result = copy.deepcopy(ack); result.update(seq=2, label="RESULT", releases=1)
        result["pads"]["pads"][0].pop("claim")
        for cut in pad.CASES:
            for restart in (1, 2):
                with self.subTest(cut=cut, restart=restart):
                    pad.check_interval([before, ack, result], cut, restart)
                    bad = copy.deepcopy(result); bad["pads"] = copy.deepcopy(before["pads"]); bad["releases"] = 0
                    with self.assertRaisesRegex(SmokeError, "terminal.*pad|release"):
                        pad.check_interval([before, ack, bad], cut, restart)
                    unclaimed = copy.deepcopy(before); unclaimed["pads"]["pads"][0].pop("claim")
                    finished = copy.deepcopy(unclaimed); finished.update(seq=1, label="RESULT")
                    pad.check_interval([unclaimed, finished], cut, restart)
                    with self.assertRaisesRegex(SmokeError, "release"):
                        pad.check_interval([unclaimed, dict(finished, releases=1)], cut, restart)

    def test_inspect_retains_independent_native_coordinates_for_every_object(self):
        for image in (native_sample()[0], released_sample()[2]["image"]):
            with self.subTest(), tempfile.TemporaryDirectory() as temp:
                world = Path(temp); write_world(world, image)
                actual = pad.inspect(world)
                self.assertEqual("KNOWN", actual["read_status"])
                self.assertEqual(image["native"]["locations"], actual["native"]["locations"])

    def test_location_uses_negative_region_coordinates_and_dimension_directories(self):
        cases = (("entities/r.-1.-2.mca", "minecraft:overworld"),
                 ("DIM-1/region/r.-1.-2.mca", "minecraft:the_nether"),
                 ("DIM1/region/r.-1.-2.mca", "minecraft:the_end"),
                 ("dimensions/example/nested/level/entities/r.-1.-2.mca", "example:nested/level"))
        for relative, dimension in cases:
            with self.subTest(relative=relative):
                world = Path("world")
                data = dict(Position=[-1, -33], xPos=-1, zPos=-33)
                expected = dict(dimension=dimension, region=relative, slot=1023,
                                chunk=[-1, -33], record_chunk=[-1, -33])
                self.assertEqual(expected, pad.native_location(world, world / relative, 1023, data))
                pad.check_native_location(dict(rockets=[{}], locations=dict(rockets=[expected])),
                    "rockets", dimension, (world / relative).parent.name, [-0.5, 100, -512.5])

    def test_missing_or_inconsistent_native_provenance_is_rejected(self):
        image, events = native_sample()
        for key in ("rockets", "loaders", "physical_pads"):
            for field, value in (("dimension", "other:dimension"), ("region", "region/r.99.0.mca"),
                                 ("slot", 999), ("chunk", [-1, -1]), ("record_chunk", None)):
                bad = copy.deepcopy(image); bad["native"]["locations"][key][0][field] = value
                with self.subTest(key=key, field=field), self.assertRaisesRegex(SmokeError, "native location"):
                    pad.check(bad, 0, None, "LF_S6_PAD_PREPARED", events)
            bad = copy.deepcopy(image); bad["native"]["locations"][key] = []
            with self.subTest(key=key), self.assertRaisesRegex(SmokeError, "native location"):
                pad.check(bad, 0, None, "LF_S6_PAD_PREPARED", events)

    def test_terminal_live_complete_pad_must_match_disk(self):
        starts = released_sample()
        identity = pad.check(starts[0]["image"], 0, None, "LF_S6_PAD_RELEASED", starts[0]["events"])["identity"]
        for restart in (1, 2):
            start = starts[restart]
            pad.check(start["image"], restart, identity, "LF_S6_PAD_RELEASED", start["events"])
            for field, value in (("owner", "other"), ("epoch", 1), ("name", "other")):
                bad = copy.deepcopy(start["events"])
                for event in bad: event["pads"]["pads"][0][field] = value
                with self.subTest(restart=restart, field=field), self.assertRaisesRegex(SmokeError, "terminal.*pad"):
                    pad.check(start["image"], restart, identity, "LF_S6_PAD_RELEASED", bad)

    def check_moved_region(self, key, mutation, prepared=False):
        starts = released_sample()
        first = starts[0]
        identity = pad.check(first["image"], 0, None, "LF_S6_PAD_RELEASED", first["events"])["identity"]
        image, events = native_sample() if prepared else (starts[2]["image"], starts[2]["events"])
        restart, cut, identity = (0, "LF_S6_PAD_PREPARED", None) if prepared else (2, "LF_S6_PAD_RELEASED", identity)
        with tempfile.TemporaryDirectory() as temp:
            world = Path(temp); write_world(world, image)
            good = pad.inspect(world)
            self.assertEqual("KNOWN", good["read_status"])
            pad.check(good, restart, identity, cut, events)
            where = image["native"]["locations"][key][0]; path = world / where["region"]
            if mutation == "dimension":
                target = world / "dimensions/reviewer/wrong_dimension" / path.parent.name / path.name
                target.parent.mkdir(parents=True); path.rename(target)
            elif mutation == "region":
                path.rename(path.with_name("r.-1.2.mca"))
            else:
                raw = bytearray(path.read_bytes()); slot = where["slot"]
                raw[(slot + 1) * 4:(slot + 2) * 4] = raw[slot * 4:(slot + 1) * 4]
                raw[slot * 4:(slot + 1) * 4] = bytes(4); path.write_bytes(raw)
            bad = pad.inspect(world)
            self.assertEqual("KNOWN", bad["read_status"])
            self.assertEqual(good["native"][key], bad["native"][key], "Mutation must leave object NBT intact")
            with self.assertRaisesRegex(SmokeError, "native location"):
                pad.check(bad, restart, identity, cut, events)

    def test_moved_region_dimension_rejects_source_survivor_loader_and_pad(self):
        for key, prepared in (("rockets", False), ("rockets", True), ("loaders", False), ("physical_pads", False)):
            with self.subTest(key=key, prepared=prepared): self.check_moved_region(key, "dimension", prepared)

    def test_wrong_native_slot_rejects_source_survivor_loader_and_pad(self):
        for key, prepared in (("rockets", False), ("rockets", True), ("loaders", False), ("physical_pads", False)):
            with self.subTest(key=key, prepared=prepared): self.check_moved_region(key, "slot", prepared)

    def test_wrong_native_region_rejects_source_survivor_loader_and_pad(self):
        for key, prepared in (("rockets", False), ("rockets", True), ("loaders", False), ("physical_pads", False)):
            with self.subTest(key=key, prepared=prepared): self.check_moved_region(key, "region", prepared)


if __name__ == "__main__": unittest.main()
