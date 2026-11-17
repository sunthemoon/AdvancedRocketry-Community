"""Synthetic, typed whole-world controls for X03's non-runtime saved-data writer."""
import copy
import gzip
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import uuid
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import loader_cut_nbt as n
import repair_v180_rebuild_source as repair
from test_loader_cut_nbt import compound as C, string as S, region, section


def I(v):
    return n.Tag(n.INT, v)


def L(v):
    return n.Tag(n.LONG, v)


def A(*v):
    return n.Tag(n.INTS, list(v))


def U(v):
    return n.uuid_tag(uuid.UUID(int=v))


def E(*v):
    return n.Tag(n.LIST, (n.COMPOUND if v else 0, list(v)))


def vector(*v):
    return n.Tag(n.LIST, (n.DOUBLE, [n.Tag(n.DOUBLE, float(x)) for x in v]))


def default(kind):
    return n.Tag(kind, {n.LIST: (0, []), n.COMPOUND: {}, n.INTS: [0, 0, 0, 99], n.LONGS: [], n.LONG: 0}[kind])


class RepairTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='arce-w377-')
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.source = self.base / 'source'
        self.backup = self.base / 'backup'
        self.reports = self.base / 'reports'
        self.reports.mkdir()
        self.output = self.base / 'repaired'
        self.planfile = self.reports / 'plan.json'
        self.log = self.base / 'candidate.log'
        self.jar = self.base / 'candidate.jar'
        self.jar.write_bytes(b'pinned-synthetic-candidate')
        self.transfer = str(uuid.UUID(int=1))
        self.log.write_text('server ARCE_TRANSFER_FENCED transfer=' + self.transfer
                            + ' reason=FLOOR_CONFLICT repair=offline-whole-world\n', encoding='utf-8')
        self.escrow = C(schema_version=I(1), resource_epoch=L(1), markers=E(), ledger_id=U(5))
        self.snapshot = C(schema_version=I(2), snapshot_id=S(str(uuid.UUID(int=6))),
                          source_dimension=S('minecraft:overworld'), source_origin=A(68, 100, 64),
                          relative_blocks=E(C(position=A(0, 0, 0), block_entity=C(
                              adapter=S('advancedrocketrycommunity:pressurized_tank_v1'),
                              data=C(tank=C(schema=I(1), fluid=C(FluidName=S('minecraft:water'), Amount=I(5000))))))),
                          content_hash=S('a' * 64), extension=C(short=n.Tag(n.SHORT, 7)))
        self.destination = copy.deepcopy(self.snapshot)
        self.destination.value['snapshot_id'] = S(str(uuid.UUID(int=7)))
        self.destination.value['source_origin'] = A(88, 100, 64)
        self.fuel = C(amount=L(1000), propulsion=C(primary=C(amount=L(1000), fluid=S('advancedrocketrycommunity:rocket_fuel'))))
        self.flight = C(state=S('FUELED'), logical_rocket_id=U(2), current_dimension=S('minecraft:overworld'),
                        current_origin=A(68, 100, 64), passengers=C(assignments=E()), fuel=copy.deepcopy(self.fuel))
        self.record = C(schema_version=I(2), transfer_id=U(1), phase=S('PREPARED'), logical_rocket_id=U(2),
                        source_entity_id=U(3), owner_id=U(4), source_snapshot=copy.deepcopy(self.snapshot),
                        destination_snapshot=self.destination, source_flight=copy.deepcopy(self.flight),
                        destination_flight=copy.deepcopy(self.flight), checksum=S('c' * 64),
                        unknown=n.Tag(n.BYTES, b'\x00\xff'))
        current = copy.deepcopy(self.snapshot)
        repair.cargo(current, 5000).value['Amount'].value = 4000
        current.value['content_hash'] = S('b' * 64)
        self.entity = C(id=S('advancedrocketrycommunity:rocket'), UUID=U(3), Pos=vector(68.5, 100, 64.5),
                        Motion=vector(0, 0, 0), RocketEntityData=C(schema_version=I(3), assembly_transaction_id=U(2),
                        owner_id=U(4), resource_escrow=copy.deepcopy(self.escrow), snapshot=current,
                        flight_data=copy.deepcopy(self.flight)))
        self.device = C(id=S('advancedrocketrycommunity:rocket_fluid_unloader'), x=I(64), y=I(100), z=I(64),
                        arce_rocket_fluid_cargo=C(schema_version=I(1), device=U(8), owner=U(4), role=S('UNLOAD'),
                        binding=C(schema_version=I(1), logical_rocket=U(2), physical_rocket=U(3), level=S('minecraft:overworld'),
                                  x=I(68), y=I(100), z=I(64)),
                        resource_escrow=copy.deepcopy(self.escrow), tank=C(FluidName=S('minecraft:water'), Amount=I(1000))))
        self.seed()

    def seed(self, profile=repair.PROFILE_DISABLED):
        if self.source.exists():
            shutil.rmtree(self.source)
        self.source.mkdir()
        (self.source / 'session.lock').write_bytes(b'\xe2\x98\x83')
        self.data = {}
        for key, (schema, epoch) in repair.CURRENT_ROOTS.items():
            if key == 'resource_escrow' and profile == repair.PROFILE_ENABLED:
                schema, epoch = 3, 'v1.8.0-resource-escrow-fluid-accounting'
            fields = {k: default(v) for k, v in repair.ROOT_REQUIRED[key].items()}
            self.data[key] = C(schema_version=I(schema), format_epoch=S(epoch), **fields)
        self.data['rocket_transfers'].value['transfers'] = E(copy.deepcopy(self.record))
        self.data['rocket_transfers'].value['opaque'] = C(z=n.Tag(n.BYTE, 1), a=n.Tag(n.SHORT, 2))
        ledger = self.data['resource_escrow']
        ledger.value['ledger_id'] = U(5)
        ledger.value['issued_through'] = L(1)
        if profile == repair.PROFILE_ENABLED:
            ledger.value['fluid_accounts'] = E()
        self.write_roots()
        self.write_entities([self.entity])
        self.write_terrain()
        self.sync()

    def write_roots(self):
        (self.source / 'data').mkdir(exist_ok=True)
        for key, data in self.data.items():
            n.write_gzip(self.source / 'data' / ('advancedrocketrycommunity_' + key + '.dat'),
                         b'root', C(DataVersion=I(3465), data=data))

    def write_entities(self, entries, level=''):
        region(self.source / level / 'entities', {(4, 4): C(Entities=E(*copy.deepcopy(entries)))})

    def write_terrain(self, destination_block=None):
        region(self.source / 'region', {
            (4, 4): C(sections=E(section(6, ['minecraft:air', 'advancedrocketrycommunity:rocket_fluid_unloader'],
                                               {(64, 100, 64): 'advancedrocketrycommunity:rocket_fluid_unloader'})),
                      block_entities=E(copy.deepcopy(self.device))),
            (5, 4): C(sections=E(section(6, ['minecraft:air', 'minecraft:iron_block'],
                                               {} if destination_block is None else {(88, 100, 64): destination_block})), block_entities=E())})

    def sync(self):
        if self.backup.exists():
            shutil.rmtree(self.backup)
        shutil.copytree(self.source, self.backup)

    def plan(self, **extra):
        args = dict(source=self.source, backup=self.backup, candidate=self.jar, log=self.log,
                    report=self.planfile, transfer=self.transfer, profile=repair.PROFILE_DISABLED,
                    operator='test-operator', reason='synthetic X03 one-record repair')
        args.update(extra)
        return repair.plan(**args)

    def inspect(self, profile=repair.PROFILE_DISABLED):
        with repair.world_lock(self.source) as lock:
            return repair.inspect(self.source, repair.manifest(self.source, lock), profile, self.transfer)

    def refusal(self, reason, fn=None):
        with self.assertRaises(repair.Refusal) as caught:
            (fn or self.inspect)()
        self.assertIn(reason, str(caught.exception))

    def hashes(self, root):
        return {p.relative_to(root).as_posix(): p.read_bytes() for p in root.rglob('*') if p.is_file()}

    def test_one_record_whole_world_copy_and_offline_census(self):
        original = self.hashes(self.source)
        document = self.plan()
        self.assertEqual('UNKNOWN_BASELINE', document['body']['account']['status'])
        self.assertNotIn('discrepancy', document['body']['account'])
        result = repair.apply(self.planfile, self.output, document['digest'])
        self.assertEqual('COPIED_NOT_PROMOTED', result['status'])
        self.assertEqual(original, self.hashes(self.source))
        self.assertEqual(original, self.hashes(self.backup))
        output = self.hashes(self.output)
        self.assertEqual([repair.JOURNAL], [k for k in original if original[k] != output[k]])
        name, journal = repair.read_gzip(self.output / repair.JOURNAL)
        self.assertEqual(b'root', name)
        self.assertEqual((0, []), repair.at(journal, 'data', 'transfers').value)
        expected = copy.deepcopy(self.data['rocket_transfers'])
        expected.value['transfers'] = E()
        self.assertEqual(repair.typed(expected), repair.typed(repair.at(journal, 'data')))
        census = repair.census(self.planfile, self.output, self.reports / 'census.json')
        self.assertFalse(census['promotion_approved'])
        self.assertEqual(dict(added=[], removed=[], changed=[repair.JOURNAL]), census['differences'])

    def test_writer_pinned_empty_end_bytes_and_reproducible_gzip(self):
        payload = repair.journal_candidate(*repair.read_gzip(self.source / repair.JOURNAL), self.transfer)
        self.assertIn(b'\x09\x00\x09transfers\x00\x00\x00\x00\x00', payload)
        first, second = repair.compressed(payload), repair.compressed(payload)
        self.assertEqual(first, second)
        self.assertEqual(bytes(4), first[4:8])
        self.assertEqual(0, first[3] & 8)  # No original filename.
        self.assertEqual(payload, gzip.decompress(first))
        self.assertEqual(2, repair.LEGACY_RECORD_SCHEMA)

    def test_snapshot_diff_rejects_order_types_unknown_fields_id_and_all_other_leaves(self):
        before = self.snapshot
        current = repair.at(self.entity, 'RocketEntityData', 'snapshot')
        repair.snapshot_rule(before, current)
        for change in ('order', 'type', 'unknown', 'id', 'origin'):
            with self.subTest(change=change):
                bad = copy.deepcopy(current)
                if change == 'order':
                    bad.value = dict(reversed(list(bad.value.items())))
                elif change == 'type':
                    bad.value['schema_version'] = L(2)
                elif change == 'unknown':
                    bad.value['extension'].value['x'] = I(1)
                elif change == 'id':
                    bad.value['snapshot_id'] = S(str(uuid.UUID(int=99)))
                else:
                    bad.value['source_origin'] = A(69, 100, 64)
                self.refusal('SNAPSHOT_DIFFERS', lambda: repair.snapshot_rule(before, bad))

    def test_fence_missing_reason_other_transfer_and_bound_hash(self):
        for text, reason in [('', 'FENCE_EVIDENCE_MISSING'),
                             (self.log.read_text().replace('FLOOR_CONFLICT', 'WAITING_UNLOADED'), 'FENCE_REASON_DIFFERS'),
                             (self.log.read_text() + self.log.read_text().replace(self.transfer, str(uuid.UUID(int=99))), 'OTHER_TRANSFER_FENCED')]:
            with self.subTest(reason=reason):
                self.log.write_text(text)
                self.refusal(reason, lambda: repair.fence(self.log, self.transfer))

    def test_record_phase_native_version_destination_checksum_and_other_transfer_refusals(self):
        baseline = copy.deepcopy(self.record)
        cases = [('schema_version', I(3), 'GOVERNED_RECORD'), ('schema_version', I(1), 'UNSUPPORTED_RECORD_VERSION'),
                 ('phase', S('COMMITTED'), 'PHASE_NOT_PREPARED'), ('native_transfer', C(), 'GOVERNED_RECORD'),
                 ('destination_entity_id', U(99), 'DESTINATION_ENTITY'), ('checksum', S('bad'), 'INVALID_CHECKSUM')]
        for key, tag, reason in cases:
            with self.subTest(reason=reason):
                bad = copy.deepcopy(baseline)
                bad.value[key] = tag
                self.data['rocket_transfers'].value['transfers'] = E(bad)
                self.write_roots()
                self.refusal(reason)
        other = copy.deepcopy(baseline)
        other.value['transfer_id'] = U(99)
        self.data['rocket_transfers'].value['transfers'] = E(baseline, other)
        self.write_roots()
        self.refusal('OTHER_TRANSFER')

    def test_scope_geometric_keep_source_is_the_only_refusal(self):
        repair.at(self.record, 'destination_snapshot').value['source_origin'] = A(76, 100, 64)
        self.data['rocket_transfers'].value['transfers'] = E(self.record)
        self.write_roots()
        with self.assertRaises(repair.Refusal) as error:
            self.inspect()
        self.assertEqual(['DESTINATION_IN_TARGET_CHUNK'], error.exception.reasons)

    def test_duplicate_rockets_all_slots_dimensions_and_passenger_trees(self):
        for placement in ('slot', 'dimension', 'passenger'):
            with self.subTest(placement=placement):
                self.seed()
                if placement == 'dimension':
                    self.write_entities([self.entity], 'dimensions/example/hidden')
                elif placement == 'slot':
                    region(self.source / 'entities', {(4, 4): C(Entities=E(self.entity)), (7, 7): C(Entities=E(self.entity))})
                else:
                    rider = C(id=S('minecraft:pig'), Passengers=E(self.entity))
                    self.write_entities([self.entity, rider])
                self.refusal('SECOND_PHYSICAL_ROCKET')

    def test_missing_record_and_r73_double_source_without_record_refuses(self):
        self.data['rocket_transfers'].value['transfers'] = E()
        self.write_roots()
        self.write_entities([self.entity, self.entity])
        self.refusal('TRANSFER_MISSING_OR_DUPLICATE')

    def test_rocket_identity_flight_passengers_motion_and_propulsion_refusals(self):
        changes = [(('UUID',), U(99), 'ROCKET_IDENTITY_DIFFERS'),
                   (('Pos',), vector(70, 100, 64), 'ROCKET_RELOCATED'),
                   (('Motion',), vector(0, 1, 0), 'ROCKET_MOVING'),
                   (('RocketEntityData', 'flight_data', 'state'), S('TRANSIT'), 'ACTIVE_FLIGHT'),
                   (('RocketEntityData', 'flight_data', 'fuel', 'amount'), L(999), 'PROPULSION_DIFFERS'),
                   (('RocketEntityData', 'flight_data', 'passengers', 'assignments'), E(C(player=U(99))), 'PASSENGER_ASSIGNMENT')]
        for path, tag, reason in changes:
            with self.subTest(reason=reason):
                entity = copy.deepcopy(self.entity)
                repair.at(entity, *path[:-1]).value[path[-1]] = tag
                self.write_entities([entity])
                self.refusal(reason)

    def test_record_passengers_reconnect_refuses(self):
        repair.at(self.data['rocket_transfers'], 'transfers').value[1][0].value['source_flight'].value['passengers'] = C(assignments=E(C(player=U(9))))
        self.write_roots()
        self.refusal('PASSENGER_ASSIGNMENT')

    def test_endpoint_binding_epoch_marker_identity_fluid_and_device_refusals(self):
        baseline = copy.deepcopy(self.device)
        changes = [(('binding', 'level'), S('minecraft:the_end'), 'BINDING_DIFFERS'),
                   (('binding', 'x'), I(69), 'BINDING_DIFFERS'),
                   (('owner',), U(9), 'BINDING_DIFFERS'),
                   (('resource_escrow', 'ledger_id'), U(9), 'ESCROW_DIFFERS'),
                   (('resource_escrow', 'resource_epoch'), L(2), 'ESCROW_DIFFERS'),
                   (('resource_escrow', 'markers'), E(C(op_id=U(9))), 'ESCROW_DIFFERS'),
                   (('role',), S('LOAD'), 'DEVICE_DIFFERS'),
                   (('tank', 'Amount'), I(999), 'RESOURCE_DIFFERS'),
                   (('tank', 'FluidName'), S('minecraft:lava'), 'RESOURCE_DIFFERS')]
        for path, tag, reason in changes:
            with self.subTest(reason=reason):
                self.device = copy.deepcopy(baseline)
                repair.at(self.device, 'arce_rocket_fluid_cargo', *path[:-1]).value[path[-1]] = tag
                self.write_terrain()
                self.refusal(reason)

    def test_empty_and_absent_native_tags_are_distinct(self):
        observation = self.inspect()
        self.assertEqual('absent', observation['native_tag'])
        for snap in (repair.at(self.entity, 'RocketEntityData', 'snapshot'), self.record.value['source_snapshot']):
            repair.cargo(snap, 4000 if snap is not self.record.value['source_snapshot'] else 5000).value['Tag'] = C()
        repair.at(self.device, 'arce_rocket_fluid_cargo', 'tank').value['Tag'] = C()
        self.data['rocket_transfers'].value['transfers'] = E(self.record)
        self.write_roots(); self.write_entities([self.entity]); self.write_terrain()
        self.assertEqual(n.digest(repair.typed(C())), self.inspect()['native_tag'])

    def test_every_competing_authority_and_disjoint_unrelated_entries(self):
        for key in ('records', 'floors', 'tombstones', 'rocket_authorities', 'armed_flights'):
            with self.subTest(key=key):
                self.seed()
                self.data['resource_escrow'].value[key] = E(C(rocket_id=U(2)))
                self.write_roots()
                self.refusal('COMPETING_AUTHORITY:' + key)
        for key in ('rocket_transactions', 'stations', 'rocket_rejoins', 'bay_receipts'):
            with self.subTest(key=key):
                self.seed()
                self.data[key].value['held'] = C(logical=U(2))
                self.write_roots()
                self.refusal('COMPETING_AUTHORITY:')
        self.seed()
        unrelated = C(rocket_id=U(1234), opaque=n.Tag(n.SHORT, 5))
        self.data['resource_escrow'].value['floors'] = E(unrelated)
        self.write_roots(); self.sync()
        doc = self.plan()
        repair.apply(self.planfile, self.output, doc['digest'])
        self.assertEqual((self.backup / repair.LEDGER).read_bytes(), (self.output / repair.LEDGER).read_bytes())

    def test_footprint_restored_tank_and_destination_occupancy_refuse(self):
        self.write_terrain('minecraft:iron_block')
        self.refusal('FOOTPRINT_OCCUPIED')

    def test_root_profiles_current_schema_epoch_and_future_roots(self):
        for key in repair.CURRENT_ROOTS:
            for field, bad in [('schema_version', I(99)), ('format_epoch', S('old'))]:
                with self.subTest(root=key, field=field):
                    self.seed()
                    self.data[key].value[field] = bad
                    self.write_roots()
                    self.refusal('ROOT_NOT_CURRENT:' + key)
        self.seed()
        self.refusal('ROOT_NOT_CURRENT:resource_escrow', lambda: self.inspect(repair.PROFILE_ENABLED))
        self.seed(repair.PROFILE_ENABLED)
        self.inspect(repair.PROFILE_ENABLED)
        self.data['resource_escrow'].value['fluid_accounts'] = E(C(rocket_id=U(2)))
        self.write_roots()
        self.refusal('COMPETING_AUTHORITY:fluid_accounts', lambda: self.inspect(repair.PROFILE_ENABLED))

    def test_unknown_managed_root_and_unreadable_sidecar_refuse(self):
        n.write_gzip(self.source / 'data/advancedrocketrycommunity_future.dat', b'', C())
        self.refusal('UNKNOWN_MANAGED_ROOT')
        (self.source / 'data/advancedrocketrycommunity_future.dat').unlink()
        (self.source / 'playerdata').mkdir()
        n.write_gzip(self.source / 'playerdata/player.dat', b'', C(obligation=U(2)))
        self.refusal('COMPETING_SIDECAR')

    def test_corrupt_truncated_concatenated_gzip_and_duplicate_keys_refuse(self):
        path = self.source / repair.JOURNAL
        raw = path.read_bytes()
        for bad in (b'not-gzip', raw[:-3], raw + raw):
            with self.subTest(size=len(bad)):
                path.write_bytes(bad)
                with self.assertRaises((repair.Refusal, zlib.error)):
                    repair.read_gzip(path)
        duplicate = b'\x0a\x00\x00\x03\x00\x01a\x00\x00\x00\x01' * 1
        duplicate += b'\x03\x00\x01a\x00\x00\x00\x02\x00'
        path.write_bytes(gzip.compress(duplicate))
        with self.assertRaises(n.NbtError):
            repair.read_gzip(path)

    def test_unknown_compression_external_entity_and_short_region_refuse(self):
        path = self.source / 'entities/r.0.0.mca'
        original = path.read_bytes()
        raw = bytearray(original)
        _, _, _, _ = n._header(raw, 132)
        raw[8196] = 99
        path.write_bytes(raw)
        with self.assertRaises(n.NbtError):
            self.inspect()
        path.write_bytes(original)
        region(path.parent, {}, external={(4, 4): C(Entities=E(self.entity))})
        self.refusal('EXTERNAL_ENTITY_RECORD')
        path.write_bytes(b'bad')
        self.refusal('CORRUPT_REGION')

    def test_bounds_and_insufficient_space(self):
        for constant in ('MAX_FILES', 'MAX_BYTES', 'MAX_LEVELS', 'MAX_REGIONS', 'MAX_OBJECTS', 'MAX_REGION_BYTES', 'MAX_CENSUS_BYTES'):
            with self.subTest(constant=constant), patch.object(repair, constant, 0):
                self.refusal('LIMIT', self.inspect)
        with patch.object(repair.shutil, 'disk_usage', return_value=shutil._ntuple_diskusage(1, 1, 0)):
            self.refusal('INSUFFICIENT_SPACE', self.plan)
        self.assertEqual((64, 100000, 256 * 1024**3, 4096, 1024, 8),
                         (repair.MAX_LEVELS, repair.MAX_FILES, repair.MAX_BYTES, repair.MAX_REGIONS, repair.MAX_OBJECTS, repair.MAX_PASSENGER_DEPTH))

    def test_world_record_lock_refuses_child_holder_on_running_platform(self):
        script = ('import sys;from pathlib import Path;sys.path.insert(0,sys.argv[1]);'
                  'import repair_v180_rebuild_source as r;'
                  '\ntry:\n with r.world_lock(Path(sys.argv[2])): pass\nexcept r.Refusal as e:\n print(e);sys.exit(2)')
        with repair.world_lock(self.source) as lock:
            before = repair.manifest(self.source, lock)
            child = subprocess.run([sys.executable, '-B', '-c', script, str(Path(repair.__file__).parent), str(self.source)],
                                   capture_output=True, text=True, timeout=20)
            self.assertEqual(2, child.returncode, child.stderr)
            self.assertIn('WORLD_LOCKED', child.stdout)
            self.assertEqual(before, repair.manifest(self.source, lock))

    def test_report_lock_alias_traversal_and_hardlink_refusals(self):
        with repair.report_lock(self.reports):
            self.refusal('WORLD_LOCKED', self.plan)
        self.refusal('PATH_OVERLAP', lambda: repair.disjoint(self.source, self.source / 'copy'))
        self.refusal('PATH_TRAVERSAL', lambda: repair.strict_path(self.source / '..' / 'source'))
        os.link(self.source / repair.JOURNAL, self.source / 'linked.dat')
        self.refusal('NONREGULAR_OR_SHARED_FILE', self.inspect)

    def test_plan_stale_candidate_log_digest_toolchain_and_no_effect(self):
        doc = self.plan()
        original = self.hashes(self.source)
        self.refusal('PLAN_UNCONFIRMED', lambda: repair.apply(self.planfile, self.output, 'wrong'))
        with patch.object(repair, 'toolchain', return_value={}):
            self.refusal('TOOLCHAIN_DIFFERS', lambda: repair.apply(self.planfile, self.output, doc['digest']))
        self.jar.write_bytes(b'changed')
        self.refusal('CANDIDATE_CHANGED', lambda: repair.apply(self.planfile, self.output, doc['digest']))
        self.jar.write_bytes(b'pinned-synthetic-candidate')
        self.log.write_text(self.log.read_text() + '\n')
        self.refusal('FENCE_EVIDENCE_CHANGED', lambda: repair.apply(self.planfile, self.output, doc['digest']))
        (self.backup / 'new-file').write_bytes(b'new')
        self.refusal('STALE_PLAN', lambda: repair.apply(self.planfile, self.output, doc['digest']))
        self.assertEqual(original, self.hashes(self.source))
        self.assertFalse(self.output.exists())

    def test_interrupted_write_and_failures_at_every_writer_stage_have_no_effect(self):
        doc = self.plan()
        original = self.hashes(self.source)
        real_read = repair.read_gzip
        cases = [('copy', patch.object(repair, 'copy_world', side_effect=OSError('copy'))),
                 ('fsync', patch.object(repair.os, 'fsync', side_effect=OSError('force'))),
                 ('replace', patch.object(repair.os, 'replace', side_effect=OSError('replace'))),
                 ('interrupt', patch.object(repair.os, 'replace', side_effect=KeyboardInterrupt())),
                 ('rename', patch.object(repair.os, 'rename', side_effect=OSError('rename'))),
                 ('stage-readback', patch.object(repair, 'read_gzip', side_effect=lambda p: (_ for _ in ()).throw(OSError('readback')) if str(p).endswith('.x03-stage') else real_read(p))),
                 ('directory-force', patch.object(repair, 'force_directory', side_effect=OSError('directory force')))]
        for name, mock in cases:
            with self.subTest(stage=name):
                with mock, self.assertRaises((OSError, KeyboardInterrupt)):
                    repair.apply(self.planfile, self.output, doc['digest'])
                self.assertEqual(original, self.hashes(self.source))
                self.assertEqual(original, self.hashes(self.backup))
                self.assertFalse(self.output.exists())
                self.assertEqual([], list(self.base.glob('.*.x03-*')))
                self.assertFalse((self.reports / '.x03-repair.lock').exists())

    def test_census_rejects_skipped_removal_added_file_and_target_changes(self):
        doc = self.plan()
        repair.apply(self.planfile, self.output, doc['digest'])
        (self.output / 'unlisted').write_bytes(b'x')
        self.refusal('EXTRA_FILE_CHANGE', lambda: repair.census(self.planfile, self.output, self.reports / 'bad.json'))
        self.assertFalse((self.reports / 'bad.json').exists())
        (self.output / 'unlisted').unlink()
        shutil.copyfile(self.backup / repair.JOURNAL, self.output / repair.JOURNAL)
        self.refusal('TRANSFER_MISSING_OR_DUPLICATE', lambda: repair.census(self.planfile, self.output, self.reports / 'bad.json'))

    def test_after_start_census_lists_changes_but_rejects_removed_and_migrated_files(self):
        doc = self.plan()
        repair.apply(self.planfile, self.output, doc['digest'])
        (self.output / 'ordinary-tick.txt').write_text('changed')
        result = repair.census(self.planfile, self.output, self.reports / 'census.json', after_start=True)
        self.assertEqual(['ordinary-tick.txt'], result['differences']['added'])
        (self.output / 'session.lock').unlink()
        self.refusal('INPUT_MISSING', lambda: repair.census(self.planfile, self.output, self.reports / 'bad.json', True))

    def test_optional_witness_unknown_valid_invalid_and_invented_zero(self):
        observation = self.inspect()
        witness = self.base / 'witness.json'
        body = dict(version=1, identity=observation['identity'], fluid='minecraft:water', native_tag='absent',
                    before=[5000, 0], after=[4000, 1000])
        witness.write_bytes(repair.canonical(dict(body=body, sha256=n.digest(repair.canonical(body)))))
        self.assertEqual(0, repair.account(witness, observation)['discrepancy'])
        body['before'] = [0, 0]
        witness.write_bytes(repair.canonical(dict(body=body, sha256=n.digest(repair.canonical(body)))))
        self.refusal('WITNESS_INVALID', lambda: repair.account(witness, observation))
        witness.write_text('{"body":{},"sha256":"bad"}')
        self.refusal('WITNESS_INVALID', lambda: repair.account(witness, observation))

    def test_accounting_enabled_copy_preserves_empty_accounts_and_unknown_history(self):
        self.seed(repair.PROFILE_ENABLED)
        doc = self.plan(profile=repair.PROFILE_ENABLED)
        self.assertEqual('UNKNOWN_BASELINE', doc['body']['account']['status'])
        repair.apply(self.planfile, self.output, doc['digest'])
        self.assertEqual((self.backup / repair.LEDGER).read_bytes(), (self.output / repair.LEDGER).read_bytes())

    def test_unrelated_real_codec_root3_accounts_are_preserved_and_orphans_refuse(self):
        self.seed(repair.PROFILE_ENABLED)
        fixture = Path(__file__).parent / 'fixtures/escrow_accounting/cargo-planned.dat'
        self.data['resource_escrow'] = copy.deepcopy(repair.at(n.read_gzip(fixture)[1], 'data'))
        self.data['resource_escrow'].value['ledger_id'] = U(5)
        self.write_roots(); self.sync()
        doc = self.plan(profile=repair.PROFILE_ENABLED)
        repair.apply(self.planfile, self.output, doc['digest'])
        self.assertEqual((self.backup / repair.LEDGER).read_bytes(), (self.output / repair.LEDGER).read_bytes())
        self.data['resource_escrow'].value['records'] = E()
        self.write_roots()
        self.refusal('INVALID_ACCOUNTING_ROOT', lambda: self.inspect(repair.PROFILE_ENABLED))

    def test_coordinate_only_transaction_and_sidecar_claims_refuse(self):
        self.data['rocket_transactions'].value['transactions'] = E(C(
            dimension=S('minecraft:overworld'), minimum=A(60, 99, 60), maximum=A(80, 101, 80)))
        self.write_roots()
        self.refusal('COMPETING_POSITION_AUTHORITY')
        self.data['rocket_transactions'].value['transactions'] = E(C(
            dimension=S('example:disjoint'), minimum=A(60, 99, 60), maximum=A(80, 101, 80)))
        self.write_roots()
        self.inspect()

    def test_unrelated_transfer_is_preserved_in_original_entry_order(self):
        other = copy.deepcopy(self.record)
        def replace(tag):
            if tag.type == n.INTS and len(tag.value) == 4:
                tag.value[-1] += 100
            elif tag.type == n.STRING and len(tag.value) == 36:
                tag.value = str(uuid.UUID(int=999)).encode()
            elif tag.type == n.INTS and len(tag.value) == 3:
                tag.value[0] += 2048
            elif tag.type == n.COMPOUND:
                for child in tag.value.values(): replace(child)
            elif tag.type == n.LIST:
                for child in tag.value[1]: replace(child)
        replace(other)
        self.data['rocket_transfers'].value['transfers'] = E(other, self.record)
        self.write_roots(); self.sync()
        doc = self.plan()
        repair.apply(self.planfile, self.output, doc['digest'])
        result = repair.items(repair.read_gzip(self.output / repair.JOURNAL)[1], 'data', 'transfers')
        self.assertEqual(1, len(result))
        self.assertEqual(repair.typed(other), repair.typed(result[0]))

    def test_input_lock_close_failure_after_publish_removes_only_own_output(self):
        from contextlib import contextmanager
        doc = self.plan()
        original_lock = repair.world_lock
        @contextmanager
        def failed_close(world):
            with original_lock(world) as lock:
                yield lock
            if world == self.source.resolve():
                raise OSError('input lock close')
        with patch.object(repair, 'world_lock', failed_close), self.assertRaises(OSError):
            repair.apply(self.planfile, self.output, doc['digest'])
        self.assertFalse(self.output.exists())
        self.assertEqual([], list(self.base.glob('.*.x03-*')))

    def test_forged_plan_account_zero_and_write_list_refuse_even_with_recomputed_digest(self):
        original = self.plan()
        for field, change, reason in [('account', {'status': 'KNOWN', 'discrepancy': 0}, 'PLAN_ACCOUNT_DIFFERS'),
                                      ('writes', ['data/other.dat'], 'PLAN_DIFFERS'),
                                      ('record_typed_hex', '00', 'PLAN_DIFFERS')]:
            with self.subTest(field=field):
                document = copy.deepcopy(original)
                document['body'][field] = change
                document['digest'] = n.digest(repair.canonical(document['body']))
                self.planfile.write_bytes(repair.canonical(document))
                self.refusal(reason, lambda: repair.apply(self.planfile, self.output, document['digest']))
                self.assertFalse(self.output.exists())

    def test_plan_cannot_overwrite_output_and_backup_mismatch_has_no_effect(self):
        doc = self.plan()
        self.output.mkdir()
        (self.output / 'foreign').write_bytes(b'owner data')
        self.refusal('OUTPUT_EXISTS', lambda: repair.apply(self.planfile, self.output, doc['digest']))
        self.assertEqual(b'owner data', (self.output / 'foreign').read_bytes())
        self.planfile.unlink()
        (self.backup / 'extra').write_bytes(b'not in source')
        self.refusal('BACKUP_UNVERIFIED', self.plan)
        self.assertFalse(self.planfile.exists())

    def test_writer_final_readback_and_close_error_remove_unpublished_copy(self):
        doc = self.plan()
        read = repair.read_gzip
        def failed_read(path):
            if path.name.endswith('.dat') and any('.x03-' in p for p in path.parts):
                raise OSError('final target readback')
            return read(path)
        with patch.object(repair, 'read_gzip', side_effect=failed_read), self.assertRaises(OSError):
            repair.apply(self.planfile, self.output, doc['digest'])
        self.assertFalse(self.output.exists())
        self.assertEqual([], list(self.base.glob('.*.x03-*')))
        opener = Path.open
        class CloseFailure:
            def __init__(self, stream):
                self.stream = stream
            def __enter__(self):
                return self.stream
            def __exit__(self, *exc):
                self.stream.close()
                raise OSError('staged close')
        def bad_close(path, *args, **kwargs):
            stream = opener(path, *args, **kwargs)
            return CloseFailure(stream) if path.name.endswith('.x03-stage') else stream
        with patch.object(Path, 'open', bad_close), self.assertRaises(OSError):
            repair.apply(self.planfile, self.output, doc['digest'])
        self.assertFalse(self.output.exists())
        self.assertEqual([], list(self.base.glob('.*.x03-*')))

    def test_census_migration_new_backup_files_and_changed_managed_payload_refuse(self):
        doc = self.plan()
        repair.apply(self.planfile, self.output, doc['digest'])
        path = self.output / 'data/advancedrocketrycommunity_celestial.dat'
        name, root = repair.read_gzip(path)
        raw = path.read_bytes()
        repair.at(root, 'data').value['schema_version'] = I(3)
        n.write_gzip(path, name, root)
        self.refusal('MIGRATION_DURING_VERIFICATION', lambda: repair.census(self.planfile, self.output, self.reports / 'bad.json', True))
        path.write_bytes(raw)
        repair.at(root, 'data').value['schema_version'] = I(2)
        repair.at(root, 'data').value['extension'] = S('unapproved')
        n.write_gzip(path, name, root)
        self.refusal('MANAGED_ROOT_CHANGED', lambda: repair.census(self.planfile, self.output, self.reports / 'bad.json', True))
        path.write_bytes(raw)
        directory = self.output / 'advancedrocketrycommunity-backups'
        directory.mkdir()
        (directory / 'migration.dat').write_bytes(b'evidence')
        self.refusal('MIGRATION_DURING_VERIFICATION', lambda: repair.census(self.planfile, self.output, self.reports / 'bad.json', True))

    def test_census_target_unknown_leaf_and_forced_chunks_changed_refuse(self):
        doc = self.plan()
        repair.apply(self.planfile, self.output, doc['digest'])
        entity = copy.deepcopy(self.entity)
        entity.value['new_field'] = I(1)
        region(self.output / 'entities', {(4, 4): C(Entities=E(entity))})
        self.refusal('TARGET_CHANGED:entity', lambda: repair.census(self.planfile, self.output, self.reports / 'bad.json', True))
        region(self.output / 'entities', {(4, 4): C(Entities=E(self.entity))})
        forced = self.output / 'data/chunks.dat'
        n.write_gzip(forced, b'', C(data=C(Forced=n.Tag(n.LONGS, []))))
        repair.census(self.planfile, self.output, self.reports / 'good.json', True)
        n.write_gzip(forced, b'', C(data=C(Forced=n.Tag(n.LONGS, [1]))))
        self.refusal('FORCED_CHUNKS_CHANGED', lambda: repair.census(self.planfile, self.output, self.reports / 'bad.json', True))

    def test_passenger_depth_missing_adapter_cargo_and_typed_amount_refusals(self):
        nested = self.entity
        for _ in range(9):
            nested = C(id=S('minecraft:pig'), Passengers=E(nested))
        self.write_entities([nested])
        self.refusal('PASSENGER_DEPTH')
        self.write_entities([self.entity])
        for key, tag, reason in [('adapter', S('example:unknown'), 'MISSING_ADAPTER')]:
            bad = copy.deepcopy(self.snapshot)
            repair.at(bad, 'relative_blocks').value[1][0].value['block_entity'].value[key] = tag
            self.refusal(reason, lambda: repair.cargo(bad, 5000))
        bad = copy.deepcopy(self.snapshot)
        repair.cargo(bad, 5000).value['Amount'] = L(5000)
        self.refusal('INVALID_NBT_TYPE', lambda: repair.cargo(bad, 5000))

    def test_region_palette_wide_and_constant_containers(self):
        for size in (1, 2, 17, 33, 257):
            with self.subTest(size=size):
                bits = max(4, (size - 1).bit_length())
                data = [0] * ((4095 // (64 // bits)) + 1)
                states = C(palette=E(*(C(Name=S('example:block' + str(i))) for i in range(size))))
                if size > 1:
                    states.value['data'] = n.Tag(n.LONGS, data)
                chunk = C(sections=E(C(Y=n.Tag(n.BYTE, 6), block_states=states)))
                self.assertEqual('example:block0', repair.block_state(chunk, 10, 100, 9))

    def test_cli_summary_refusal_is_bounded_and_has_no_force_option(self):
        result = subprocess.run([sys.executable, '-B', str(Path(repair.__file__)), 'apply', '--plan', str(self.planfile),
                                 '--output', str(self.output), '--confirm', 'wrong'], capture_output=True, text=True, timeout=20)
        self.assertEqual(2, result.returncode)
        self.assertEqual('REFUSED', result.stdout.splitlines()[0])
        self.assertLessEqual(len(result.stdout.splitlines()), 5)
        self.assertTrue(all(len(line) <= 160 for line in result.stdout.splitlines()[1:]))

    def test_ambiguous_json_and_report_publication_failure_preserve_existing_reports(self):
        path = self.reports / 'invalid.json'
        for raw in ('{"a":1,"a":2}', '{"a":NaN}'):
            path.write_text(raw)
            with self.assertRaises(repair.Refusal):
                repair.read_json(path)
        target = self.reports / 'new.json'
        with patch.object(repair.os, 'link', side_effect=OSError('publication')), self.assertRaises(OSError):
            repair.publish_json(target, dict(version=1))
        self.assertFalse(target.exists())
        self.assertEqual([], list(self.reports.glob('.*.x03-*')))
        target.write_bytes(b'original report')
        self.refusal('OUTPUT_EXISTS', lambda: repair.publish_json(target, dict(version=1)))
        self.assertEqual(b'original report', target.read_bytes())

    def test_all_noncurrent_roots_are_reported_together(self):
        for key in repair.CURRENT_ROOTS:
            self.data[key].value['schema_version'] = I(99)
        self.write_roots()
        with self.assertRaises(repair.Refusal) as result:
            self.inspect()
        self.assertEqual(len(repair.CURRENT_ROOTS), len(result.exception.reasons))

    def test_root_table_is_checked_against_current_java_source(self):
        root = Path(__file__).resolve().parents[1] / 'src/main/java/io/github/sunthemoon/advancedrocketrycommunity'
        text = (root / 'persistence/migration/ManagedSavedDataType.java').read_text(encoding='utf-8')
        names = set(re.findall(r'"advancedrocketrycommunity_([a-z_]+)"', text))
        self.assertEqual(set(repair.CURRENT_ROOTS), names)
        self.assertIn('EscrowAccountingFormat.ENABLED ? 3 : 2', text)
        switch = text.split('public int currentSchemaVersion()')[1].split('public String formatEpoch()')[0]
        self.assertRegex(switch, r'case CLASSIC_PROGRESSION, ROCKET_REJOINS -> 1;')
        self.assertIn('default -> SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION;', switch)
        mappings = {'STATIONS': 'StationLimits.REGISTRY_SCHEMA_VERSION',
                    'SATELLITE_MISSIONS': 'SatelliteLimits.REGISTRY_SCHEMA_VERSION',
                    'ENDGAME': 'EndgameLimits.ROOT_SCHEMA_VERSION',
                    'ROCKET_TRANSFERS': 'FIRST_MOON_LANDING_JOURNAL_SCHEMA'}
        for name, expression in mappings.items():
            self.assertIn('case ' + name + ' -> ' + expression + ';', switch)
        # Pin every required tag as well as each schema/epoch. New root fields
        # must not be silently accepted by an outdated offline parser.
        declarations = list(re.finditer(r'^    ([A-Z_]+)\(', text, re.MULTILINE))
        kinds = {'LIST': n.LIST, 'COMPOUND': n.COMPOUND, 'INT_ARRAY': n.INTS, 'LONG_ARRAY': n.LONGS, 'LONG': n.LONG}
        for index, declaration in enumerate(declarations):
            end = declarations[index + 1].start() if index + 1 < len(declarations) else text.index('private static final long FILE_OVERHEAD_BYTES')
            block = text[declaration.start():end]
            key = re.search(r'"advancedrocketrycommunity_([a-z_]+)"', block)[1]
            for field, kind in re.findall(r'new RequiredTag\("([a-z_]+)", Tag.TAG_([A-Z_]+)\)', block):
                self.assertEqual(kinds[kind], repair.ROOT_REQUIRED[key][field])
        constants = [('station/model/StationLimits.java', 'CONTROLS_REGISTRY_SCHEMA_VERSION', 'stations'),
                     ('satellite/model/SatelliteLimits.java', 'GROUND_REGISTRY_SCHEMA_VERSION', 'satellite_missions'),
                     ('endgame/model/EndgameLimits.java', 'ROOT_SCHEMA_VERSION', 'endgame'),
                     ('resourceescrow/BayReceiptCodec.java', 'SCHEMA', 'bay_receipts'),
                     ('persistence/migration/SavedDataSchemaMigrator.java', 'CURRENT_SCHEMA_VERSION', 'celestial'),
                     ('persistence/migration/ManagedSavedDataType.java', 'FIRST_MOON_LANDING_JOURNAL_SCHEMA', 'rocket_transfers')]
        for file, name, key in constants:
            source = (root / file).read_text(encoding='utf-8')
            actual = int(re.search(r'\b' + name + r'\s*=\s*(\d+)', source)[1])
            self.assertEqual(actual, repair.CURRENT_ROOTS[key][0])
        for schema, epoch in repair.CURRENT_ROOTS.values():
            self.assertIn('"' + epoch + '"', text + ''.join((root / f).read_text(encoding='utf-8') for f, _, _ in constants))


if __name__ == '__main__':
    unittest.main()
