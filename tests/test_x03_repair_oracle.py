"""Independent raw-file checks and collateral-mutation controls, without a JVM."""
import copy
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import loader_cut_nbt as nbt
import x03_repair_oracle as oracle
from test_loader_cut_nbt import compound as C, string as S, region
from test_repair_v180_rebuild_source import RepairFixture, E, I, L, U, A, vector, target


class BookkeepingClockTest(RepairFixture):
    def compare_clock(self, observed=20, logical=20, extra=None, root_key='satellite_missions'):
        if root_key != 'satellite_missions':
            self.data[root_key].value['clock'] = C(logical_game_time=L(0), last_observed_game_time=L(0))
            self.write_roots()
        before = oracle.read(self.source)
        self.data['rocket_transfers'].value['transfers'] = E()
        data = self.data[root_key]
        data.value['clock'].value['logical_game_time'].value = logical
        data.value['clock'].value['last_observed_game_time'].value = observed
        if extra:
            extra(data)
        self.write_roots()
        oracle.compare_repair(before, oracle.read(self.source), self.transfer)

    def test_forward_equal_clock_step_is_accepted(self):
        self.compare_clock()

    def test_decreased_observed_time_is_rejected(self):
        with self.assertRaisesRegex(oracle.OracleError, 'OBJECT_CHANGED:.*satellite_missions'):
            self.compare_clock(-1, -1)

    def test_unequal_clock_increases_are_rejected(self):
        with self.assertRaisesRegex(oracle.OracleError, 'OBJECT_CHANGED:.*satellite_missions'):
            self.compare_clock(20, 19)

    def test_clock_step_with_changed_epoch_is_rejected(self):
        with self.assertRaisesRegex(oracle.OracleError, 'OBJECT_CHANGED:.*satellite_missions'):
            self.compare_clock(extra=lambda data: data.value.update(save_epoch=L(2)))

    def test_clock_step_with_other_changed_field_is_rejected(self):
        with self.assertRaisesRegex(oracle.OracleError, 'OBJECT_CHANGED:.*satellite_missions'):
            self.compare_clock(extra=lambda data: data.value.update(other=I(1)))

    def test_same_paths_in_another_root_are_not_exempt(self):
        with self.assertRaisesRegex(oracle.OracleError, 'OBJECT_CHANGED:.*endgame'):
            self.compare_clock(root_key='endgame')

    def test_clock_scalar_type_change_is_not_exempt(self):
        with self.assertRaisesRegex(oracle.OracleError, 'OBJECT_CHANGED:.*satellite_missions'):
            self.compare_clock(extra=lambda data: data.value['clock'].value.update(logical_game_time=I(20)))

    def test_measurement_reports_each_path_and_restores_strict_checks(self):
        before = C(a=L(1), b=I(2))
        after = C(a=L(3), b=L(2))
        findings = []
        with oracle.measurement(findings):
            oracle.equal(before, after, 'data/example.dat')
        self.assertEqual(['/a', '/b'], [row['path'] for row in findings])
        self.assertTrue(all(row['file'] == 'data/example.dat' for row in findings))
        with self.assertRaises(oracle.OracleError):
            oracle.equal(before, after, 'data/example.dat')

    def test_measurement_records_all_failed_unload_invariants_with_typed_locations(self):
        state = oracle.read(self.source)
        state['journal']['transfers'] = []
        findings = []
        with oracle.measurement(findings):
            oracle.check_state(state, state, unloaded=True)
        self.assertEqual({'WATER_ENDPOINTS', 'DEVICE_EPOCH', 'ROCKET_EPOCH'}, {row['reason'] for row in findings})
        self.assertTrue(all(row['file'] and row['path'].startswith('/') for row in findings))

    def test_measurement_keeps_parsed_fields_after_a_failed_readback(self):
        line = ('ARCE_RELEASE_CARGO_CUT_RESULT cut=FF_X03_LAUNCH state=LANDED action_authority=true journal=0 '
                'device=1000 device_markers=0 rocket=4000 mutation_allowed=false rocket_markers=0 debit=retained')
        findings = []
        with oracle.measurement(findings):
            fields = oracle.observe([line], 'FF_X03_LAUNCH', dict(tank=1000, cargo=4000), landed=True)
        self.assertEqual('retained', fields['debit'])
        self.assertEqual([dict(reason='ROCKET_READBACK')], findings)
        with self.assertRaisesRegex(oracle.OracleError, 'ROCKET_READBACK'):
            oracle.observe([line], 'FF_X03_LAUNCH', dict(tank=1000, cargo=4000), landed=True)

    def test_measurement_reports_both_generation_failures_and_later_roots(self):
        before = oracle.read(self.source)
        after = copy.deepcopy(before)
        satellite = 'data/advancedrocketrycommunity_satellite_missions.dat'
        after['managed'][satellite].value['data'].value['save_epoch'].value += 1
        findings = []
        with oracle.measurement(findings):
            oracle.compare_functional(before, after, action='unload')
        self.assertEqual({'WRITER_GENERATION', 'UNLOAD_GENERATION', 'OBJECT_CHANGED'}, {row['reason'] for row in findings})
        with self.assertRaisesRegex(oracle.OracleError, 'WRITER_GENERATION'):
            oracle.compare_functional(before, after, action='unload')

    def test_progression_receipt_has_exact_codec_fields_and_matches_the_landing(self):
        root = C(data=C(schema_version=I(1), format_epoch=S('v1.8.0-classic-progression'),
                        first_moon_landing=C(occurred=nbt.Tag(nbt.BYTE, 1), source=S('rocket_transfers'), event=C()),
                        first_station_warp=C(occurred=nbt.Tag(nbt.BYTE, 0))), DataVersion=I(3465))
        name = 'data/advancedrocketrycommunity_classic_progression.dat'
        oracle.validate_new_functional_root(name, root, C())
        for field, value in (('source', S('stations')), ('source', I(1)), ('occurred', I(1)),
                             ('occurred', nbt.Tag(nbt.BYTE, 0)), ('other', I(1))):
            changed = copy.deepcopy(root)
            changed.value['data'].value['first_moon_landing'].value[field] = value
            with self.subTest(field=field, value=value), self.assertRaises(oracle.OracleError):
                oracle.validate_new_functional_root(name, changed, C())
        changed = copy.deepcopy(root)
        changed.value['data'].value['first_station_warp'].value['occurred'].value = 1
        with self.assertRaisesRegex(oracle.OracleError, 'NEW_CLASSIC_AUTHORITY'):
            oracle.validate_new_functional_root(name, changed, C())
        with self.assertRaisesRegex(oracle.OracleError, 'first-moon-receipt'):
            oracle.validate_new_functional_root(name, root, C(terminal_origin=A(8, 100, 8)))
        del root.value['data'].value['first_moon_landing'].value['source']
        with self.assertRaisesRegex(oracle.OracleError, 'NEW_CLASSIC_AUTHORITY'):
            oracle.validate_new_functional_root(name, root, C())


