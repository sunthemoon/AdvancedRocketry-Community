# TASK W11b2, continuation 07: durable O2 to O4 and the O-veto

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) first. You are a new session. Your context is the
W11b2 record, [records/W11B2.md](records/W11B2.md) (at `30e6e3fc`). Read in full the "Continuation
05" and "Continuation 06" sections, including the settlement design written before code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (continue on top of `30e6e3fc`) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Record | Add a "Continuation 07" section to `records/W11B2.md`. Keep the existing text |

## 1. Merge the shared branch first

Merge `codex/v1.8.0-classic-content` (`f58f4c9f` or later) into this branch. It brings:
- W36's fix for both escrow barrier GameTests, so the `SUPERSEDING_SAVE` failure should no longer
  appear in your runs;
- W33's torches;
- W38's fall scaling.

Resolve conflicts while keeping every assertion. Under the W28 rule, no GameTest callback may
schedule another. Do not rewrite commits up to `3c47fa07`; Root merges that range as part 1.

## 2. O2 to O4 and the O-veto (the whole scope of this continuation)

Build on the O1 intents of continuation 06 and its design section.
- **O2.** A durable survivor-or-absence decision.
- **O3.** An acknowledged tombstone, with the matching journal removal.
- **O4.** An acknowledged release, and floor retirement.
- **The O-veto**, for every choice and for connected chains.

Requirements:
- RAM absence never authorizes release or retirement.
- No record is dropped in a way that lets an unproved returning identity replay.
- Forced-stop cuts go before and after each acknowledgement. Each cut needs two restarts, pinned to
  the source and the jar.
- If the design section needs to change, update it in the record first and say what changed.

Credited-world reconciliation and the rest of the native matrix stay for a later continuation.
Keep the scheduler's fail-closed behaviour.

## Cleanup

At the end, delete your generated `build/gametest/world` with a plain
`Remove-Item -Recurse -Force build\gametest\world` or `rm -rf build/gametest/world` from the
worktree root. If the tool refuses, record it, and Root deletes it.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker.
- The native cuts, with evidence under 100 MB.

## Not in this task

- Reconciliation, the remaining matrix, W11b3 and the bay slices.
- Production admission.
- ADRs and the risk register (Root).
- Status files and the ledger.
