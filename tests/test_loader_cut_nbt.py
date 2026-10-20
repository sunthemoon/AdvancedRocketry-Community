"""W107C's bounded NBT and Anvil fixture tools, without a JVM or native process."""
from pathlib import Path
import random
import struct
import sys
import tempfile
import unittest
import uuid
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_cut_nbt as nbt
from inspect_celestial_saved_data import NbtReader
from loader_cut_nbt import BYTE, BYTES, COMPOUND, DOUBLE, FLOAT, INT, INTS, LIST, LONG, LONGS, SHORT, STRING, Tag


def string(value: str) -> Tag:
    return Tag(STRING, value.encode("utf-8"))


def compound(**fields) -> Tag:
    return Tag(COMPOUND, dict(fields))


def section(y: int, palette: list[str], blocks: dict) -> Tag:
    longs = [0] * 256
    for (x, by, z), name in blocks.items():
        index = (by & 15) << 8 | (z & 15) << 4 | x & 15
        longs[index >> 4] |= palette.index(name) << ((index & 15) * 4)
    longs = [value - (1 << 64) if value >= 1 << 63 else value for value in longs]
    states = compound(palette=Tag(LIST, (COMPOUND, [compound(Name=string(name)) for name in palette])), data=Tag(LONGS, longs))
    return compound(Y=Tag(BYTE, y), block_states=states)


def loader_entity(x: int, amount: int = 4000) -> Tag:
    marker = compound(op_id=nbt.uuid_tag(uuid.UUID(int=7)), seq=Tag(LONG, 1), role=string("SOURCE"), epoch_after=Tag(LONG, 1))
    escrow = compound(schema_version=Tag(INT, 1), resource_epoch=Tag(LONG, 1), ledger_id=nbt.uuid_tag(uuid.UUID(int=9)),
                      markers=Tag(LIST, (COMPOUND, [marker])))
    root = compound(device=nbt.uuid_tag(uuid.UUID(int=3)), tank=compound(FluidName=string("advancedrocketrycommunity:rocket_fuel"),
                                                                     Amount=Tag(INT, amount)), resource_escrow=escrow)
    return compound(id=string(nbt.LOADER_ID), x=Tag(INT, x), y=Tag(INT, 100), z=Tag(INT, 64), arce_fluid_fuel_loader=root)


def fixture_chunk(**extra) -> Tag:
    sections = Tag(LIST, (COMPOUND, [section(0, ["minecraft:air", "minecraft:bedrock"], {(0, 0, 0): "minecraft:bedrock"}),
                                     section(6, ["minecraft:air", nbt.LOADER_ID], {(2, 100, 0): nbt.LOADER_ID})]))
    return compound(DataVersion=Tag(INT, 3465), sections=sections, block_entities=Tag(LIST, (COMPOUND, [loader_entity(66)])), **extra)