class RawOracleTest(RepairFixture):
    def landed(self, before):
        after = copy.deepcopy(before)
        entity = after['entity']
        entity.value['UUID'] = U(10)
        entity.value['Pos'] = vector(8.5, 100, 8.5)
        rocket = entity.value['RocketEntityData']
        snapshot = rocket.value['snapshot']
        snapshot.value.update(snapshot_id=S('00000000-0000-0000-0000-000000000011'),
                              source_dimension=S('advancedrocketrycommunity:moon'), source_origin=A(8, 100, 8),
                              created_at_game_time=L(201), content_hash=S('c' * 64))
        rocket.value['resource_escrow'].value['resource_epoch'] = L(0)
        flight = rocket.value['flight_data']
        flight.value.update(state=S('LANDED'), current_body=S('advancedrocketrycommunity:moon'),
                            current_dimension=S('advancedrocketrycommunity:moon'), current_origin=A(8, 100, 8),
                            state_started_game_time=L(201), current_target=target('advancedrocketrycommunity:moon'))
        fuel = flight.value['fuel']
        fuel.value.update(amount=L(683), committed_debits=nbt.Tag(nbt.LIST, (nbt.INTS, [U(12)])))
        fuel.value['propulsion'].value['primary'].value['amount'] = L(683)
        fuel.value['propulsion'].value['revision'] = L(2)
        after['rocket'] = nbt.plain(entity)['RocketEntityData']
        after['rocket_key'] = ('dimensions/advancedrocketrycommunity/moon/entities/r.0.0.mca', 0, 0)
        return after

    def test_launch_comparison_masks_only_the_named_action_fields(self):
        before = oracle.read(self.source)
        after = self.landed(before)
        oracle.compare_launched_rocket(before, after)
        after['entity'].value['CustomName'] = S('collateral edit')
        with self.assertRaisesRegex(oracle.OracleError, 'OBJECT_CHANGED:landed-rocket'):
            oracle.compare_launched_rocket(before, after)

    def test_landing_claim_must_match_the_physical_file_and_position(self):
        before = oracle.read(self.source)
        after = self.landed(before)
        after['entity'].value['Pos'] = vector(80.5, 100, 64.5)
        with self.assertRaisesRegex(oracle.OracleError, 'LANDED_PHYSICAL_LOCATION'):
            oracle.compare_launched_rocket(before, after)

    def test_launch_debit_is_checked_per_fluid_and_against_disk(self):
        before = oracle.read(self.source)
        after = self.landed(before)
        fields = dict(debit='primary:317,oxidizer:0,revision:1,resource:' + 'a' * 64 + ',catalog:' + 'b' * 64,
                      fuel_before='1000', fuel_after='683')
        self.assertEqual(317, oracle.launch_debit(fields, before, after)['primary'])
        fields['debit'] = 'unobserved'
        with self.assertRaisesRegex(oracle.OracleError, 'TYPED_PROPULSION_DEBIT_MISSING'):
            oracle.launch_debit(fields, before, after)

    def test_reads_physical_water_without_counting_historical_journal_inventory(self):
        state = oracle.read(self.source)
        self.assertEqual((4000, 1000), (state['cargo'], state['tank']))
        oracle.check_state(state, state, repaired=False)

    def test_one_record_removal_preserves_every_other_typed_field(self):
        baseline = oracle.read(self.source)
        self.data['rocket_transfers'].value['transfers'] = E()
        self.write_roots()
        oracle.compare_repair(baseline, oracle.read(self.source), self.transfer)

    def test_skip_removal_is_not_success(self):
        state = oracle.read(self.source)
        with self.assertRaisesRegex(oracle.OracleError, 'JOURNAL_REMOVAL'):
            oracle.compare_repair(state, state, self.transfer)

    def test_duplicate_rocket_in_another_level_or_passenger_is_not_absent(self):
        for passenger in (False, True):
            with self.subTest(passenger=passenger):
                if passenger:
                    host = C(id=S('minecraft:pig'), Passengers=E(copy.deepcopy(self.entity)))
                    self.write_entities([self.entity, host])
                else:
                    self.write_entities([self.entity], 'dimensions/example/remote')
                with self.assertRaisesRegex(oracle.OracleError, 'DUPLICATED_OR_MISSING'):
                    oracle.read(self.source)
                if passenger:
                    self.write_entities([self.entity])
                else:
                    shutil.rmtree(self.source / 'dimensions')

    def test_wrong_fluid_cannot_cancel_water_loss(self):
        state = oracle.read(self.source)
        state['cargo'] -= 1
        with self.assertRaisesRegex(oracle.OracleError, 'WATER_ENDPOINTS'):
            oracle.check_state(state, state, repaired=False)

    def test_invented_authority_is_not_success(self):
        state = oracle.read(self.source)
        state['journal']['transfers'] = []
        state['ledger']['floors'] = [{'invented': 0}]
        with self.assertRaisesRegex(oracle.OracleError, 'FABRICATED_AUTHORITY'):
            oracle.check_state(state, state)

    def test_non_target_entity_in_same_listed_file_is_compared(self):
        other = C(id=S('minecraft:pig'), Health=I(20))
        self.write_entities([self.entity, other])
        before = oracle.chunks(self.source)
        changed = copy.deepcopy(before)
        key = next(k for k in changed if k[0].startswith('entities/'))
        changed[key].value['Entities'].value[1][1].value['Health'].value = 19
        # Test-only coordinator variant omits per-object comparison: targets pass.
        oracle.check_state(oracle.read(self.source), oracle.read(self.source), repaired=False)
        with self.assertRaisesRegex(oracle.OracleError, 'entities/r.0.0.mca.*Health'):
            oracle.compare_chunks(before, changed)

    def test_nontarget_terrain_object_inside_listed_region_is_compared(self):
        before = oracle.chunks(self.source)
        changed = copy.deepcopy(before)
        key = next(k for k in changed if k[0].startswith('region/'))
        changed[key].value['collateral'] = I(9)
        with self.assertRaisesRegex(oracle.OracleError, 'region/r.0.0.mca'):
            oracle.compare_chunks(before, changed)

    def test_chunk_clock_allowance_does_not_allow_data_version_or_other_object_changes(self):
        root = C(LastUpdate=I(1), InhabitedTime=I(2), DataVersion=I(3465))
        other = copy.deepcopy(root)
        other.value['LastUpdate'].value += 1
        oracle.compare_chunks({('region/r.0.0.mca', 0, 0): root}, {('region/r.0.0.mca', 0, 0): other})
        other.value['DataVersion'].value += 1
        with self.assertRaisesRegex(oracle.OracleError, 'DataVersion'):
            oracle.compare_chunks({('region/r.0.0.mca', 0, 0): root}, {('region/r.0.0.mca', 0, 0): other})

    def test_truncated_region_is_a_failure_not_an_empty_level(self):
        (self.source / 'entities/r.0.0.mca').write_bytes(b'bad')
        with self.assertRaisesRegex(oracle.OracleError, 'TRUNCATED_REGION'):
            oracle.read(self.source)


