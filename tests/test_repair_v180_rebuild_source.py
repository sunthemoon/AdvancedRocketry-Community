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


def descriptor():
    fuels = {role: n.Tag(n.LIST, (n.STRING, [S('advancedrocketrycommunity:' + fluid)])) for role, fluid in (
        ('MONO', 'rocket_fuel'), ('BIPROPELLANT_FUEL', 'hydrogen'), ('OXIDIZER', 'oxygen'), ('WORKING_FLUID', 'hydrogen'))}
    return C(schema=I(1), mode=S('MONO'), primary=L(2000), oxidizer=L(0), numerator=L(1), denominator=L(1),
             thrust_units=L(1 << 56), capacity_units=L(1 << 56), core_units=L(1 << 56),
             gravity=n.Tag(n.BYTE, 1), required=n.Tag(n.BYTE, 1), hand=n.Tag(n.BYTE, 1), fuels=C(**fuels))


def target(body='advancedrocketrycommunity:earth'):
    return C(schema_version=I(1), type=S('advancedrocketrycommunity:body_surface'), body_id=S(body))


def default(kind):
    return n.Tag(kind, {n.LIST: (0, []), n.COMPOUND: {}, n.INTS: [0, 0, 0, 99], n.LONGS: [], n.LONG: 0, n.INT: 0}[kind])


class RepairFixture(unittest.TestCase):
    def setUp(self):
        enough = patch.object(repair.shutil, 'disk_usage', return_value=shutil._ntuple_diskusage(2**50, 0, 2**50))
        enough.start()
        self.addCleanup(enough.stop)
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
                          bounding_box=A(0, 0, 0, 0, 0, 0),
                          block_palette=E(C(id=S('advancedrocketrycommunity:pressurized_tank'), properties=C())),
                          relative_blocks=E(C(position=A(0, 0, 0), palette=I(0), block_entity=C(
                              adapter=S('advancedrocketrycommunity:pressurized_tank_v1'),
                              data=C(tank=C(schema=I(1), fluid=C(FluidName=S('minecraft:water'), Amount=I(5000))))))),
                          passenger_anchors=n.Tag(n.LIST, (0, [])),
                          mass_inputs=C(block_count=I(1), mass=L(1), thrust=L(1), fuel_capacity=L(2000),
                                        engine_count=I(1), seat_count=I(0), guidance_count=I(1), block_entity_count=I(1)),
                          created_at_game_time=L(100), content_hash=S('a' * 64), propulsion=descriptor())
        self.destination = copy.deepcopy(self.snapshot)
        self.destination.value['snapshot_id'] = S(str(uuid.UUID(int=7)))
        self.destination.value['source_origin'] = A(88, 100, 64)
        self.fuel = C(capacity=L(2000), amount=L(1000), committed_debits=E(), propulsion=C(schema=I(1), descriptor=descriptor(),
                      primary=C(branch=S('FLUID'), amount=L(1000), fluid=S('advancedrocketrycommunity:rocket_fuel')),
                      oxidizer=C(branch=S('EMPTY'), amount=L(0)), revision=L(1)))
        self.flight = C(schema_version=I(2), state=S('FUELED'), logical_rocket_id=U(2), current_dimension=S('minecraft:overworld'),
                        current_origin=A(68, 100, 64), passengers=C(seat_capacity=I(0), assignments=E()), fuel=copy.deepcopy(self.fuel),
                        current_body=S('advancedrocketrycommunity:earth'), state_started_game_time=L(200), current_target=target())
        historical = copy.deepcopy(self.flight)
        historical.value.update(state=S('TRANSIT'), active_transfer_id=U(1), plan=C(schema_version=I(4), request_id=U(9),
            source_body=S('advancedrocketrycommunity:earth'), destination_body=S('advancedrocketrycommunity:moon'),
            source_dimension=S('minecraft:overworld'), destination_dimension=S('minecraft:overworld'), required_fuel=L(1000),
            created_at_game_time=L(150), destination_target=target('advancedrocketrycommunity:moon'),
            propulsion_debit=C(schema=I(1), primary=L(1000), oxidizer=L(0), revision=L(0), resources=S('d' * 64), catalog=S('e' * 64))))
        arrival = copy.deepcopy(historical)
        arrival.value.update(state=S('DESCENT'), current_origin=A(88, 100, 64),
                             current_body=S('advancedrocketrycommunity:moon'), current_target=target('advancedrocketrycommunity:moon'))
        self.record = C(schema_version=I(2), transfer_id=U(1), phase=S('PREPARED'), logical_rocket_id=U(2),
                        source_entity_id=U(3), owner_id=U(4), source_snapshot=copy.deepcopy(self.snapshot),
                        destination_snapshot=self.destination, source_flight=historical,
                        destination_flight=arrival, checksum=S('c' * 64),
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
        for root, field in (('rocket_transfers', 'classic_first_moon_landing'), ('stations', 'classic_first_warp')):
            self.data[root].value[field] = C(schema_version=I(1), state=S('EMPTY'))
        self.data['stations'].value['orbital_clock'] = C(schema_version=I(1), logical_tick=L(0))
        self.data['satellite_missions'].value.update(clock=C(logical_game_time=L(0), last_observed_game_time=L(0)), save_epoch=L(1))
        self.data['endgame'].value['save_epoch'] = L(1)
        for field in ('first_moon_landing', 'first_station_warp'):
            self.data['classic_progression'].value[field] = C(occurred=n.Tag(n.BYTE, 0))
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
                         b'root', C(DataVersion=I(3465), opaque=C(z=n.Tag(n.BYTE, 1), a=n.Tag(n.SHORT, 2)), data=data))

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


