# TASK W156: Moon landings at the real surface (real-client finding F2 of 2026-10-06)

## Resumption under ADR-075 (2026-10-27)

The first session stopped before code because the accepted ADR-033 and ADR-063 keep the Moon's y=80 floor
([records/W156.md](records/W156.md)). The owner has since accepted
[ADR-075](../../decisions/ADR-075-MOON-LANDING-ON-THE-HEIGHTMAP.md) (2026-10-27), which replaces that rule for new
Moon arrivals. A fresh session implements it.

- **Plan:** follow [records/W165.md](records/W165.md). Use its classes, its test-first order and its inventory of the
  Moon-flying tests, fixtures and scripts.
- **Tests:** ADR-075's two named test transitions, new cases 1 to 12 and the native restart case for recovery
  identities.
- **Worktree and branch:** `D:/GitHub/arce-v180-w156h` on `codex/v1.8.0-w156h`, from the commit named in your prompt.
  Append a "Resumption" section to `records/W156.md`.
- **Limits:**
  - No change to bounds, budgets, saved formats or the protocol beyond what ADR-075 states.
  - Shape the pad area first when a test needs a specific height. Do not weaken any assertion.
  - Move the rule out of `RocketLandingPadSelector` (501 lines) as the plan says.
- **Verification:**
  - each changed unit class alone, then the whole unit suite;
  - one complete GameTest with the unchanged log checker;
  - the native restart case with two restarts;
  - the native scripts the plan says to rerun.

  Run heavy work under the heavy-JVM lock with the 90-second hand-off. Root reviews the diff before merge.

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md), finding F2 in
`D:/GitHub/ARCE-Task-Evidence/pre-v18-client-20261006/REPORT.md`, and the W83b row in [QUEUE](QUEUE.md) (the arrival
path it changed) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w156` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w156` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W156.md` (new; a short plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W156/` |

## The finding

`RocketLandingPadSelector.originAtSurface` raises the landing surface on the Moon to
`SafeCelestialTravel.FIXED_FEET_POSITION.getY()`. Where the Moon's terrain is lower, a landed rocket stands in the
air with nothing under it, and its passengers fall when they leave it. The 2026-10-06 real-client test saw this at
y=80.

## What to do

1. Find why the floor exists: the commit and ADR that introduced it, and what `FIXED_FEET_POSITION` protects (a fixed
   arrival platform, a flat legacy Moon, a void guard). Record it in the plan.
2. Land on the real surface: the highest motion-blocking block under the footprint, as on every other body. Keep
   whatever protection the floor really gave, through the existing checks: build limits, loaded and inspected
   columns, the bounded inspection count, pad and footprint exclusions, safe placement of passengers. If the floor is
   still needed somewhere (for example a column with no ground), state where, and apply it only there.
3. If the change touches a saved format, a bound, a budget, an ADR's text or a protocol field, stop and report.

## Verification

Unit tests for the origin computation (Moon columns below, at and above the old floor; a column with no ground; mixed
footprints). GameTests: a rocket landing on generated Moon terrain below the old floor rests on the ground, and its
passengers dismount onto it without fall damage beyond vanilla's. A landing at a pad and a station arrival behave as
before. Each changed class alone, the whole unit suite, and one complete GameTest with the unchanged log checker,
under the heavy-JVM lock with the atomic pattern and the 90-second hand-off rule. Check C: and D: above 10 GiB and
6 GiB free memory first.

## Not in this task

The slow-falling mob hover (vanilla's motion dead zone, a separate W38 follow-up), the rocket position sync interval
(finding F3), other real-client findings, ADRs, the risk register, status files and the ledger (Root).
