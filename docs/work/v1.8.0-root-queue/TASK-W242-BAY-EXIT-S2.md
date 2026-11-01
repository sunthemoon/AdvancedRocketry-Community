# TASK W242: bay safe exit, slice S2, server integration

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w242` (new; branch `codex/v1.8.0-w242` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W242.md`, its design note at the top; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W242/` |
| Design | [W230-DESIGN](records/W230-DESIGN.md) Revision 1, sections 1 to 9 and row W230-S2 of section 10 (scope, classes, the size limits per class, and what may change in each integration class) |
| Model | W239's `rocket/bay/exit/` (merged at `3a4767501`) and its review and recheck in `D:/GitHub/ARCE-Task-Evidence/root-queue/W239-review/` and `W239-recheck/` |
| Decisions | Q1 = A (R243) and Q2's message semantics (R234), in [OWNER-DECISIONS-20261101](records/OWNER-DECISIONS-20261101.md) and QUEUE |

**Root decision R254.** Implement W230-S2 as section 10 describes:

- the server permits;
- the local and checked endpoint selection, through W239's model;
- separate live and history state, as W230-R3 requires;
- the bounded, source-aware ordinary-fall safeguard, as W230-R1 requires;
- the read-only teleport-generation and descent-continuity observers, as W230-R2 requires;
- the physical self-exit, with its English and Chinese feedback.

Production admission stays disabled.

1. **Design note first.** List the exact hooks and events, each integration class's change with its line budget,
   and how each W230-R1 to W230-R4 rule is enforced. Commit the note before the code. Under the owner's delegation,
   Root decides design points, so list them for Root.
2. **Integration limits.** New `rocket/server/BayPassengerExit*` classes and one event adapter. Only the narrow
   delegations section 10 names may go into `RocketEntity`, `RocketRuntime`, `RocketManager`, `RocketFlightService`,
   `RocketTransferEntities`, `BayTransferRecovery`, `BayPassengerRouting`, `BayReleasedResume` and
   `BayReleasedFlight`, about 80 changed lines in all.
   - No class crosses 800 lines.
   - Any class crossing 500 gets a responsibility review.
   - Do not change playerdata authority, cancel dismount, release or reassign seats, add a persisted field, schema or
     packet, force a chunk load, or edit terrain.
3. **Tests.**
   - The section 9 A0 and A1 rows, including the W230-R1 to R4 cases: an ordinary fall, a dripstone landing, a
     non-FALL source through `causeFallDamage`, a teleport inside the radius, an upward reposition, a failed
     transfer, T-A then T-B then T-A, logout and re-login, death and re-entry, and contention.
   - The actual ordinary and released passenger paths, unchanged authority assertions and no-load counters.
   - runData determinism, and one full GameTest run with the log checker.
4. **Drafts for Root.** The R-042 and R-043 text that S2 makes true, in `D:/GitHub/ARCE-Task-Evidence/root-queue/W242/`
   with an audit.

## Verification

As listed in section 10's S2 row, plus the whole unit suite and the repository validator.

An independent review of the actual diff follows before the merge, because the slice touches recovery. S3 (the
two-client and restart evidence) is a later task.

## Not in this task

S3, bay admission, W238's cap, status files and the ledger.
