# TASK W91N2: the three whole-root escrow consumers under ordinary traffic (Root decision R29)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W91N-P1-F3-NATIVE-PROBES](TASK-W91N-P1-F3-NATIVE-PROBES.md)
(R27) and [records/W91N.md](records/W91N.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91n-native` (unchanged; at W91N's record `8101bf08` plus this task) |
| Branch | `codex/v1.8.0-w91n-native` (local commits only; Root merges) |
| Record | `records/W91N.md` (extend it with a W91N2 section) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/native-w91n2-20261018/` |

## Root decision R29 (recorded here)

W91N measured the ordinary traffic (hard criterion 0 in five sessions) but could not run the three whole-root
consumers: their barrier and ledger-write durations come from `EscrowTimingSink`, which `EscrowRuntime` selects, outside
the packages R27 named. Root decides:

1. **Wiring the existing timing sink is authorized** for these sessions: a gated selection in `EscrowRuntime` (the
   same gate as R27: `EscrowTestEndpoints.HOOKS`, `requireEnabled(server)` and a property or an `escrowCase` value) and
   read-only timing calls at the existing escrow decision points. When the gate is unset nothing changes. No escrow
   decision, proof, ledger write, acknowledgement order or saved format changes.
2. **R27 items 4 and 5 still apply:** the consumers may run in separate sessions, each under the corrected staggered
   eight-flight traffic (`05e93ffd`'s receipt-based staggering); where the only pre-F2 probe pauses for a process cut,
   a non-pausing variant in the same gated probe family is allowed.
3. **W91N's GameTest failure** (`ownTerrainAndEntityStoresConfirmTheExactFrozenFrontier`, `SUPERSEDING_SAVE`) is the
   known escrow-core flake that W94 handles on the shared branch; the W91 branches do not have W94 yet. Record it as
   that, with no action here. A rerun that fails only that test, with that reason, is the same known failure; any other
   failure is a finding.
4. **The admission spikes over 500 ms** (one per session, at the first admission) are recorded for the
   reference-performance campaign; this task does not chase them. If your sessions show them again, report the tick and
   value the same way.

## What to do

1. The gated sink selection and any non-pausing consumer variant (R29 items 1 and 2), with unit tests that the default
   (gate unset) is unchanged.
2. Extend `scripts/run_v180_transfer_concurrency.py` with the three consumer cases and their test: one governed F1
   admission, one pre-F2 cancellation and one settlement, each started during the traffic. For each, report the
   attempts, the refusals or stalls with their reasons, the completion latency, whether it completed, the measured
   barrier and ledger-write durations, and the longest interval with no journal mutation while it waited. Each session
   keeps one orderly restart check. State the number of starts.
3. On committed source: the whole unit suite, the runner's tests, `python -B scripts/validate_repository.py
   --require-approved-identity`, and one full GameTest with the unchanged log checker (R29 item 3 applies).

## Write scope

`resourceescrow/EscrowRuntime` (the gated sink selection only) and read-only timing calls at existing escrow decision
points, any non-pausing consumer variant in the existing gated probe family, the runner and its test, new unit tests,
and the record. No other production behaviour change; no relaxed assertion, limit, budget or timeout. Stop and report if
one seems needed.

## Commands and not in this task

As TASK-W91N says (heavy-JVM lock for every GameTest and native run, released between runs when another worker waits;
10 GiB free on C: and D: before each heavy start; delete server copies after each session; clean your own lock and
world, or write the paths in the record and final answer if the tool refuses).

Not in this task: the F3 and N2 cuts, the merge with the shared branch, the independent review, the admission spikes'
cause, ADRs, the risk register, status files and the ledger (Root).
