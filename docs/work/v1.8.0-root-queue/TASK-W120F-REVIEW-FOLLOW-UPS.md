# TASK W120F: W120's review follow-ups (W120-R1, R2 and R4) under Root decision R47

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W120](TASK-W120-CARGO-D-A-ITEM-DEVICES.md),
W120's record (`docs/work/v1.8.0-root-queue/records/W120.md` on your branch) and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W120-review/REVIEW.md` (SHA-256 `f9f0457f…5baa`) with its probe in that folder,
first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w120`, branch `codex/v1.8.0-w120` at `57e6f3ed`; continue on that branch |
| Record | append a "W120F" section to `docs/work/v1.8.0-root-queue/records/W120.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W120F/` |

## Root decision R47 (2026-10-21)

1. **W120-R1 (High), fixed before merge.** The device menu hands vanilla a copy of each slot and ignores changes to that
   copy, so vanilla's in-place item overrides (a bundle's right-click, and any modded item that changes the slot stack
   in place) can duplicate items without limit or delete them. Device-slot changes must reach the device first: handle
   device-slot clicks on the server so every outcome is written through the device (its lock and section 5.3's bounds),
   or prove conservation on every click and refuse the click otherwise. Prefer the first. This also removes W120-R3.
2. **W120-R2 (Medium), fixed before merge.** A device filled while unbound up to the root's 2,048-node limit cannot be
   bound: the bind throws an uncaught exception that the server logs at FATAL on every click, with no message. Reserve
   room in the root for a binding and one escrow marker when accepting items, so a filled device can always be bound,
   and make any bind refusal a clean refusal with a status message, not an exception.
3. **W120-R4 (Low), fixed with R1.** Add menu tests for right-click, number-key swap, throw, double-click and drag, in
   addition to the existing left-click pickup and shift-click.

## What to do, each in its own commit with a test that fails before the fix

1. A GameTest (or a unit test where the menu can be exercised without a server) that duplicates or deletes items with a
   bundle through a device slot on the current code; then the R1 fix; then the R4 tests.
2. The probe's R2 case as a permanent test; then the reservation and the clean refusal.
3. The record, including what changed for W120-R3.

No saved format, schema, bound, budget or timeout changes (the reservation is an admission rule within the existing root
bounds); no existing assertion is relaxed. A new WARN or ERROR that a GameTest drives needs its own exact manifest entry.
Stop and report if any of these seems needed.

## Verification

Each changed test class alone, then the whole unit suite. Then one full GameTest with the unchanged log checker on
committed source, under the heavy-JVM lock, releasing it and deleting `build/gametest/world` in the `finally` of the
same detached script that took it (WORKER-RULES). If the checker refuses only the host-overload warning count, record
the count and the number of agent sessions running. Check that 6 GiB of memory is free before each Gradle run.

## Not in this task

D-E (admission), W117's merge (Root's; the review lists the one conflict in `RocketCargoBindingChecks`), ADRs, the risk
register, status files and the ledger (Root).
