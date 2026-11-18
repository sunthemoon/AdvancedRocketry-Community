"""X03 command, seed-fence and orchestration checks; never starts a server."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import tempfile
from types import SimpleNamespace
import copy

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_v180_x03_repair as runner
from test_repair_v180_rebuild_source import RepairFixture, E
from test_loader_cut_nbt import compound as C, section

HOOKS = "-Dadvancedrocketrycommunity.releaseTestHooks=true"
SCENARIO = "-Dadvancedrocketrycommunity.escrowScenario="
CASE = "-Dadvancedrocketrycommunity.escrowCase="
ROUTE = "-Dadvancedrocketrycommunity.escrowVerifyRoute="


class VerificationCommandTest(unittest.TestCase):
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
        for name, reason in runner.RUNTIME_FILES.items():
            self.assertNotIn('*', name)
            self.assertNotIn('..', Path(name).parts)
            self.assertTrue(reason.strip())

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
        row = campaign.family_specs()[-1]
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

    def test_both_controls_detect_actual_valid_file_mutations(self):
        root, _ = runner.nbt.chunk(self.source / 'region', 4, 4)
        runner.nbt.write_chunk(self.source / 'region', 4, 4, root)
        # A non-target chunk, as the real native world's spawn area supplies.
        from test_loader_cut_nbt import region
        original = runner.oracle.chunks(self.source)
        terrain = {(key[1], key[2]): tag for key, tag in original.items() if key[0] == 'region/r.0.0.mca'}
        terrain[(0, 0)] = C(sections=E(section(0, ['minecraft:air', 'minecraft:bedrock'], {})), block_entities=E())
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
        results = runner.controls(None, session, self.reports, [cycle, copy.deepcopy(cycle)], context)
        self.assertEqual(['unlisted-file', 'listed-object'], [row['control'] for row in results])
        self.assertTrue(all(row['target_checks'] == 'PASS' and row['status'] == 'REFUSED_AS_REQUIRED' for row in results))


if __name__ == "__main__":
    unittest.main()
