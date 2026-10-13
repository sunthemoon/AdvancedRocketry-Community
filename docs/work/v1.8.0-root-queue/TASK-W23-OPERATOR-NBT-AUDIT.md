# TASK W23: operator-only item NBT for every owner, identity or guarded root

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w23-operator-nbt` |
| Branch | `codex/v1.8.0-w23-operator-nbt` (local only; Root merges) |
| Kind | Parallel bug fix (QUEUE.md, "Parallel bugs") |
| Record | `docs/work/v1.8.0-root-queue/records/W23.md` |

## Why

In 1.20.1, `BlockItem.updateCustomBlockEntityTag` loads an item's `BlockEntityTag` into the
placed block entity. It skips this only when `onlyOpCanSetNbt()` returns true and the placer
is not an operator. The W17 and W18 reviews both found block entities that store an owner or
a guarded root without that override. A creative player without operator rights could then:
- forge an owner;
- plant identities;
- push a chunk into the save guard.

About 64 classes extend `BlockEntity`. Only these override the method today:
- `PumpBlockEntity`;
- `ForceFieldNativeBlockEntity`;
- `EndgameDeviceBlockEntity`;
- `SolarGeneratorBlockEntity` and `SolarArrayBlockEntity`;
- `PlanetaryBeaconBlockEntity`;
- `LandingPadBlockEntity`.

## Scope

1. **Audit every `BlockEntity` subclass** under `src/main/java`. For each, record in a table
   in your record:
   - whether it stores an owner, member, UUID or identity, a generation, a binding,
     retained, guarded or unknown-root data, or any server-authority state that item NBT
     must not set;
   - whether it already returns `true`;
   - your decision, with a one-line reason.
2. **Override `onlyOpCanSetNbt()`** to return `true` on every class that stores any of these.
   Leave plain inventory or display-only block entities alone, and say why for each.
3. **Tests.**
   - Add one data-driven test that lists the classes that must be operator-only and asserts
     the override, so a new class cannot silently miss it. Model it on the assertion at
     `ForceFieldGameTests` line 308.
   - Add a GameTest for one representative newly covered class: placing an item that
     carries a `BlockEntityTag` without operator rights yields a fresh block entity.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- Any other behaviour change.
- The escrow and observatory work.
- Status files and the ledger.
