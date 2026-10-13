# TASK W40: the space safety return (C18a, ADR-066 section 3.3)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w40-safety-return` |
| Branch | `codex/v1.8.0-w40-safety-return` (local only; Root merges) |
| Ledger row | `event:PlanetEventHandler.playerTick(LivingUpdateEvent)`, shared with underwater breathing (W39) |
| Record | `docs/work/v1.8.0-root-queue/records/W40.md` |

## Goal

Read the full "Space safety return" bullet of ADR-066 section 3.3 before you start. It is
precise; implement it as written. The main points:

- **Eligibility.**
  - Only an actual connected player in Space is eligible.
  - The player must be outside every committed station region.
  - The player must not be in a rocket transfer or an elevator ride transaction.
- **Timing.**
  - After 100 consecutive ticks adrift, the player becomes eligible for a return.
  - At most one attempt per player per 200 ticks.
  - The phase is cleared on Level change, logout, entering a vehicle, or the return itself.
- **The destination.**
  - The nearest station safe arrival that the player is *authorized* to VISIT.
  - "Nearest" is the squared horizontal distance from the captured query-start position. Ties go
    by station UUID.
  - This replaces the legacy farthest policy.
- **The reverse index.**
  - A small lifecycle-owned reverse index maps an owner or member to station IDs.
  - Bounds: at most 4,096 stations with 32 members each.
  - It is updated only after committed create, team and remove changes.
  - It is rebuilt from bounded registry records at startup, never on a player query.
  - Operators use the bounded all-station ID index.
  - Do not use the 32-name access-list prefix as the candidate set.
- **The resumable fair query.** The limits are:

  | Limit | Bound |
  |---|---|
  | Jobs per player | 1 |
  | Jobs in total | 64 |
  | Candidate inspections per job per pass | 32 |
  | Inspections per server tick, globally | 128 |
  | Loaded-cell safety probes per server tick, globally | 256 |

  The query keeps a stable index cursor and its best candidate until the whole candidate set is
  examined. Never return the first safe prefix, and never retry 4,096 records synchronously.
- **Safety probes.**
  - They are interruptible inside the probe budget.
  - An unsafe or unloaded destination is skipped with no world access that loads chunks.
  - No new station ticket and no force load.
- **The commit.**
  - Revalidate VISIT, the body and profile, loaded safety, the same Level, and that the player is
    still adrift.
  - Generation changes of the index or access invalidate the selection, and a fresh bounded pass
    resumes.
  - Entering a committed region cancels the job.
  - If a complete pass finds nothing, use the safe Overworld return.
  - An unknown or blocked registry cannot authorize a station and is not an empty successful query.
- **Movement and messages.**
  - Use the existing server travel services for coordinate selection, the protection and
    TELEPORT event, and the transfer lifecycle.
  - Announce the destination or the refusal in English and Chinese translations.

This slice adds a lifecycle-owned index and a scheduler. It persists nothing new; if you find
that it must, stop and say so. Write a one-page design section at the top of your record before
the code. Root will have the slice reviewed independently.

## Tests

- **Unit tests:**
  - the index bounds and updates;
  - the fair query's budgets, cursor and tie-break;
  - the eligibility phase machine.
- **Native GameTests:**
  - a player adrift for 100 ticks returns to the nearest authorized station's safe arrival, not
    the farthest one;
  - an unauthorized nearer station is skipped;
  - an unloaded nearer arrival is skipped with no chunk loaded (count the chunks and tickets);
  - with no candidate, the player returns to the Overworld;
  - entering a region cancels the job;
  - a player in a rocket transfer or an elevator ride is not eligible;
  - the rate limit holds;
  - many adrift players stay inside the global budgets.
- **Regressions:** the station, elevator and rocket suites pass unchanged.

## Commands

- The whole unit suite.
- `runData` under the lock, for the language strings.
- `runGameTestServer` under the lock, then the log checker.

## Not in this task

- Underwater breathing (W39).
- Other C18a rules.
- Station registry schema changes.
- Status files, the ledger, ADRs and the risk register (Root).