class RepairTest(RepairFixture):
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
                    bad.value['extension'] = C(x=I(1))
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

    def test_every_competing_authority_and_unvalidated_disjoint_entries_refuse(self):
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
        self.refusal('UNVALIDATED_AUTHORITY:resource_escrow/floors', self.plan)
        self.assertFalse(self.output.exists())
        self.assertEqual(self.hashes(self.source), self.hashes(self.backup))

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

    def test_unrelated_real_codec_root3_accounts_and_orphans_refuse(self):
        self.seed(repair.PROFILE_ENABLED)
        fixture = Path(__file__).parent / 'fixtures/escrow_accounting/cargo-planned.dat'
        self.data['resource_escrow'] = copy.deepcopy(repair.at(n.read_gzip(fixture)[1], 'data'))
        self.data['resource_escrow'].value['ledger_id'] = U(5)
        self.write_roots(); self.sync()
        self.refusal('UNVALIDATED_AUTHORITY:resource_escrow/records', lambda: self.plan(profile=repair.PROFILE_ENABLED))
        self.assertFalse(self.output.exists())
        self.assertEqual(self.hashes(self.source), self.hashes(self.backup))
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
        self.refusal('UNVALIDATED_AUTHORITY:rocket_transactions/transactions')

    def test_unvalidated_unrelated_transfer_refuses_without_deleting_either_record(self):
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
        self.refusal('UNVALIDATED_AUTHORITY:rocket_transfers/transfers', self.plan)
        result = repair.items(repair.read_gzip(self.source / repair.JOURNAL)[1], 'data', 'transfers')
        self.assertEqual(2, len(result))
        self.assertEqual(repair.typed(other), repair.typed(result[0]))
        self.assertFalse(self.output.exists())
        self.assertEqual(self.hashes(self.source), self.hashes(self.backup))

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


