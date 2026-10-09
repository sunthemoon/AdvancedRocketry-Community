# Strict validator phase diagnostics 08

Date: 2026-10-09. Status: IN_PROGRESS. Root implementation; independent Codex
review and fixed-source execution required. No Claude. This is current v1.8
qualification tooling, not a release or new-version feature.

## Scope and ownership

Preparation base: Main 9b8b5a28f03964a81f0074342ffd95e63f31ca4c. The source task
uses an exclusive new worktree under D:/GitHub, branch
fix/v1.8.0-strict-phase-diagnostics. Source write_scope is exactly:

- scripts/validate_repository.py
- tests/test_validate_repository.py

Root owns task/checkpoint, implementation log, CURRENT_VERSION and the canonical
COMPLETION-PLAN. Main's user-managed AGENTS and inherited work are excluded.
The read-only exploration worker has no write or command-execution authority.
Source/execution identities will be recorded after actual commits, not inferred.

## Observable outcome and constraints

Provide bounded, immediately flushed phase entry/normal-return diagnostics on
stderr for the existing 33 main checks and optional package check, including
normal-return monotonic duration and cumulative result counts. Emit no paths,
individual files or arbitrary check messages in phase diagnostics. An exception
must propagate unchanged, with no fabricated normal-return or final verdict.
No phase marker is a PASS verdict; only the existing check results decide exit.

Preserve every original check, order, argument, optional condition, result list,
final grouped stdout content and failure exit behavior. Flush final reporting
without changing its text. Keep CLI flags, imports' policy and all original
limits. Do not clear old failures, use cached verdicts, omit historical checks,
modify their predicates or enlarge the external 180-second qualification window.

The source-level reporting gap is established in sealed diagnosis03. The actual
historical timeout phase/cause is not. New committed execution may establish its
own reached phases; it does not reconstruct old runs or promise timely completion.
Startup/imports before main, internal subphases and descendant attribution remain
outside these main-check diagnostics.

## Non-goals and dependencies

Do not change validate_release_checksums.py or its nested Git query in this task;
that separate Medium containment finding remains open. No optimization, general
observability framework, new library, player/Java/runtime/resource helper, sleep
policy, assets, persistence, Gate approval or evidence-budget waiver. Sealed
04/05/06/07/diagnosis03 packets stay unchanged. Refused targets are never retried.

## Verification

Use existing unittest/mocking conventions. Cover real Results reporting, flush
before check entry/after return, finite stderr shape/counts, original 33-call order
and arguments, shared Results, optional final package check, continuation after
recorded failures, unchanged success/failure exit and unchanged sentinel exception.
Keep isolated CLI help coverage. Run targeted tests and adjacent Python suite.

Before edits run baseline tests; after commit run a distinctly named strict
qualification with original argv/180-second limit and bounded separate raw streams.
Retain timeout, execution/output errors and partial termination outcomes without
retry/relabel. Prospective Python evidence is in an exclusive task08 leaf <=4 MiB;
no archives/source/world copies. Required standard Gradle qualification, if run,
uses a separately assigned <=100 MiB leaf with actual source/input binding and
disk checks. Do not call either evidence leaf the offline JSON resource suite.

Independent reviewer starts with fixed actual source/diff in a separate read-only
checkout, runs relevant tests/qualification in its own bounded evidence/Temp and
reports findings first, source identities, actual results and residual limits.
Root reviews raw results, commits/pushes only this task, and updates current
records. Whole v1.8 G0-G9, unwaived logs and product acceptance remain open.