class BoundaryTest(unittest.TestCase):
    def test_generation_extent_contains_the_measured_ring_but_no_next_chunk(self):
        ring = [(x, z) for x in range(-13, 14) for z in range(-13, 14) if max(abs(x), abs(z)) > 10]
        self.assertEqual(288, len(ring))
        name = oracle.MOON + '/region/r.0.0.mca'
        self.assertTrue(all(oracle.in_functional_box((name, x, z)) for x, z in ring))
        for x, z in ((14, 0), (-14, 0), (0, 14), (0, -14)):
            self.assertFalse(oracle.in_functional_box((name, x, z)))

    def test_existing_margin_chunks_are_immutable_even_for_clock_only_writes(self):
        key = (oracle.MOON + '/region/r.0.0.mca', 3, 0)
        before = {key: C(LastUpdate=L(1), InhabitedTime=L(0))}
        oracle.compare_chunks(before, copy.deepcopy(before), generation_margin=True)
        after = copy.deepcopy(before)
        after[key].value['LastUpdate'].value += 1
        with self.assertRaisesRegex(oracle.OracleError, 'EXISTING_GENERATION_MARGIN'):
            oracle.compare_chunks(before, after, generation_margin=True)
        # The same ordinary clock step in the FULL ticket box remains permitted.
        key2 = (key[0], 2, 0)
        oracle.compare_chunks({key2: before[key]}, {key2: after[key]}, generation_margin=True)

    def test_every_raid_allows_only_nondecreasing_int_tick(self):
        for name in oracle.RAID_FILES:
            before = {name: C(data=C(Tick=I(1), NextAvailableID=I(2)), DataVersion=I(3465))}
            after = copy.deepcopy(before)
            after[name].value['data'].value['Tick'].value = 2
            oracle.compare_raids(before, after)
            for field, value in (('Tick', I(0)), ('Tick', L(2)), ('NextAvailableID', I(3)), ('other', I(1))):
                bad = copy.deepcopy(before)
                bad[name].value['data'].value[field] = value
                with self.subTest(name=name, field=field), self.assertRaises(oracle.OracleError):
                    oracle.compare_raids(before, bad)
        with self.assertRaisesRegex(oracle.OracleError, 'RAID_FILE_SET_CHANGED'):
            oracle.compare_raids(before, {})

    def test_functional_file_allowance_is_dimension_and_box_bounded(self):
        self.assertEqual(['data/advancedrocketrycommunity_resource_escrow.dat'],
                         oracle.functional_boundary({}, {'data/advancedrocketrycommunity_resource_escrow.dat': 'x'}, {})['added'])
        inside = 'dimensions/advancedrocketrycommunity/moon/region/r.-1.-1.mca'
        oracle.functional_boundary({}, {inside: 'x'}, {})
        for outside in ('dimensions/example/other/region/r.0.0.mca',
                        'dimensions/advancedrocketrycommunity/moon/region/r.1.1.mca', 'playerdata/unrelated.dat'):
            with self.subTest(outside=outside), self.assertRaisesRegex(oracle.OracleError, 'UNLISTED_FILE'):
                oracle.functional_boundary({}, {outside: 'x'}, {})

    def test_chunk_box_does_not_admit_unrelated_slots_in_an_allowed_region_file(self):
        self.assertTrue(oracle.in_functional_box(('dimensions/advancedrocketrycommunity/moon/region/r.0.0.mca', 0, 0)))
        self.assertFalse(oracle.in_functional_box(('dimensions/advancedrocketrycommunity/moon/region/r.0.0.mca', 31, 31)))

    def test_added_removed_and_unlisted_changed_files_refuse(self):
        for after in ({'b': '1'}, {}, {'a': '2'}):
            with self.subTest(after=after), self.assertRaises(oracle.OracleError):
                oracle.boundary({'a': '1'}, after, {})
        self.assertEqual([], oracle.boundary({'a': '1'}, {'a': '1'}, {'a'})['changed'])

    def test_tamper_selection_uses_bytewise_order_and_both_runs(self):
        before = {'z.dat': 'a', 'a.dat': 'b', 'b.dat': 'c'}
        self.assertEqual('b.dat', oracle.tamper_target(before, [before, dict(before, **{'a.dat': 'changed'})], {}))
        self.assertEqual('z.dat', oracle.tamper_target(before, [before, before], {'a.dat', 'b.dat'}))

    def test_missing_target_and_single_dry_run_are_refusals(self):
        with self.assertRaisesRegex(oracle.OracleError, 'NO_TAMPER_TARGET'):
            oracle.tamper_target({'a.txt': 'x'}, [{'a.txt': 'x'}] * 2, {})
        with self.assertRaisesRegex(oracle.OracleError, 'TWO_DRY_RUNS_REQUIRED'):
            oracle.tamper_target({}, [{}], {})

    def test_tamper_rule_checks_actual_gzip_type_and_nonempty_region_headers(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'a.dat').write_bytes(b'opaque-not-gzip')
            (root / 'entities').mkdir()
            (root / 'entities/r.0.0.mca').write_bytes(bytes(8192))
            before = oracle.manifest(root)
            with self.assertRaisesRegex(oracle.OracleError, 'NO_TAMPER_TARGET'):
                oracle.tamper_target(before, [before, before], {}, root)
            nbt.write_gzip(root / 'b.dat', b'', C(count=I(1)))
            before = oracle.manifest(root)
            self.assertEqual('b.dat', oracle.tamper_target(before, [before, before], {}, root))

    def test_test_only_omission_of_final_file_check_misses_collateral_tamper(self):
        before = {'level.dat': 'a', 'data/raids.dat': 'b'}
        after = dict(before, **{'data/raids.dat': 'c'})
        # The resource/identity variant would pass with identical target objects;
        # the unmodified boundary reports exactly the collateral filename.
        self.assertEqual(before['level.dat'], after['level.dat'])
        with self.assertRaisesRegex(oracle.OracleError, '^UNLISTED_FILE:data/raids.dat$'):
            oracle.boundary(before, after, {'level.dat'})

    def test_typed_comparison_keeps_number_types_and_entry_order(self):
        for other in (C(x=nbt.Tag(nbt.LONG, 1), y=I(2)), C(y=I(2), x=I(1))):
            with self.assertRaises(oracle.OracleError):
                oracle.equal(C(x=I(1), y=I(2)), other, 'object')

    def test_accepting_comparison_separates_signed_zero_as_measurement_does(self):
        # W380R finding 4: Python's == calls 0.0 and -0.0 equal; their NBT bytes differ.
        for kind in (nbt.DOUBLE, nbt.FLOAT):
            before = C(Motion=nbt.Tag(nbt.LIST, (kind, [nbt.Tag(kind, 0.0), nbt.Tag(kind, 0.0)])))
            after = C(Motion=nbt.Tag(nbt.LIST, (kind, [nbt.Tag(kind, 0.0), nbt.Tag(kind, -0.0)])))
            with self.subTest(kind=kind):
                self.assertEqual('/Motion/1', oracle.first_difference(before, after))
                with self.assertRaisesRegex(oracle.OracleError, '^OBJECT_CHANGED:entity:/Motion/1$'):
                    oracle.equal(before, after, 'entity')
                self.assertEqual(['/Motion/1'], [row['path'] for row in oracle.typed_differences(before, after)])
                key = ('entities/r.0.0.mca', 0, 0)
                with self.assertRaisesRegex(oracle.OracleError, 'OBJECT_CHANGED:.*entities/r.0.0.mca.*:/Motion/1$'):
                    oracle.compare_chunks({key: before}, {key: after})

    def test_accepting_comparison_holds_an_unchanged_nan_equal_as_measurement_does(self):
        # The other half of finding 4: NaN != NaN in Python, which refused an unchanged object.
        for kind in (nbt.DOUBLE, nbt.FLOAT):
            before = C(Rotation=nbt.Tag(nbt.LIST, (kind, [nbt.Tag(kind, float('nan'))])), Health=nbt.Tag(kind, float('nan')))
            after = nbt.read(nbt.write(b'', before))[1]
            with self.subTest(kind=kind):
                self.assertIsNone(oracle.first_difference(before, after))
                oracle.equal(before, after, 'entity')
                self.assertEqual([], list(oracle.typed_differences(before, after)))
                key = ('entities/r.0.0.mca', 0, 0)
                oracle.compare_chunks({key: before}, {key: after})
                changed = copy.deepcopy(after)
                changed.value['Health'].value = 1.0
                with self.assertRaisesRegex(oracle.OracleError, '^OBJECT_CHANGED:entity:/Health$'):
                    oracle.equal(before, changed, 'entity')

    def test_oracle_has_no_planner_dependency(self):
        import ast
        tree = ast.parse(Path(oracle.__file__).read_text(encoding='utf-8'))
        imports = [n.name for node in ast.walk(tree) if isinstance(node, ast.Import) for n in node.names]
        self.assertNotIn('repair_v180_rebuild_source', imports)


