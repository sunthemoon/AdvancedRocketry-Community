# TASK W120-review: independent read-only review of W120 (cargo leaf D-A, the item devices) before merge

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. A Claude session wrote W120 and both Codex slots are
busy, so this is a fresh Claude session with no context from the author (as for W118-review).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w120` at `57e6f3ed` (base `53e9fa1a`; tested source `10aca023`) in `D:/GitHub/arce-v180-w120`. Read it with `git -C D:/GitHub/arce-v180-w120 diff 53e9fa1a..57e6f3ed`, `git show` and the file reader; never check out, commit or edit there |
| Task it answers | [TASK-W120](TASK-W120-CARGO-D-A-ITEM-DEVICES.md) |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 5.1 (the item column), 5.3, 5.4, 5.5 and 7, and section 6's D-A row; R-035's cargo paragraph and ADR-065's cargo note |
| Mirror | C-A: [records/W109.md](records/W109.md) and `D:/GitHub/ARCE-Task-Evidence/root-queue/W109-review/REVIEW.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W120.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W120/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W120-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The root** `arce_rocket_item_cargo`: schema 1, 32 KiB, depth 20, 2,048 nodes; four slots under section 5.3's stack
   bounds; the role equal to the block's; the closed field set and `{}` for an empty slot (W118-R6); an unknown, future,
   invalid or mismatched root kept raw and protecting the device. Does D-P's code read every root W120 writes, and the
   reverse?
2. **Save protection** under section 7 and R-035, exactly as C-A's: only an observed native incompatibility uses
   `GuardedChunkSaves`; any new save refusal beyond what R-035 states is a finding.
3. **The capability and menu**: insert-only and extract-only roles; simulation without side effects; nothing for stale,
   off-thread or reentrant calls; `isItemValid` with the stack bounds; the owner-only menu within 8 blocks; shift-click
   and every slot change through the device lock and the bounds. Can a player or automation move an item past the lock or
   a bound, or duplicate one (shift-click, drag, hopper, a menu kept open while the device locks)? The record names one
   menu residual (a modded item that encodes to a different size each time): is it real and bounded?
4. **Drops and protection**: the buffer spills exactly once and the device item carries nothing, so the placer owns it;
   an escrow-locked or unsupported root refuses player removal before loot, leaves the explosion list, spills and drops
   nothing and refuses extraction; `onlyOpCanSetNbt` and the refused `BlockEntityTag`.
5. **The shared edits**: `RocketCargoBindingChecks` (new owner-and-binding overloads the fluid methods now call),
   `RocketFluidCargoIndex` (both device kinds under one placement UUID), `RocketCargoDeviceHold` (`CONTAINER` for items,
   `TANK` for fluids) and `LinkerItem`. Is the fluid devices' behaviour unchanged? W117 (C-E1, being fixed in W117F) also
   edits the fluid device path; say what a merge must keep.
6. **Admission is unwired**: nothing in production starts an escrow operation from these devices.
7. **Resources**: recipes exactly as section 5.5 (I `item_io_board`, C `control_board`), loot, models, blockstates, en_us
   and zh_cn text, the preauthoring and generated provenance, the art drawn in code and distinct from the fluid devices';
   the four new log-manifest entries exact and justified.
8. **Tests**: do the A0 and A1 tests cover the D-A row (C-A's checks for items, the spill exactly once, an oversized stack
   both offered and already held)? No existing test changed: confirm it. Classes over 500 lines.
9. **The record** against the code.

## Rerun

Export the subject with `git -C D:/GitHub/arce-v180-w120 archive 57e6f3ed` into a new folder under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W120-review/` and, there only, run with JDK 17.0.7
(`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: the two new unit classes, the existing tests of every
touched class (`RocketCargoBindingChecks*`, `RocketFluidCargo*`, `RocketCargoDeviceHold*`, the linker and registration
tests, `RegisteredContentResourcesTest`, `RegisteredItemReachabilityTest`) and `python -B scripts/validate_v180_provenance.py`
if it runs without network. You may add a probe test to check a finding; keep it in the output folder. Run no GameTest,
`runData` or server, check that 6 GiB of memory is free before each Gradle run, and run one Gradle process at a time.
Delete the export's `build` and `.gradle` folders afterwards and keep only logs.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W120-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
