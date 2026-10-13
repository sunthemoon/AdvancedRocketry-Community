# TASK W24: legacy landing-pad selector occupancy (the W13 review's R1, legacy half)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w24-legacy-pad-occupancy` |
| Branch | `codex/v1.8.0-w24-legacy-pad-occupancy` (local only; Root merges) |
| Kind | Parallel bug fix |
| Record | `docs/work/v1.8.0-root-queue/records/W24.md` |

## Problem

The W13 review's R1 is in `D:/GitHub/ARCE-Task-Evidence/root-queue/W13-review/REVIEW.md`;
read it. W13F fixed it for named pads in `NamedLandingPadStaging` (commit `3a8d4ad9`). See
[records/W13F.md](records/W13F.md) and [TASK-W13F](TASK-W13F-PAD-OCCUPANCY.md).

The legacy `RocketLandingPadSelector`, which is used when no named pad is chosen, still finds
occupants with an entity query over the arrival box. A rocket entity's box is only about
1.5 by 4 around its origin. A wide parked rocket whose structure reaches the arrival region is
therefore not seen, and a second rocket can land overlapping it.

## Root's decision

Apply the same rule as W13F to the legacy selector, reusing W13F's code where possible:
1. **Real extents.** Occupancy, for the initial selection **and** for any pre-spawn recheck the
   legacy path has, compares the candidate arrival region with every nearby rocket's actual
   structure extent, taken from its immutable snapshot bounds.
2. **Bounded discovery.** Search loaded entities only, within the arrival box expanded by the
   maximum legal rocket half-extent and the height bound. Keep the existing limits.
   W13F's explicit 64-rocket bound applies, with a translated refusal.
3. **Refusal.** An overlap is a stated refusal: no claim and no destination entity. If the
   legacy selector's contract is to try the next candidate pad instead, keep that, and say
   which it does.
4. **Tests.** GameTests with the review's geometry for the legacy path:
   - a wide parked rocket that covers a pad while its entity box does not;
   - the overlapping arrival is refused, or the next pad is chosen;
   - nothing is created;
   - a pad without an overlap is still accepted.

   Pace any loading wait with `GameTestTickPacer`.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- The named-pad path, unless you share code with it. If you do, its tests must still pass
  unchanged.
- Other pad behaviour.
- Status files and the ledger.
