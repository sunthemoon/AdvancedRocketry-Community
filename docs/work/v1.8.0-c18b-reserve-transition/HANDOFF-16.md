# C18b reserve transition: source handoff 16

2026-10-08. Author: a fresh delegated Claude worker, not Root, reviewer or
approver. Task: `D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-thermite-source-execution-20261008-01/TASK-RESERVE-SOURCE16.md`.
Base: `692aa35063f7e22c1c48e33bf248f14e0b11caed` on
`codex/v1.8.0-claude-reserve-transition16-20261008`. Nothing here was compiled,
run or committed. Root owns all Git writes.

## Completed scope

The uncalled pure helper adopted by [ADOPTION-01](ADOPTION-01.md) under
[CONTRACT-01](CONTRACT-01.md), with JUnit 5 tests written but not run.

## Interface

```java
public final class SuitReserveTransition {
    public static Result tick(PlayerLifeSupportInput input, int reserveUnits,
            int reserveCapacity, int targetUnits, boolean reserveEligible);
    public record Result(PlayerLifeSupportDecision decision,
            int reserveUnits, int transferredUnits) { }
}
```

- Validation runs first and does not depend on eligibility or input state. A
  null input throws NullPointerException. Capacity below 0, reserve outside
  0..capacity, or target outside 1..2,000 throws IllegalArgumentException.
- A transfer is admitted only when all of these hold: `reserveEligible`, 4
  suit pieces, base atmosphere not breathable, volume not `BREATHABLE`, and
  input phase 19. Otherwise x = 0. When admitted, x = min(max(0, T - A), R).
- The helper always builds a new `PlayerLifeSupportInput` that is equal to the
  input except that oxygen is A + x, then calls the unchanged
  `PlayerLifeSupportEngine.tick` exactly once. It returns
  `(decision, R - x, x)`.
- No arithmetic adds A to R. A + x is at most max(A, T), which is at most 2,000.
- `Result` requires a non-null decision, a reserve of at least 0 and a transfer
  in 0..2,000. It does not tie reserve to capacity or certify provenance.
- Only java.util.Objects, AtmosphereLimits and existing life-support types are
  used. There are no instances, no mutable state and no caller.

## Files (all NEW)

- `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/life/SuitReserveTransition.java` (73 lines)
- `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/atmosphere/life/SuitReserveTransitionTest.java` (445 lines, 16 test methods)
- `docs/work/v1.8.0-c18b-reserve-transition/HANDOFF-16.md` (this file)

No existing code, test, resource, build, registry, network, schema, state,
ledger, AGENTS, draft or evidence file was changed.

## Planned tests (written, NOT executed)

- The 20 phases, the three volume states, ambient protection, and complete,
  partial and missing suits.
- Eligibility false, including the scheduled active debit.
- Active buffer empty, partial and full; target below, equal to and above
  active; reserve zero, insufficient, exact and excess.
- Reserve and capacity at Integer.MAX_VALUE.
- Illegal scalars for both eligibility values and for admitted and ambient
  inputs; null input.
- `Result` field validation and accepted boundaries.
- The existing engine-test scenarios replayed through the helper with x = 0.
- Two successive-tick runs: steady refill over 100 ticks, and reserve
  exhaustion falling back to the engine over 80 ticks.
- A 180,000-case finite grid that compares the decision with the engine on an
  independently computed transferred input.
- Every call checks long conservation: A + R = decision oxygen + remaining
  reserve + consumed.

Expected values are hand-derived from the contract. The grid uses the existing
engine as its decision oracle, with the expected transfer computed in the test.
There is no instrumentation for "engine called once". The single phase advance
and the decision equality imply it, so a reviewer should confirm it in source.

## Not done and unrun

- Not done: compilation; focused JUnit; `./gradlew clean build`, `test`,
  `runData`, `git diff --exit-code` and `runGameTestServer`; dedicated server,
  restart, S1/S2 and V0/V1/V2; G0-G9.
- Not done: caller/adapter work, tank, config/default, persistence, network
  and HUD changes.
- No content unit or Gate is closed, and v1.8.0 stays IN_PROGRESS.

## Known risks

1. If the target is 1, or the active buffer starts empty, active is 0 between
   scheduled debits. The engine then reports `OXYGEN_EMPTY` (unprotected, but
   no damage) for up to 19 ticks before the phase-19 transfer. The steady-refill
   test asserts this (19 unprotected ticks). This follows the adopted contract
   and is not a defect in this leaf. A future adapter or target choice should
   still evaluate whether that status matters for display or for readers of
   `protectedFromVacuum`.
2. Compile errors or test failures are possible because nothing has been run.
   The grid's runtime has not been measured.
3. `reserveEligible` is not authority. The adapter obligations in CONTRACT-01
   remain open.

## Merge and rollback

The files depend only on existing types at the base commit. To roll back,
delete the three new files. No migration is involved.
