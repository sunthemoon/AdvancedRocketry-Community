# GameTest log expectations: Claude handoff 01

Date: 2026-10-10. Author: Claude (Claude Code session). Status: implemented and
verified against retained logs, **not reviewed or integrated**. This record does not
approve any Required Gate, accept the ADR proposal, change the version status or
replace Root's independent review and integration.

## Authorization

In the interactive Claude Code session on 2026-10-10, Claude reported that all 62
GameTest log errors come from deliberate negative tests. It proposed an
expected-error manifest with a checking script and asked whether to deliver it
"按上次的方式", that is, as a fix branch with the manifest, the checking script and
an ADR draft for Codex to review and merge. The owner answered (wording as given):
"按上次做吧". In the earlier delivery of that session, the owner had chosen an
isolated worktree branch that commits only its own files and is pushed without force.
Scope covers this branch only. It covers no edits to the shared branch, the hosted
workflow, central status files or ledgers.

## Identity

| Item | Value |
|---|---|
| Branch | `fix/v1.8.0-gametest-log-expectations` |
| Base | `ba680996` (shared `codex/v1.8.0-classic-content` when the branch was created) |
| Source commit | `e2ed43eb` |
| New files | `scripts/check_gametest_log.py`, `scripts/gametest_expected_log.json`, `tests/test_check_gametest_log.py` |
| Records | this handoff, [ADR-PROPOSAL-01](ADR-PROPOSAL-01.md) and [evidence-01.zip](evidence-01.zip) |
| Production code, build and workflow | unchanged |

The SHA-256 values of the three committed files equal the tested files listed in
the evidence (`results/tested-files.sha256`).

## Design

- The checker splits `build/gametest/logs/latest.log` into entries, assigns each
  one to the test batch that was running (`Running test batch '<name>:<n>'`, with
  the index dropped), and keeps the stack-trace lines that follow it.
- Each entry must match exactly one expectation: level, logger, full message
  pattern, declared batch and, when given, a stack-trace substring that names the
  test. ERROR expectations have exact per-batch counts. WARN expectations have
  exact counts too, except six environment warnings that have a `max`.
- It fails on FATAL entries, unmatched or ambiguous entries, an expected message in
  an undeclared batch, any count difference and a log without the completion
  banner. Exit codes: 0 pass, 1 fail, 2 unreadable log or invalid manifest.
- `--inventory` lists every ERROR, FATAL and WARN group with batch, logger and test
  frames, so a maintainer can write a new expectation.
- The manifest has 31 expectations: 13 ERROR (62 entries), 12 WARN with exact
  counts (52 entries) and 6 environment WARN bounds (109 entries in a fresh run).
  Each one names its test and reason.

## Verification

Python 3.13.15 on Windows. Evidence: [evidence-01.zip](evidence-01.zip), 1,301,854 B,
SHA-256 `29e4b5b8c0b16e41a749e9655df9d1a521ad47b9b01acc9daf9e6f93c4abe145`. Its
`SHA256SUMS.txt` has SHA-256
`3a8fd0dc9a0158cf71e67306f50672715214d472448ddcc6b0aa845a7ffc9121`.
No JVM, Gradle or game run was started for this task; all logs are retained copies.

| Check | Result |
|---|---|
| Seven complete logs of 2026-10-10 (one Claude run, six Root runs, including `f9bdc800` and `30ca28ef`) | all exit 0: every entry matched, FATAL 0, ERROR 62, WARN 161 |
| Gradle console log of the same run as log 07 | exit 1: abbreviated logger names, as designed |
| Two stall-experiment logs (`ARCE_EXPERIMENT_STALL` warnings) | exit 1, naming the experiment warnings; the unpatched one also names its two failed tests |
| Hosted Linux CI `latest.log` of 2026-10-07 (525 tests, one failure) | exit 1 for exactly the failed test and the then-missing jackhammer batch; its other 62 ERROR and 157 WARN entries matched |
| `python -m unittest tests.test_check_gametest_log` | 27 tests pass |
| 11 single mutations of the checker | all 11 caught by the unit tests |
| `validate_v1plus_planning.py`; `validate_v180_content_ledger.py --require-accepted` | both exit 0 |
| `python -m unittest` of `test_validate_repository`, `test_bootstrap_version_dispatch`, `test_audit_upstream`, `test_validate_v002_final_g0_review` | 148, 5, 7 and 28 tests pass |
| `validate_repository.py --require-approved-identity` | exit 1: 44 checks pass; the Markdown link check fails on existing files that this branch does not touch, and it stops after 256 errors |
| The same link check run on the two new documents only | 6 links, none broken |

The Linux log confirms that the format and environment patterns hold on the hosted
runner. It comes from an older commit, so the current counts still need one hosted
run.

## Root actions and open items

1. An independent review of `e2ed43eb` and of the ADR proposal, then integration.
   Root assigns the ADR number, records acceptance or rejection and updates central
   records.
2. Hosted workflow (Root's file): after `./gradlew runGameTestServer` in the step
   "Run all Forge GameTests", add
   `python -B scripts/check_gametest_log.py build/gametest/logs/latest.log 2>&1 | tee "$EVIDENCE_DIR/gametest-log-check.log"`.
   The workflow's `shell: bash` runs with `-eo pipefail`, so the step fails when the
   check fails. The first hosted run of the current sources is the first Linux check
   of the current counts.
3. Optional: add a sentence to the master test plan, section 6, that refers to the
   accepted ADR for GameTest logs.
4. Not covered: dedicated-server, native-harness and client logs (unchanged policy);
   strict Python validation; every Gate.
