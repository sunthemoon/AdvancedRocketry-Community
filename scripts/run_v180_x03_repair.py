#!/usr/bin/env python3
"""X03 accounting-enabled native repair family. Run under the caller's heavy-JVM lock.

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
import subprocess
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
# R490 / design 9.4: the reviewed union of W380G's two complete dry runs.
# Exact names, never directory wildcards; session.lock was unchanged and is absent.
RUNTIME_FILES = {
    'level.dat': 'Vanilla full save of level metadata and game time.',
    'level.dat_old': 'Vanilla previous level metadata retained at every save.',
    repair.JOURNAL: 'Checked journal baseline capture in each verification start.',
    'data/advancedrocketrycommunity_satellite_missions.dat': 'Monotonic mission clock autosave; only the reviewed equal-delta clock change is admitted.',
    **{name: 'Raids.tick advances data/Tick and marks dirty every 200 ticks; Raids.save checkpoints it in this Level.'
       for name in oracle.RAID_FILES},
    'region/r.-1.-1.mca': 'Vanilla spawn tickets; chunk clocks and region-header timestamps, all other object fields compared.',
    'region/r.-1.0.mca': 'Vanilla spawn tickets; chunk clocks and region-header timestamps, all other object fields compared.',
    'region/r.0.-1.mca': 'Vanilla spawn tickets; chunk clocks and region-header timestamps, all other object fields compared.',
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
# Design 5.3 (R501): `plan` must refuse each of these natively seeded worlds. Each row is the label, the
# seed's case, the required reason, and whether that reason must be the only one.
NATIVE_REFUSAL_SEEDS = (
    ('keep-source', 'FF_KEEP_SOURCE', 'DESTINATION_IN_TARGET_CHUNK', True),
    ('stale-prepared', 'FF_STALE_PREPARED', 'SECOND_PHYSICAL_ROCKET', False),
    ('reconnect', 'FF_RECONNECT', 'PASSENGER_ASSIGNMENT', False),
)


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
    fences = [line for line in lines if 'ARCE_TRANSFER_FENCED' in line]
    matches = [re.search(r'ARCE_TRANSFER_FENCED transfer=([0-9a-f-]{36}) reason=([A-Z_]+) repair=offline-whole-world(?:\s|$)', line)
               for line in fences]
    oracle.require(bool(matches)
                   and all(match is not None and match[1] == transfer
                           and match[2] in ('FLOOR_CONFLICT', 'ENDPOINT_JOIN_PROOF_REQUIRED') for match in matches)
                   and any(match[2] == 'FLOOR_CONFLICT' for match in matches),
                   'SEED_FLOOR_CONFLICT_MISSING_OR_OTHER_TRANSFER')


def transfer_id(world):
    data = nbt.plain(nbt.read_gzip(world / repair.JOURNAL)[1])['data']['transfers']
    oracle.require(len(data) == 1, 'SEED_TRANSFER_COUNT')
    words = data[0]['transfer_id']
    return str(uuid.UUID(bytes=b''.join((word & 0xffffffff).to_bytes(4, 'big') for word in words)))


def configure_session(session, world, port):
    # The smoke helper creates an immutable startup-properties witness. It is
    # a session operation, not a per-restart operation.
    if not (session / 'eula.txt').exists():
        smoke.write_server_configuration(session, port, True)
    properties = smoke.expected_server_properties(port, True)
    properties.update({'level-name': world.name, 'level-type': 'minecraft:flat', 'max-tick-time': '60000',
                       'generator-settings': json.dumps({'layers': [{'block': 'minecraft:bedrock', 'height': 1}], 'biome': 'minecraft:plains'})})
    (session / 'server.properties').write_text(''.join(f'{k}={v}\n' for k, v in sorted(properties.items())), encoding='ascii')


def start(args, session, evidence, label, world, case, scenario, *, forced=False, legacy=False):
    require_lock(args.lock_owner)
    oracle.require(bool(args.lock_owner), 'LOCK_OWNER_REQUIRED')
    verify_candidate(args.jar, args.source_commit, args.candidate_commit, args.jar_sha256)
    installed_hash = args.root_2_jar_sha256 if legacy else args.jar_sha256
    if legacy:
        verify_root_2(args)
    oracle.require(oracle.digest(session / 'mods' / args.jar.name) == installed_hash, 'INSTALLED_CANDIDATE_CHANGED')
    preflight()
    measured = getattr(args, 'measurement_mode', False)
    before = disk_snapshot(world) if measured else None
    port = smoke.allocate_port()
    configure_session(session, world, port)
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
    row = dict(label=label, case=case, scenario=scenario, command=command, log=log.name,
               jar_sha256=installed_hash, legacy_root_2=legacy)
    write_json(evidence / (label + '-invocation.json'), row)
    process = smoke.CapturedProcess(command, session, log)
    try:
        if forced:
            process.wait_for(re.compile(re.escape(expected)), args.startup_timeout)
            process.process.kill()
            process.abort()
            row['forced_stop'] = True
        else:
            row.update(observation_stop(process, expected, args.startup_timeout, label, measured))
        errors = [line.strip() for line in process.lines if '/ERROR]' in line or '/FATAL]' in line]
        if not measured:
            oracle.require(not errors, 'NATIVE_ERROR:' + label)
        row.setdefault('contradictions', []).extend(dict(reason='NATIVE_ERROR', file=log.name, path=':log', line=line) for line in errors)
        row['status'] = 'STOPPED_WITH_CONTRADICTIONS' if row['contradictions'] else 'STOPPED'
        row['log_sha256'] = oracle.digest(log)
        row['configuration'] = {'server.properties': oracle.digest(session / 'server.properties')}
        row['configuration'][command[-2].removeprefix('@')] = oracle.digest(session / command[-2].removeprefix('@'))
        for directory in (session / 'config', world / 'serverconfig'):
            if directory.is_dir():
                row['configuration'].update({path.relative_to(session).as_posix(): oracle.digest(path)
                                             for path in directory.rglob('*') if path.is_file()})
        row['manifest'] = oracle.manifest(world)
        if measured:
            record_stage(evidence, label, before, disk_snapshot(world), row['contradictions'])
        write_json(evidence / (label + '-invocation.json'), row)
        return process.lines, log
    finally:
        if not process.log_stream.closed:
            process.abort()


def observation_stop(process, expected, timeout, label, measured):
    """A missing native observation remains a failure, but diagnostics retain its stopped files."""
    contradictions = []
    try:
        process.wait_for(re.compile(re.escape(expected)), timeout)
    except smoke.SmokeError as error:
        if not measured:
            raise
        contradictions.append(dict(reason='NATIVE_OBSERVATION_MISSING', file=label + '.log', path=':result-marker', detail=str(error)))
    if process.process.poll() is None:
        process.command('stop')
    code = process.finish()
    if not measured:
        oracle.require(code == 0, 'UNCLEAN_STOP:' + label)
    elif code != 0:
        contradictions.append(dict(reason='UNCLEAN_STOP', file=label + '.log', path=':exit-code', exit_code=code))
    return dict(exit_code=code, contradictions=contradictions)


def seed_world(args, session, evidence, label, case='FF_REBUILD_SOURCE', *, legacy=False):
    world = session / ('world-' + label)
    seed_options = {'legacy': True} if legacy else {}
    start(args, session, evidence, label + '-forced', world, case, 'start', forced=True, **seed_options)
    transfer = transfer_id(world)
    for cycle in (1, 2):
        lines, log = start(args, session, evidence, label + '-protected-' + str(cycle), world, case, 'recover', **seed_options)
        if case in ('FF_REBUILD_SOURCE', 'FF_KEEP_SOURCE'):
            check_fence(lines, transfer)
        else:
            # The other seeds keep their own family's contract: the declared fence reason in every start.
            wanted = seed.CASES[case].get('fence')
            oracle.require(wanted is None or wanted in seed.fences(lines), 'SEED_FENCE_MISSING:' + case)
        # The seed family's raw-file oracle: a later refusal cannot come from a world of another shape.
        disk = seed.inspect(world)
        seed.check(case, disk, True)
    return world, transfer, log


def make_plan(args, source, backup, log, transfer, report, witness=None):
    return repair.plan(source, backup, args.jar, log, report, transfer, getattr(args, 'profile', repair.PROFILE_ENABLED),
                       'X03 native harness', 'X03 whole-world copy repair; no resource reconstruction', witness)


def verify_root_2(args):
    # An explicitly supplied historical artifact, not the activated candidate.
    oracle.require(bool(args.root_2_jar and args.root_2_commit and args.root_2_jar_sha256), 'ROOT_2_PINS_REQUIRED')
    oracle.require(bool(re.fullmatch(r'[0-9a-f]{40}', args.root_2_commit))
                   and bool(re.fullmatch(r'[0-9a-f]{64}', args.root_2_jar_sha256)), 'ROOT_2_PINS_INVALID')
    oracle.require(oracle.digest(args.root_2_jar) == args.root_2_jar_sha256, 'ROOT_2_JAR_CHANGED')
    subprocess.run(['git', 'merge-base', '--is-ancestor', args.root_2_commit, args.source_commit], cwd=REPO, check=True)
    source = subprocess.run(['git', 'show', args.root_2_commit + ':src/main/java/io/github/sunthemoon/'
                             'advancedrocketrycommunity/resourceescrow/EscrowAccountingFormat.java'],
                            cwd=REPO, capture_output=True, text=True, check=True).stdout
    oracle.require('public static final boolean ENABLED = false;' in source, 'ROOT_2_SOURCE_NOT_DISABLED')
    return dict(commit=args.root_2_commit, jar_sha256=args.root_2_jar_sha256)


def prepare_world(args, session, evidence, label):
    if getattr(args, 'world_origin', 'fresh-root-3') != 'upgraded-root-2':
        return seed_world(args, session, evidence, label)
    evidence_tools.install_candidate(args.root_2_jar, session / 'mods' / args.jar.name, args.root_2_jar_sha256)
    world, transfer, log = seed_world(args, session, evidence, label, legacy=True)
    before = oracle.read(world)
    oracle.check_state(before, before, repaired=False, expected_schema=2)
    refusal(args, world, log, transfer, evidence, label + '-root-2-before-upgrade', 'ROOT_NOT_CURRENT')
    evidence_tools.install_candidate(args.jar, session / 'mods' / args.jar.name, args.jar_sha256)
    lines, log = start(args, session, evidence, label + '-qualifying-upgrade', world, 'FF_REBUILD_SOURCE', 'recover')
    check_fence(lines, transfer)
    after = oracle.read(world)
    oracle.check_state(after, before, repaired=False, expected_schema=3)
    oracle.require(after['ledger']['ledger_id'] == before['ledger']['ledger_id'], 'MIGRATION_LEDGER_ID_CHANGED')
    backups = {name: digest for name, digest in oracle.manifest(world).items()
               if 'advancedrocketrycommunity-backups' in Path(name).parts}
    oracle.require(backups, 'MIGRATION_BACKUP_MISSING')
    write_json(evidence / (label + '-upgrade.json'), dict(status='MIGRATED_BEFORE_REPAIR',
               before_ledger=before['ledger'], after_ledger=after['ledger'], migration_backups=backups,
               transfer=transfer, qualifying_log=log.name, qualifying_log_sha256=oracle.digest(log)))
    return world, transfer, log


def fixture_witness(state, transfer, path):
    def uuid_text(words):
        return str(uuid.UUID(bytes=b''.join((word & 0xffffffff).to_bytes(4, 'big') for word in words)))
    identity = oracle.identity(state)
    identity = {key: uuid_text(value) if isinstance(value, list) else value for key, value in identity.items()}
    identity.update(transfer=transfer, ledger=uuid_text(state['ledger']['ledger_id']))
    # These before-values describe the pinned native fixture's create() call,
    # not an inference that an operator's historical journal is a baseline.
    # Both protected restart files independently prove its physical after-pair.
    oracle.require((state['cargo'], state['tank']) == (4000, 1000), 'WITNESS_ENDPOINTS')
    body = dict(version=1, identity=identity, fluid='minecraft:water', native_tag='absent', before=[5000, 0], after=[4000, 1000])
    raw = json.dumps(body, sort_keys=True, separators=(',', ':'), ensure_ascii=True, allow_nan=False).encode('ascii')
    write_json(path, dict(body=body, sha256=nbt.digest(raw)))
    return path


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


def future_root(world):
    path = world / repair.LEDGER
    name, root = nbt.read_gzip(path)
    root.value['data'].value['schema_version'].value = 4
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


def disk_snapshot(world):
    if not world.exists():
        return dict(manifest={}, objects={})
    objects = {}
    for path in sorted(world.rglob('*')):
        if path.is_file() and path.name.endswith(('.dat', '.dat_old')):
            with path.open('rb') as stream:
                gzip_header = stream.read(2) == b'\x1f\x8b'
            if gzip_header:
                objects[(path.relative_to(world).as_posix(), '')] = nbt.read_gzip(path)[1]
    objects.update(oracle.chunks(world))
    return dict(manifest=oracle.manifest(world), objects=objects)


def record_stage(evidence, label, before, after, findings=None):
    changes = []
    for key in sorted(before['objects'].keys() | after['objects'].keys()):
        left, right = before['objects'].get(key), after['objects'].get(key)
        rows = [dict(path='', before_type=left.type if left else None, after_type=right.type if right else None)] \
            if left is None or right is None else oracle.typed_differences(left, right)
        changes.extend(dict(file=key[0], chunk=list(key[1:]) if len(key) == 3 else None, **row) for row in rows)
    row = dict(stage=label, status='MEASUREMENT_ONLY', promotion_approved=False,
               differences=oracle.differences(before['manifest'], after['manifest']),
               manifest=after['manifest'], typed_changes=changes, contradictions=findings or [])
    write_json(evidence / (label + '-measurement.json'), row)
    return row


def stage_check(args, check, findings):
    if not getattr(args, 'measurement_mode', False):
        return check()
    with oracle.measurement(findings):
        return oracle.checked(check)


def file_check(args, before, after, findings, *, functional=False):
    if not getattr(args, 'measurement_mode', False):
        check = oracle.functional_boundary if functional else oracle.boundary
        return measure(lambda: check(before, after, RUNTIME_FILES), findings, args.derive_runtime_list)
    # Check every file separately so the first unlisted write cannot hide others.
    check = oracle.functional_boundary if functional else oracle.boundary
    for name in sorted(before.keys() | after.keys()):
        try:
            check({name: before[name]} if name in before else {}, {name: after[name]} if name in after else {}, RUNTIME_FILES)
        except oracle.OracleError as error:
            findings.append(dict(reason=str(error), file=name, path=':file-sha256'))


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
    for section in root.value['sections'].value[1]:
        states = section.value.get('block_states')
        if states is None:
            continue
        names = [nbt.plain(entry)['Name'] for entry in states.value['palette'].value[1]]
        x, y, z = key[1] * 16, section.value['Y'].value * 16, key[2] * 16
        old = nbt.block(root, x, y, z)
        # set_block deliberately cannot expand/repack a palette. Pick an
        # already represented, uniquely named state, changing exactly one cell.
        replacement = next((name for name in names if name != old and names.count(name) == 1), None)
        if replacement is not None:
            nbt.set_block(root, x, y, z, replacement)
            nbt.write_chunk(world / 'region', key[1], key[2], root)
            return key
    raise oracle.OracleError('NO_NONTARGET_BLOCK_IN_SELECTED_CHUNK')


def coordinator(context, world, baseline_manifest):
    current = oracle.read(world)
    oracle.check_state(current, context['baseline'], expected_schema=context.get('expected_schema', 2))
    oracle.boundary(baseline_manifest, oracle.manifest(world), RUNTIME_FILES)
    oracle.compare_repair(context['baseline'], current, context['transfer'], expected_schema=context.get('expected_schema', 2))


def controls(args, session, evidence, cycles, context):
    stable = []
    for cycle in cycles:
        baseline = cycle['baseline_manifest']
        stopped = cycle['stages'][1]['manifest']
        stable.append({name for name in baseline if stopped.get(name) == baseline[name]})
    baseline = cycles[-1]['baseline_manifest']
    common = {name: baseline[name] for name in stable[0] & stable[1]}
    target = oracle.tamper_target(common, [common, common], RUNTIME_FILES, context['twice'])
    results = []
    for name in ('unlisted-file', 'listed-object'):
        control = session / ('control-' + name)
        copy_verified(context['twice'], control)
        location = tamper_file(control, target) if name == 'unlisted-file' else repr(tamper_object(control))
        # This check alone deliberately cannot see either collateral mutation.
        # The shipped coordinator below always includes both independent boundaries.
        oracle.check_state(oracle.read(control), context['baseline'], expected_schema=context.get('expected_schema', 2))
        try:
            coordinator(context, control, baseline)
        except oracle.OracleError as error:
            # Each control names its own mutation: the exact file, or the tampered object's exact key (design 5.3).
            oracle.require(str(error) == 'UNLISTED_FILE:' + target if name == 'unlisted-file'
                           else str(error).startswith('OBJECT_CHANGED:' + location + ':'),
                           'CONTROL_FAILED_FOR_OTHER_REASON:' + str(error))
            result = dict(control=name, location=location, target_checks='PASS', status='REFUSED_AS_REQUIRED', reason=str(error))
            results.append(result)
            write_json(evidence / (name + '-control.json'), result)
        else:
            raise oracle.OracleError('CONTROL_NOT_DETECTED:' + name)
    return results


def repaired_cycle(args, session, evidence, number):
    label = 'cycle-' + str(number)
    original, transfer, log = prepare_world(args, session, evidence, label)
    baseline = oracle.read(original)
    expected_schema = repair.current_roots(getattr(args, 'profile', repair.PROFILE_ENABLED))['resource_escrow'][0]
    oracle.check_state(baseline, baseline, repaired=False, expected_schema=expected_schema)
    write_json(evidence / (label + '-baseline.json'),
               {key: nbt.plain(baseline[key]) if isinstance(baseline[key], nbt.Tag) else baseline[key]
                for key in ('entity', 'block', 'ledger', 'journal', 'cargo', 'tank')})
    backup = session / ('backup-' + label)
    original_hashes = copy_verified(original, backup)
    if getattr(args, 'measurement_mode', False):
        record_stage(evidence, label + '-backup', disk_snapshot(original), disk_snapshot(backup))
    world_size = sum(path.stat().st_size for path in original.rglob('*') if path.is_file())
    oracle.require(shutil.disk_usage(session).free >= 6 * world_size, 'INSUFFICIENT_SPACE_FOR_HARNESS_COPIES')
    planfile = evidence / (label + '-plan.json')
    refusals = [refusal(args, original, log, transfer, evidence, label + '-old-root', 'ROOT_NOT_CURRENT', old_root),
                refusal(args, original, log, transfer, evidence, label + '-unknown-authority',
                        'UNVALIDATED_AUTHORITY', unknown_authority)]
    if expected_schema == 3:
        refusals.append(refusal(args, original, log, transfer, evidence, label + '-future-root', 'ROOT_NOT_CURRENT', future_root))
        unknown = make_plan(args, original, backup, log, transfer, evidence / (label + '-unknown-baseline-plan.json'))
        oracle.require(unknown['body']['account']['status'] == 'UNKNOWN_BASELINE'
                       and 'discrepancy' not in unknown['body']['account'], 'INVENTED_ACCOUNT_ZERO')
    # The seed's ordinary fixture is the numeric before-witness. It created a
    # 5000/0 water pair, then logged the applied ordinary unload before the cut.
    witness = fixture_witness(baseline, transfer, evidence / (label + '-fixture-witness.json'))
    document = make_plan(args, original, backup, log, transfer, planfile, witness)
    if getattr(args, 'measurement_mode', False):
        record_stage(evidence, label + '-plan', disk_snapshot(original), disk_snapshot(backup))
    candidate = session / ('candidate-' + label)
    applied = repair.apply(planfile, candidate, document['digest'])
    if getattr(args, 'measurement_mode', False):
        record_stage(evidence, label + '-apply', disk_snapshot(backup), disk_snapshot(candidate))
    oracle.require(oracle.differences(original_hashes, oracle.manifest(candidate)) == dict(added=[], removed=[], changed=[repair.JOURNAL]),
                   'OFFLINE_EDIT_BOUNDARY')
    findings, stages, snapshots = [], [], []
    for restart in (1, 2, 3):
        stage_findings = []
        lines, _ = start(args, session, evidence, label + '-observe-' + str(restart), candidate, 'FF_X03_OBSERVE', SCENARIO)
        state = oracle.read(candidate)
        write_json(evidence / (label + '-observe-' + str(restart) + '-raw.json'),
                   dict(entity=nbt.plain(state['entity']), block=nbt.plain(state['block']),
                        managed={name: nbt.plain(root) for name, root in state['managed'].items()},
                        object_differences=[dict(file=k[0], chunk=list(k[1:]),
                                                 path=oracle.first_difference(baseline['chunks'][k], state['chunks'][k]))
                                            for k in baseline['chunks'].keys() & state['chunks'].keys()
                                            if baseline['chunks'][k] != state['chunks'][k]]))
        stage_check(args, lambda: oracle.observe(lines, 'FF_X03_OBSERVE', state), stage_findings)
        # List derivation measures filenames, not exceptions to the semantic contract.
        stage_check(args, lambda: oracle.compare_repair(baseline, state, transfer, expected_schema=expected_schema), stage_findings)
        manifest = oracle.manifest(candidate)
        diff = oracle.differences(original_hashes, manifest)
        file_check(args, original_hashes, manifest, stage_findings)
        census = measure(lambda: repair.census(planfile, candidate, evidence / (label + '-census-' + str(restart) + '.json'), True),
                         stage_findings, getattr(args, 'measurement_mode', False))
        findings.extend(stage_findings)
        stages.append(dict(restart=restart, differences=diff, manifest=manifest, contradictions=stage_findings, census=census))
        write_json(evidence / (label + '-observe-' + str(restart) + '-checks.json'), stages[-1])
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
        stage_findings = []
        lines, _ = start(args, session, evidence, label + '-functional-' + str(i), descendant, case, SCENARIO)
        state = oracle.read(descendant)
        write_json(evidence / (label + '-functional-' + str(i) + '-raw.json'),
                   dict(entity=nbt.plain(state['entity']), block=nbt.plain(state['block']),
                        managed={name: nbt.plain(root) for name, root in state['managed'].items()},
                        added_chunks=[list(k) for k in state['chunks'].keys() - before['chunks'].keys()],
                        object_differences=[dict(file=k[0], chunk=list(k[1:]),
                                                 path=oracle.first_difference(before['chunks'][k], state['chunks'][k]))
                                            for k in before['chunks'].keys() & state['chunks'].keys()
                                            if before['chunks'][k] != state['chunks'][k]]))
        launched = i >= 2
        stage_check(args, lambda: oracle.check_state(state, baseline, unloaded=True, launched=launched,
                                                   expected_schema=expected_schema), stage_findings)
        fields = stage_check(args, lambda: oracle.observe(lines, case, state, landed=launched), stage_findings) or {}
        if case == 'FF_X03_UNLOAD':
            stage_check(args, lambda: oracle.require(fields.get('switch') == 'withdrawn', 'UNLOAD_SWITCH_NOT_WITHDRAWN'), stage_findings)
        action = 'unload' if i == 0 else 'launch' if i == 2 else None
        stage_check(args, lambda: oracle.compare_functional(before, state, action=action), stage_findings)
        debit = stage_check(args, lambda: oracle.launch_debit(fields, before, state), stage_findings) if case == 'FF_X03_LAUNCH' else None
        file_check(args, snapshots[0], oracle.manifest(descendant), stage_findings, functional=True)
        findings.extend(stage_findings)
        functional.append(dict(case=case, observation=fields, debit=debit,
                               differences=oracle.differences(snapshots[0], oracle.manifest(descendant)), contradictions=stage_findings))
        write_json(evidence / (label + '-functional-' + str(i) + '-checks.json'), functional[-1])
        before = state
    oracle.require(oracle.manifest(original) == original_hashes == oracle.manifest(backup), 'SOURCE_OR_BACKUP_CHANGED')
    row = dict(status='MEASURED_NOT_PROMOTED' if args.derive_runtime_list else 'VERIFIED_NOT_PROMOTED',
               transfer=transfer, baseline_manifest=original_hashes, stages=stages, functional=functional,
               findings=findings, refusals=refusals, apply=applied, plan_digest=document['digest'])
    write_json(evidence / (label + '-result.json'), row)
    return row, dict(original=original, backup=backup, candidate=candidate, twice=twice, plan=planfile,
                     transfer=transfer, baseline=baseline, log=log, expected_schema=expected_schema)


def run(args):
    if getattr(args, 'world_origin', None) == 'both':
        return run_both(args)
    if getattr(args, 'measurement_mode', False):
        args.derive_runtime_list = True
    oracle.require(bool(args.lock_owner), 'LOCK_OWNER_REQUIRED')
    require_lock(args.lock_owner)
    pins = verify_candidate(args.jar, args.source_commit, args.candidate_commit, args.jar_sha256)
    if getattr(args, 'world_origin', None) == 'upgraded-root-2':
        pins['root_2_seed'] = verify_root_2(args)
    generation = generation_rule()
    preflight()
    session, evidence = args.session_dir.resolve(), args.evidence_dir.resolve()
    oracle.require(not session.exists() and not evidence.exists() and session not in evidence.parents and evidence not in session.parents,
                   'EXISTING_OR_OVERLAPPING_OUTPUT')
    oracle.require(REPO not in session.parents and REPO not in evidence.parents, 'OUTPUT_INSIDE_REPOSITORY')
    # Refuse a mismatched template before making any server copy or starting Java.
    argsfile = _server_command(args.java)[-2].removeprefix('@')
    oracle.require((args.source_server / argsfile).is_file(), 'FORGE_TEMPLATE_ARGUMENT_FILE_MISSING:' + argsfile)
    evidence.mkdir(parents=True)
    session.mkdir(parents=True)
    token = uuid.uuid4().hex
    sentinel = session / '.w380-owned'
    sentinel.write_text(token, encoding='ascii')
    report = dict(status='IN_PROGRESS', pins=pins, runtime_files=RUNTIME_FILES, generation_rule=generation, cycles=[], cleanup=False,
                  development=args.derive_runtime_list, promotion_approved=False,
                  world_origin=getattr(args, 'world_origin', 'fresh-root-3'),
                  profile=getattr(args, 'profile', repair.PROFILE_ENABLED),
                  harness={name: oracle.digest(REPO / 'scripts' / name) for name in
                           ('run_v180_x03_repair.py', 'x03_repair_oracle.py', 'repair_v180_rebuild_source.py', 'loader_cut_nbt.py')})
    try:
        shutil.copytree(args.source_server / 'libraries', session / 'libraries')
        (session / 'mods').mkdir()
        evidence_tools.install_candidate(args.jar, session / 'mods' / args.jar.name, args.jar_sha256)
        report['refusals'] = []
        for label, case, reason, exact in NATIVE_REFUSAL_SEEDS:
            world, transfer, log = seed_world(args, session, evidence, label, case)
            row = refusal(args, world, log, transfer, evidence, label, reason, exact=exact)
            report['refusals'].append(dict(row, seed=case, required_reason=reason, transfer=transfer))
            write_json(evidence / 'results.json', report)
        contexts = []
        for number in (1, 2):
            row, context = repaired_cycle(args, session, evidence, number)
            report['cycles'].append(row)
            contexts.append(context)
            write_json(evidence / 'results.json', report)
        for row, context in zip(report['cycles'], contexts):
            oracle.require(oracle.manifest(context['original']) == row['baseline_manifest'] == oracle.manifest(context['backup']),
                           'SOURCE_OR_BACKUP_CHANGED_AFTER_ALL_RUNS')
        if args.derive_runtime_list:
            report['status'] = 'MEASURED_NOT_PROMOTED'
            report['native_contradictions'] = [dict(stage=path.stem, **finding)
                                               for path in sorted(evidence.glob('*-invocation.json'))
                                               for finding in json.loads(path.read_text(encoding='utf-8')).get('contradictions', [])]
        else:
            report['refusals'].extend(row for cycle in report['cycles'] for row in cycle['refusals'])
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
            lines, _ = start(args, session, evidence, 'promoted-observe', context['original'], 'FF_X03_OBSERVE', SCENARIO)
            oracle.observe(lines, 'FF_X03_OBSERVE', oracle.read(context['original']))
            coordinator(context, context['original'], original_before)
            oracle.require(oracle.manifest(preserved) == original_before == oracle.manifest(context['backup']),
                           'PRESERVED_ORIGINAL_OR_BACKUP_CHANGED')
            report['status'] = 'PASS_ACCOUNTING_ENABLED' if report['profile'] == repair.PROFILE_ENABLED else 'PASS_DEVELOPMENT_ROOT_2'
    except BaseException as error:
        report.update(status='FAILED', error=str(error))
        raise
    finally:
        if sentinel.is_file() and sentinel.read_text(encoding='ascii') == token and session.resolve() == session and not session.is_symlink():
            shutil.rmtree(session)
            report['cleanup'] = not session.exists()
        write_json(evidence / 'results.json', report)
    return report


def run_both(args):
    if getattr(args, 'measurement_mode', False):
        args.derive_runtime_list = True
    verify_root_2(args)
    oracle.require(not args.evidence_dir.exists(), 'EXISTING_OR_OVERLAPPING_OUTPUT')
    args.evidence_dir.mkdir(parents=True)
    result = dict(status='IN_PROGRESS', cases=[], promotion_approved=False)
    try:
        for origin in ('fresh-root-3', 'upgraded-root-2'):
            child = copy.copy(args)
            child.world_origin = origin
            child.evidence_dir = args.evidence_dir / origin
            child.session_dir = args.session_dir.with_name(args.session_dir.name + '-' + origin)
            report = run(child)
            result['cases'].append(dict(origin=origin, status=report['status'], cleanup=report['cleanup']))
        result['status'] = 'MEASURED_NOT_PROMOTED' if args.derive_runtime_list else 'PASS_ACCOUNTING_ENABLED_BOTH_ORIGINS'
    except BaseException as error:
        result.update(status='FAILED', error=str(error))
        raise
    finally:
        write_json(args.evidence_dir / 'results.json', result)
    return result


def generation_rule():
    ring = [(x, z) for x in range(-13, 14) for z in range(-13, 14) if max(abs(x), abs(z)) > 10]
    oracle.require(len(ring) == 288 and all(oracle.in_functional_box((oracle.MOON + '/region/r.0.0.mca', x, z))
                                          for x, z in ring), 'MEASURED_RING_OUTSIDE_DERIVED_BOX')
    return dict(boxes=oracle.FUNCTIONAL_BOXES, destination_ticket_radius=oracle.DESTINATION_TICKET_RADIUS,
                probe_landing_ticket_distance=oracle.PROBE_LANDING_TICKET_DISTANCE,
                vanilla_dependency_margin=oracle.GENERATION_MARGIN, existing_margin_chunks='immutable', measured_ring_chunks=len(ring))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source_server', type=Path)
    for name in ('jar', 'session-dir', 'evidence-dir'):
        parser.add_argument('--' + name, type=Path, required=True)
    for name in ('source-commit', 'candidate-commit', 'jar-sha256', 'java', 'lock-owner'):
        parser.add_argument('--' + name, required=True)
    parser.add_argument('--startup-timeout', type=float, default=420)
    parser.add_argument('--derive-runtime-list', action='store_true')
    parser.add_argument('--measurement-mode', action='store_true',
                        help='Collect contradictions through both sequences; never accept or promote (implies derivation).')
    parser.add_argument('--world-origin', choices=('both', 'fresh-root-3', 'upgraded-root-2'), default='both')
    parser.add_argument('--root-2-jar', type=Path)
    parser.add_argument('--root-2-jar-sha256')
    parser.add_argument('--root-2-commit')
    try:
        run(parser.parse_args(argv))
        return 0
    except (ValueError, OSError, smoke.SmokeError) as error:
        print('FAIL: ' + str(error), flush=True)
        return 1


if __name__ == '__main__':
    sys.exit(main())
