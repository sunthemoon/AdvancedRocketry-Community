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
        fuel.value.update(amount=L(683), committed_debits=E(U(12)))
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

    def test_oracle_has_no_planner_dependency(self):
        import ast
        tree = ast.parse(Path(oracle.__file__).read_text(encoding='utf-8'))
        imports = [n.name for node in ast.walk(tree) if isinstance(node, ast.Import) for n in node.names]
        self.assertNotIn('repair_v180_rebuild_source', imports)


if __name__ == '__main__':
    unittest.main()
