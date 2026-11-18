"""X03 command, seed-fence and orchestration checks; never starts a server."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch, MagicMock
import tempfile
from types import SimpleNamespace
import copy

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_v180_x03_repair as runner
from test_repair_v180_rebuild_source import RepairFixture, E, I, L, U, A, vector
from test_loader_cut_nbt import compound as C, string as S, section
from test_cargo_s4_s5_cuts_runner import rocket as seed_rocket, stale_disk

HOOKS = "-Dadvancedrocketrycommunity.releaseTestHooks=true"
SCENARIO = "-Dadvancedrocketrycommunity.escrowScenario="
CASE = "-Dadvancedrocketrycommunity.escrowCase="
ROUTE = "-Dadvancedrocketrycommunity.escrowVerifyRoute="


class VerificationCommandTest(unittest.TestCase):
    def test_missing_native_observation_is_retained_only_in_measurement_mode(self):
        process = MagicMock()
        process.wait_for.side_effect = runner.smoke.SmokeError('native result absent')
        process.process.poll.return_value = 0
        process.finish.return_value = 0
        row = runner.observation_stop(process, 'result', 420, 'unload', True)
        self.assertEqual('NATIVE_OBSERVATION_MISSING', row['contradictions'][0]['reason'])
        self.assertEqual(420, process.wait_for.call_args.args[1])
        process.command.assert_not_called()
        with self.assertRaisesRegex(runner.smoke.SmokeError, 'native result absent'):
            runner.observation_stop(process, 'result', 420, 'unload', False)

    def test_failed_native_stop_is_not_accepted_and_all_details_are_retained(self):
        process = MagicMock()
        process.process.poll.return_value = None
        process.finish.return_value = 1
        row = runner.observation_stop(process, 'result', 420, 'unload', True)
        self.assertEqual('UNCLEAN_STOP', row['contradictions'][0]['reason'])
        process.command.assert_called_once_with('stop')
        with self.assertRaisesRegex(runner.oracle.OracleError, 'UNCLEAN_STOP:unload'):
            runner.observation_stop(process, 'result', 420, 'unload', False)

    def test_measurement_decodes_vanilla_gzip_paths_as_well_as_managed_roots(self):
        with tempfile.TemporaryDirectory() as directory:
            world = Path(directory)
            (world / 'data').mkdir()
            path = world / 'data/raids.dat'
            runner.nbt.write_gzip(path, b'', C(data=C(Tick=runner.nbt.Tag(runner.nbt.INT, 1))))
            before = runner.disk_snapshot(world)
            runner.nbt.write_gzip(path, b'', C(data=C(Tick=runner.nbt.Tag(runner.nbt.INT, 2))))
            after = runner.disk_snapshot(world)
            row = runner.record_stage(world, 'raids', before, after)
            self.assertEqual(['data/raids.dat'], row['differences']['changed'])
            self.assertEqual('/data/Tick', row['typed_changes'][0]['path'])
            self.assertEqual(runner.nbt.INT, row['typed_changes'][0]['before_type'])

    def test_restarts_reuse_the_session_startup_properties_witness(self):
        with tempfile.TemporaryDirectory() as directory:
            session = Path(directory)
            runner.configure_session(session, session / 'first-world', 25565)
            witness = session / 'server.properties.v002-startup'
            original = witness.read_bytes()
            runner.configure_session(session, session / 'second-world', 25566)
            self.assertEqual(original, witness.read_bytes())
            properties = runner.smoke.parse_java_properties((session / 'server.properties').read_bytes())
            self.assertEqual('second-world', properties['level-name'])
            self.assertEqual('25566', properties['server-port'])

    def test_each_case_sets_the_hooks_the_verify_scenario_and_exactly_that_case(self):
        self.assertEqual(["FF_X03_LAUNCH", "FF_X03_OBSERVE", "FF_X03_UNLOAD"], sorted(runner.CASES))
        for case in runner.CASES:
            command = runner.verification_command("java", case)
            self.assertEqual("java", command[0])
            self.assertEqual(1, command.count(HOOKS))
            self.assertEqual([SCENARIO + "verify"], [value for value in command if value.startswith(SCENARIO)])
            self.assertEqual([CASE + case], [value for value in command if value.startswith(CASE)])
            self.assertEqual([], [value for value in command if value.startswith(ROUTE)])
            self.assertEqual("nogui", command[-1])

    def test_no_case_is_started_under_the_start_or_recover_scenario(self):
        for case in runner.CASES:
            command = runner.verification_command("java", case)
            self.assertNotIn(SCENARIO + "start", command)
            self.assertNotIn(SCENARIO + "recover", command)

    def test_only_the_launch_takes_a_route_and_only_a_known_one(self):
        command = runner.verification_command("java", "FF_X03_LAUNCH", "MOON")
        self.assertEqual([ROUTE + "MOON"], [value for value in command if value.startswith(ROUTE)])
        for case, route in (("FF_X03_OBSERVE", "MOON"), ("FF_X03_UNLOAD", "MOON"), ("FF_X03_LAUNCH", "MARS"),
                            ("FF_X03_LAUNCH", "")):
            with self.assertRaises(ValueError):
                runner.verification_command("java", case, route)

    def test_unknown_names_are_refused(self):
        for case in ("", "X03_OBSERVE", "ff_x03_observe", "OBSERVE", "FF-X03-OBSERVE"):
            with self.assertRaises(ValueError):
                runner.verification_command("java", case)

    def test_the_descendant_sequence_and_the_result_markers(self):
        self.assertEqual(("FF_X03_UNLOAD", "FF_X03_OBSERVE", "FF_X03_LAUNCH", "FF_X03_OBSERVE"), runner.DESCENDANT_SEQUENCE)
        self.assertEqual("ARCE_RELEASE_CARGO_CUT_RESULT cut=FF_X03_UNLOAD state=UNLOADED ", runner.marker("FF_X03_UNLOAD"))
        self.assertEqual({"OBSERVED", "UNLOADED", "LANDED"}, {spec["state"] for spec in runner.CASES.values()})

    def test_seed_requires_its_own_floor_conflict_for_the_selected_transfer(self):
        transfer = '00000000-0000-0000-0000-000000000001'
        good = 'ARCE_TRANSFER_FENCED transfer=' + transfer + ' reason=FLOOR_CONFLICT repair=offline-whole-world'
        runner.check_fence([good], transfer)
        for lines in ([], [good.replace('FLOOR_CONFLICT', 'WAITING_UNLOADED')], [good, good.replace(transfer, 'other')]):
            with self.assertRaisesRegex(runner.oracle.OracleError, 'SEED_FLOOR_CONFLICT'):
                runner.check_fence(lines, transfer)

    def test_hash_verified_copy_keeps_all_files_and_detects_a_partial_copy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'source'
            source.mkdir()
            (source / 'opaque').write_bytes(b'preserve')
            self.assertEqual(runner.oracle.manifest(source), runner.copy_verified(source, root / 'copy'))
            with patch.object(runner.shutil, 'copytree'):
                with self.assertRaisesRegex(runner.oracle.OracleError, 'COPY_HASH_MISMATCH'):
                    runner.copy_verified(source, root / 'partial')

    def test_revision3_fence_sequence_accepts_only_transient_and_derived_floor_for_one_transfer(self):
        transfer = '00000000-0000-0000-0000-000000000001'
        floor = 'ARCE_TRANSFER_FENCED transfer=' + transfer + ' reason=FLOOR_CONFLICT repair=offline-whole-world'
        transient = floor.replace('FLOOR_CONFLICT', 'ENDPOINT_JOIN_PROOF_REQUIRED')
        runner.check_fence([transient, floor, transient], transfer)
        for lines in ([], [transient, transient], [floor, floor.replace('FLOOR_CONFLICT', 'WAITING_UNLOADED')],
                      [floor, transient.replace(transfer, '00000000-0000-0000-0000-000000000099')],
                      [floor, 'ARCE_TRANSFER_FENCED malformed']):
            with self.subTest(lines=lines), self.assertRaisesRegex(runner.oracle.OracleError, 'SEED_FLOOR_CONFLICT'):
                runner.check_fence(lines, transfer)

    def test_measurement_failure_is_not_a_passing_acceptance_check(self):
        def failing():
            raise runner.oracle.OracleError('collateral object')
        findings = []
        runner.measure(failing, findings, True)
        self.assertEqual(['collateral object'], findings)
        with self.assertRaisesRegex(runner.oracle.OracleError, 'collateral object'):
            runner.measure(failing, [], False)

    def test_runtime_allowances_have_exact_names_and_reasons(self):
        self.assertEqual(set(runner.RUNTIME_FILES), {
            *runner.oracle.RAID_FILES, runner.repair.JOURNAL,
            'data/advancedrocketrycommunity_satellite_missions.dat', 'entities/r.0.0.mca',
            'level.dat', 'level.dat_old', 'region/r.-1.-1.mca', 'region/r.-1.0.mca',
            'region/r.0.-1.mca', 'region/r.0.0.mca'})
        self.assertEqual(18, len(runner.RUNTIME_FILES))
        self.assertEqual(set(runner.repair.RAID_FILES), set(runner.oracle.RAID_FILES))
        for name in ('session.lock', 'data/unrelated.dat', 'region/r.1.0.mca'):
            with self.assertRaisesRegex(runner.oracle.OracleError, 'UNLISTED_FILE'):
                runner.oracle.boundary({}, {name: 'changed'}, runner.RUNTIME_FILES)
        for name, reason in runner.RUNTIME_FILES.items():
            self.assertNotIn('*', name)
            self.assertNotIn('..', Path(name).parts)
            self.assertTrue(reason.strip())

    def test_generation_rule_refuses_when_the_measured_ring_does_not_fit(self):
        self.assertEqual(288, runner.generation_rule()['measured_ring_chunks'])
        self.assertEqual(0, runner.generation_rule()['probe_landing_ticket_distance'])
        self.assertEqual(2, runner.generation_rule()['destination_ticket_radius'])
        with patch.dict(runner.oracle.FUNCTIONAL_BOXES, {runner.oracle.MOON: (-12, 13, -13, 13)}):
            with self.assertRaisesRegex(runner.oracle.OracleError, 'MEASURED_RING_OUTSIDE_DERIVED_BOX'):
                runner.generation_rule()

    def test_cli_requires_the_lock_and_all_candidate_pins(self):
        arguments = ['template', '--jar', 'candidate.jar', '--session-dir', 'session', '--evidence-dir', 'evidence',
                     '--source-commit', 'a' * 40, '--candidate-commit', 'b' * 40, '--jar-sha256', 'c' * 64,
                     '--java', 'java', '--lock-owner', 'owner']
        with patch.object(runner, 'run') as run:
            self.assertEqual(0, runner.main(arguments))
        args = run.call_args.args[0]
        self.assertFalse(args.derive_runtime_list)
        self.assertEqual('owner', args.lock_owner)

    def test_campaign_appends_one_full_case_and_forwards_candidate_pins(self):
        import run_v180_integrated_campaign as campaign
        # W373B appends its accounting families after this one; X03 keeps its own position, row 71.
        row = campaign.family_specs()[71]
        self.assertEqual(runner.FAMILY, row['name'])
        self.assertEqual('scripts/run_v180_x03_repair.py', row['runner'])
        self.assertEqual([], row['arguments'])


class NativeOperationFixtureTest(RepairFixture):
    def test_measurement_continues_all_observations_and_functional_actions(self):
        args = SimpleNamespace(jar=self.jar, derive_runtime_list=True, measurement_mode=True)
        with patch.object(runner, 'seed_world', return_value=(self.source, self.transfer, self.log)), \
                patch.object(runner, 'start', return_value=([], self.log)) as start, \
                patch.object(runner.oracle, 'observe', return_value={'switch': 'withdrawn'}), \
                patch.object(runner.oracle, 'compare_repair', side_effect=runner.oracle.OracleError('OBJECT_CHANGED:collateral')), \
                patch.object(runner.oracle, 'compare_functional', side_effect=runner.oracle.OracleError('OBJECT_CHANGED:descendant')), \
                patch.object(runner.oracle, 'launch_debit', return_value={'primary': 1}):
            row, _ = runner.repaired_cycle(args, self.base, self.reports, 1)
        self.assertEqual(7, start.call_count)
        self.assertEqual(3, len(row['stages']))
        self.assertEqual(4, len(row['functional']))
        self.assertEqual('MEASURED_NOT_PROMOTED', row['status'])
        self.assertTrue(all(stage['contradictions'] for stage in row['stages'] + row['functional']))

    def test_measurement_file_boundary_reports_every_changed_file(self):
        args = SimpleNamespace(derive_runtime_list=True, measurement_mode=True)
        findings = []
        runner.file_check(args, {'a': '1', 'b': '2'}, {'a': '3', 'b': '4'}, findings)
        self.assertEqual(['a', 'b'], [row['file'] for row in findings])
        self.assertTrue(all(row['path'] == ':file-sha256' for row in findings))

    def test_measurement_mode_cannot_enter_controls_or_promotion(self):
        args = SimpleNamespace(measurement_mode=True, derive_runtime_list=False, lock_owner='owner', jar=self.jar,
                               source_commit='a', candidate_commit='b', jar_sha256='c', java='java',
                               session_dir=self.base / 'session', evidence_dir=self.base / 'evidence',
                               source_server=self.base / 'template')
        args.source_server.mkdir()
        (args.source_server / 'libraries').mkdir()
        argfile = args.source_server / runner._server_command('java')[-2].removeprefix('@')
        argfile.parent.mkdir(parents=True, exist_ok=True)
        argfile.touch()
        manifest = runner.oracle.manifest(self.source)
        row = dict(baseline_manifest=manifest)
        context = dict(original=self.source, backup=self.backup)
        with patch.object(runner, 'require_lock'), patch.object(runner, 'verify_candidate', return_value={}), \
                patch.object(runner, 'preflight'), patch.object(runner.evidence_tools, 'install_candidate'), \
                patch.object(runner, 'seed_world', return_value=(self.source, self.transfer, self.log)), \
                patch.object(runner, 'refusal', return_value={}), \
                patch.object(runner, 'repaired_cycle', return_value=(row, context)) as cycle, \
                patch.object(runner, 'controls') as controls, patch.object(runner, 'start') as start:
            report = runner.run(args)
        self.assertEqual(2, cycle.call_count)
        controls.assert_not_called()
        start.assert_not_called()
        self.assertEqual('MEASURED_NOT_PROMOTED', report['status'])
        self.assertFalse(report['promotion_approved'])
        self.assertNotIn('whole_world_replacement', report)
        self.assertTrue(report['cleanup'])

    def test_dry_run_stops_at_first_semantic_contradiction(self):
        args = SimpleNamespace(jar=self.jar, derive_runtime_list=True)
        with patch.object(runner, 'seed_world', return_value=(self.source, self.transfer, self.log)), \
                patch.object(runner, 'start', return_value=([], self.log)) as start, \
                patch.object(runner.oracle, 'observe'), \
                patch.object(runner.oracle, 'compare_repair', side_effect=runner.oracle.OracleError('OBJECT_CHANGED:collateral')):
            with self.assertRaisesRegex(runner.oracle.OracleError, '^OBJECT_CHANGED:collateral$'):
                runner.repaired_cycle(args, self.base, self.reports, 1)
        self.assertEqual(1, start.call_count)
        self.assertFalse((self.base / 'functional-cycle-1').exists())

    def test_fixture_witness_is_accepted_as_confirmed_without_editing_worlds(self):
        before = self.hashes(self.source)
        witness = runner.fixture_witness(runner.oracle.read(self.source), self.transfer, self.base / 'witness.json')
        plan = self.plan(witness=witness)
        self.assertEqual('CONFIRMED', plan['body']['account']['status'])
        self.assertEqual(0, plan['body']['account']['discrepancy'])
        self.assertEqual(before, self.hashes(self.source))

    def test_current_root_and_uninterpreted_row_refusals_are_no_effect(self):
        args = SimpleNamespace(jar=self.jar)
        for name, reason, mutation in (('old', 'ROOT_NOT_CURRENT', runner.old_root),
                                       ('opaque', 'UNVALIDATED_AUTHORITY', runner.unknown_authority)):
            row = runner.refusal(args, self.source, self.log, self.transfer, self.reports, name, reason, mutation)
            self.assertEqual('REFUSED_AS_REQUIRED', row['status'])

    def control_inputs(self, positions=((0, 0),)):
        root, _ = runner.nbt.chunk(self.source / 'region', 4, 4)
        runner.nbt.write_chunk(self.source / 'region', 4, 4, root)
        # A non-target chunk, as the real native world's spawn area supplies.
        from test_loader_cut_nbt import region
        original = runner.oracle.chunks(self.source)
        terrain = {(key[1], key[2]): tag for key, tag in original.items() if key[0] == 'region/r.0.0.mca'}
        for position in positions:
            terrain[position] = C(sections=E(section(0, ['minecraft:air', 'minecraft:bedrock'], {})), block_entities=E())
        region(self.source / 'region', terrain)
        self.sync()
        plan = self.plan()
        runner.repair.apply(self.planfile, self.output, plan['digest'])
        manifest = runner.oracle.manifest(self.source)
        stopped = runner.oracle.manifest(self.output)
        cycle = dict(baseline_manifest=manifest, stages=[{}, dict(manifest=stopped)])
        context = dict(twice=self.output, baseline=runner.oracle.read(self.source), transfer=self.transfer)
        session = self.base / 'controls'
        session.mkdir()
        return session, [cycle, copy.deepcopy(cycle)], context

    def test_both_controls_detect_actual_valid_file_mutations(self):
        session, cycles, context = self.control_inputs()
        results = runner.controls(None, session, self.reports, cycles, context)
        self.assertEqual(['unlisted-file', 'listed-object'], [row['control'] for row in results])
        self.assertTrue(all(row['target_checks'] == 'PASS' and row['status'] == 'REFUSED_AS_REQUIRED' for row in results))
        # W380R finding 3 (R501): the reason names the tampered object's exact key, then its path.
        self.assertEqual("('region/r.0.0.mca', 0, 0)", results[1]['location'])
        self.assertTrue(results[1]['reason'].startswith("OBJECT_CHANGED:('region/r.0.0.mca', 0, 0):/sections/"))
        self.assertEqual('UNLISTED_FILE:' + results[0]['location'], results[0]['reason'])

    def test_object_control_refuses_a_difference_in_any_other_object(self):
        # The slot-order rule selects chunk (1, 0). Chunk (0, 1) also differs and has the smaller key, so the
        # oracle names it; "some object changed" must not pass for "the tampered object was detected".
        tamper = runner.tamper_object

        def tamper_two(world):
            key = tamper(world)
            self.assertEqual(('region/r.0.0.mca', 1, 0), key)
            other, _ = runner.nbt.chunk(world / 'region', 0, 1)
            runner.nbt.set_block(other, 0, 0, 16, 'minecraft:bedrock')
            runner.nbt.write_chunk(world / 'region', 0, 1, other)
            return key

        session, cycles, context = self.control_inputs(positions=((1, 0), (0, 1)))
        with patch.object(runner, 'tamper_object', side_effect=tamper_two):
            with self.assertRaisesRegex(runner.oracle.OracleError,
                                        r"^CONTROL_FAILED_FOR_OTHER_REASON:OBJECT_CHANGED:\('region/r.0.0.mca', 0, 1\):"):
                runner.controls(None, session, self.reports, cycles, context)
        self.assertFalse((self.reports / 'listed-object-control.json').exists())

    def test_object_control_requires_the_whole_key(self):
        tamper = runner.tamper_object
        session, cycles, context = self.control_inputs()
        with patch.object(runner, 'tamper_object', side_effect=lambda world: tamper(world)[:2]):
            with self.assertRaisesRegex(runner.oracle.OracleError,
                                        r"^CONTROL_FAILED_FOR_OTHER_REASON:OBJECT_CHANGED:\('region/r.0.0.mca', 0, 0\):"):
                runner.controls(None, session, self.reports, cycles, context)


class NativeRefusalSeedTest(RepairFixture):
    """Design 5.3's three natively seeded refusals (W380R finding 1, R501); no server is started here."""

    FENCE = '[Server thread/WARN] ARCE_TRANSFER_FENCED transfer={} reason={} repair=offline-whole-world'

    def stale_prepared_disk(self):
        disk = stale_disk()
        disk['rockets'].append(seed_rocket(x=76.5, cargo=5000, epoch=0, bound=False))
        disk['rocket'] = None
        return disk

    def seeded(self, case, disk, reason='FLOOR_CONFLICT'):
        """seed_world with the starts and the raw read replaced; the seed family's real oracle judges `disk`."""
        lines = [self.FENCE.format(self.transfer, reason)]
        args = SimpleNamespace(jar=self.jar)
        with patch.object(runner, 'start', return_value=(lines, self.log)) as start, \
                patch.object(runner, 'transfer_id', return_value=self.transfer), \
                patch.object(runner.seed, 'inspect', return_value=disk) as inspect, \
                patch.object(runner.seed, 'check', wraps=runner.seed.check) as check:
            result = runner.seed_world(args, self.base, self.reports, 'seed', case)
        return result, start, inspect, check

    def test_the_seed_table_is_the_three_rows_of_section_5_3(self):
        self.assertEqual((('keep-source', 'FF_KEEP_SOURCE', 'DESTINATION_IN_TARGET_CHUNK', True),
                          ('stale-prepared', 'FF_STALE_PREPARED', 'SECOND_PHYSICAL_ROCKET', False),
                          ('reconnect', 'FF_RECONNECT', 'PASSENGER_ASSIGNMENT', False)), runner.NATIVE_REFUSAL_SEEDS)
        for _, case, _, _ in runner.NATIVE_REFUSAL_SEEDS:
            self.assertEqual('C-S5', runner.seed.CASES[case]['family'])
            self.assertEqual('FLOOR_CONFLICT', runner.seed.CASES[case]['fence'])

    def test_each_new_seed_is_one_forced_and_two_protected_starts_checked_by_the_seed_oracle(self):
        for case, disk in (('FF_STALE_PREPARED', self.stale_prepared_disk()), ('FF_RECONNECT', stale_disk())):
            with self.subTest(case=case):
                (world, transfer, log), start, inspect, check = self.seeded(case, disk)
                self.assertEqual((self.base / 'world-seed', self.transfer, self.log), (world, transfer, log))
                self.assertEqual([('seed-forced', case, 'start', True), ('seed-protected-1', case, 'recover', False),
                                  ('seed-protected-2', case, 'recover', False)],
                                 [(call.args[3], call.args[5], call.args[6], call.kwargs.get('forced', False))
                                  for call in start.call_args_list])
                self.assertTrue(all(call.args[4] == world for call in start.call_args_list))
                # The raw files are read and judged after each protected start, as restarted final states.
                self.assertEqual([((world,),)] * 2, [(call.args,) for call in inspect.call_args_list])
                self.assertEqual([(case, disk, True)] * 2, [call.args for call in check.call_args_list])

    def test_a_world_of_another_shape_is_not_a_refusal_seed(self):
        second = seed_rocket(x=76.5, cargo=5000, epoch=0, bound=False)
        broken = {
            'FF_STALE_PREPARED': [lambda d: d['rockets'].pop(), lambda d: d['rockets'][1].update(cargo=4000),
                                  lambda d: d['rockets'][1].update(x=80.5), lambda d: d.update(journal=0),
                                  lambda d: d['rockets'].append(copy.deepcopy(second))],
            'FF_RECONNECT': [lambda d: d['rockets'].append(copy.deepcopy(second)), lambda d: d.update(journal=0),
                             lambda d: d['rockets'][0].update(passengers=[dict(seat=0)]),
                             lambda d: d['rockets'][0].update(cargo=5000), lambda d: d['device'].update(amount=0)]}
        for case, mutations in broken.items():
            for index, mutate in enumerate(mutations):
                disk = self.stale_prepared_disk() if case == 'FF_STALE_PREPARED' else stale_disk()
                mutate(disk)
                with self.subTest(case=case, mutation=index), self.assertRaises(runner.smoke.SmokeError):
                    self.seeded(case, disk)

    def test_a_new_seed_without_its_declared_fence_reason_is_not_a_refusal_seed(self):
        for case, disk in (('FF_STALE_PREPARED', self.stale_prepared_disk()), ('FF_RECONNECT', stale_disk())):
            with self.subTest(case=case), self.assertRaisesRegex(runner.oracle.OracleError, '^SEED_FENCE_MISSING:' + case + '$'):
                self.seeded(case, disk, reason='WAITING_UNLOADED')

    def run_family(self, seed_world, refusal):
        args = SimpleNamespace(measurement_mode=True, derive_runtime_list=False, lock_owner='owner', jar=self.jar,
                               source_commit='a', candidate_commit='b', jar_sha256='c', java='java',
                               session_dir=self.base / 'session', evidence_dir=self.base / 'evidence',
                               source_server=self.base / 'template')
        args.source_server.mkdir()
        (args.source_server / 'libraries').mkdir()
        argfile = args.source_server / runner._server_command('java')[-2].removeprefix('@')
        argfile.parent.mkdir(parents=True, exist_ok=True)
        argfile.touch()
        row = dict(baseline_manifest=runner.oracle.manifest(self.source))
        context = dict(original=self.source, backup=self.backup)
        with patch.object(runner, 'require_lock'), patch.object(runner, 'verify_candidate', return_value={}), \
                patch.object(runner, 'preflight'), patch.object(runner.evidence_tools, 'install_candidate'), \
                patch.object(runner, 'seed_world', side_effect=seed_world) as seeds, \
                patch.object(runner, 'refusal', side_effect=refusal) as refusals, \
                patch.object(runner, 'repaired_cycle', return_value=(row, context)) as cycle:
            try:
                report = runner.run(args)
            except (runner.smoke.SmokeError, runner.oracle.OracleError) as error:
                report = error
        return report, seeds, refusals, cycle, args

    def test_run_seeds_and_refuses_all_three_before_the_first_cycle(self):
        order = []

        def seed_world(args, session, evidence, label, case):
            order.append(('seed', label, case))
            return session / ('world-' + label), self.transfer, self.log

        def refusal(args, source, log, transfer, evidence, name, reason, mutate=None, exact=False):
            order.append(('plan', name, source.name, reason, exact, mutate))
            return dict(case=name, status='REFUSED_AS_REQUIRED', reasons=[reason, 'OTHER'])

        report, seeds, refusals, cycle, args = self.run_family(seed_world, refusal)
        self.assertEqual([('seed', 'keep-source', 'FF_KEEP_SOURCE'),
                          ('plan', 'keep-source', 'world-keep-source', 'DESTINATION_IN_TARGET_CHUNK', True, None),
                          ('seed', 'stale-prepared', 'FF_STALE_PREPARED'),
                          ('plan', 'stale-prepared', 'world-stale-prepared', 'SECOND_PHYSICAL_ROCKET', False, None),
                          ('seed', 'reconnect', 'FF_RECONNECT'),
                          ('plan', 'reconnect', 'world-reconnect', 'PASSENGER_ASSIGNMENT', False, None)], order)
        self.assertEqual(2, cycle.call_count)
        self.assertEqual([('FF_KEEP_SOURCE', 'DESTINATION_IN_TARGET_CHUNK'), ('FF_STALE_PREPARED', 'SECOND_PHYSICAL_ROCKET'),
                          ('FF_RECONNECT', 'PASSENGER_ASSIGNMENT')],
                         [(row['seed'], row['required_reason']) for row in report['refusals']])
        # Reasons beside the required one are kept for the record.
        self.assertTrue(all(row['reasons'][1:] == ['OTHER'] and row['transfer'] == self.transfer for row in report['refusals']))

    def test_a_failed_seed_stops_the_family_before_any_cycle(self):
        def broken_seed(args, session, evidence, label, case):
            if case == 'FF_STALE_PREPARED':
                raise runner.smoke.SmokeError('Raw rockets differ: []')
            return session / ('world-' + label), self.transfer, self.log

        def refused(args, source, log, transfer, evidence, name, reason, mutate=None, exact=False):
            return dict(case=name, status='REFUSED_AS_REQUIRED', reasons=[reason])

        error, seeds, refusals, cycle, args = self.run_family(broken_seed, refused)
        self.assertIsInstance(error, runner.smoke.SmokeError)
        self.assertEqual((2, 1, 0), (seeds.call_count, refusals.call_count, cycle.call_count))
        self.assertEqual('FAILED', runner.json.loads((args.evidence_dir / 'results.json').read_text(encoding='utf-8'))['status'])

    def test_an_accepted_plan_on_a_seed_stops_the_family_before_any_cycle(self):
        def seed_world(args, session, evidence, label, case):
            return session / ('world-' + label), self.transfer, self.log

        def accepted(args, source, log, transfer, evidence, name, reason, mutate=None, exact=False):
            if name == 'reconnect':
                raise runner.oracle.OracleError('REFUSAL_ACCEPTED:' + name)
            return dict(case=name, status='REFUSED_AS_REQUIRED', reasons=[reason])

        error, seeds, refusals, cycle, args = self.run_family(seed_world, accepted)
        self.assertEqual('REFUSAL_ACCEPTED:reconnect', str(error))
        self.assertEqual((3, 3, 0), (seeds.call_count, refusals.call_count, cycle.call_count))
        report = runner.json.loads((args.evidence_dir / 'results.json').read_text(encoding='utf-8'))
        self.assertEqual(('FAILED', 2), (report['status'], len(report['refusals'])))

    def native_destination(self):
        """The seeds' record puts its destination eight blocks east, in the source chunk (EscrowCargoRecoveryCuts)."""
        record = runner.repair.at(self.data['rocket_transfers'], 'transfers').value[1][0]
        record.value['destination_snapshot'].value['source_origin'] = A(76, 100, 64)
        record.value['destination_flight'].value['current_origin'] = A(76, 100, 64)
        return record

    def stale_prepared_world(self):
        record = self.native_destination()
        self.write_roots()
        landed = copy.deepcopy(record.value['destination_flight'])
        landed.value['state'] = S('LANDED')
        for key in ('plan', 'active_transfer_id'):
            landed.value.pop(key)
        stale = C(id=S('advancedrocketrycommunity:rocket'), UUID=U(30), Pos=vector(76.5, 100, 64.5), Motion=vector(0, 0, 0),
                  RocketEntityData=C(schema_version=I(3), assembly_transaction_id=U(2), owner_id=U(4),
                                     resource_escrow=C(schema_version=I(1), resource_epoch=L(0), markers=E()),
                                     snapshot=copy.deepcopy(record.value['destination_snapshot']), flight_data=landed))
        self.write_entities([self.entity, stale])
        self.sync()

    def reconnect_world(self):
        record = self.native_destination()
        for key in ('source_flight', 'destination_flight'):
            record.value[key].value['passengers'] = C(seat_capacity=I(1), assignments=E(C(seat_index=I(0), passenger=U(9))))
        self.write_roots()
        self.sync()

    def test_the_tool_refuses_both_synthetic_seed_shapes_with_the_required_reason_and_no_effect(self):
        args = SimpleNamespace(jar=self.jar)
        for name, build, reason, beside in (
                ('stale-prepared', self.stale_prepared_world, 'SECOND_PHYSICAL_ROCKET',
                 ['ADDITIONAL_ROCKET_IN_FOOTPRINT', 'DESTINATION_IN_TARGET_CHUNK']),
                ('reconnect', self.reconnect_world, 'PASSENGER_ASSIGNMENT', ['DESTINATION_IN_TARGET_CHUNK'])):
            with self.subTest(name=name):
                self.seed()
                build()
                before = self.hashes(self.source)
                row = runner.refusal(args, self.source, self.log, self.transfer, self.reports, name, reason)
                self.assertEqual(('REFUSED_AS_REQUIRED', [reason] + beside), (row['status'], row['reasons']))
                self.assertEqual(before, self.hashes(self.source))
                self.assertFalse((self.reports / (name + '-plan.json')).exists())
                self.assertEqual(row, runner.json.loads((self.reports / (name + '-refusal.json')).read_text(encoding='utf-8')))
                # The required reason must be present: another refusal, or an accepted plan, is not this control.
                for other, wanted in (('wrong', 'PASSENGER_ASSIGNMENT' if name == 'stale-prepared' else 'SECOND_PHYSICAL_ROCKET'),
                                      ('alone', reason)):
                    with self.assertRaisesRegex(runner.oracle.OracleError, '^WRONG_REFUSAL:'):
                        runner.refusal(args, self.source, self.log, self.transfer, self.reports, name + '-' + other, wanted,
                                       exact=other == 'alone')

    def test_the_unseeded_fixture_is_not_refused_for_either_reason(self):
        args = SimpleNamespace(jar=self.jar)
        for reason in ('SECOND_PHYSICAL_ROCKET', 'PASSENGER_ASSIGNMENT'):
            with self.subTest(reason=reason), self.assertRaisesRegex(runner.oracle.OracleError, '^REFUSAL_ACCEPTED:plain-'):
                runner.refusal(args, self.source, self.log, self.transfer, self.reports, 'plain-' + reason, reason)


if __name__ == "__main__":
    unittest.main()
