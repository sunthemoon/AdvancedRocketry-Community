#!/usr/bin/env python3
"""X03 root-2 native repair family. Run under the caller's heavy-JVM lock.

--derive-runtime-list collects two complete, non-promotable development runs.
It reports deviations instead of authorizing them. Campaign runs use only the
fixed allowances below and stop on the first failed check.
"""
from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
import re
import shutil
import sys
import uuid

import bay_integrated_evidence as evidence_tools
import loader_cut_nbt as nbt
import repair_v180_rebuild_source as repair
import run_dedicated_server_smoke as smoke
import run_v180_cargo_s4_s5_cuts as seed
from run_v180_cargo_integrated import verify_candidate
from run_v180_cargo_reconciliation import preflight
from run_v180_rocket_settlement_completion import require_lock
import x03_repair_oracle as oracle
from run_v060_flight_server_smoke import _server_command

REPO = Path(__file__).resolve().parents[1]
FAMILY = 'cargo-X03-REPAIR'
ROUTE = 'MOON'
# Candidate list; Root reviews both dry-run reports before using this family in
# an acceptance campaign. These are exact names, not directory wildcards.
RUNTIME_FILES = {
    'level.dat': 'Vanilla full save of level metadata and game time.',
    'level.dat_old': 'Vanilla previous level metadata retained at every save.',
    'session.lock': 'Vanilla rewrites and locks this file at each start.',
    repair.JOURNAL: 'Checked journal baseline capture in each verification start.',
    'region/r.0.0.mca': 'Source, pad and device chunks held by probe tickets; chunk clocks advance.',
    'entities/r.0.0.mca': 'Source rocket entity storage flushed by the probe.',
}
SCENARIO = "verify"
# The launch routes the Java probe accepts: the names of RocketDestination. The runner fixes the route (section 5.1).
ROUTES = ("EARTH", "MOON", "SPACE_STATION")
# One distinct server process per case. `state` is the state of the probe's result line.
CASES = {
    "FF_X03_OBSERVE": dict(state="OBSERVED", functional=False),
    "FF_X03_UNLOAD": dict(state="UNLOADED", functional=True),
    "FF_X03_LAUNCH": dict(state="LANDED", functional=True),
}
# The functional descendant's starts, in order; each is followed by a clean stop (section 5.1).
DESCENDANT_SEQUENCE = ("FF_X03_UNLOAD", "FF_X03_OBSERVE", "FF_X03_LAUNCH", "FF_X03_OBSERVE")


def verification_command(java: str, case: str, route: str | None = None) -> list[str]:
    """The command of one verification start: the release-test hooks, the verify scenario and exactly one case."""
    if case not in CASES:
        raise ValueError(f"Unknown X03 verification case: {case!r}")
    if route is not None and (case != "FF_X03_LAUNCH" or route not in ROUTES):
        raise ValueError(f"Route {route!r} is not accepted for {case}")
    command = _server_command(java)
    command[2] = "-Xmx4G"
    properties = ["-Dadvancedrocketrycommunity.escrowScenario=" + SCENARIO,
                  "-Dadvancedrocketrycommunity.escrowCase=" + case]
    if route is not None:
        properties.append("-Dadvancedrocketrycommunity.escrowVerifyRoute=" + route)
    command[1:1] = properties
    return command


def marker(case: str) -> str:
    """The prefix of the probe's single result line for `case`."""
    return f"ARCE_RELEASE_CARGO_CUT_RESULT cut={case} state={CASES[case]['state']} "


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def copy_verified(source, output):
    before = oracle.manifest(source)
    shutil.copytree(source, output)
    oracle.require(oracle.manifest(output) == before and oracle.manifest(source) == before, 'COPY_HASH_MISMATCH')
    return before


def check_fence(lines, transfer):
    expected = 'ARCE_TRANSFER_FENCED transfer=' + transfer + ' reason=FLOOR_CONFLICT repair=offline-whole-world'
    fences = [line for line in lines if 'ARCE_TRANSFER_FENCED' in line]
    oracle.require(bool(fences) and all(expected in line for line in fences), 'SEED_FLOOR_CONFLICT_MISSING_OR_OTHER_TRANSFER')


def transfer_id(world):
    data = nbt.plain(nbt.read_gzip(world / repair.JOURNAL)[1])['data']['transfers']
    oracle.require(len(data) == 1, 'SEED_TRANSFER_COUNT')
    words = data[0]['transfer_id']
    return str(uuid.UUID(bytes=b''.join((word & 0xffffffff).to_bytes(4, 'big') for word in words)))


