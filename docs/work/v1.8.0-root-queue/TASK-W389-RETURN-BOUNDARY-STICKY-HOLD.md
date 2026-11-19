# TASK W389: a launch-boundary refusal during a return proof callback must not hold the return until a restart

Root: Claude. Date: 2026-11-19. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w389`, branch `codex/v1.8.0-w389` from the shared branch |
| Decision | Root decision R519 (QUEUE row W389) |
| Source of the finding | [W11B2-C50](records/W11B2-C50.md), item 1, the paragraph that ends "I left it for Root"; ADR-065, the second paragraph "Open before W11b3 or any admission"; [FUEL-ADMISSION-REPORT](records/FUEL-ADMISSION-REPORT.md), row F19a |
| Record | `docs/work/v1.8.0-root-queue/records/W389.md` |
| Write scope | `resourceescrow/EscrowFlightSource.java`, `EscrowGovernedReturn.java`, `EscrowGovernedTerminal*.java` only if the terminal's behaviour must follow, their unit tests, one new GameTest class or method in the existing escrow return GameTests, its batch registration, the record. No other production file, no limit, budget, timeout or log-manifest change without stopping for Root first. |

## The finding

`EscrowFlightSource.same()` includes `boundary.getAsBoolean()`, the launch boundary. `observe()` sets the sticky
`unchanged` flag to false on any mismatch except transient motion. When the boundary refuses at the exact moment of a
return proof callback (for example while an overlapping assembly transaction holds the region), the flag is set for
good. `EscrowGovernedReturn.checked()` then fails `state.source.observe()` on every later visit and holds the return
with `RETURN_ENVELOPE` until a restart clears the in-memory work. The return's own early check
(`!boundary.getAsBoolean()` returns HELD without touching the flag) shows that a boundary refusal is meant to be a
"not now" answer, as transient motion is (R17 item 5, W11B2-C50 item 1). The terminal recaptures in this case; the
return does not.

## What to do

1. **Reproduce first.** Write a test that fails on the current source: a unit test of `EscrowFlightSource.observe`
   and `EscrowGovernedReturn` with a boundary that refuses once and then passes, and a GameTest that makes the launch
   boundary refuse during one return proof callback window (for example by holding an overlapping assembly
   transaction for that window, in the style of `motionDuringReturnProofsIsTransientAndCompletesCancelledF7AndF8`),
   then releases it, and requires the return to complete without a restart. Record the failing run.
2. **Fix it with the smallest change.** Treat a boundary refusal in `observe()` as transient: answer false without
   setting the sticky flag, so the proof callback releases its proof and proves again on a later visit under the
   existing retry schedule. Every persistent field comparison stays sticky. Do not add a recapture of the return's
   source, and do not add a per-visit full capture: a persistent boundary refusal must stay as cheap as the return's
   existing early HELD check. Check every other caller of `EscrowFlightSource.observe()` (the terminal's F6
   callback, F1, F4, the pre-F2 cancellation) and say in the record what each one now does on a boundary refusal; if
   one of them needs the old sticky behaviour, keep it for that caller and say why.
3. **Keep the disk side unchanged.** The disk validators and saved proofs are not part of this change.
4. **Tests.** The new unit tests and GameTest must pass after the fix and fail before it. Keep the existing
   transient-motion tests unchanged and passing. Add a negative control: a persistent mismatch (for example a moved
   rocket) still sets the flag and still holds with `RETURN_ENVELOPE`.
5. **Verification.** Compile; run the changed unit classes, the whole unit suite once, and the escrow return GameTest
   batches. Run one full GameTest run with the log checker under the heavy-JVM lock (WORKER-RULES), with
   `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. A new expected WARN or ERROR needs Root's approval first.
6. **Record.** Write `records/W389.md`: the reproduction (before and after), the change, each caller's behaviour, the
   tests, the commands and results, and what stays open. The last line of the record is its own path.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Commit on the branch in reviewable commits (the failing test may be its own commit), in the style of AGENTS.md
  section 6. Do not push.
- A different engine reviews the change read-only before Root merges it.
- The last line of your answer is the path of the record.
