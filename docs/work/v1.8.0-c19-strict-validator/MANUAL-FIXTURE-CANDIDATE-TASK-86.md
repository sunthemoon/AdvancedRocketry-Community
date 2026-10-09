# C19 manual repository fixture isolation 86

Date: 2026-10-10. Version: v1.8.0 under ADR-060. Status: committed scoped candidate,
literal whole qualification failed; not Main-integrated.

[Checkpoint87](MANUAL-FIXTURE-CHECKPOINT-87.md) records actual f258 source, tests,
review, timeout and open organization prerequisites. Root86 preserves the issued
original frozen contract; the implementation/verification scope below is unchanged.

## Identity, checkout and scope

Root is sole implementer and Main integrator. Base source is committed
`18bde6da456d853af08b39e75c07a7866557954c`. Create branch
`fix/v1.8.0-manual-fixture-isolation` in fresh worktree
D:/GitHub/arce-v180-manual-fixture-20261010-86. Source write scope is only
tests/test_collect_v002_manual_evidence.py, new tests/manual_evidence_fixture.py
and new tests/test_manual_evidence_fixture.py. Root separately owns this task,
its review/checkpoint/status/log annotations and an explicit test-size ADR
description; no size waiver or ADR acceptance is assigned.

## Observable outcome and invariants

Build the existing initial synthetic manual-evidence Git repository once per
unittest class, then copy its full ordinary file tree and complete Git database
into every case's fresh owned TEMP. Keep the original .gitattributes/.gitignore/
README/content-manifest bytes and initial Git construction command semantics.
The seed has no approved collector/validator results. Register class cleanup
before preparation can fail. Each case has independent regular files, modes,
Git objects/config/index, a clean initial HEAD and independent artifact paths.
No hardlinks, alternates, checkout rematerialization, shared mutable case state
or approval/validation/result cache. Preserve all original 137 manual methods
and the CLI method, their exact bodies/assertions, ready_session and collection/
staged/committed/final phases. Keep original patcher and per-case cleanup scope.

Use a small fixture-only mixin in the new helper module; retain the original
test class/selection names. Move only initial repository construction out of
setUp; the later per-case three artifact files remain freshly created. Add
direct separate tests for raw files/modes/history preservation, isolation of
file/index/config mutations, and unittest cleanup after class construction
failure. No production, source-reader, Git policy or command budget changes.
The existing large scenario class must not grow; record its exact before/after
size and retain the independent organization finding and decomposition need.

## Verification and evidence

Own new evidence leaf under D:/GitHub/ARCE-Task-Evidence/v1.8.0:
c19-manual-fixture-root-20261010-86. Fresh absolute sibling TEMP/TMP prefix is
c19-manual-fixture-runtime-20261010-86- with a distinct suffix per command.
Use pinned Python 3.13.15, -X utf8 -B, original 180 seconds per authored command,
complete raw streams and original Popen wait/exit/PID/argv/UTC. File/read 1 MiB,
combined streams 256 KiB, leaf 4 MiB. Check C:/D: >=10 GiB before execution.
Before/after bind HEAD/tree/full status/index/Python inputs/helper/task/executable.

Static checks must compare every original test method/helper body and discovery
name/order with the base. Execute the full new fixture module, unchanged full
138-test manual module and applicable reader/protocol modules once each. Fresh
independent actual-diff review reruns new fixture and original manual modules,
scheduled without competing workloads. Wait for final peer source bindings
before explicit three-file staging/stat/check, commit and non-force push.
Record actual committed fixture execution separately from precommit runs.
If scoped checks pass, run literal complete discovery once at the committed
candidate under the same 180 seconds; timeout is failure, never a targeted sum.

Only authored cleanup and nonrecursive removal of fresh empty own runtime are
allowed; retain nonempty runtime. Distinct maximum 10-second owned wait/drain
may observe timeout termination, not qualify it. No descendants/unowned process
termination, retries, inherited cleanup, unknown acquisition, asset import,
Main integration, Gradle/native/client/resource execution, sleep/writer
activation, delivery/Gate/ADR approval or historical evidence repair.