def start(args, session, evidence, label, world, case, scenario, *, forced=False):
    require_lock(args.lock_owner)
    oracle.require(bool(args.lock_owner), 'LOCK_OWNER_REQUIRED')
    verify_candidate(args.jar, args.source_commit, args.candidate_commit, args.jar_sha256)
    preflight()
    port = smoke.allocate_port()
    smoke.write_server_configuration(session, port, True)
    properties = smoke.expected_server_properties(port, True)
    properties.update({'level-name': world.name, 'level-type': 'minecraft:flat', 'max-tick-time': '60000',
                       'generator-settings': json.dumps({'layers': [{'block': 'minecraft:bedrock', 'height': 1}], 'biome': 'minecraft:plains'})})
    (session / 'server.properties').write_text(''.join(f'{k}={v}\n' for k, v in sorted(properties.items())), encoding='ascii')
    if scenario == SCENARIO:
        command = verification_command(args.java, case, ROUTE if case == 'FF_X03_LAUNCH' else None)
        expected = marker(case)
    else:
        command = _server_command(args.java)
        command[2] = '-Xmx4G'
        command[1:1] = ['-Dadvancedrocketrycommunity.escrowScenario=' + scenario,
                         '-Dadvancedrocketrycommunity.escrowCase=' + case]
        expected = seed.marker(case, 0 if forced else 1)
    log = evidence / (label + '.log')
    row = dict(label=label, case=case, scenario=scenario, command=command, log=log.name)
    write_json(evidence / (label + '-invocation.json'), row)
    process = smoke.CapturedProcess(command, session, log)
    try:
        process.wait_for(re.compile(re.escape(expected)), args.startup_timeout)
        if forced:
            process.process.kill()
            process.abort()
            row['forced_stop'] = True
        else:
            process.command('stop')
            row['exit_code'] = process.finish()
            oracle.require(row['exit_code'] == 0, 'UNCLEAN_STOP:' + label)
        oracle.require(not any('/ERROR]' in line or '/FATAL]' in line for line in process.lines), 'NATIVE_ERROR:' + label)
        row['status'] = 'STOPPED'
        row['log_sha256'] = oracle.digest(log)
        row['manifest'] = oracle.manifest(world)
        write_json(evidence / (label + '-invocation.json'), row)
        return process.lines, log
    finally:
        if process.process.poll() is None:
            process.abort()


def seed_world(args, session, evidence, label, case='FF_REBUILD_SOURCE'):
    world = session / ('world-' + label)
    start(args, session, evidence, label + '-forced', world, case, 'start', forced=True)
    transfer = transfer_id(world)
    for cycle in (1, 2):
        lines, log = start(args, session, evidence, label + '-protected-' + str(cycle), world, case, 'recover')
        if case in ('FF_REBUILD_SOURCE', 'FF_KEEP_SOURCE'):
            check_fence(lines, transfer)
        disk = seed.inspect(world)
        seed.check(case, disk, True)
    return world, transfer, log


def make_plan(args, source, backup, log, transfer, report, witness=None):
    return repair.plan(source, backup, args.jar, log, report, transfer, repair.PROFILE_DISABLED,
                       'W380 native harness', 'X03 whole-world copy repair; no resource reconstruction', witness)


def refusal(args, source, log, transfer, evidence, name, reason, mutate=None, exact=False):
    original = oracle.manifest(source)
    working = source.parent / ('refusal-source-' + name)
    copy_verified(source, working)
    if mutate:
        mutate(working)
    backup = working.with_name(working.name + '-backup')
    copy_verified(working, backup)
    before = oracle.manifest(working)
    try:
        make_plan(args, working, backup, log, transfer, evidence / (name + '-plan.json'))
    except repair.Refusal as error:
        matches = [r for r in error.reasons if r == reason or r.startswith(reason + ':')]
        oracle.require(bool(matches) and (not exact or error.reasons == [reason]), 'WRONG_REFUSAL:' + repr(error.reasons))
        oracle.require(oracle.manifest(working) == before == oracle.manifest(backup) and oracle.manifest(source) == original,
                       'REFUSAL_CHANGED_INPUT')
        result = dict(case=name, status='REFUSED_AS_REQUIRED', reasons=error.reasons, manifest=before)
        write_json(evidence / (name + '-refusal.json'), result)
        return result
    raise oracle.OracleError('REFUSAL_ACCEPTED:' + name)


