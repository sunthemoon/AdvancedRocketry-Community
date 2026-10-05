# Private report-stage consumer: source handoff

Date: 2026-10-05. Task: C18c-01-NATIVE04-REPORT-STAGES.
Author scope: four NEW files, uncommitted in an isolated registered worktree.
Independent actual-source review and Root committed-source replay remain open.

## Completed scope and interface

`parse_stage_report(raw: bytes) -> StageReportObservation` observes one
nonterminal row under the published
[private task](NATIVE-TASK-04-REPORT-STAGES.md) and
[disposition](NATIVE-REVIEW-DISPOSITION-04-REPORT-STAGES.md).
It calls the existing REPORT syntax consumer exactly once before field checks.
Inherited JSON failures propagate; invalid field shape raises only
`FixtureStageError("RECORD_SHAPE")`, without supplied content in diagnostics.
No existing consumer or test was edited.

The 21 phase/event/index rows and exact goal mapping are covered. Returned
records are frozen/slotted with tuple collections. Numeric observations retain
raw bytes/hash; fixed position numbers retain the original `JsonNumber`
lexeme and labelled projection. Integer magnitude/range is checked lexically
before bounded conversion. Position equality uses exact decimal coefficient
and exponent spelling, not binary64 rounding or unbounded conversion.

All Boolean fields observe either truth value. Shape acceptance is not
authentication, native success, inventory/progression interpretation, file
identity, freshness, sequence, ownership, disposal or stop permission. No
permission-bearing handle or inferred acceptance flag is returned.

## Exact checkout and files

Base: `6bddc0326247ba79df845bcd8c0c606f447854d2`.
Worktree: `D:/GitHub/arce-v180-c18c-stages-20261005`.
Branch: `codex/v1.8.0-inventory-report-stages`.
Effective live AGENTS is 12,047 bytes / SHA-256
`1be0391c3f69f566ae3f627e37107adfbd0bbfa6f581b105ff6bd2a2c2630dc0`.

New paths:

- `scripts/classic_inventory_fixture_stages.py`: 394 lines; one private byte
  consumer, immutable records and field validators.
- `scripts/test_classic_inventory_fixture_stages.py`: 615 lines; five bounded
  assertion groups plus a shared fixture/assertion base. No class exceeds 500
  lines. Module length reflects contract-wide field and phase mutation tests,
  not gameplay/lifecycle/domain responsibilities in one class.
- `NATIVE-PROGRESS-04-REPORT-STAGES.md` and this handoff in this task directory.

Exact file bytes/SHA-256, creation patch and input/result checks are external at
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18c-stages-author-20261005-f63b781a`.
`OWN-FILES-03.json` binds all four postimages; `SOURCE-03.patch` proposes their
creation. The evidence manifest/checksum list binds raw outputs and failures.
No full source/JAR/world export or ZIP is included.

## Actual verification and failures

- First focused source run: 33 tests, exit 0, 1.658 seconds reported by unittest.
- Final source run: 200 tests, exit 0, 2.885 seconds reported by unittest
  (3.0812476000282913 seconds child elapsed). This is 35 new methods plus 165
  unchanged terminal/JSON/phase01/properties-file methods, with no failures,
  errors or skips. Raw argv/environment/logs are `scoped-02-*`.
- Selected 20 existing input files and live governance: pre/post hash drift 0.
  Tracked/index diffs remain empty; only the exact four new paths are owned.
- Fixture cleanup: actual native PowerShell child exit 0; 154 nonrecursive
  literal removal calls, 99,375 regular bytes, six aliases unlinked without
  following targets. The fresh test fixture root is absent. Physical freed
  allocation was not measured.

The original absent lowercase version-document locator and orchestration
JavaScript syntax failure are retained as intake/tooling failures, not passed
tests. Final collectors also retained a no-index difference-exit assumption
failure and a Windows path-separator metadata lookup failure; neither changed
source or test results. Corrected checks are separately versioned. Both
successful test-run raw outputs remain unchanged. No historical cleanup
refusal was retried.

## Unfinished scope, integration and rollback

Only the four proposed new files may be integrated by Root after independent
review. No registration or central patch is needed. Root commits/non-force
pushes the reviewed bytes, then replays the fixed commit before result adoption.
Rollback removes only these four introduced paths under Root's Git protocol;
it does not touch existing parsers, schemas, files or player data.

No IO/CLI/dispatch/file acquisition/cohort/logging/native driver, receipt writer,
Java/Gradle/GameTest/server/client, host admission or normal-server verification
was implemented or run. C18c native delivery, R-021, C17/O3, ledger delivery and
all v1.8 Required Gates remain open. Passing private tests does not close them.
