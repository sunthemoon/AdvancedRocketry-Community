# TASK W27b: the satellite bay block (deployment disabled)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w27b-satellite-bay` (from the shared branch) |
| Branch | `codex/v1.8.0-w27b-satellite-bay` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W27B.md` (new) |
| Contract | [records/W27A-DESIGN.md](records/W27A-DESIGN.md), frozen (rounds 1 to 3): section 6 ("The bay block") and the W27b row of section 9's slice table. Sections 2 to 5, 7 and 8 belong to W27c1 to W27d; read them only for context |
| Risk | R-038 in `docs/11-RISK-REGISTER.md`, registered by Root before this task (the save refusal of a bay root that native serialization cannot keep) |
| Ledger | `block_variant:loader/1` (the rocket satellite bay) stays PLANNED: deployment arrives with W27d and W27e |

## Scope (the W27b row of section 9)

Build, with deployment **disabled**:
- the bay block, block entity, menu and item capability;
- the locks: no live slot while captured in a rocket; insertion and extraction refused while an
  assembly or disassembly transaction claims the position; the menu opens for the owner or an
  operator, never a spectator, within 8 blocks;
- the schema-1 root exactly as section 6 specifies (owner UUID, then at most one of `package` or
  `consumed_op`; the package bound is the native item bound of 4,096 bytes, depth 16, 256 nodes,
  `Count` 1; unknown fields, both entries, or wrong types quarantine the bay and keep the data);
- the package identity decoder through `SatelliteItemData.readTag` and
  `SatelliteTerminalInventory.validPackage`, with FUTURE and unknown-item packages kept raw;
- the host rocket adapter `advancedrocketrycommunity:satellite_bay_v1`, registered beside
  `VanillaContainerRocketAdapter` in `RocketAdapterRegistry.freeze()` and
  `RocketBlockEntityAdapters.defaults()`. Capture copies the root exactly, and restore must recapture
  an equal payload. Bays share the 128-payload budget;
- section 6's removal policy:
  - a valid, available, unclaimed world bay drops its package once, as the ordinary item, plus an
    empty bay item on a normal break or explosion; a `consumed_op` bay drops only the empty bay;
  - loot and removal callbacks share one extraction flag;
  - there is no `BlockEntityTag` carrier, and no owner or root restoration on ordinary placement;
  - guarded roots (raw, unknown, future, quarantined) refuse player break, leave the explosion's
    block list, and drop nothing;
  - a serializable quarantined root is saved unchanged. Only where native serialization cannot keep
    it, use the existing `GuardedChunkSaves` denial, as R-038 states;
  - `onlyOpCanSetNbt` for the block entity type;
- the status slots: two `ContainerData` slots (phase and reason). With deployment disabled they show
  a fixed "deployment not available" reason;
- the recipe, original art made in code (WORKER-RULES: never copy Minecraft or Forge art; copy from
  the original Advanced Rocketry repository only when the ledger already lists the file), and
  English and Chinese text for every message.

No receipt store, no admission authority, no registry sequence, no deployment, and no hook into the
transfer service: those are W27c1 to W27d.

## Tests (the W27b row of section 9)

- **A0:** the root codec at every bound (4,096 bytes, depth 16, 256 nodes, `Count` 1, the field set),
  just inside and just outside each bound.
- **A1 (GameTests):**
  - the menu;
  - a hopper and a dropper (simulation and the actual calls);
  - the owner refusal;
  - the transaction lock;
  - rocket assembly and disassembly keep the root byte for byte;
  - raw, unknown and future roots;
  - `onlyOpCanSetNbt`;
  - a valid bay drops exactly once on a normal break and on an explosion;
  - a guarded root drops nothing, and non-operator placement restores nothing.
- **S1:** a native save and reload of a bay with a package. Also the serializer refusal, with the exact
  chunk and Level scope that R-038 states. Use the existing native harness pattern; two restarts.

## Commands

- The whole unit suite once.
- `runData` under the lock (new block, item, model, language, recipe and loot table).
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker. A new expected
  WARN or ERROR needs its own manifest entry with an exact count, and you must say why.
- The native S1 run under the lock, evidence under 100 MB in
  `D:/GitHub/ARCE-Task-Evidence/root-queue/W27B/`.
- At the end, delete your generated `build/gametest/world`, native copies and old jars with a plain
  `rm -rf`, and make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths;
  Root deletes them.

## Not in this task

- Receipts, admission, the registry sequence, deployment, the transfer hook, and anything for W27c1
  to W27f.
- ADRs, the risk register, status files and the ledger (Root). If R-038's wording does not match what
  you build, stop and report.

## Root decision (2026-10-15): fix the review's W27B-R1 before merge

The independent review is at `D:/GitHub/ARCE-Task-Evidence/root-queue/W27B-review/REVIEW.md`, SHA-256
`8ea41a4c29a117f9095acbad62e3e743dbadf31b4b99341e645f6b9d43da1e53`. Its verdict is FIX-FIRST, with one
High finding: `nativelyKeepable` treats the 2 MiB validation ceiling as a native serialization failure.

- **The defect.** A quarantined or future root just over 2 MiB writes and reads unchanged through the
  terrain path. `RegionFileStorage.read` uses `NbtAccounter.UNLIMITED`. Yet that root denies its whole
  chunk.
- **The rule.** Keep R-038 and section 6 exactly as written: refuse a save only when native
  serialization cannot keep the root.
  - Separate three things: the operational schema limits, the bounded validation work, and real native
    write or read incompatibility.
  - A root that the terrain writer and reader keep losslessly is retained without any save denial.
  - Keep the real lossy-write and depth protections.
  - Do not remove the traversal bounds. If a guarded root is too large to validate within the work
    bound, it stays guarded and unchanged. It is saved verbatim, not denied.
- **Tests.**
  - A regression that round-trips the reviewer's oversized fixture, a shallow byte array just over
    2 MiB, through `NbtIo` in the real `block_entities` nesting. Check that its save-admission decision
    is "keep, no denial".
  - Keep the existing tests for roots that really cannot be kept.
- **Rerun** the bay unit tests, the whole unit suite, and `runGameTestServer` under the lock on a
  committed SHA, then the log checker. Add a short "W27B-R1" section to the record.
