# TASK W88: the intermittent research fairness GameTest failure

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w88-fairness-flake` (created by Root from `codex/v1.8.0-classic-content`) |
| Branch | `codex/v1.8.0-w88-research-fairness-flake` (local commits only; Root pushes and merges) |
| Subject | `ResearchEnvironmentGameTests.moreThan32ProcessorsShareOneFairBudgetAcrossTwoLevels` (batch `research_fairness`) |
| Record | `docs/work/v1.8.0-root-queue/records/W88.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W88/` |

This is a parallel bug task; the W11b2 work keeps priority for the heavy-JVM lock.

## What is known

The test builds 34 research fixtures, alternating between the GameTest Level and the Moon, and for each one starts a
ground survey, advances it, then asserts `service.intent(fixture.controller, owner, 4)` ("Fairness processor claim
refused"). It passed in most full GameTest runs but failed twice with that message:
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C34/r14-gametest/latest.log` (search for the message);
- `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C38/step2-gametest/latest.log`, line 2981. The same source passed all
  tests on a repeat (`step2-repeat-gametest`).

Both failing runs were on W11b2 branches, but the test and the research code are the same on the shared branch. The
machine was also running other workers' unit suites, so load or timing may matter.

## What to do

1. Find the cause. Read `ResearchRuntime`, the research service's `intent` path and the fixture helpers
   (`ResearchGameTests.ready`, `ResearchFixture`) and list every condition under which `intent` returns false. Compare
   the failing logs with a passing one around the test (for example chunk or Level readiness of the Moon fixtures,
   rate limits, game-time checks, or state left by an earlier test in the same batch order).
2. Reproduce it deterministically if you can, preferably with a unit test that does not need a server, or with a
   GameTest that forces the condition.
3. Fix the real cause. If the product is right and the fixture races, make the fixture wait for the exact condition it
   needs. Under AGENTS.md section 5 you must not delete or weaken an assertion, extend a timeout, or skip the test.
4. Verify: your new or changed unit tests, the whole unit suite once, and one full `runGameTestServer` with the
   unchanged log checker, under the heavy-JVM lock. If you cannot reproduce or explain it, stop after step 1 and report
   what you found, with evidence.

Write a short record (cause, evidence, fix, commands and results) and commit the fix and the record in separate
commits.

## Write scope and boundaries

The research runtime and service classes involved, `ResearchEnvironmentGameTests`, `ResearchGameTests` and their
fixtures, their unit tests, and the record. No change to the log checker or its expectations, to escrow or rocket code,
or to central files (registries, build scripts, workflows, status files, the ledger).

Never remove a lock you do not own. Check that C: and D: have more than 10 GiB free before a GameTest run. Remove your
own GameTest world afterwards; if the tool policy refuses it, list the exact path in the record for Root and continue.

## Not in this task

Any W11b2 or escrow work; `countdownwaitsforownstorearmandpreparedacknowledgement` (a separate open observation on the
W11b2 branch); performance budgets.