class RevisionTest(RepairFixture):
    def assert_no_effect_refusal(self):
        self.sync()
        before = self.hashes(self.source)
        with self.assertRaises(repair.Refusal):
            self.plan()
        self.assertEqual(before, self.hashes(self.source))
        self.assertEqual(before, self.hashes(self.backup))
        self.assertEqual([], list(self.reports.iterdir()))
        self.assertFalse(self.output.exists())

    def test_every_nonempty_uninterpreted_collection_refuses_at_current_root_stamps(self):
        for root, fields in repair.ROOT_REQUIRED.items():
            for field, kind in fields.items():
                if kind != n.LIST or (root, field) == ('rocket_transfers', 'transfers'):
                    continue
                for entries in (E(C(unrecognized=S('entry'))), E(C(schema_version=I(999))),
                                E(C(id=U(900)), C(id=U(900))), n.Tag(n.LIST, (n.INT, [I(1)]))):
                    with self.subTest(root=root, field=field, entries=entries):
                        self.seed()
                        self.data[root].value[field] = entries
                        self.write_roots()
                        self.assert_no_effect_refusal()
        for entries in (E(C(unrecognized=S('entry'))), E(C(schema_version=I(999))), E(C(id=U(900)), C(id=U(900)))):
            self.seed(repair.PROFILE_ENABLED)
            self.data['resource_escrow'].value['fluid_accounts'] = entries
            self.write_roots(); self.sync()
            with self.assertRaises(repair.Refusal):
                self.plan(profile=repair.PROFILE_ENABLED)
            self.assertFalse(self.output.exists())
        for extra in (C(unrecognized=S('entry')), E(C(opaque=L(1)))):
            self.seed()
            self.data['rocket_transactions'].value['unknown_authority'] = extra
            self.write_roots()
            self.assert_no_effect_refusal()

    def transaction(self, x):
        # RocketTransactionCodec.encodeEntry framing, including the nested snapshot.
        # Hash acceptance is deliberately NOT claimed by this synthetic control.
        snapshot = copy.deepcopy(self.snapshot)
        snapshot.value.update(snapshot_id=S(str(uuid.UUID(int=606))), source_origin=A(x, 100, 64))
        return C(transaction_id=U(901), type=S('ASSEMBLY'), phase=S('LOCKED'), snapshot_id=U(606),
                 content_hash=copy.deepcopy(snapshot.value['content_hash']), dimension=S('minecraft:overworld'),
                 minimum=A(x, 100, 64), maximum=A(x, 100, 64), progress=I(0), owner_id=U(904), snapshot=snapshot)

    def test_native_transaction_framing_overlap_disjoint_malformed_unsupported_and_duplicate_refuse(self):
        baseline = self.transaction(160)
        unsupported = copy.deepcopy(baseline)
        unsupported.value['type'] = S('FUTURE_OPERATION')
        bad_snapshot = copy.deepcopy(baseline)
        repair.at(bad_snapshot, 'snapshot').value['schema_version'] = I(999)
        wrong_position = copy.deepcopy(baseline)
        wrong_position.value['minimum'] = A()
        for entries in (E(self.transaction(68)), E(self.transaction(88)), E(baseline), E(unsupported),
                        E(bad_snapshot), E(wrong_position), E(baseline, copy.deepcopy(baseline)), E(C(unrecognized=S('entry')))):
            with self.subTest(entries=entries):
                self.seed()
                self.data['rocket_transactions'].value['transactions'] = entries
                self.write_roots()
                self.assert_no_effect_refusal()

    def test_apply_rechecks_authority_even_with_rebound_manifest_and_digest(self):
        document = self.plan()
        self.data['rocket_transactions'].value['transactions'] = E(C(unrecognized=S('entry')))
        self.write_roots(); self.sync()
        with repair.world_lock(self.source) as lock:
            document['body']['manifest'] = repair.manifest(self.source, lock)
        document['body']['manifest_sha256'] = n.digest(repair.canonical(document['body']['manifest']))
        document['digest'] = n.digest(repair.canonical(document['body']))
        self.planfile.write_bytes(repair.canonical(document))
        before = self.hashes(self.source)
        self.refusal('UNVALIDATED_AUTHORITY:rocket_transactions/transactions',
                     lambda: repair.apply(self.planfile, self.output, document['digest']))
        self.assertEqual(before, self.hashes(self.source))
        self.assertEqual(before, self.hashes(self.backup))
        self.assertFalse(self.output.exists())
        self.assertEqual([], list(self.base.glob('.*.x03-*')))

    def nested_versions(self):
        snapshots = [repair.at(self.entity, 'RocketEntityData', 'snapshot'), self.record.value['source_snapshot'],
                     self.record.value['destination_snapshot']]
        flights = [repair.at(self.entity, 'RocketEntityData', 'flight_data'), self.record.value['source_flight'],
                   self.record.value['destination_flight']]
        result = [(s, 'schema_version', 2) for s in snapshots + flights]
        result += [(s.value['propulsion'], 'schema', 1) for s in snapshots]
        result += [(repair.items(s, 'relative_blocks')[0].value['block_entity'].value['data'].value['tank'], 'schema', 1) for s in snapshots]
        for flight in flights:
            result += [(repair.at(flight, 'fuel', 'propulsion'), 'schema', 1),
                       (repair.at(flight, 'fuel', 'propulsion', 'descriptor'), 'schema', 1),
                       (flight.value['current_target'], 'schema_version', 1)]
            if 'plan' in flight.value:
                result += [(flight.value['plan'], 'schema_version', 4),
                           (repair.at(flight, 'plan', 'propulsion_debit'), 'schema', 1),
                           (repair.at(flight, 'plan', 'destination_target'), 'schema_version', 1)]
        result += [(repair.at(self.entity, 'RocketEntityData', 'resource_escrow'), 'schema_version', 1),
                   (repair.at(self.device, 'arce_rocket_fluid_cargo'), 'schema_version', 1),
                   (repair.at(self.device, 'arce_rocket_fluid_cargo', 'resource_escrow'), 'schema_version', 1),
                   (repair.at(self.device, 'arce_rocket_fluid_cargo', 'binding'), 'schema_version', 1)]
        return result

    def test_every_nested_schema_missing_wrongly_typed_old_and_future_refuses_without_effect(self):
        baseline = copy.deepcopy((self.entity, self.record, self.device))
        count = len(self.nested_versions())
        for index in range(count):
            for case in ('missing', 'wrong-type', 'old', 'future'):
                with self.subTest(object=index, case=case):
                    self.entity, self.record, self.device = copy.deepcopy(baseline)
                    obj, field, supported = self.nested_versions()[index]
                    if case == 'missing': del obj.value[field]
                    else: obj.value[field] = L(supported) if case == 'wrong-type' else I(supported - 1 if case == 'old' else 999)
                    self.seed()
                    self.assert_no_effect_refusal()

    def test_matching_future_snapshot_and_current_flight_only_cannot_bypass_equality(self):
        for bad in (None, L(2), I(1), I(999)):
            with self.subTest(bad=bad):
                before, current = copy.deepcopy((self.snapshot, repair.at(self.entity, 'RocketEntityData', 'snapshot')))
                for snapshot in (before, current):
                    if bad is None: del snapshot.value['schema_version']
                    else: snapshot.value['schema_version'] = bad
                with self.assertRaises(repair.Refusal):
                    repair.snapshot_rule(before, current)
        journal = (self.source / repair.JOURNAL).read_bytes()
        repair.at(self.entity, 'RocketEntityData', 'flight_data').value['schema_version'] = I(999)
        self.write_entities([self.entity])
        self.assertEqual(journal, (self.source / repair.JOURNAL).read_bytes())
        self.assert_no_effect_refusal()

    def test_equal_unsupported_fuel_and_cargo_payload_schemas_refuse(self):
        baseline = copy.deepcopy((self.entity, self.record))
        for branch in ('native-fuel', 'fuel-descriptor', 'snapshot-descriptor', 'cargo-tank'):
            for bad in (None, L(1), I(0), I(999)):
                with self.subTest(branch=branch, bad=bad):
                    self.entity, self.record = copy.deepcopy(baseline)
                    snapshots = [repair.at(self.entity, 'RocketEntityData', 'snapshot'),
                                 self.record.value['source_snapshot'], self.record.value['destination_snapshot']]
                    flights = [repair.at(self.entity, 'RocketEntityData', 'flight_data'),
                               self.record.value['source_flight'], self.record.value['destination_flight']]
                    for snapshot, flight in zip(snapshots, flights):
                        if branch == 'native-fuel': obj = repair.at(flight, 'fuel', 'propulsion')
                        elif branch == 'fuel-descriptor': obj = repair.at(flight, 'fuel', 'propulsion', 'descriptor')
                        elif branch == 'snapshot-descriptor': obj = snapshot.value['propulsion']
                        else: obj = repair.items(snapshot, 'relative_blocks')[0].value['block_entity'].value['data'].value['tank']
                        if bad is None: del obj.value['schema']
                        else: obj.value['schema'] = bad
                    self.seed()
                    self.assert_no_effect_refusal()

    def test_typed_unoccupied_seat_anchors_are_not_passenger_assignments(self):
        for snapshot in (repair.at(self.entity, 'RocketEntityData', 'snapshot'), self.record.value['source_snapshot'],
                         self.record.value['destination_snapshot']):
            snapshot.value['passenger_anchors'] = n.Tag(n.LIST, (n.INTS, [A(0, 0, 0)]))
            snapshot.value['mass_inputs'].value['seat_count'] = I(1)
        for flight in (repair.at(self.entity, 'RocketEntityData', 'flight_data'), self.record.value['source_flight'],
                       self.record.value['destination_flight']):
            flight.value['passengers'].value['seat_capacity'] = I(1)
        self.seed()
        self.inspect()

    def test_required_nested_framing_and_duplicate_debits_refuse(self):
        baseline = copy.deepcopy((self.entity, self.record))
        for case in ('missing-capacity', 'wrong-amount', 'missing-debits', 'duplicate-debits', 'wrong-bank',
                     'wrong-bank-branch', 'missing-revision', 'wrong-switch', 'missing-mass', 'wrong-palette',
                     'wrong-tank-data', 'wrong-fluid', 'wrong-seat-capacity'):
            with self.subTest(case=case):
                self.entity, self.record = copy.deepcopy(baseline)
                snapshot = repair.at(self.entity, 'RocketEntityData', 'snapshot')
                flight = repair.at(self.entity, 'RocketEntityData', 'flight_data')
                fuel = flight.value['fuel']
                if case == 'missing-capacity': del fuel.value['capacity']
                elif case == 'wrong-amount': fuel.value['amount'] = I(1000)
                elif case == 'missing-debits': del fuel.value['committed_debits']
                elif case == 'duplicate-debits': fuel.value['committed_debits'] = E(C(transaction_id=U(55)), C(transaction_id=U(55)))
                elif case == 'wrong-bank': fuel.value['propulsion'].value['oxidizer'] = E()
                elif case == 'wrong-bank-branch': repair.at(fuel, 'propulsion', 'primary').value['branch'] = I(1)
                elif case == 'missing-revision': del fuel.value['propulsion'].value['revision']
                elif case == 'wrong-switch': snapshot.value['propulsion'].value['hand'] = I(1)
                elif case == 'missing-mass': del snapshot.value['mass_inputs'].value['mass']
                elif case == 'wrong-palette': repair.items(snapshot, 'relative_blocks')[0].value['palette'] = L(0)
                elif case == 'wrong-tank-data': repair.items(snapshot, 'relative_blocks')[0].value['block_entity'].value['data'] = E()
                elif case == 'wrong-fluid': repair.cargo(snapshot, 4000).value['Amount'] = L(4000)
                else: flight.value['passengers'].value['seat_capacity'] = L(0)
                if case in ('missing-capacity', 'wrong-amount', 'missing-debits', 'duplicate-debits', 'wrong-bank',
                            'wrong-bank-branch', 'missing-revision'):
                    # Do not let the old byte-equality check mask missing fuel validation.
                    self.record.value['source_flight'].value['fuel'] = copy.deepcopy(fuel)
                if case in ('wrong-switch', 'missing-mass', 'wrong-palette'):
                    before = copy.deepcopy(snapshot)
                    repair.cargo(before, 4000).value['Amount'] = I(5000)
                    before.value['content_hash'] = S('a' * 64)
                    self.record.value['source_snapshot'] = before
                self.seed()
                self.assert_no_effect_refusal()

    def cli_plan(self):
        # Unit subprocesses replace only the environmental space measurement,
        # not eligibility, parsing, locking, or the refusal boundary.
        bootstrap = ('import sys,shutil;sys.path.insert(0,sys.argv.pop(1));'
                     'import repair_v180_rebuild_source as r;'
                     'r.shutil.disk_usage=lambda p:shutil._ntuple_diskusage(2**50,0,2**50);'
                     'sys.exit(r.main())')
        return subprocess.run([sys.executable, '-B', '-c', bootstrap, str(Path(repair.__file__).parent), 'plan',
                               '--source', str(self.source), '--backup', str(self.backup), '--candidate', str(self.jar),
                               '--log', str(self.log), '--report', str(self.planfile), '--transfer', self.transfer,
                               '--profile', repair.PROFILE_DISABLED, '--operator', 'test', '--reason', 'synthetic control'],
                              capture_output=True, text=True, timeout=20)

    def test_malformed_vectors_and_containers_have_bounded_cli_refusals_and_no_effect(self):
        baseline = copy.deepcopy((self.entity, self.record))
        for shape in ('empty-origin', 'short-origin', 'wrong-origin', 'short-pos', 'wrong-pos', 'short-motion',
                      'wrong-motion', 'nan-motion', 'short-flight-origin', 'wrong-flight-origin', 'wrong-flight',
                      'wrong-block-list', 'wrong-block', 'wrong-passengers'):
            with self.subTest(shape=shape):
                self.entity, self.record = copy.deepcopy(baseline)
                current = repair.at(self.entity, 'RocketEntityData', 'snapshot')
                if shape in ('empty-origin', 'short-origin', 'wrong-origin'):
                    bad = E() if shape == 'wrong-origin' else A(*(() if shape == 'empty-origin' else (68, 100)))
                    current.value['source_origin'] = copy.deepcopy(bad)
                    self.record.value['source_snapshot'].value['source_origin'] = bad
                elif shape == 'short-pos': self.entity.value['Pos'] = vector(68.5)
                elif shape == 'wrong-pos': self.entity.value['Pos'] = A(68, 100, 64)
                elif shape == 'short-motion': self.entity.value['Motion'] = vector(0, 0)
                elif shape == 'wrong-motion': self.entity.value['Motion'] = A(0, 0, 0)
                elif shape == 'nan-motion': self.entity.value['Motion'] = vector(0, float('nan'), 0)
                elif shape in ('short-flight-origin', 'wrong-flight-origin'):
                    repair.at(self.entity, 'RocketEntityData', 'flight_data').value['current_origin'] = A(68) if shape == 'short-flight-origin' else C()
                elif shape == 'wrong-flight': self.entity.value['RocketEntityData'].value['flight_data'] = E()
                elif shape == 'wrong-block-list': self.record.value['source_snapshot'].value['relative_blocks'] = C()
                elif shape == 'wrong-block': self.record.value['source_snapshot'].value['relative_blocks'] = n.Tag(n.LIST, (n.INT, [I(1)]))
                else: repair.at(self.entity, 'RocketEntityData', 'flight_data').value['passengers'] = I(0)
                self.seed()
                before = self.hashes(self.source)
                result = self.cli_plan()
                self.assertEqual(2, result.returncode, result.stderr)
                self.assertTrue(result.stdout.startswith('REFUSED\n'), result.stdout)
                self.assertLessEqual(len(result.stdout.splitlines()), 5)
                self.assertTrue(all(len(s) <= 160 for s in result.stdout.splitlines()[1:]))
                self.assertNotIn('Traceback', result.stderr)
                self.assertEqual('', result.stderr)
                self.assertEqual(before, self.hashes(self.source))
                self.assertEqual(before, self.hashes(self.backup))
                self.assertEqual([], list(self.reports.iterdir()))
                self.assertFalse(self.output.exists())

    def disjoint_rocket(self, x=88):
        entity = copy.deepcopy(self.entity)
        entity.value['UUID'] = U(300)
        entity.value['Pos'] = vector(x + .5, 100, 64.5)
        data = repair.at(entity, 'RocketEntityData')
        data.value['assembly_transaction_id'] = U(200)
        data.value['owner_id'] = U(400)
        repair.at(data, 'snapshot').value['snapshot_id'] = S(str(uuid.UUID(int=600)))
        repair.at(data, 'snapshot').value['source_origin'] = A(x, 100, 64)
        repair.at(data, 'flight_data').value['logical_rocket_id'] = U(200)
        repair.at(data, 'flight_data').value['current_origin'] = A(x, 100, 64)
        return entity

    def test_disjoint_identity_rocket_occupancy_across_slots_levels_and_passengers(self):
        for placement in ('source', 'destination', 'slot', 'level', 'passenger'):
            with self.subTest(placement=placement):
                self.seed()
                other = self.disjoint_rocket(68 if placement == 'source' else 88)
                if placement == 'slot':
                    region(self.source / 'entities', {(4, 4): C(Entities=E(self.entity)), (5, 4): C(Entities=E(other))})
                elif placement == 'level':
                    repair.at(self.data['rocket_transfers'], 'transfers').value[1][0].value['destination_snapshot'].value['source_dimension'] = S('example:other')
                    self.write_roots()
                    self.write_entities([other], 'dimensions/example/other')
                    region(self.source / 'dimensions/example/other/region', {(5, 4): C(sections=E(section(6, ['minecraft:air'], {})), block_entities=E())})
                elif placement == 'passenger': self.write_entities([self.entity, C(id=S('minecraft:pig'), Passengers=E(other))])
                else: self.write_entities([self.entity, other])
                self.refusal('ADDITIONAL_ROCKET_IN_FOOTPRINT')
        self.seed()
        self.write_entities([self.entity, self.disjoint_rocket(160)])
        self.inspect()  # A genuinely disjoint physical rocket is still allowed.
        other = self.disjoint_rocket()
        repair.at(other, 'RocketEntityData', 'flight_data').value['current_dimension'] = S('example:disjoint')
        repair.at(other, 'RocketEntityData', 'snapshot').value['source_dimension'] = S('example:disjoint')
        self.write_entities([other], 'dimensions/example/disjoint')
        self.inspect()  # Coordinates alone do not intersect another dimension.

    def test_uninterpretable_extra_rockets_cannot_prove_disjoint_occupancy(self):
        for other in (C(id=S('advancedrocketrycommunity:rocket'), Pos=vector(160, 100, 64)), self.disjoint_rocket(160)):
            if 'RocketEntityData' in other.value:
                repair.at(other, 'RocketEntityData', 'snapshot').value['schema_version'] = I(999)
            self.write_entities([self.entity, other])
            with self.assertRaises(repair.Refusal):
                self.inspect()

    def test_unknown_endpoint_collections_and_other_rocket_debit_authority_refuse(self):
        baseline = copy.deepcopy((self.entity, self.device))
        for endpoint in ('rocket', 'device'):
            self.entity, self.device = copy.deepcopy(baseline)
            parent = repair.at(self.entity, 'RocketEntityData') if endpoint == 'rocket' else repair.at(self.device, 'arce_rocket_fluid_cargo')
            parent.value['resource_escrow'].value['unknown_authority'] = E(C(unrecognized=S('entry')))
            self.seed()
            self.assert_no_effect_refusal()
        self.entity, self.device = copy.deepcopy(baseline)
        self.seed()
        other = self.disjoint_rocket(160)
        repair.at(other, 'RocketEntityData', 'flight_data', 'fuel').value['committed_debits'] = E(C(transaction_id=U(1)))
        self.write_entities([self.entity, other])
        self.refusal('COMPETING_ENTITY_AUTHORITY')

    def test_uninterpreted_player_vehicle_and_other_device_markers_refuse(self):
        (self.source / 'playerdata').mkdir()
        sidecar = self.source / 'playerdata/other.dat'
        for root_vehicle in (C(unrecognized=S('entry')), E(C(unrecognized=S('entry')))):
            n.write_gzip(sidecar, b'', C(RootVehicle=root_vehicle))
            self.refusal('UNVALIDATED_AUTHORITY:RootVehicle')
        sidecar.unlink()
        other = copy.deepcopy(self.device)
        data = other.value['arce_rocket_fluid_cargo']
        data.value.update(device=U(800), owner=U(400))
        data.value['binding'].value.update(logical_rocket=U(200), physical_rocket=U(300), x=I(160))
        data.value['resource_escrow'].value['markers'] = E(C(unrecognized=S('entry')))
        other.value['x'] = I(160)
        region(self.source / 'dimensions/example/other/region', {(10, 4): C(
            sections=E(section(6, ['minecraft:air'], {})), block_entities=E(other))})
        self.refusal('UNVALIDATED_AUTHORITY:markers')


