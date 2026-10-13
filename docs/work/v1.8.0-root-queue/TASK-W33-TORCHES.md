# TASK W33: combustion torches, unlit torches and fire starters (ADR-066 section 3.2)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w33-torches` |
| Branch | `codex/v1.8.0-w33-torches` (local only; Root merges) |
| Ledger rows | `block:unlitTorch`, `command:root/addtorch`, `config:OXYGEN.torchBlocks`, `config:OXYGEN.dropExtinguishedTorches`, `event:...blockPlacedEvent`, `event:...blockRightClicked` (W30 section 2.3) |
| Record | `docs/work/v1.8.0-root-queue/records/W33.md` |

This slice adds a block entity root, `arce_unlit_torch`, at schema 1. AGENTS.md section 13
applies:
- **First commit:** a one-page design note, `records/W33-DESIGN.md`, before any code.
- **Before merge:** one independent read-only review, which Root dispatches.

## Goal

Implement ADR-066 section 3.2's torch rules exactly. Read the whole subsection; the points
below summarize it.

- **Unlit torches.** Standing and wall `unlit_torch` blocks. The `arce_unlit_torch` schema-1
  root keeps the exact original torch-pair ID, block ID and item ID. It is limited to 2 KiB,
  depth 8 and 64 nodes. An undecodable root is kept under AGENTS.md section 3.3, and the block
  overrides `onlyOpCanSetNbt()` under the W23 rule.
- **Tags.** `combustion_torches`, defaulting to the vanilla standing and wall torches, and
  `combustion_fire_starters` (flint and steel, fire charge, blaze rod, blaze powder). They
  replace `addtorch` and `torchBlocks`.
- **The `torch_pairs` catalog.** A reviewed data catalog at schema 1:
  - at most 64 pairs;
  - no duplicate block mapping;
  - the vanilla pair is built in.

  Unsupported tagged torch blocks refuse placement instead of guessing.
- **Conversion.** When a tagged torch is placed, or an atmosphere invalidation the torch
  subscribes to arrives, a compatible torch whose eye cell or adjacent cell cannot sustain
  combustion becomes an unlit torch that keeps its orientation.
  - Existing torches are reconsidered on block and volume events, never by an unbounded Level
    scan.
  - The pending queue is fair, and capped at 8,192 positions per Level, as the section states.
- **Fire starters.** A fire starter relights a torch only where combustion is possible.
- **`dropExtinguishedTorches`.** Default false. When true, the torch is removed once and drops
  one original torch. When false, it is kept as an unlit torch. Follow the section's exact
  wording.
- **Thermite torches** are never in the tag; they stay lit in vacuum.
- **Authority.** No arbitrary block removal by a client request. Everything is decided on the
  server.
- **Configuration.** If you add configuration keys, follow the WORKER-RULES configuration-key
  rule: an exact log expectation and the `CommonConfigTest` count.

## Tests

- **Unit tests:** the catalog codec and its bounds, and the root codec at its limits.
- **GameTests:**
  - placement in vacuum converts a torch, and orientation is kept for both standing and wall
    torches;
  - a fire starter relights the torch where combustion is possible, and refuses where it is
    not;
  - the `dropExtinguishedTorches` modes;
  - an unsupported tagged block refuses placement;
  - a thermite torch is unaffected;
  - the queue cap;
  - root preservation;
  - operator-only NBT.

  Pace loading waits with `GameTestTickPacer`. Never schedule GameTest callbacks from inside a
  callback.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- The other C18a environment rules.
- Status files and the ledger.
