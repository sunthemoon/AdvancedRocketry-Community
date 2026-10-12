# TASK W13F: named-pad occupancy must use the parked rocket's real structure

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w13-landing-pads` (at the W13 delivery `65cedba9`) |
| Branch | `codex/v1.8.0-w13-landing-pads` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W13-review/REVIEW.md`, SHA-256 `d5b1db4adc436c5059af516a5637904038d1ab125fd9d80117471e667c167a63`. Verdict BLOCK on R1 only. |
| Record | `docs/work/v1.8.0-root-queue/records/W13F.md` |

## Problem

Read R1. `NamedLandingPadStaging` finds occupants with an entity query over the arrival
box. A rocket entity's bounding box is only 1.5 × 4 around its origin, so a wide rocket
parked over the pad, whose structure extends into the arrival region, is not found. A
second rocket can then land overlapping it.

## Root's decision

1. Named-pad occupancy (initial selection **and** the pre-spawn recheck) compares the
   candidate arrival region with every nearby rocket's **actual structure extent**, taken
   from its immutable snapshot bounds.
2. Discovery is bounded and loaded-only. Search for rocket entities within a region that
   contains any rocket whose structure could reach the arrival box: the arrival box
   expanded by the maximum legal rocket half-extent in X and Z, plus the height bound,
   inside the station region and the already-required loaded chunks. Only ever use
   loaded-entity lookups. Keep the existing limits of 16 landing chunks and 2,048
   inspected blocks; if the discovery cost needs its own bound, add an explicit one and
   test it.
3. An overlap is a stated refusal. No claim and no destination entity are created.
4. The legacy (unnamed) selector has the same pattern. Do not change it in this task.
   Record it as a follow-up with the review's reference, so Root can schedule it.
5. Tests (GameTests), covering the review's geometry:
   - a wide parked rocket whose structure covers the pad while its entity box does not;
   - a named-pad flight targeting that pad is refused, both at preparation and at the
     pre-spawn recheck;
   - no new claim or entity;
   - a non-overlapping pad is still accepted.

   Pace any loading wait with `GameTestTickPacer`.

## Commands

- The whole unit suite once.
- `runGameTestServer` (under the lock), then the log checker. Report the totals.

## Not in this task

- The legacy selector.
- Other pad behaviour.
- Status files and the ledger.
