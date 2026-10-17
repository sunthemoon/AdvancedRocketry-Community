# TASK W11b2, continuation 08: merge the part 1 fixes, close N1, credited-world reconciliation

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (continue on top of `f447affd`) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Record | Add a "Continuation 08" section to `records/W11B2.md`. Keep the existing text |

Read the "Continuation 06" and "Continuation 07" sections and their design text in full.

Root has now merged W11b2 part 1 into the shared branch (`61f818c3`), together with the part 1
fixes (W11B2-P1F, `records/W11B2-P1F.md`). The ADR-065 W11b2 part 1 note and R-035 describe them.
Continuations 06 and 07 get an independent review in parallel with this task. Root forwards its
findings separately.

## 1. Merge the shared branch first

- Merge `codex/v1.8.0-classic-content` (`1514c6c3` or later) and commit the merge on its own.
- The P1F fixes changed `RocketEscrowGate`, `RocketEscrowInterlocks`, `RocketTransferSavedData`,
  `RocketJournalCheckpoint`, `RocketJournalWriter`, `RocketFlightService`, `RocketTransferService`
  and `RocketFuelState`. Resolve every conflict so both sides hold:
  - P1F's semantics: ledger-only interlocks, dismounts never cancelled, own-record waits, scalar
    legacy fuel, the directory force, `Throwable` to UNKNOWN, discard callbacks;
  - your settlement semantics.
- Keep every assertion from both sides. If the two cannot both hold, stop and report.
- Generated language files: regenerate them with `runData` under the lock.

## 2. N1 (from Root's P1F review, SHA-256 `8266bf6f…`)

- P1F made `escrowIdle` return false for any section with a `ledger_id`. W27a R3 keeps `ledger_id`
  once a section has an epoch above 0, so a settled rocket stays locked forever.
- Restore part 1's rule: a section bound to a **different** ledger, or any bound section when no
  ledger exists, is not idle. For the current ledger, rely on the existing checks:
  - records naming the rocket;
  - logical locks;
  - tombstones;
  - markers.
- Add a GameTest: a rocket settled through the real O2 to O4 path, bound to the current ledger, is
  idle again. It can mount, refuel and launch.

## 3. Credited-world reconciliation

Implement the credited-world reconciliation that continuation 07's record lists as open, as the
W11b2 design and W27a section 8 describe.
- If the design does not freeze it precisely enough, write the missing design text first in the
  record and stop for Root.
- Keep the scheduler fail-closed.
- RAM absence never authorizes release.
- Forced-stop cuts go before and after each acknowledgement, with two restarts each, pinned to the
  source and the jar.

## Cleanup

Root deleted continuation 07's refused cleanup targets. At the end, delete your generated
`build/gametest/world` and Temp copies with a plain `rm -rf`. If the tool refuses, record the paths
and Root deletes them.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker.
- The native cuts, with evidence under 100 MB.

## Not in this task

- Signed per-fluid loss accounting (record it as still open), production admission, W11b3 and the
  bay slices.
- F3, F8 and F9. They stay open in R-035.
- ADRs, the risk register, status files and the ledger (Root).
