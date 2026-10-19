# TASK W98-review: independent read-only review of W98 (P1-N3 leaf N3-A1) before merge

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. A Claude session wrote W98 and both Codex slots are
busy, so this is a fresh Claude session with no context from the author (AGENTS.md section 13 allows that).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w98` at `4c0d034d` (final code `397e7898`; merge base with the shared branch `cb536a1f`), in the worktree `D:/GitHub/arce-v180-w98`. Read it with `git -C D:/GitHub/arce-v180-w98 diff cb536a1f..4c0d034d`, `git show` and the file reader; never check out, commit or edit there |
| Task it answers | [TASK-W98](TASK-W98-N3-A1-SOURCE-RETURN.md) with Root decision R34 (W96's design with the amendments W96-R1 to W96-R5 and R5(c)) |
| Design | [records/W96-DESIGN.md](records/W96-DESIGN.md) and the W96 review `D:/GitHub/ARCE-Task-Evidence/root-queue/W96-review/REVIEW.md` |
| Report | `docs/work/v1.8.0-root-queue/records/W98.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W98/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W98-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

This is a persistence slice: it changes when a transfer record leaves the checked journal. Review the actual diff,
not the record's summary of it.

## What to check

1. **R34 as implemented.** For each of R1 to R5 and R5(c), find the code and say whether it does what R34 says:
   the reservation held for one bounded proof attempt with a tick deadline and released on failure, backoff or any
   wait; the pending map keyed by L with at most 64 entries; R2's I3 restriction (retire only a settled record whose
   source is stationary at its origin); R3's crew rule (offline crew never block a countdown cancellation); R4's
   census; R5's sub-quota (at most 8 pending, at most 4 entity captures per Level per batch), the silent checkpoint
   sink, the save-off hold; R5(c)'s fallback with one WARN per Level.
2. **Nothing governed changed.** The changes to `EscrowBarrier`, `EscrowBarrierBudget` and `EscrowChunkStore` must
   be the smallest additions R5 needs: check that a governed probe, proof, lease or the replay exclusion cannot see
   or be delayed by an ordinary attempt beyond the stated sub-quota, and that N2's reserve-before-mutation and W91's
   per-record scheduling are unchanged. Look at the two wiring lines in `RocketTransferService`, which are outside
   the task's literal write scope.
3. **The existing GameTests in the record's section 6.3** (about 20). For each, say whether the change is the
   expectation R34 implies (T now stays until the proof) or a relaxation: a removed or weakened assertion, a raised
   timeout or bound, or a wait that can hide a real failure. AGENTS.md section 5 forbids relaxations.
4. **The seven new log expectations** in `scripts/gametest_expected_log.json`: each must be driven by new code, with an
   exact count and batch scope.
5. **Correctness of the helper** (`RocketOrdinaryRetirement`, `RocketRetirementProof`): the order of proof, removal
   and follow-up effects; restart in each state (the native cuts N3_STORE, N3_FORCE and N3_REMOVAL); what happens if
   the proof never completes, the Level unloads, the server stops, or the rocket is removed meanwhile; any path where
   a record could be removed before its survivor is proved, or kept for ever by mistake. `RocketOrdinaryRetirement`
   is 554 lines: say whether it mixes responsibilities (AGENTS.md section 3.4).
6. **The holds that can last indefinitely** (offline crew, a save-off Level, an unloaded slot): their scope (one
   rocket or more), what the player sees, their log volume and their recovery. AGENTS.md section 3.3 requires this
   in the risk register before merge; propose the R-035 text Root should add (Root writes it).
7. **The native evidence** (`native-01` to `native-03`): do the results support the record's claims, and is the
   harness change gated (observation or pause only)?
8. Known, not yours to fix: the Space sleep test (record section 7.1) depends on its structure's position, and Root
   fixes that test separately. Say only if you think W98's code, not the position, causes it.

## Rerun

Export the subject with `git -C D:/GitHub/arce-v180-w98 archive 4c0d034d` into a new folder under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W98-review/` and, there only, run with JDK 17.0.7
(`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: the three new unit classes
(`RocketReconnectRetirementTest`, `RocketRetirementProofTest`, `EscrowOrdinaryProofTest`) and the existing
`EscrowBarrier*` and `RocketTransferRecovery*` unit tests. Run no GameTest, `runData` or server. Delete the export's
`build` and `.gradle` folders afterwards and keep only logs.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W98-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The proposed R-035 text for the indefinite holds.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
