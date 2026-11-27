"""Synthetic metadata and JEI-only inputs; no OS-specific calls, live clients or downloads."""
import copy
import gzip
import io
import json
import unittest
from unittest.mock import patch

from scripts import loader_cut_nbt as nbt
from scripts.campaign_client import baseline as b
from scripts.campaign_client.baseline_launch import prepare_baseline
from scripts.campaign_client.baseline_pairing import load_pairing, pairing_identity
from scripts.campaign_client.baseline_world import create_world
from scripts.campaign_client.cli import main
from scripts.campaign_client.evidence import dumps, sha256
from scripts.campaign_client.protocol import load_session
from scripts.campaign_client.session import Session, run_session
from scripts.v100_compatibility_runtime import SmokeError
import test_campaign_client_baseline as fixtures
from test_campaign_client_support import FakeWindows, Fixture
from test_campaign_client_session import FakeProcess


def text(value):
    return nbt.Tag(nbt.STRING, value.encode('ascii'))


class PairingTests(Fixture):
    def setUp(self):
        super().setUp()
        source = self.root / 'source'
        source.mkdir()
        self.world = create_world(source)
        self.file = self.world / 'level.dat'
        self.outer = nbt.read_gzip(self.file)[1]
        self.data = self.outer.value['Data'].value
        self.gen = self.data['WorldGenSettings'].value
        self.gen['seed'].value = -6512649996917166507
        self.data['SpawnX'].value = 42
        self.data['SpawnAngle'] = nbt.Tag(nbt.FLOAT, 25.5)
        self.data['Time'].value = 123456
        self.data['DayTime'].value = 9000
        self.data['rainTime'] = nbt.Tag(nbt.INT, 77)
        self.save()

    def save(self):
        nbt.write_gzip(self.file, b'', self.outer)
        self.pin = {'path': str(self.file), 'sha256': sha256(self.file)}

    def pairing(self):
        return load_pairing(self.root, self.pin)

    def output(self):
        game = self.root / 'output'
        game.mkdir()
        return nbt.read_gzip(create_world(game, self.pairing()) / 'level.dat')[1].value['Data'].value

    def player(self):
        self.data['Player'] = nbt.Tag(nbt.COMPOUND, {
            'Dimension': text('minecraft:overworld'),
            'Pos': nbt.Tag(nbt.LIST, (nbt.DOUBLE, [nbt.Tag(nbt.DOUBLE, v) for v in (4.5, 150.0, 10.5)])),
            'Rotation': nbt.Tag(nbt.LIST, (nbt.FLOAT, [nbt.Tag(nbt.FLOAT, v) for v in (45.0, -20.0)])),
            'Inventory': nbt.Tag(nbt.LIST, (nbt.COMPOUND, [nbt.Tag(nbt.COMPOUND, {'id': text('mod:item')})])),
            'ForgeCaps': nbt.Tag(nbt.COMPOUND, {'mod:data': text('discard')})})
        self.save()
        return self.data['Player'].value

    def test_exact_typed_seed_generator_state_rules_and_no_source_changes(self):
        before = self.file.read_bytes()
        data = self.output()
        for name in ('SpawnX', 'SpawnY', 'SpawnZ', 'SpawnAngle', 'Time', 'DayTime', 'GameRules', 'rainTime'):
            self.assertEqual(self.data[name], data[name], name)
        for name in ('seed', 'bonus_chest', 'generate_features'):
            self.assertEqual(self.gen[name], data['WorldGenSettings'].value[name], name)
        self.assertEqual(self.gen['dimensions'].value['minecraft:overworld'],
                         data['WorldGenSettings'].value['dimensions'].value['minecraft:overworld'])
        self.assertEqual(before, self.file.read_bytes())
        self.assertEqual(['level.dat'], [p.name for p in self.file.parent.iterdir()])
        self.assertEqual(['level.dat'], [p.name for p in (self.root / 'output/saves/baseline').iterdir()])

    def test_mod_dimensions_root_data_and_packs_are_not_imported(self):
        self.gen['dimensions'].value['mod:moon'] = nbt.Tag(nbt.COMPOUND, {'type': text('mod:moon')})
        self.data['ForgeData'] = nbt.Tag(nbt.COMPOUND, {'mod:block': text('private')})
        self.data['DataPacks'].value['Enabled'].value[1].extend([text('mod:forge'), text('mod:advancedrocketrycommunity')])
        self.save()
        data = self.output()
        self.assertNotIn('ForgeData', data)
        self.assertEqual({'minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end'},
                         set(data['WorldGenSettings'].value['dimensions'].value))
        self.assertEqual(['vanilla'], nbt.plain(data['DataPacks'])['Enabled'])

    def test_viewpoint_is_copied_without_inventory_or_capabilities(self):
        player = self.player()
        data = self.output()['Player'].value
        for name in ('Pos', 'Rotation', 'Dimension'):
            self.assertEqual(player[name], data[name])
        self.assertNotIn('Inventory', data)
        self.assertNotIn('ForgeCaps', data)
        self.assertEqual('Player.Pos/Rotation', self.pairing()['viewpoint_source'])

    def test_missing_player_is_explicitly_spawn_only_not_a_claim_of_matching_camera(self):
        identity = pairing_identity(self.pairing())
        self.assertIn('no saved Player camera', identity['viewpoint_source'])
        self.assertNotIn('Player', self.output())
        self.assertEqual(self.pin['sha256'], json.loads(dumps(identity))['sha256'])

    def test_builtin_noise_presets_preserved_exactly(self):
        world = self.gen['dimensions'].value['minecraft:overworld'].value
        world['generator'] = nbt.Tag(nbt.COMPOUND, {
            'type': text('minecraft:noise'), 'settings': text('minecraft:overworld'),
            'biome_source': nbt.Tag(nbt.COMPOUND, {'type': text('minecraft:multi_noise'), 'preset': text('minecraft:overworld')})})
        self.save()
        self.assertEqual(world['generator'], self.output()['WorldGenSettings'].value['dimensions'].value['minecraft:overworld'].value['generator'])

    def test_candidate_hash_change_is_refused(self):
        self.file.write_bytes(self.file.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'level.dat.sha256'):
            self.pairing()

    def test_bad_pair_schema_file_and_hash_are_refused(self):
        for pin in (None, {}, dict(self.pin, extra=True), dict(self.pin, sha256='bad'),
                    dict(self.pin, path=str(self.world)), dict(self.pin, path=str(self.world / 'missing.dat'))):
            with self.subTest(pin=pin), self.assertRaises(ValueError):
                load_pairing(self.root, pin)

    def test_wrong_version_missing_and_wrong_typed_state_refused(self):
        original = copy.deepcopy(self.data)
        for name, replacement in (('DataVersion', nbt.Tag(nbt.INT, 3466)), ('SpawnX', None),
                                  ('Time', nbt.Tag(nbt.INT, 123)), ('SpawnAngle', nbt.Tag(nbt.FLOAT, float('nan')))):
            self.data.clear(); self.data.update(copy.deepcopy(original))
            if replacement is None:
                del self.data[name]
            else:
                self.data[name] = replacement
            self.save()
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, name):
                self.pairing()

    def test_nonvanilla_unknown_inline_or_extra_generator_fields_refused(self):
        world = self.gen['dimensions'].value['minecraft:overworld'].value
        original = copy.deepcopy(world['generator'])
        for name in ('mod:flat', 'minecraft:unknown', 'minecraft:debug'):
            world['generator'] = copy.deepcopy(original)
            world['generator'].value['type'] = text(name)
            self.save()
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'generator.type'):
                self.pairing()
        world['generator'] = copy.deepcopy(original)
        world['generator'].value['unknown'] = nbt.Tag(nbt.INT, 1)
        self.save()
        with self.assertRaisesRegex(ValueError, 'generator'):
            self.pairing()

    def test_nonvanilla_dimension_type_biome_block_and_height_refused(self):
        world = self.gen['dimensions'].value['minecraft:overworld'].value
        settings = world['generator'].value['settings'].value
        cases = [(world, 'type', text('mod:overworld'), 'overworld.type'),
                 (settings, 'biome', text('mod:plains'), 'flat.biome'),
                 (settings, 'biome', text('minecraft:not_real'), 'flat.biome')]
        layer = settings['layers'].value[1][0].value
        cases += [(layer, 'block', text('mod:stone'), 'flat.layer.block'),
                  (layer, 'block', text('minecraft:not_real'), 'flat.layer.block'),
                  (layer, 'height', nbt.Tag(nbt.INT, 385), 'flat.layer.height')]
        for parent, key, value, message in cases:
            original = parent[key]
            parent[key] = value
            self.save()
            with self.subTest(message=message), self.assertRaisesRegex(ValueError, message):
                self.pairing()
            parent[key] = original

    def test_nonempty_structure_override_and_excess_total_height_refused(self):
        settings = self.gen['dimensions'].value['minecraft:overworld'].value['generator'].value['settings'].value
        settings['structure_overrides'].value[1].append(text('mod:structure'))
        self.save()
        with self.assertRaisesRegex(ValueError, 'flat.structure_overrides'):
            self.pairing()
        settings['structure_overrides'].value[1].clear()
        settings['layers'].value[1][0].value['height'].value = 384
        self.save()
        with self.assertRaisesRegex(ValueError, 'flat.layers'):
            self.pairing()

    def test_external_datapacks_refused(self):
        for name in ('file/custom.zip', 'mod:unknown', 'bundle', 'file/arce_v100_upgrade', 'mod:arce_adapter_test'):
            packs = self.data['DataPacks'].value['Enabled'].value[1]
            packs.append(text(name))
            self.save()
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, 'DataPacks.Enabled.*' + name):
                self.pairing()
            packs.pop()

    def test_custom_inline_noise_settings_or_biomes_refused(self):
        world = self.gen['dimensions'].value['minecraft:overworld'].value
        valid = {'type': text('minecraft:noise'), 'settings': text('minecraft:overworld'),
                 'biome_source': nbt.Tag(nbt.COMPOUND, {'type': text('minecraft:multi_noise'), 'preset': text('minecraft:overworld')})}
        for key, value, message in (
                ('settings', text('mod:noise'), 'noise.settings'),
                ('settings', nbt.Tag(nbt.COMPOUND, {}), 'noise.settings'),
                ('biome_source', nbt.Tag(nbt.COMPOUND, {'type': text('mod:biomes'), 'preset': text('minecraft:overworld')}), 'noise.biome_source'),
                ('biome_source', nbt.Tag(nbt.COMPOUND, {'type': text('minecraft:multi_noise'), 'biomes': nbt.Tag(nbt.LIST, (0, []))}), 'noise.biome_source')):
            gen = copy.deepcopy(valid)
            gen[key] = value
            world['generator'] = nbt.Tag(nbt.COMPOUND, gen)
            self.save()
            with self.subTest(key=key, message=message), self.assertRaisesRegex(ValueError, message):
                self.pairing()

    def test_missing_overworld_extra_generation_flag_or_wrong_seed_type_refused(self):
        original = copy.deepcopy(self.gen)
        for kind in ('missing', 'extra', 'seed'):
            self.gen.clear(); self.gen.update(copy.deepcopy(original))
            if kind == 'missing':
                del self.gen['dimensions'].value['minecraft:overworld']
            elif kind == 'extra':
                self.gen['custom'] = nbt.Tag(nbt.INT, 1)
            else:
                self.gen['seed'] = nbt.Tag(nbt.INT, 1)
            self.save()
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                self.pairing()

    def test_mutating_output_does_not_mutate_parsed_source(self):
        pairing = self.pairing()
        pairing['worldgen']['seed'].value = 42
        pairing['state']['GameRules'].value.clear()
        self.assertEqual(-6512649996917166507, self.gen['seed'].value)
        self.assertIn('doMobSpawning', self.data['GameRules'].value)
        self.assertEqual(-6512649996917166507, self.pairing()['worldgen']['seed'].value)

    def test_unknown_gamerules_and_invalid_values_refused(self):
        rules = self.data['GameRules'].value
        for key, value in (('mod:rule', 'true'), ('randomTickSpeed', '-1'),
                           ('randomTickSpeed', '2147483648'), ('doDaylightCycle', 'maybe')):
            original = rules.get(key)
            rules[key] = text(value)
            self.save()
            with self.subTest(key=key, value=value), self.assertRaisesRegex(ValueError, 'GameRules.' + key):
                self.pairing()
            if original is None:
                del rules[key]
            else:
                rules[key] = original

    def test_player_mod_dimension_and_bad_viewpoint_refused(self):
        player = self.player()
        for dimension in ('mod:moon', 'minecraft:the_end'):
            player['Dimension'] = text(dimension)
            self.save()
            with self.subTest(dimension=dimension), self.assertRaisesRegex(ValueError, 'Player.Dimension'):
                self.pairing()
        player['Dimension'] = text('minecraft:overworld')
        player['Pos'].value[1][0].value = float('inf')
        self.save()
        with self.assertRaisesRegex(ValueError, 'Player.Pos'):
            self.pairing()

    def test_compressed_decoded_and_corrupt_nbt_bounds(self):
        for raw in (b'x' * (nbt.MAX_COMPRESSED + 1), b'not gzip',
                    gzip.compress(b'x' * (nbt.MAX_DECODED + 1))):
            self.file.write_bytes(raw)
            self.pin['sha256'] = sha256(self.file)
            with self.subTest(size=len(raw)), self.assertRaisesRegex(ValueError, 'level.dat'):
                self.pairing()

    def test_linked_candidate_refused(self):
        link = self.root / 'linked'
        try:
            link.symlink_to(self.world, target_is_directory=True)
        except (OSError, NotImplementedError):
            self.skipTest('symlinks unavailable')
        with self.assertRaises(ValueError):
            load_pairing(self.root, dict(self.pin, path=str(link / 'level.dat')))


class BaselineIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.f = fixtures.BaselineTests()
        self.f.setUp()
        self.addCleanup(self.f.doCleanups)
        source = self.f.root / 'source'
        source.mkdir()
        self.file = create_world(source) / 'level.dat'
        self.f.session_spec['paired_world'] = {'path': str(self.file), 'sha256': sha256(self.file)}

    def jei(self):
        f = self.f
        f.forge()
        jar = Fixture.make_jar(f.template / 'mods' / b.JEI_FILE, 'jei')
        self.enterContext(patch.object(b, 'JEI_SHA256', sha256(jar)))
        entries, digest = b.jei_mods()
        f.raw.update(profile=b.FORGE_JEI, mods={'path': 'client/mods', 'entries': entries, 'sha256': digest})
        f.dump_baseline()
        return jar

    def test_pairing_parses_relative_pin_and_only_for_baselines(self):
        f = self.f
        f.session_spec['paired_world']['path'] = self.file.relative_to(f.root).as_posix()
        spec = load_session(f.dump_session())
        self.assertEqual(self.file.resolve(), spec['paired_world']['path'])
        for kind in ('candidate', None):
            if kind is None:
                f.session_spec.pop('launch_kind', None)
            else:
                f.session_spec['launch_kind'] = kind
            with self.subTest(kind=kind), self.assertRaisesRegex(ValueError, 'only supported for baseline'):
                load_session(f.dump_session())

    def test_bad_pair_refused_before_platform_lock_output_or_spawn(self):
        f = self.f
        f.session_spec['paired_world']['sha256'] = '0' * 64
        with patch('scripts.campaign_client.session.require_windows') as probe, patch('subprocess.Popen') as spawn:
            with self.assertRaisesRegex(ValueError, 'hash mismatch'):
                run_session(f.dump_session(), f.root / 'lock')
        probe.assert_not_called(); spawn.assert_not_called()
        self.assertFalse((f.root / 'run').exists())
        self.assertFalse((f.root / 'lock').exists())

    def test_pair_check_cli_is_offline(self):
        with patch('subprocess.Popen') as spawn, patch('sys.stdout', new_callable=io.StringIO):
            self.assertEqual(0, main(['check', str(self.f.dump_session())]))
        spawn.assert_not_called()

    def test_late_pair_change_refused_before_game_creation(self):
        f = self.f
        spec = load_session(f.dump_session())
        self.file.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'hash mismatch'):
            prepare_baseline(b.load_baseline(f.manifest), spec['clients'][0], f.root / 'new-game', 'java', spec['paired_world'])
        self.assertFalse((f.root / 'new-game').exists())

    def test_run_overlap_with_source_world_refused_before_writes(self):
        f = self.f
        before = self.file.read_bytes()
        for run in (self.file.parent, self.file.parent / 'run', self.file.parent.parent):
            f.session_spec['run_dir'] = str(run)
            session = Session(load_session(f.dump_session()), b.load_baseline(f.manifest), FakeWindows())
            with self.subTest(run=run), patch('scripts.campaign_client.session.CapturedProcess') as spawn:
                with self.assertRaisesRegex(ValueError, 'overlap'):
                    session.setup()
                spawn.assert_not_called()
                self.assertEqual(before, self.file.read_bytes())

    def test_direct_preparation_cannot_write_inside_the_candidate_world(self):
        f = self.f
        spec = load_session(f.dump_session())
        game = self.file.parent / 'must-not-create'
        before = self.file.read_bytes()
        with self.assertRaisesRegex(ValueError, 'overlap the paired source world'):
            prepare_baseline(b.load_baseline(f.manifest), spec['clients'][0], game, 'java', spec['paired_world'])
        self.assertFalse(game.exists())
        self.assertEqual(before, self.file.read_bytes())

    def test_ready_receipt_pair_identity_and_jei_launch_copy(self):
        f = self.f
        jar = self.jei()
        spec = load_session(f.dump_session())
        session = Session(spec, b.load_baseline(f.manifest), FakeWindows())
        before = self.file.read_bytes()
        with patch('scripts.campaign_client.session.CapturedProcess', return_value=FakeProcess()) as spawn:
            session.setup()
            summary = json.loads((session.run / 'summary.json').read_text())
            self.assertTrue(summary['jei'])
            self.assertEqual('47.4.10', summary['forge'])
            self.assertIsNone(summary['candidate_sha256'])
            self.assertEqual(sha256(self.file), summary['paired_world']['sha256'])
            game = session.run / 'client-TesterOne'
            self.assertEqual([b.JEI_FILE], [p.name for p in (game / 'mods').iterdir()])
            self.assertEqual(jar.read_bytes(), (game / 'mods' / b.JEI_FILE).read_bytes())
            self.assertIn('cpw.mods.bootstraplauncher.BootstrapLauncher', spawn.call_args.args[0])
            session.cleanup()
        self.assertEqual(before, self.file.read_bytes())
        self.assertFalse(game.exists())
        b.check_jei_game(f.template)

    def test_jei_identity_records_exact_pin(self):
        jar = self.jei()
        cell = b.load_baseline(self.f.manifest)
        self.assertEqual(b.FORGE_JEI, cell['profile'])
        self.assertEqual(b.jei_mods()[0], cell['baseline_identity']['mods']['entries'])
        self.assertEqual(sha256(jar), cell['baseline_identity']['mods']['entries'][0]['sha256'])

    def test_jei_missing_extra_hidden_and_directory_entries_refused(self):
        jar = self.jei()
        raw = jar.read_bytes()
        jar.unlink()
        with self.assertRaisesRegex(ValueError, 'exactly the pinned JEI'):
            b.load_baseline(self.f.manifest)
        jar.write_bytes(raw)
        for directory in b.EMPTY_DIRS:
            for name in ('candidate.jar', '.hidden', 'unexpected.txt'):
                path = self.f.template / directory / name
                path.write_bytes(b'extra')
                with self.subTest(directory=directory, name=name), self.assertRaises(ValueError):
                    b.load_baseline(self.f.manifest)
                path.unlink()
        (self.f.template / 'mods/extra-directory').mkdir()
        with self.assertRaises(ValueError):
            b.load_baseline(self.f.manifest)

    def test_jei_tamper_refused_even_if_manifest_relabelled(self):
        jar = self.jei()
        jar.write_bytes(b'not the campaign JEI')
        self.f.raw['mods']['entries'][0]['sha256'] = sha256(jar)
        self.f.dump_baseline()
        with self.assertRaisesRegex(ValueError, 'JEI SHA-256'):
            b.load_baseline(self.f.manifest)

    def test_jei_wrong_inventory_hash_or_empty_profile_refused(self):
        self.jei()
        original = copy.deepcopy(self.f.raw)
        for mutate in ('hash', 'entries', 'profile'):
            self.f.raw = copy.deepcopy(original)
            if mutate == 'hash':
                self.f.raw['mods']['sha256'] = '0' * 64
            elif mutate == 'entries':
                self.f.raw['mods']['entries'] = []
            else:
                self.f.raw['profile'] = b.FORGE
            self.f.dump_baseline()
            with self.subTest(mutate=mutate), self.assertRaises(ValueError):
                b.load_baseline(self.f.manifest)

    def test_late_jei_change_refused_before_output(self):
        jar = self.jei()
        cell = b.load_baseline(self.f.manifest)
        jar.write_bytes(b'changed')
        with self.assertRaises(ValueError):
            prepare_baseline(cell, self.f.session_spec['clients'][0], self.f.root / 'new-game', 'java')
        self.assertFalse((self.f.root / 'new-game').exists())

    def test_jei_without_pairing_retains_the_default_world(self):
        self.jei()
        f = self.f
        game = f.root / 'new-game'
        prepare_baseline(b.load_baseline(f.manifest), f.session_spec['clients'][0], game, 'java')
        data = nbt.plain(nbt.read_gzip(game / 'saves/baseline/level.dat')[1])['Data']
        self.assertEqual(0, data['WorldGenSettings']['seed'])
        self.assertEqual('minecraft:flat', data['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['type'])
        self.assertEqual(6000, data['DayTime'])
        self.assertEqual([b.JEI_FILE], [p.name for p in (game / 'mods').iterdir()])

    def test_jei_link_refused(self):
        jar = self.jei()
        source = self.f.root / 'outside-jei.jar'
        jar.rename(source)
        try:
            jar.symlink_to(source)
        except (OSError, NotImplementedError):
            self.skipTest('symlinks unavailable')
        with self.assertRaisesRegex(SmokeError, 'Linked runtime path'):
            b.load_baseline(self.f.manifest)


if __name__ == '__main__':
    unittest.main()