class RegionHeaderTest(unittest.TestCase):
    def test_zero_byte_regions_are_empty_but_remain_in_manifest_and_boundary(self):
        with tempfile.TemporaryDirectory() as directory:
            world = Path(directory)
            for kind in ('region', 'entities', 'poi'):
                path = world / kind / 'r.0.0.mca'
                path.parent.mkdir()
                path.write_bytes(b'')
            before = oracle.manifest(world)
            self.assertEqual({kind + '/r.0.0.mca': nbt.digest(b'') for kind in ('region', 'entities', 'poi')}, before)
            self.assertEqual({}, oracle.chunks(world))
            path.write_bytes(bytes(8192))
            self.assertEqual({}, oracle.chunks(world))
            with self.assertRaisesRegex(oracle.OracleError, 'UNLISTED_FILE:poi/r.0.0.mca'):
                oracle.boundary(before, oracle.manifest(world), {})

    def test_short_nonempty_regions_refuse_but_full_empty_header_is_valid(self):
        with tempfile.TemporaryDirectory() as directory:
            world = Path(directory)
            for kind in ('region', 'entities', 'poi'):
                path = world / kind / 'r.0.0.mca'
                path.parent.mkdir()
                for size in (1, 8191):
                    with self.subTest(kind=kind, size=size):
                        path.write_bytes(bytes(size))
                        with self.assertRaisesRegex(oracle.OracleError, 'TRUNCATED_REGION'):
                            oracle.chunks(world)
                path.write_bytes(bytes(8192))
                self.assertEqual({}, oracle.chunks(world))


if __name__ == '__main__':
    unittest.main()
