# TASK W159F: fix the W159 review findings (W159-R1 to W159-R3)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W159](TASK-W159-W80-REJOIN-CORE.md) (R105,
R106), the top of W159's record (`records/W159.md` on the branch), section 4 of
[records/W80-DESIGN.md](records/W80-DESIGN.md) with its R108 correction, and the review
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W159-review/REVIEW.md`, SHA-256 `79f90704…19b6`, verdict BLOCK) first. You are
a new session; W159's own session is too large to resume.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w159` (W159's; it has finished) |
| Branch | `codex/v1.8.0-w159` at `397a0a525` (local commits only; Root merges) |
| Record | append a "W159F" section to `docs/work/v1.8.0-root-queue/records/W159.md`, with a short plan at its top before code |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W159F/` (W159's evidence stays as it is, apart from item 3) |

## Root decision R108 (2026-10-25)

All three findings are accepted as the review states them. Fix each in its own commit, with a regression that fails
before the fix.

1. **P2 is the commit point (W159-R1 and W159-R2).** A readback result applies to the exact still-current session at
   the moment P2 is installed, not only when it is submitted. Bind P2's installation to a lease that the owner thread
   maintains and the IO thread only reads, as an atomic flag; the IO thread still touches no world state. The lease
   holds only while both of these are true:
   - the session is current: no death, clone or new login has invalidated it. A clean logout does not invalidate it,
     because the design lets a retained attempt finish offline;
   - for an online player, the target passed the full safety check on the owner thread in the latest tick.
2. **A failed lease keeps the obligation and allows a new attempt.** When the lease fails before P2 is installed:
   - the obligation stays durable;
   - a cloned or re-logged session is classified and placed again with a new P1 attempt;
   - an unsafe target is replaced by the next safe candidate;
   - a held P2 never blocks the sidecar or later obligations.

   You may change how the rejoin writer replaces a held capture whose lease failed and that was never installed, but
   all of these must still hold:
   - checked generations only increase;
   - an installed root never moves backwards;
   - every installed transition is one the schema already allows: PENDING to a new PENDING attempt, or PENDING to
     VERIFIED;
   - S2's existing proof lease and its retries behave as before;
   - there is no new saved field or schema, and no change to the journal root, bounds, budgets, timeouts or
     protocols.

   Stop and report only if the mechanism would need a new saved field, or a change to the journal root, bounds,
   budgets or timeouts.
3. **Linearization.** A death or a safety change that happens after the lease check, during the replacement itself,
   counts as after P2. That is the same as cut C11.
4. **The residual after P2.** After P2's acknowledgement, the fresh safety check before unguarding stays. If it fails,
   which can only happen in the window between the last lease refresh and the acknowledgement:
   - keep the player guarded;
   - place them again in the same session from the same 64 candidates, with no new durable record;
   - unguard only after a safe recheck.

   If no candidate becomes safe by the deadline, the protected disconnect saves the pinned position. State this
   residual and its window in your record.
5. **Evidence correction (W159-R3).** Leave W159's sealed files unchanged. In `D:/GitHub/ARCE-Task-Evidence/root-queue/W159/`,
   add these two files:
   - `CORRECTION-01.md`, stating the mismatch, its cause (the launcher kept writing the session events after sealing)
     and the date;
   - `SHA256SUMS-correction-01.txt`, made now that W159's session has ended.

   In your own W159F manifest, leave out the launcher's live `run-*` folders, and say so.

## Tests

- **R1.** Hold P2 before its installation with a checked-writer gate. Kill the guarded player, and in a separate case
  clone them through respawn. Then release the writer. Check that:
  - P2 is not installed;
  - the obligation stays PENDING;
  - the new session is guarded, placed with a new attempt, and verified.
- **R2.** Make the target unsafe, by removing its support or blocking it, at each of these three points:
  - after P2 is submitted and before it is installed;
  - after it is installed and before its acknowledgement is applied;
  - just before unguarding.

  Check another login and a restart before any successful unguarding.
- **Unit tests** for the lease and for the writer's replacement of a held, never-installed capture, including S2's
  unchanged behavior.
- **Native cuts.** Add two cuts with a held P2 and a failed lease, one for death and one for an unsafe target. Run
  each with two restarts and a kill during the first recovery. Inspect the sidecar and each player's dimension,
  `Pos`, marker, `RootVehicle` and health. Rerun C9 to C11 and C13.
- **Shutdown.** Name the test that stops the server with a scan in flight and checks that no thread or ticket is
  left. If none exists, add one.

## Verification

- Each fix's tests alone, failing before the fix.
- The `rocket.rejoin` package.
- The whole unit suite.
- One complete GameTest with the unchanged log checker.
- The native cuts above.
- The W159 Python tests and repository validation.

Run heavy work under the heavy-JVM lock, with the atomic pattern and the 90-second hand-off rule; W152F shares the
lock. Before starting, check that C: and D: each have more than 10 GiB free and that 6 GiB of memory is free. Keep the
evidence under 100,000,000 bytes. A fresh Codex session re-reviews afterwards.

## Not in this task

- The W160 command, the `rejoins` view and the command matrix.
- Merging with the shared head or W152; Root does that.
- Power-loss durability.
- ADRs, the risk register, `build.gradle`, status files and the ledger, which stay with Root.