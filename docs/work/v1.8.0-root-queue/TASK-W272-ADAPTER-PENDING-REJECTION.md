# TASK W272: adapter disassembly fixtures that stop watching a pending request

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w272`, branch `codex/v1.8.0-w272`, created by Root from the shared head |
| Subject | `RocketComponentGameTests.externalComponentsDriveActualAssemblyAndZeroFuelDisassembly` (batch `w164_adapter_success`, source set `src/adapterTest`) and fixtures with the same wait |
| Evidence | W271's local full run: `D:/GitHub/ARCE-Task-Evidence/root-queue/W271/full-latest.log` (SHA-256 `5ed6336abb0f44bfe7f6d5c3bfe73fa91fbef2da81a650bd6bbbfb9b1d201e06`), lines 13940 to 14090; W248's local full run: `D:/GitHub/ARCE-Task-Evidence/root-queue/W248/full/all/latest.log`, near line 13385 |
| Record | `docs/work/v1.8.0-root-queue/records/W272.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W272/` |
| Decision | Root decision R302 (2026-11-03): a test-reliability fix, run while no content task is open |

## What the logs show

The test failed with `Original component block not yet published` in two local full runs (W248 on 2026-11-02 and
W271 on 2026-11-03); no hosted failure of it is recorded. In W271's run, batch `w164_adapter_success` starts seven tests at
13:58:47. The component rocket (`entity=2428da15-...`, `blocks=5`) is admitted with `disassembly_pending` at tick 7.
At 13:58:58.430 its sealed disassembly logs `result=REJECTED`, together with three other rockets of the same batch.
Those three log `ARCE_ADAPTER_ADMISSION ... transition=PENDING_REJECTED`, are refused once with `region_busy`, are
admitted again and are sealed by 13:59:10. The component rocket logs nothing more, and the test times out at
13:59:58.

Root's reading of the fixture: `AdapterAsyncFixture.Submission.advance` notices a rejected pending request only when
it is called again (the player's reply becomes `region_busy`). `RocketComponentGameTests` calls it only until the
first admission (`thenWaitUntil(() -> assertTrue(disassembly[0].advance(helper), ...))`) and then waits for the
blocks without calling it, so a rejection of the pending request is never seen and never resubmitted.
`AdapterFaultGameTests` (its healthy path, lines 144 to 149) keeps calling `advance` while it waits, with the comment
"Observe terminal rejection before a fresh request". `AdapterRegistrationGameTests` (lines 126 to 129) appears to
have the same gap as the component test.

## What to do

1. **Confirm the cause.** Find why the sealed disassembly of a pending request is REJECTED here (which proof or
   conflict, and whether that is a designed refusal the player answers by asking again). If it is not a designed
   outcome, stop and report it with the evidence instead of changing the fixture.
2. **Reproduce it deterministically.** Make the component test's pending request be rejected once (for example by the
   same kind of concurrent work that the batch produced, or a test-side hook that already exists for rejected
   pending requests) and show that the current fixture times out with the same message.
3. **Fix the fixtures, not production.** While waiting for the published blocks, keep calling `advance` as
   `AdapterFaultGameTests` does, so a rejected pending request is resubmitted once its reply arrives; never reissue
   accepted pending work. Apply the same change to `AdapterRegistrationGameTests` and any other fixture with this
   wait, and list the rest with the reason they are not affected. Keep every assertion, message and timeout.

Production code, `AdapterAsyncFixture.Submission`'s rules, the expected-log manifest and the checker stay unchanged
unless step 1 finds a production defect, in which case stop and report. Do not delete or skip a test, loosen an
assertion or raise a timeout (AGENTS.md section 5).

## Verification

The deterministic reproduction failing before the change and passing after it; the batches `w164_adapter_success`
and `w164_adapter_fault` 20 times in a row under load (state how you made the load), each with a search for
undeclared WARN or ERROR lines; the adapter GameTest classes you changed; the whole unit suite; the full GameTest run
with `scripts/check_gametest_log.py` under the heavy-JVM lock; the repository validator.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Production disassembly, sealing and admission behaviour, the adapter API, W271's bay pacing, other intermittent tests
in the status file, the status file, QUEUE and the ledger.
