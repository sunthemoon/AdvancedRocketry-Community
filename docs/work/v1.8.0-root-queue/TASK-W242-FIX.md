# TASK W242-fix: the R2 correction boundary and the open full-run failures

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w242` (existing; branch `codex/v1.8.0-w242` at `4d3beafef`, which is W242's `c59ae628b` with Root's merge of the shared head `dbc3c90c2`) |
| Blocker | `docs/work/v1.8.0-root-queue/records/W242.md`, sections "R2 integration blocker" and "Failures, remaining scope and risks"; evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W242/` (the reposition trace in `smoke-4/latest.log`) |
| Design | [W230-DESIGN](records/W230-DESIGN.md) Revision 1, rule W230-R2, and [TASK-W242](TASK-W242-BAY-EXIT-S2.md) |
| Record | Append a "W242-fix" section to `records/W242.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W242-fix/` |

**Root decision R260**, under the owner's delegation, narrows W230-R2 for one case:

- On the tick after detach, vanilla's `ServerEntity` sends the removed passenger one tracking correction
  (`connection.teleport`) to the position the server already holds. That correction moves nobody, so it cannot carry
  a protection offer to another place.
- **Accept, without revoking, at most one correction per offer whose target is exactly the player's current server
  position** (equal coordinates, no relative flags) and whose counter step is exactly one, inside the offer's existing
  deadline. It does not rebaseline the descent: the origin, radius, movement allowance, deadline and every other R2
  limit continue from the exit's own baseline.
- Any correction that changes the position, a second correction, a counter step other than one, a different
  connection, or uncertainty about the target still revokes, as R2 says.
- Observation stays read-only. Either resolve one more cached read-only field (the pending teleport target, through
  `ObfuscationReflectionHelper` as `f_9767_` is) or compare the player's position across the correction; say which
  and why. No reflective write, ASM, Access Transformer, teleport allowlist or deferred grant.

**Root decision R264 (2026-11-01, 19:50) replaces R260's acceptance test**, after W242-fix showed that the pending
target equals the new server position even for a moving correction and that relative flags leave no trace in the
allowed observations:

- Accept at most one teleport-counter step beyond the exit's own correction, within the first two server ticks after
  detach, on the same connection, **only if the next position sample still satisfies every existing continuity
  limit against the previous sample** (no upward move, at most 1 block horizontal and 4 down per tick, within 8
  blocks of the exit's origin, finite, ordinary state). Relative flags do not matter: only the resulting server
  position is judged, and it is judged by the limits ordinary movement already has.
- It never rebaselines: origin, radius, allowance and deadline stay the exit's own. A second extra step, a step
  after the two ticks, a step other than one, another connection, or a sample that fails continuity revokes as R2
  says.
- No new observation is needed (the existing counter and position samples), and none of the forbidden mechanisms.
  Vanilla's removed-passenger correction (`ServerEntity.sendChanges` teleports the passenger to its own current
  position) passes by construction.

## Steps

1. **The R2 boundary.** Implement R260 with unit tests for each accepting and revoking case above (failing first where
   they check a behaviour change). Then rerun `bay_exit_ordinary_fall_only` unchanged: it must pass, with the offer
   surviving vanilla's correction and exactly one cancelled attack.
2. **The other live-descent cases.** Trace why `nonfallcauseinsidevanillalandingisnotprotected` lost its live offer
   before its stimulus, and recheck the later-landing and expiry cases the record names, so each fails or passes for
   its own named reason, not an early revocation.
3. **The two unchanged tests that failed in the full run** (`doublechestboundaryandlazylootstaysealedundertickingautomation`
   timed out at 400 ticks; `akilledrocketleaksitsrejectedreceiptuntiltheoperatorretiresit` got 0 from the operator
   retirement). Run each in a focused run on this branch and on `9ace3837a`, W242's base. If W242's changes cause one
   (for example through `BayTransferRecovery` or `RocketTransferEntities`), fix it; if it also fails on the base, keep
   both logs and say so. Change no assertion, timeout or budget.
4. **R-042 and R-043 drafts** updated for R260, with an audit.

## Verification

- The new and changed unit tests, the whole unit suite and the repository validator.
- The bay exit GameTest batches and the two unchanged tests in focused runs under the heavy-JVM lock.
- One full GameTest run with the log checker.

Commit on the branch; do not push. Root pushes for a sharded run, and an independent review of W242's whole diff
follows before the merge.

## Not in this task

S3, bay admission, status files, the ledger and the risk register itself (drafts only).
