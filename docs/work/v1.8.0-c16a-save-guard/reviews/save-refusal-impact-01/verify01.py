"""Read-only source/primary/archive consistency controls, not a Java/native replay."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import unittest
import zipfile

sys.stdout.reconfigure(encoding='utf-8')
B = Path(__file__).resolve().parent
R = Path('D:/GitHub/AdvancedRocketry-Community')
C = '3f3d62aed3980186fe0acc9592cf93ca436405fb'
P = 'src/main/java/io/github/sunthemoon/advancedrocketrycommunity/'
I = json.loads((B / 'INPUT-EVIDENCE-01.json').read_text(encoding='utf-8'))
O = {'scope': 'Static/pinned historical artifact controls only; no Java/native execution',
     'commit': C, 'inputs': {}, 'observations': {}}

def meta(raw):
    return {'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()}

def code(short):
    name = P + short
    p = subprocess.run(['git', 'show', C + ':' + name], cwd=R, capture_output=True, timeout=60)
    assert p.returncode == 0, p.stderr
    O['inputs'][C + ':' + name] = meta(p.stdout)
    if C + ':' + name in I['inputs']:
        assert meta(p.stdout) == I['inputs'][C + ':' + name]
    return p.stdout.decode('utf-8')

def manifested_archive(path):
    z = zipfile.ZipFile(path)
    O['inputs']['archive:' + str(path)] = meta(path.read_bytes())
    assert z.testzip() is None
    raw = z.read('EVIDENCE-MANIFEST.json')
    O['inputs']['archive-member:' + str(path) + '!/EVIDENCE-MANIFEST.json'] = meta(raw)
    d = json.loads(raw)
    entries = d.get('entries', d.get('files'))
    if isinstance(entries, list):
        entries = {e['path']: {k: e[k] for k in ('bytes', 'sha256')} for e in entries}
    def member(name):
        raw = z.read(name)
        assert meta(raw) == {k: entries[name][k] for k in ('bytes', 'sha256')}
        O['inputs']['archive-member:' + str(path) + '!/' + name] = meta(raw)
        return raw
    return z, member

class ConsistencyControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.denials = code('persistence/ChunkSaveDenials.java')
        cls.guard = code('persistence/GuardedChunkSaves.java')
        cls.bound = code('persistence/BoundedNbt.java')
        cls.protections = {k: code('machine/' + k + '/' + n + 'Protection.java') for k, n in
                           [('tank', 'Tank'), ('pump', 'Pump'), ('combustion', 'Combustion'),
                            ('recipe', 'RecipeSignature')]}
        base = R / 'docs/work/v1.8.0-c16a-save-guard/reviews'
        cls.iz, cls.incident = manifested_archive(base / 'native-incident-01-evidence.zip')
        cls.nz, cls.native = manifested_archive(base / 'native-correction-03-evidence.zip')
        cls.cm = cls.incident('bytecode/patched-runtime-ChunkMap.txt').decode('utf-8')
        cls.mcm = cls.incident('bytecode/mapped-ChunkMap.txt').decode('utf-8')
        cls.stdout = cls.native('native/tank-native-03/oversized-refusal/stdout.txt').decode('utf-8')

    @classmethod
    def tearDownClass(cls):
        cls.iz.close()
        cls.nz.close()

    def test_01_per_level_capacity_and_saturation_source(self):
        capacity = int(re.search(r'MAX_CHUNKS = (\d+);', self.denials)[1])
        self.assertEqual(capacity, 256)
        self.assertIn('reasons.containsKey(chunk)) { return; }', self.denials)
        self.assertIn('reasons.size() == MAX_CHUNKS) { saturated = true; return; }', self.denials)
        self.assertIn('saturated ? Optional.of(SATURATED) : Optional.empty()', self.denials)
        O['observations']['capacity'] = {'distinct_records': capacity,
            'overflow_scope': 'all otherwise-unrecorded coordinates in that ServerLevel',
            'basis': 'source branch and existing unit boundary assertions, not new Java execution'}

    def test_02_lifecycle_not_saved_and_no_native_reset(self):
        self.assertIn('implements ICapabilityProvider {', self.guard)
        self.assertNotIn('INBTSerializable', self.guard)
        self.assertIn('private final ChunkSaveDenials state = new ChunkSaveDenials();', self.guard)
        self.assertIn('state.close(); capability.invalidate();', self.guard)
        self.assertIn('!(level.getServer() instanceof GameTestServer)', self.guard)
        self.assertIn('if (closed || saturated)', self.denials)

    def test_03_record_before_throw_and_retry_dirty(self):
        start = self.guard.index('public static void refuse(')
        body = self.guard[start:self.guard.index('/** Disposable GT', start)]
        self.assertLess(body.index('.deny('), body.index('fail(event, reason);'))
        self.assertLess(body.index('setUnsaved(true)'), body.index('throw new IllegalStateException(reason)'))
        self.assertIn('@SubscribeEvent(priority = EventPriority.HIGHEST)', self.guard)
        for source in self.protections.values():
            self.assertIn('GuardedChunkSaves.beforeSave(event)', source)
            self.assertIn('GuardedChunkSaves.refuse(event', source)

    def test_04_actual_four_guard_consumers_and_six_be_ids(self):
        for k, needle in [('tank', 'pressurized_tank'), ('pump', 'pump'),
                          ('combustion', 'combustion_generator')]:
            self.assertIn(needle, self.protections[k])
        for needle in ('rolling_machine', 'precision_assembler', 'electrolyzer'):
            self.assertIn(needle, self.protections['recipe'])
        O['observations']['guarded_be_ids'] = ['pressurized_tank', 'pump', 'combustion_generator',
                                             'rolling_machine', 'precision_assembler', 'electrolyzer']

    def test_05_below_byte_cap_refusals_are_not_byte_only(self):
        self.assertIn('++nodes > maxNodes || node.depth() > maxDepth', self.bound)
        self.assertIn('list.isEmpty() && list.getElementType() != Tag.TAG_END', self.bound)
        self.assertIn('Float.floatToRawIntBits(value) != Float.floatToIntBits(value)', self.bound)
        for k, n in [('tank', 'Tank'), ('pump', 'Pump'), ('combustion', 'Combustion')]:
            save = code('machine/' + k + '/' + n + 'Save.java')
            self.assertRegex(save, r'(8_192|8192)')
            self.assertRegex(save, r'BoundedNbt.fits\(\w+, MAX_BYTES, 16, (1_024|1024)\)')
        migration = code('machine/recipe/RecipeSignatureMigration.java')
        self.assertIn('65_536, 20, 4_096', migration)
        self.assertIn('MAX_BYTES, 16, 256', migration)
        O['observations']['refusal_classes'] = ['bytes', 'depth/nodes', 'native nonlossless/foreign shape']

    def test_06_native_event_catch_prevents_writer(self):
        start = self.cm.index('private boolean m_140258_(')
        body = self.cm[start:self.cm.index('\n  ', start + 3) if False else self.cm.index('Exception table:', start)]
        self.assertLess(body.index('net/minecraft/world/entity/ai/village/poi/PoiManager.'),
                        body.index('ChunkDataEvent$Save'))
        self.assertLess(body.index('ChunkDataEvent$Save'), body.index('// Method m_63502_'))
        self.assertIn('Failed to save chunk {},{}', body)
        O['observations']['terrain_write_order'] = ['POI flush', 'dirty false', 'serialize',
                                                   'Forge Save event', 'region write']

    def test_07_unload_ignores_save_boolean(self):
        start = self.cm.index('private void m_202998_(')
        body = self.cm[start:self.cm.index('LineNumberTable:', start)]
        self.assertRegex(body, r'Method m_140258_:[^\n]+\n\s+79: pop')
        self.assertGreater(body.index('ServerLevel.m_8712_'), body.index('79: pop'))

    def test_08_failed_save_has_no_success_cooldown(self):
        start = self.mcm.index('private boolean saveChunkIfNeeded(')
        body = self.mcm[start:self.mcm.index('LineNumberTable:', start)]
        self.assertIn('91: ifeq          111', body)
        self.assertIn('long 10000l', body)
        O['observations']['retry'] = {'restored_dirty': True, 'guard_retry_limit': None,
            'vanilla_success_cooldown_ms': 10000,
            'failed_write_does_not_set_that_cooldown': True,
            'no_claim_of_fixed_native_attempt_frequency': True}

    def test_09_historical_native_log_pairs_and_clean_exit(self):
        event = [(i, l) for i, l in enumerate(self.stdout.splitlines(), 1)
                 if '/ERROR]' in l and 'Exception caught during firing event:' in l]
        chunk = [(i, l) for i, l in enumerate(self.stdout.splitlines(), 1)
                 if '/ERROR]' in l and 'Failed to save chunk 11,11' in l]
        receipt = json.loads(self.native('native/tank-native-03/oversized-refusal/receipt.json'))
        self.assertEqual(len(event), len(chunk))
        self.assertGreater(len(event), 0)
        self.assertEqual(receipt['log_counts']['error_count'], len(event) + len(chunk))
        self.assertEqual(receipt['exit_code'], 0)
        self.assertIn('All chunks are saved', self.stdout)
        O['observations']['historical_native03_log_headers'] = {
            'EventBus': event, 'ChunkMap': chunk, 'total': len(event) + len(chunk),
            'clean_exit': receipt['exit_code'], 'host_sha256': receipt['host_sha256'],
            'current_commit_native_proof': False}

    def test_10_historical_native03_original_compressed_chunk_preserved(self):
        before = self.native('native/tank-native-03/oversized-patch/chunk-11-11.nbt.zlib')
        after = self.native('native/tank-native-03/oversized-refusal/chunk-11-11.nbt.zlib')
        self.assertEqual(before, after)
        self.assertEqual(after, self.native('native/independent-actual-chunk-11-11.nbt.zlib'))
        O['observations']['historical_native03_chunk_identity'] = meta(before)
        for short in ('ChunkSaveDenials.java', 'GuardedChunkSaves.java'):
            old = self.native('reviewed-source/io/github/sunthemoon/advancedrocketrycommunity/persistence/' + short)
            now = code('persistence/' + short).encode('utf-8')
            O['observations']['historical_guard_same_as_commit:' + short] = old == now

    def test_11_cross_store_and_be_clear_primary_controls(self):
        level = self.incident('bytecode/patched-runtime-ServerLevel.txt').decode('utf-8')
        chunk = self.incident('bytecode/patched-runtime-LevelChunk.txt').decode('utf-8')
        server = self.incident('bytecode/patched-runtime-MinecraftServer.txt').decode('utf-8')
        for needle in ('PersistentEntitySectionManager.', 'ServerChunkCache.'):
            self.assertIn(needle, level)
        self.assertIn('BlockEntity.onChunkUnloaded', chunk)
        self.assertIn('java/util/Map.clear', chunk)
        self.assertIn('Saving players', server)
        O['observations']['independent_stores'] = ['POI', 'Level SavedData', 'entities', 'players']

    def test_12_precision_migration_has_disk_readback_not_only_save_void(self):
        manager = code('machine/precision/PrecisionAssemblerManager.java')
        verifier = code('machine/precision/PrecisionAssemblerMigrationSaveVerifier.java')
        self.assertIn('verifier.controllerSaved', manager)
        self.assertIn('verifier.portsSaved', manager)
        self.assertIn('getChunkSource().save(true)', manager)
        self.assertIn('readChunk', verifier)
        O['observations']['precision_legacy_migration'] = 'separate disk-root verification after void save barrier'

if __name__ == '__main__':
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(ConsistencyControls)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    O['actual_result'] = {'tests': result.testsRun, 'failures': len(result.failures),
                         'errors': len(result.errors), 'skips': len(result.skipped)}
    (B / 'VERIFY-01.json').write_text(json.dumps(O, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    sys.exit(not result.wasSuccessful())
