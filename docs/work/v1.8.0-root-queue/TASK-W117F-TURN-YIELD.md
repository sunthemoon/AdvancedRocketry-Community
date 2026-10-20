# TASK W117F: W117's review follow-up, yielding an unused next-turn token (W117-R1)

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W117](TASK-W117-CARGO-C-E1-ADMISSION-QUEUE.md),
W117's record (`docs/work/v1.8.0-root-queue/records/W117.md` on your branch) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W117-review/REVIEW.md` (SHA-256 `46ee3410…f3cb7`) with its probe
`probes/W117TurnCancellationProbeTest.java` in that folder, first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w117`, branch `codex/v1.8.0-w117` at `18b5f795`; continue on that branch |
| Record | append a "W117F" section to `docs/work/v1.8.0-root-queue/records/W117.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W117F/` |

**Root decision R46 (2026-10-21).** W117-review blocked the merge on W117-R1: a blocked, registered device receives
the rocket's next-turn token before it has shown it can do work, and a terminal early return (`DEVICE_EMPTY`,
`DEVICE_FULL`, `EMPTY`, incompatible fuel, a changed binding, a closed switch) or a cancellation before the ticket never
yields it, so every other device of that rocket is refused indefinitely. The fuel loader is exposed through the new
busy-token assignment. Fix it before merge: a device yields only its own RAM next-token when an attempt ends without
work, is refused by a closed switch, or is cancelled, including the path before it claims a turn, in both device
adapters (`EscrowFluidCargoAdmission` and `FluidFuelLoaderBlockEntity`). A running preparation's active claim and other
devices' tokens are kept; no durable reservation is released and no endpoint, flight or journal hold is bypassed.

## What to do, each in its own commit with a test that fails before the fix

1. The yield, in both adapters, as R46 states.
2. Regressions: a waiting cargo device that becomes empty, full or disabled; a cancellation before the ticket (the
   review's probe as a permanent test); an empty fuel loader that receives a busy token and then returns `EMPTY`. In
   each, another eligible device then takes its turn. The existing successful handoff tests must remain unchanged.
3. The record: add R1 and its coverage, and distinguish the unchanged pure fill rules from the newly wired turn
   scheduling and registration (the review's note on "fill unchanged").

No saved format, schema, bound, budget, lifetime or timeout changes; no existing assertion is relaxed. Stop and report if
any of these seems needed.

## Verification

Each changed test class alone, then the `resourceescrow`, `rocket.cargo` and `rocket.fuel.loader` packages, then the
whole unit suite, each on committed source; one Gradle process at a time, with 6 GiB of free memory checked before each.
No GameTest is needed: Root runs the integrated full GameTest after the merge, when the machine is quieter.

## Not in this task

The two aggregate GameTest failures and the overload warnings (Root keeps them open), C-E2, the W118 integration
(Root's merge), ADRs, the risk register, status files and the ledger (Root).
