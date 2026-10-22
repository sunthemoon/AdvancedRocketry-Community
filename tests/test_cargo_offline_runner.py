"""Stopped-world fixture edits exercise the real bounded NBT and region writer."""
import copy
import gzip
from pathlib import Path
import sys
import tempfile
import unittest
import uuid
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import cargo_recovery_fixture as fixture
import cargo_recovery_oracle as oracle
import loader_cut_nbt as nbt
import run_v180_cargo_cuts as runner
from test_loader_cut_nbt import compound, string, region, section
from loader_cut_nbt import Tag, INT, INTS, LIST, COMPOUND, LONG, BYTES


class CargoOfflineFixtureTest(unittest.TestCase):
    def seed(self, world):
        block = compound(position=Tag(INTS, [0, 0, 0]), block_entity=compound(unchanged=Tag(BYTES, b'payload')))
        before = compound(relative_blocks=Tag(LIST, (COMPOUND, [block])), bounding_box=Tag(INTS, [0] * 6),
                          content_hash=string('a' * 64), untouched=Tag(LONG, 123))
        after = copy.deepcopy(before)
        after.value['relative_blocks'].value[1][0].value['position'].value = [0, 1, 0]
        after.value['bounding_box'].value = [0, 1, 0, 0, 1, 0]
        after.value['content_hash'] = string('b' * 64)
        physical = nbt.uuid_tag(uuid.UUID(int=7))
        candidate = compound(physical=physical, before=before, after=after)
        path = world / fixture.CANDIDATE; path.parent.mkdir(parents=True)
        path.write_bytes(gzip.compress(nbt.write(b'', candidate)))
        rocket = compound(UUID=physical, RocketEntityData=compound(snapshot=before, retained=Tag(BYTES, b'wrapper')))
        other = compound(unrelated=Tag(BYTES, b'other record'))
        region(world / 'entities', {(4, 4): compound(Entities=Tag(LIST, (COMPOUND, [rocket])), retained=Tag(INT, 1)), (5, 4): other})
        device = compound(tank=compound(FluidName=string('minecraft:water'), Amount=Tag(INT, 5000)), retained=Tag(BYTES, b'device root'))
        entry = compound(id=string('advancedrocketrycommunity:rocket_fluid_loader'), x=Tag(INT, 64), y=Tag(INT, 100), z=Tag(INT, 64), arce_rocket_fluid_cargo=device)
        chunk = compound(block_entities=Tag(LIST, (COMPOUND, [entry])), sections=Tag(LIST, (COMPOUND,
            [section(6, ['minecraft:air', 'advancedrocketrycommunity:rocket_fluid_loader'], {(0, 100, 0): 'advancedrocketrycommunity:rocket_fluid_loader'})])))
        region(world / 'region', {(4, 4): chunk, (5, 4): other})
        op = compound(seq=Tag(LONG, 1), phase=string('PREPARED'), payload=compound(cursor=compound(p=Tag(INTS, [0, 0, 0]))))
        ledger = compound(data=compound(issued_through=Tag(LONG, 1), records=Tag(LIST, (COMPOUND, [op]))))
        path = world / 'data' / nbt.LEDGER_FILE; path.parent.mkdir()
        path.write_bytes(gzip.compress(nbt.write(b'', ledger)))
        return candidate

    def test_cursor_and_other_edits_preserve_typed_unrelated_roots_and_original_bytes(self):
        for case in fixture.CASES:
            with self.subTest(case=case), tempfile.TemporaryDirectory() as tmp:
                world = Path(tmp) / 'world'; candidate = self.seed(world)
                directory = world / ('entities' if case.endswith('CURSOR') else 'region')
                path = directory / 'r.0.0.mca'; original = path.read_bytes()
                expected = copy.deepcopy(nbt.chunk(directory, 4, 4)[0]); other = nbt.chunk(directory, 5, 4)[0]
                if case.endswith('CURSOR'):
                    expected.value['Entities'].value[1][0].value['RocketEntityData'].value['snapshot'] = candidate.value['after']
                else:
                    expected.value['block_entities'].value[1][0].value['arce_rocket_fluid_cargo'].value['tank'].value['FluidName'].value = b'minecraft:lava'
                untouched = world / ('region' if case.endswith('CURSOR') else 'entities') / 'r.0.0.mca'
                other_file = untouched.read_bytes(); ledger = (world / 'data' / nbt.LEDGER_FILE).read_bytes()
                keep = Path(tmp) / 'keep'; result = fixture.edit(world, case, keep)
                self.assertEqual(original, (keep / path.name).read_bytes())
                self.assertEqual(nbt.digest(original), result['before_sha256'])
                self.assertEqual(nbt.digest(path.read_bytes()), result['after_sha256'])
                self.assertEqual(expected, nbt.chunk(directory, 4, 4)[0])
                self.assertEqual(other, nbt.chunk(directory, 5, 4)[0])
                self.assertEqual(other_file, untouched.read_bytes())
                self.assertEqual(ledger, (world / 'data' / nbt.LEDGER_FILE).read_bytes())
                if case.endswith('CURSOR'):
                    self.assertEqual((world / fixture.CANDIDATE).read_bytes(), (keep / fixture.CANDIDATE.name).read_bytes())
                with self.assertRaises(nbt.NbtError):
                    fixture.edit(world, case, keep)

    def test_cursor_candidate_must_match_identity_baseline_and_only_the_intended_change(self):
        for change in ('identity', 'baseline', 'unrelated', 'hash', 'position'):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                world = Path(tmp) / 'world'; candidate = self.seed(world)
                if change == 'identity': candidate.value['physical'] = nbt.uuid_tag(uuid.UUID(int=8))
                elif change == 'baseline': candidate.value['before'].value['untouched'].value = 456
                elif change == 'unrelated': candidate.value['after'].value['untouched'].value = 456
                elif change == 'hash': candidate.value['after'].value['content_hash'] = string('a' * 64)
                else: candidate.value['after'].value['relative_blocks'].value[1][0].value['position'].value = [9, 9, 9]
                (world / fixture.CANDIDATE).write_bytes(gzip.compress(nbt.write(b'', candidate)))
                before = (world / 'entities/r.0.0.mca').read_bytes()
                with self.assertRaises(nbt.NbtError): fixture.edit(world, fixture.CASES[0], Path(tmp) / 'keep')
                self.assertEqual(before, (world / 'entities/r.0.0.mca').read_bytes())
                self.assertFalse((Path(tmp) / 'keep').exists())

    def test_relocation_stays_an_offline_exact_block_and_root_move(self):
        with tempfile.TemporaryDirectory() as tmp:
            world = Path(tmp) / 'world'; self.seed(world)
            root = nbt.chunk(world / 'region', 4, 4)[0]
            expected = copy.deepcopy(root); nbt.set_block(expected, 64, 100, 64, 'minecraft:air')
            nbt.set_block(expected, 72, 100, 64, 'advancedrocketrycommunity:rocket_fluid_loader')
            nbt.compound(expected, 'block_entities').value[1][0].value['x'].value = 72
            result = oracle.relocate(world, Path(tmp) / 'keep')
            self.assertEqual(expected, nbt.chunk(world / 'region', 4, 4)[0])
            self.assertEqual(nbt.digest((Path(tmp) / 'keep/r.0.0.mca').read_bytes()), result['before_sha256'])

    def test_runner_requires_successful_forced_stop_and_ownership_before_edit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); sentinel = root / 'owner'; sentinel.write_text('token')
            with patch.object(runner, 'raw_snapshot', return_value={}), patch.object(fixture, 'edit', return_value={}) as edit:
                for start, token in [({}, 'token'), ({'forced_stop': True, 'process_error': 'crash'}, 'token'), ({'forced_stop': True}, 'wrong')]:
                    with self.assertRaises(runner.native.smoke.SmokeError): runner.offline_damage(root, fixture.CASES[0], root, start, sentinel, token)
                edit.assert_not_called()
                result = runner.offline_damage(root, fixture.CASES[0], root, {'forced_stop': True}, sentinel, 'token')
                edit.assert_called_once(); self.assertEqual(runner.native.digest(root / result['raw_before']['file']), result['raw_before']['sha256'])


if __name__ == '__main__':
    unittest.main()