def region(directory: Path, chunks: dict, external: dict | None = None) -> Path:
    """A sector-aligned region file holding inline zlib records; ``external`` chunks get vanilla's stub and .mcc."""
    directory.mkdir(parents=True, exist_ok=True)
    data = bytearray(2 * nbt.SECTOR)
    for (x, z), root in list(chunks.items()) + list((external or {}).items()):
        encoded = zlib.compress(nbt.write(b"", root)); slot = (x & 31) + ((z & 31) << 5)
        outside = external and (x, z) in external
        record = struct.pack(">IB", 1, 2 | 128) if outside else struct.pack(">IB", len(encoded) + 1, 2) + encoded
        if outside:
            (directory / f"c.{x}.{z}.mcc").write_bytes(encoded)
        sectors = -(-len(record) // nbt.SECTOR); sector = len(data) // nbt.SECTOR
        data.extend(record + b"\x00" * (sectors * nbt.SECTOR - len(record)))
        struct.pack_into(">I", data, slot * 4, sector << 8 | sectors)
    path = directory / "r.0.0.mca"; path.write_bytes(bytes(data))
    return path


class NbtCodecTest(unittest.TestCase):
    def test_every_tag_type_round_trips_byte_for_byte_and_matches_the_untyped_reader(self):
        root = compound(b=Tag(BYTE, -3), s=Tag(SHORT, 300), i=Tag(INT, -70000), l=Tag(LONG, 1 << 40), f=Tag(FLOAT, 1.5),
                        d=Tag(DOUBLE, -2.25), bytes=Tag(BYTES, bytes(range(256))), text=Tag(STRING, b"modified \xc0\x80 utf"),
                        ints=Tag(INTS, [1, -2, 3]), longs=Tag(LONGS, [-(1 << 62), 5]), empty=Tag(LIST, (0, [])),
                        nested=Tag(LIST, (COMPOUND, [compound(x=Tag(INT, 1)), compound()])))
        payload = nbt.write(b"root", root)
        self.assertEqual((b"root", root), nbt.read(payload))
        self.assertEqual(payload, nbt.write(*nbt.read(payload)))
        plain = compound(i=Tag(INT, 4), name=string("x"), ints=Tag(INTS, [1, 2, 3, 4]), bytes=Tag(BYTES, b"\x01\xff"))
        self.assertEqual(NbtReader(nbt.write(b"", plain)).read_root(), nbt.plain(plain))

    def test_bounds_and_malformed_payloads_are_refused(self):
        payload = nbt.write(b"", compound(i=Tag(INT, 1)))
        for bad in (payload + b"\x00", payload[:-1], b"\x09" + payload[1:], b"\x0a\x00\x00\x09\x00\x01\x00\x00\x00\x01\x00"):
            with self.assertRaises(nbt.NbtError): nbt.read(bad)
        deep = compound()
        for _ in range(nbt.MAX_DEPTH + 1):
            deep = compound(child=deep)
        with self.assertRaises(nbt.NbtError): nbt.read(nbt.write(b"", deep))
        with self.assertRaises(nbt.NbtError): nbt.write(b"", Tag(LIST, (INT, [])))
        with self.assertRaises(nbt.NbtError): nbt.write(b"", compound(mixed=Tag(LIST, (INT, [Tag(LONG, 1)]))))

    def test_uuid_tag_is_minecrafts_int_array(self):
        value = uuid.UUID("01234567-89ab-cdef-0123-456789abcdef")
        self.assertEqual([0x01234567, 0x89abcdef - (1 << 32), 0x01234567, 0x89abcdef - (1 << 32)], nbt.uuid_tag(value).value)


class AnvilTest(unittest.TestCase):
    def test_inline_and_external_records_read_and_only_inline_records_are_rewritten(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            noisy = fixture_chunk(noise=Tag(BYTES, b"\x00" * 64))
            region(directory, {(4, 4): fixture_chunk()}, external={(5, 4): noisy})
            root, info = nbt.chunk(directory, 4, 4)
            self.assertEqual(fixture_chunk(), root); self.assertFalse(info["external"])
            outside, info = nbt.chunk(directory, 5, 4)
            self.assertEqual(noisy, outside); self.assertTrue(info["external"]); self.assertEqual(1, info["sectors"])
            with self.assertRaises(nbt.NbtError): nbt.write_chunk(directory, 5, 4, outside)
            with self.assertRaises(nbt.NbtError): nbt.chunk(directory, 6, 4)

    def test_a_grown_record_moves_to_new_sectors_without_touching_other_slots(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            path = region(directory, {(4, 4): fixture_chunk(), (0, 0): compound(other=Tag(INT, 1))})
            before = path.read_bytes()
            grown = fixture_chunk(padding=Tag(BYTES, random.Random(1).randbytes(3 * nbt.SECTOR)))
            nbt.write_chunk(directory, 4, 4, grown)
            self.assertEqual(grown, nbt.chunk(directory, 4, 4)[0])
            self.assertEqual(compound(other=Tag(INT, 1)), nbt.chunk(directory, 0, 0)[0])
            after = path.read_bytes()
            self.assertEqual(0, len(after) % nbt.SECTOR); self.assertGreater(len(after), len(before))
            self.assertEqual(before[0:4], after[0:4], "The other slot's location entry is unchanged")

    def test_palette_entries_are_read_and_set_in_place(self):
        root = fixture_chunk()
        self.assertEqual(nbt.LOADER_ID, nbt.block(root, 66, 100, 64))
        self.assertEqual("minecraft:air", nbt.block(root, 68, 100, 64))
        nbt.set_block(root, 66, 100, 64, "minecraft:air")
        self.assertEqual("minecraft:air", nbt.block(root, 66, 100, 64))
        with self.assertRaises(nbt.NbtError): nbt.set_block(root, 66, 100, 64, "minecraft:stone")
        with self.assertRaises(nbt.NbtError): nbt.block(root, 66, 40, 64)


class R3EditTest(unittest.TestCase):
    def world(self, temporary: str) -> Path:
        world = Path(temporary) / "world"
        region(world / "region", {(4, 4): fixture_chunk()})
        (world / "data").mkdir(parents=True)
        ledger_id = nbt.uuid_tag(uuid.UUID(int=9))
        record = compound(op_id=nbt.uuid_tag(uuid.UUID(int=7)), seq=Tag(LONG, 1))
        def outer(generation, issued, records):
            return compound(data=compound(ledger_id=ledger_id, issued_through=Tag(LONG, issued), writer_generation=Tag(LONG, generation),
                                          records=Tag(LIST, (COMPOUND if records else 0, records))), DataVersion=Tag(INT, 3465))
        nbt.write_gzip(world / "data" / nbt.LEDGER_FILE, b"", outer(2, 1, [record]))
        (world / nbt.CAPTURE).parent.mkdir()
        nbt.write_gzip(world / nbt.CAPTURE, b"", outer(1, 0, []))
        return world

    def test_each_endpoint_edit_changes_exactly_its_state_and_keeps_the_original_bytes(self):
        for name in ("R3_ENDPOINT_FOREIGN", "R3_ENDPOINT_OTHER", "R3_ENDPOINT_MISSING", "R3_ENDPOINT_RELOCATED"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                world = self.world(temporary); original = (world / "region" / "r.0.0.mca").read_bytes()
                manifest = nbt.r3_edit(world, name, Path(temporary) / "keep")
                self.assertEqual(original, (Path(temporary) / "keep" / "r.0.0.mca").read_bytes())
                edit = manifest["edits"][0]
                self.assertEqual(nbt.digest(original), edit["before_sha256"])
                self.assertEqual(nbt.digest((world / "region" / "r.0.0.mca").read_bytes()), edit["after_sha256"])
                self.assertNotEqual(edit["before_sha256"], edit["after_sha256"])
                root = nbt.plain(nbt.chunk(world / "region", 4, 4)[0]); entries = root["block_entities"]
                edited = nbt.chunk(world / "region", 4, 4)[0]
                if name == "R3_ENDPOINT_MISSING":
                    self.assertEqual([], entries); self.assertEqual("minecraft:air", nbt.block(edited, 66, 100, 64))
                    continue
                loader = entries[0]["arce_fluid_fuel_loader"]
                if name == "R3_ENDPOINT_RELOCATED":
                    self.assertEqual(68, entries[0]["x"])
                    self.assertEqual(("minecraft:air", nbt.LOADER_ID), (nbt.block(edited, 66, 100, 64), nbt.block(edited, 68, 100, 64)))
                else:
                    self.assertEqual(66, entries[0]["x"]); self.assertEqual(nbt.LOADER_ID, nbt.block(edited, 66, 100, 64))
                self.assertEqual(3000 if name == "R3_ENDPOINT_OTHER" else 4000, loader["tank"]["Amount"])
                self.assertEqual(name != "R3_ENDPOINT_FOREIGN", loader["resource_escrow"]["markers"][0]["op_id"] == list(nbt.uuid_tag(uuid.UUID(int=7)).value))

    def test_ledger_edits_remove_put_back_or_rebind_the_captured_generation(self):
        for name in ("R3_LEDGER_MISSING", "R3_LEDGER_BEHIND", "R3_LEDGER_REPLACED"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as temporary:
                world = self.world(temporary); target = world / "data" / nbt.LEDGER_FILE; original = target.read_bytes()
                edit = nbt.r3_edit(world, name, Path(temporary) / "keep")["edits"][0]
                self.assertEqual(original, (Path(temporary) / "keep" / nbt.LEDGER_FILE).read_bytes())
                self.assertEqual(nbt.digest(original), edit["before_sha256"])
                if name == "R3_LEDGER_MISSING":
                    self.assertFalse(target.exists()); self.assertIsNone(edit["after_sha256"]); continue
                captured = (world / nbt.CAPTURE).read_bytes()
                data = nbt.plain(nbt.read_gzip(target)[1])["data"]
                self.assertEqual((1, 0, []), (data["writer_generation"], data["issued_through"], data["records"]))
                if name == "R3_LEDGER_BEHIND":
                    self.assertEqual(captured, target.read_bytes()); self.assertEqual(data["ledger_id"], list(nbt.uuid_tag(uuid.UUID(int=9)).value))
                else:
                    self.assertNotEqual(data["ledger_id"], list(nbt.uuid_tag(uuid.UUID(int=9)).value))
                    self.assertEqual(data["ledger_id"], list(nbt.uuid_tag(uuid.UUID(edit["replacement_ledger_id"])).value))
                self.assertEqual(nbt.digest(target.read_bytes()), edit["after_sha256"]); self.assertEqual(nbt.digest(captured), edit["captured_sha256"])

    def test_unexpected_states_refuse_the_edit(self):
        with tempfile.TemporaryDirectory() as temporary:
            world = self.world(temporary); (world / nbt.CAPTURE).unlink()
            with self.assertRaises(nbt.NbtError): nbt.r3_edit(world, "R3_LEDGER_BEHIND", Path(temporary) / "keep")
        with tempfile.TemporaryDirectory() as temporary:
            world = self.world(temporary)
            nbt.write_gzip(world / nbt.CAPTURE, b"", compound(data=compound(ledger_id=nbt.uuid_tag(uuid.UUID(int=9)),
                           issued_through=Tag(LONG, 1), writer_generation=Tag(LONG, 2), records=Tag(LIST, (0, [])))))
            with self.assertRaises(nbt.NbtError): nbt.r3_edit(world, "R3_LEDGER_REPLACED", Path(temporary) / "keep")
        with tempfile.TemporaryDirectory() as temporary:
            world = self.world(temporary)
            region(world / "region", {(4, 4): compound(sections=fixture_chunk().value["sections"],
                                                       block_entities=Tag(LIST, (COMPOUND, [loader_entity(66, 5000)])))})
            with self.assertRaises(nbt.NbtError): nbt.r3_edit(world, "R3_ENDPOINT_OTHER", Path(temporary) / "keep")
        with tempfile.TemporaryDirectory() as temporary:
            world = self.world(temporary)
            with self.assertRaises(nbt.NbtError): nbt.r3_edit(world, "R3_ENDPOINT_ELSEWHERE", Path(temporary) / "keep")


if __name__ == "__main__": unittest.main()
