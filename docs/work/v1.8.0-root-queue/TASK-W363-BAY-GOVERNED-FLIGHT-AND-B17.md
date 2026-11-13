# TASK W363: the bay on a governed flight (W358's B6.8 finding), and a gated control for B17.3

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w363`, branch `codex/v1.8.0-w363`, at the shared head once W358 has merged |
| Decision | Root decision R444 (QUEUE row W363) |
| Finding | `records/W358.md` (merged with W358's branch), "Feasibility and exclusions": B6.8 and B17.3 |
| Record | `docs/work/v1.8.0-root-queue/records/W363.md` |
| Write scope | Part 1: the governed-flight and bay code needed for the fix, with its unit tests and GameTests. Part 2: one gated release-test seam that does nothing without the release-test property. Both parts: W358's runner `scripts/run_v180_bay_uncovered.py` and its tests, and the record. No register, ADR, status file, QUEUE, build or workflow change. |

## Why

W358 built B6.8 natively. A real fluid loader filled the rocket and its operation retired, which left an escrow
binding. The launch then took a governed flight. `RocketTransferService.tick` skips the legacy `tickLive` for a record
with `nativeTransfer`, and the bay's ascent callback is in `tickLive`. So no bay claim was made, nothing deployed, and
both menu reasons stayed NONE. The packages were preserved through the forced stop and two restarts. The designed
outcome is a refusal at D0 with `BAY_SOURCE_PROTECTED` and that menu reason, as the GameTest
`BayDeploymentGameTests.anEscrowBoundSourceIsRefusedWithBaySourceProtectedAndKeepsItsPackages` shows for a direct D0
call. In production this is reachable only once the fuel loader's admission is on, and it is off. The owner's rule
of 2026-11-09 is that a problem found is fixed first and tested again.

W358 could not make B17.3 deterministic: the coordinator renews its own source and destination tickets, and no
gated control keeps one group's end unloaded while another group runs.

## Part 1: the bay on a governed flight

1. **Confirm the cause** with code locations. Establish which bay steps a governed flight skips: D0 only, or later
   steps too. Find out whether a bay on a governed flight whose source is neutral could deploy at all.
2. **Propose the minimal fix in the record before you implement it.** The aim: on a governed flight, a bay gets the
   same D0 decision as on a legacy flight. A protected source is refused with `BAY_SOURCE_PROTECTED` and its menu
   reason, packages are kept byte-equal, and no claim, receipt or registration is written. Do not make the bay deploy
   on governed flights unless the existing design already allows it; if it does, say so and stop for Root's decision.
3. **Stop and report** if the fix changes a saved form, a network packet or a public ID. Such a change needs a design
   note and a read-only review first (AGENTS.md section 13).
4. **Implement and test.** Add a GameTest that launches a governed flight with a bay on an escrow-bound rocket and
   expects the D0 refusal and the menu reason. Rerun W358's `forced-W358-B6-BOUND` family natively with both
   restarts; its existing oracle should now pass unchanged.

## Part 2: a gated control for B17.3

Add one gated seam, in the style of W332's `ReleaseTestLoadFailure`, that keeps a chosen group's end from becoming
ready while another group runs. It must do nothing without the release-test property, and production ticket and
readiness behaviour must stay byte-identical. Build B17.3 (an unloaded group ahead of healthy groups) as a new
tranche of `run_v180_bay_uncovered.py`, with a native smoke and both restarts. If no deterministic seam fits, explain
why and leave B17.3 NOT_COVERED.

## Rules

- Do not delete tests, loosen assertions, raise timeouts or change an existing oracle's expectation (AGENTS.md
  section 5).
- Run the targeted tests, the full unit suite, and the full GameTest server with the unchanged log checker. Native
  runs and the GameTest server go under the heavy-JVM lock (WORKER-RULES). Keep evidence under
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W363/`, and delete server copies after each run.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives the cause, the fix and why it is minimal, the commands and results, the native results and
  anything not done. Its last line, and the last line of your answer, is the path of `records/W363.md`.