class BookkeepingClockTest(RepairFixture):
    def clock_census(self, observed=20, logical=20, extra=None, root_key='satellite_missions'):
        document = self.plan()
        repair.apply(self.planfile, self.output, document['digest'])
        path = self.output / ('data/advancedrocketrycommunity_' + root_key + '.dat')
        name, root = n.read_gzip(path)
        data = root.value['data']
        if root_key != 'satellite_missions':
            data.value['clock'] = C(logical_game_time=L(0), last_observed_game_time=L(0))
        data.value['clock'].value['logical_game_time'].value = logical
        data.value['clock'].value['last_observed_game_time'].value = observed
        if extra:
            extra(data)
        n.write_gzip(path, name, root)
        return repair.census(self.planfile, self.output, self.reports / 'clock-census.json', True)

    def test_forward_equal_clock_step_is_accepted(self):
        self.assertEqual('SEMANTIC_CENSUS_ONLY', self.clock_census()['status'])

    def test_decreased_observed_time_is_rejected(self):
        self.refusal('MANAGED_ROOT_CHANGED', lambda: self.clock_census(-1, -1))

    def test_unequal_clock_increases_are_rejected(self):
        self.refusal('MANAGED_ROOT_CHANGED', lambda: self.clock_census(20, 19))

    def test_clock_step_with_changed_epoch_is_rejected(self):
        self.refusal('MANAGED_ROOT_CHANGED', lambda: self.clock_census(extra=lambda data: data.value.update(save_epoch=L(2))))

    def test_clock_step_with_other_changed_field_is_rejected(self):
        self.refusal('MANAGED_ROOT_CHANGED', lambda: self.clock_census(extra=lambda data: data.value.update(other=I(1))))

    def test_same_paths_in_another_root_are_not_exempt(self):
        self.refusal('MANAGED_ROOT_CHANGED', lambda: self.clock_census(root_key='endgame'))

    def test_clock_scalar_type_change_is_not_exempt(self):
        self.refusal('MANAGED_ROOT_CHANGED', lambda: self.clock_census(
            extra=lambda data: data.value['clock'].value.update(logical_game_time=I(20))))