def old_root(world):
    path = world / repair.LEDGER
    name, root = nbt.read_gzip(path)
    root.value['data'].value['schema_version'].value = 1
    nbt.write_gzip(path, name, root)


def unknown_authority(world):
    path = world / repair.LEDGER
    name, root = nbt.read_gzip(path)
    root.value['data'].value['rocket_authorities'] = nbt.Tag(nbt.LIST, (nbt.COMPOUND, [nbt.Tag(nbt.COMPOUND, {'opaque': nbt.Tag(nbt.LONG, 7)})]))
    nbt.write_gzip(path, name, root)


def measure(check, findings, derive):
    try:
        return check()
    except (oracle.OracleError, repair.Refusal) as error:
        if not derive:
            raise
        findings.append(str(error))
        return None


def numeric_leaf(tag):
    """Change an existing numeric value, never add a record or a field."""
    if tag.type in (nbt.BYTE, nbt.SHORT, nbt.INT, nbt.LONG):
        tag.value = 0 if tag.value else 1
        return True
    children = tag.value.values() if tag.type == nbt.COMPOUND else tag.value[1] if tag.type == nbt.LIST else ()
    return any(numeric_leaf(child) for child in children)


def tamper_file(world, relative):
    path = world / relative
    if path.suffix == '.dat':
        name, root = nbt.read_gzip(path)
        oracle.require(numeric_leaf(root), 'NO_NUMERIC_TAMPER_LEAF')
        nbt.write_gzip(path, name, root)
        nbt.read_gzip(path)
        return relative
    records = oracle.chunks(world)
    key = next((key for key in records if key[0] == relative), None)
    oracle.require(key is not None, 'EMPTY_TAMPER_REGION')
    root = records[key]
    oracle.require(numeric_leaf(root), 'NO_NUMERIC_TAMPER_LEAF')
    nbt.write_chunk(path.parent, key[1], key[2], root)
    nbt.chunk(path.parent, key[1], key[2])
    return repr(key)


def tamper_object(world):
    records = oracle.chunks(world)
    # r.0.0.mca slot order; exclude the rocket/device chunk [4,4] and pad [5,4].
    keys = sorted((k for k in records if k[0] == 'region/r.0.0.mca' and (k[1], k[2]) not in ((4, 4), (5, 4))),
                  key=lambda key: (key[2] % 32) * 32 + key[1] % 32)
    oracle.require(bool(keys), 'NO_NONTARGET_CHUNK')
    key = keys[0]
    root = records[key]
    section = root.value['sections'].value[1][0].value['Y'].value
    x, y, z = key[1] * 16, section * 16, key[2] * 16
    old = nbt.block(root, x, y, z)
    nbt.set_block(root, x, y, z, 'minecraft:stone' if old != 'minecraft:stone' else 'minecraft:dirt')
    nbt.write_chunk(world / 'region', key[1], key[2], root)
    return key


def coordinator(context, world, baseline_manifest):
    current = oracle.read(world)
    oracle.check_state(current, context['baseline'])
    oracle.boundary(baseline_manifest, oracle.manifest(world), RUNTIME_FILES)
    oracle.compare_repair(context['baseline'], current, context['transfer'])


def controls(args, session, evidence, cycles, context):
    stable = []
    for cycle in cycles:
        baseline = cycle['baseline_manifest']
        stopped = cycle['stages'][1]['manifest']
        stable.append({name for name in baseline if stopped.get(name) == baseline[name]})
    baseline = cycles[-1]['baseline_manifest']
    common = {name: baseline[name] for name in stable[0] & stable[1]}
    target = oracle.tamper_target(common, [common, common], RUNTIME_FILES)
    results = []
    for name in ('unlisted-file', 'listed-object'):
        control = session / ('control-' + name)
        copy_verified(context['twice'], control)
        location = tamper_file(control, target) if name == 'unlisted-file' else repr(tamper_object(control))
        # This check alone deliberately cannot see either collateral mutation.
        # The shipped coordinator below always includes both independent boundaries.
        oracle.check_state(oracle.read(control), context['baseline'])
        try:
            coordinator(context, control, baseline)
        except oracle.OracleError as error:
            required = 'UNLISTED_FILE:' + target if name == 'unlisted-file' else 'OBJECT_CHANGED:'
            oracle.require(str(error).startswith(required), 'CONTROL_FAILED_FOR_OTHER_REASON:' + str(error))
            result = dict(control=name, location=location, target_checks='PASS', status='REFUSED_AS_REQUIRED', reason=str(error))
            results.append(result)
            write_json(evidence / (name + '-control.json'), result)
        else:
            raise oracle.OracleError('CONTROL_NOT_DETECTED:' + name)
    return results


