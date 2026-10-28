# TASK W187: the autosave guard for the remaining native-capture fixtures

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w187` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w187` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W187.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W187/` |
| Background | [records/W184.md](records/W184.md), failures 2 and 3 and "Not done"; [records/W166-DIAGNOSIS.md](records/W166-DIAGNOSIS.md) |

W166 and W184 found that the vanilla periodic autosave can supersede a pending native capture. A fixture that admits a
release or requests a governed flight then fails, or logs an unexpected WARN, at roughly 0.3 to 1 percent per run.
W184 guarded `RocketRejoinAdmissionGameTests` and `EscrowMoonLandingRuleGameTests` with `GameTestAutosave.within`.

## What to do

1. **Audit** each fixture W184 named: `EscrowGovernedMarginGameTests`, `EscrowGovernedWarmupGameTests`,
   `EscrowGovernedWarmupRefusalGameTests`, `EscrowFillLaunchGameTests`, `EscrowCargoPreparationGameTests`,
   `EscrowRetireBeforeFillGameTests` and `RocketSettlementIdleGameTests`. Also check the later stages of the governed
   Moon flights (F4 and the boundary return). Then search for any other GameTest that starts a native capture. For
   each, say whether an autosave inside its window can fail the test or write an undeclared line.
   - **A new case, first.** Hosted run 40725066567 at `29c0411d` (the W184 merge) failed only
     `LandingPadGameTests.allFiveRecoveryPhasesAndFourPresenceCutsReleaseClaimsAfterReload` with "Recovery cut
     COMMITTED/1 failed: NOT_FOUND". The source's retirement was proved and then abandoned in the same second
     (`ARCE_TRANSFER_RETIREMENT_PROVED`, then `..._ABANDONED`, transfer `76d5a576`). The job log is
     `D:/GitHub/ARCE-Task-Evidence/root-queue/W184-hosted/29c0411d/job-122945997201.log`, around line 7071. Find
     what abandoned the proof; if it is the autosave, guard the fixture; if not, report the cause.
2. **Guard** each one that can, the way W184 did: wait before the step while the next autosave is within the step's
   window, with the window derived and commented. Each guard in its own commit.
3. Do not change a timeout, bound, budget or assertion, and do not add a manifest entry.

## Verification

- each changed batch three times under the heavy-JVM lock;
- the whole unit suite if a shared fixture changes.

Root's hosted run on the pushed branch stands in for a complete local GameTest.

## Not in this task

Releasing the harness's forced chunks (W184's option A), production code, ADRs, status files and the ledger.