class Revision3Test(RepairFixture):
    def test_mixed_fence_sequence_binds_whole_log_and_first_floor_conflict(self):
        floor = self.log.read_text(encoding='utf-8').rstrip('\n')
        transient = floor.replace('FLOOR_CONFLICT', 'ENDPOINT_JOIN_PROOF_REQUIRED')
        first_floor = 'first ' + floor
        self.log.write_text('\n'.join((transient, first_floor, transient, 'later ' + floor, transient)) + '\n',
                            encoding='utf-8')
        evidence = self.plan()['body']['fence']
        self.assertEqual(first_floor, evidence['line'])
        self.assertEqual(repair.file_digest(self.log), evidence['sha256'])

    def test_transient_fence_for_another_transfer_refuses(self):
        floor = self.log.read_text(encoding='utf-8')
        other = floor.replace(self.transfer, str(uuid.UUID(int=99))).replace('FLOOR_CONFLICT', 'ENDPOINT_JOIN_PROOF_REQUIRED')
        self.log.write_text(floor + other, encoding='utf-8')
        self.refusal('OTHER_TRANSFER_FENCED', lambda: repair.fence(self.log, self.transfer))

    def test_other_derived_fence_reason_refuses_even_with_floor_conflict(self):
        floor = self.log.read_text(encoding='utf-8')
        self.log.write_text(floor + floor.replace('FLOOR_CONFLICT', 'WAITING_UNLOADED'), encoding='utf-8')
        self.refusal('FENCE_REASON_DIFFERS', lambda: repair.fence(self.log, self.transfer))

    def test_only_transient_fences_refuse(self):
        self.log.write_text(self.log.read_text(encoding='utf-8').replace('FLOOR_CONFLICT', 'ENDPOINT_JOIN_PROOF_REQUIRED') * 2,
                            encoding='utf-8')
        self.refusal('FENCE_EVIDENCE_MISSING', lambda: repair.fence(self.log, self.transfer))

    def test_no_fence_lines_refuses(self):
        self.log.write_text('unrelated server output\n', encoding='utf-8')
        self.refusal('FENCE_EVIDENCE_MISSING', lambda: repair.fence(self.log, self.transfer))

    def test_zero_byte_regions_stay_hashed_in_plan_apply_and_census(self):
        names = [kind + '/r.1.1.mca' for kind in ('region', 'entities', 'poi')]
        for relative in names:
            path = self.source / relative
            path.parent.mkdir(exist_ok=True)
            path.write_bytes(b'')
        self.sync()
        document = self.plan()
        for relative in names:
            self.assertEqual(0, document['body']['manifest'][relative]['size'])
            self.assertEqual(n.digest(b''), document['body']['manifest'][relative]['sha256'])
        repair.apply(self.planfile, self.output, document['digest'])
        census = repair.census(self.planfile, self.output, self.reports / 'census.json')
        for relative in names:
            self.assertEqual(document['body']['manifest'][relative], census['manifest'][relative])
            self.assertEqual(b'', (self.output / relative).read_bytes())

    def test_short_nonempty_regions_refuse_but_full_empty_header_is_valid(self):
        for kind in ('region', 'entities', 'poi'):
            path = self.source / kind / 'r.1.1.mca'
            path.parent.mkdir(exist_ok=True)
            for size in (1, 8191):
                with self.subTest(kind=kind, size=size):
                    path.write_bytes(bytes(size))
                    self.refusal('CORRUPT_REGION')
            path.write_bytes(bytes(8192))
            self.inspect()


if __name__ == '__main__':
    unittest.main()