def repaired_cycle(args, session, evidence, number):
    label = 'cycle-' + str(number)
    original, transfer, log = seed_world(args, session, evidence, label)
    baseline = oracle.read(original)
    oracle.check_state(baseline, baseline, repaired=False)
    write_json(evidence / (label + '-baseline.json'),
               {key: nbt.plain(baseline[key]) if isinstance(baseline[key], nbt.Tag) else baseline[key]
                for key in ('entity', 'block', 'ledger', 'journal', 'cargo', 'tank')})
    backup = session / ('backup-' + label)
    original_hashes = copy_verified(original, backup)
    planfile = evidence / (label + '-plan.json')
    # The seed's ordinary fixture is the numeric before-witness. It created a
    # 5000/0 water pair, then logged the applied ordinary unload before the cut.
    document = make_plan(args, original, backup, log, transfer, planfile)
    candidate = session / ('candidate-' + label)
    applied = repair.apply(planfile, candidate, document['digest'])
    oracle.require(oracle.differences(original_hashes, oracle.manifest(candidate)) == dict(added=[], removed=[], changed=[repair.JOURNAL]),
                   'OFFLINE_EDIT_BOUNDARY')
    findings, stages, snapshots = [], [], []
    for restart in (1, 2, 3):
        lines, _ = start(args, session, evidence, label + '-observe-' + str(restart), candidate, 'FF_X03_OBSERVE', SCENARIO)
        state = oracle.read(candidate)
        write_json(evidence / (label + '-observe-' + str(restart) + '-raw.json'),
                   dict(entity=nbt.plain(state['entity']), block=nbt.plain(state['block']),
                        managed={name: nbt.plain(root) for name, root in state['managed'].items()},
                        object_differences=[dict(file=k[0], chunk=list(k[1:]),
                                                 path=oracle.first_difference(baseline['chunks'][k], state['chunks'][k]))
                                            for k in baseline['chunks'].keys() & state['chunks'].keys()
                                            if baseline['chunks'][k] != state['chunks'][k]]))
        oracle.observe(lines, 'FF_X03_OBSERVE', state)
        measure(lambda: oracle.compare_repair(baseline, state, transfer), findings, args.derive_runtime_list)
        manifest = oracle.manifest(candidate)
        diff = oracle.differences(original_hashes, manifest)
        measure(lambda: oracle.boundary(original_hashes, manifest, RUNTIME_FILES), findings, args.derive_runtime_list)
        measure(lambda: repair.census(planfile, candidate, evidence / (label + '-census-' + str(restart) + '.json'), True), findings, args.derive_runtime_list)
        stages.append(dict(restart=restart, differences=diff, manifest=manifest))
        if restart == 2:
            # Functional verification is a separate descendant, never promoted.
            twice = session / ('twice-' + label)
            copy_verified(candidate, twice)
            snapshots.append(manifest)
    descendant = session / ('functional-' + label)
    copy_verified(twice, descendant)
    functional = []
    before = oracle.read(descendant)
    for i, case in enumerate(DESCENDANT_SEQUENCE):
        lines, _ = start(args, session, evidence, label + '-functional-' + str(i), descendant, case, SCENARIO)
        state = oracle.read(descendant)
        launched = i >= 2
        oracle.check_state(state, baseline, unloaded=True, launched=launched)
        fields = oracle.observe(lines, case, state, landed=launched)
        if case == 'FF_X03_UNLOAD':
            oracle.require(fields.get('switch') == 'withdrawn', 'UNLOAD_SWITCH_NOT_WITHDRAWN')
        action = 'unload' if i == 0 else 'launch' if i == 2 else None
        measure(lambda: oracle.compare_functional(before, state, action=action), findings, args.derive_runtime_list)
        debit = oracle.launch_debit(fields, before, state) if case == 'FF_X03_LAUNCH' else None
        functional.append(dict(case=case, observation=fields, debit=debit,
                               differences=oracle.differences(snapshots[0], oracle.manifest(descendant))))
        before = state
    oracle.require(oracle.manifest(original) == original_hashes == oracle.manifest(backup), 'SOURCE_OR_BACKUP_CHANGED')
    row = dict(status='MEASURED_NOT_PROMOTED' if args.derive_runtime_list else 'VERIFIED_NOT_PROMOTED',
               transfer=transfer, baseline_manifest=original_hashes, stages=stages, functional=functional,
               findings=findings, apply=applied, plan_digest=document['digest'])
    write_json(evidence / (label + '-result.json'), row)
    return row, dict(original=original, backup=backup, candidate=candidate, twice=twice, plan=planfile,
                     transfer=transfer, baseline=baseline, log=log)


def run(args):
    oracle.require(bool(args.lock_owner), 'LOCK_OWNER_REQUIRED')
    require_lock(args.lock_owner)
    pins = verify_candidate(args.jar, args.source_commit, args.candidate_commit, args.jar_sha256)
    preflight()
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    oracle.require(not session.exists() and not evidence.exists() and session not in evidence.parents and evidence not in session.parents,
                   'EXISTING_OR_OVERLAPPING_OUTPUT')
    oracle.require(REPO not in session.parents and REPO not in evidence.parents, 'OUTPUT_INSIDE_REPOSITORY')
    evidence.mkdir(parents=True)
    session.mkdir(parents=True)
    token = uuid.uuid4().hex
    sentinel = session / '.w380-owned'
    sentinel.write_text(token, encoding='ascii')
    report = dict(status='IN_PROGRESS', pins=pins, runtime_files=RUNTIME_FILES, cycles=[], cleanup=False,
                  development=args.derive_runtime_list, promotion_approved=False,
                  harness={name: oracle.digest(REPO / 'scripts' / name) for name in
                           ('run_v180_x03_repair.py', 'x03_repair_oracle.py', 'repair_v180_rebuild_source.py', 'loader_cut_nbt.py')})
    try:
        shutil.copytree(args.source_server / 'libraries', session / 'libraries')
        (session / 'mods').mkdir()
        evidence_tools.install_candidate(args.jar, session / 'mods' / args.jar.name, args.jar_sha256)
        for number in (1, 2):
            row, context = repaired_cycle(args, session, evidence, number)
            report['cycles'].append(row)
            write_json(evidence / 'results.json', report)
        if args.derive_runtime_list:
            report['status'] = 'MEASURED_NOT_PROMOTED'
        else:
            report['refusals'] = [refusal(args, context['original'], context['log'], context['transfer'], evidence,
                                         'old-root', 'ROOT_NOT_CURRENT', old_root),
                                  refusal(args, context['original'], context['log'], context['transfer'], evidence,
                                          'unknown-authority', 'UNVALIDATED_AUTHORITY', unknown_authority)]
            keep, transfer, log = seed_world(args, session, evidence, 'keep-source', 'FF_KEEP_SOURCE')
            report['refusals'].append(refusal(args, keep, log, transfer, evidence, 'keep-source',
                                              'DESTINATION_IN_TARGET_CHUNK', exact=True))
            report['controls'] = controls(args, session, evidence, report['cycles'], context)
            # Promotion replaces the entire original directory, with a verified
            # sibling first. Never overlay stores. The descendant is not used.
            original_before = oracle.manifest(context['original'])
            staged = session / 'promotion-stage'
            expected = copy_verified(context['candidate'], staged)
            preserved = session / 'original-preserved'
            context['original'].rename(preserved)
            staged.rename(context['original'])
            oracle.require(oracle.manifest(context['original']) == expected
                           and oracle.manifest(preserved) == original_before
                           and oracle.manifest(context['backup']) == original_before, 'PROMOTION_COPY_DIFFERS')
            report['whole_world_replacement'] = dict(status='REPLACED_COMPLETE_COPY', manifest=expected)
            report['status'] = 'PASS_DEVELOPMENT_ROOT_2'
    except BaseException as error:
        report.update(status='FAILED', error=str(error))
        raise
    finally:
        if sentinel.is_file() and sentinel.read_text(encoding='ascii') == token and session.resolve() == session and not session.is_symlink():
            shutil.rmtree(session)
            report['cleanup'] = not session.exists()
        write_json(evidence / 'results.json', report)
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source_server', type=Path)
    for name in ('jar', 'session-dir', 'evidence-dir'):
        parser.add_argument('--' + name, type=Path, required=True)
    for name in ('source-commit', 'candidate-commit', 'jar-sha256', 'java', 'lock-owner'):
        parser.add_argument('--' + name, required=True)
    parser.add_argument('--startup-timeout', type=float, default=420)
    parser.add_argument('--derive-runtime-list', action='store_true')
    try:
        run(parser.parse_args(argv))
        return 0
    except (ValueError, OSError, smoke.SmokeError) as error:
        print('FAIL: ' + str(error), flush=True)
        return 1


if __name__ == '__main__':
    sys.exit(main())
