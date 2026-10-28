# TASK W177: canonical identifier length in content parsers, and W168-R5's guards

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w177` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w177` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W177.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W177/` |
| Design | [records/W173-DESIGN.md](records/W173-DESIGN.md), sections 6.3 (the content parsers) and the W168-R5 part; its probes are in `D:/GitHub/ARCE-Task-Evidence/root-queue/W173/` |

W173 found that three content parsers bound an identifier's **spelling** at 128 characters, not its **canonical** form,
which gains `minecraft:` when the namespace is omitted:
- `SatelliteKindDefinition.parse`;
- `ResourceTableCodec.id`;
- `SatelliteComponentDefinition.location`.

A datapack can therefore define a satellite whose canonical ID has 138 characters. The shipped catalog accepts it, and
a satellite launched from it saves. On the next load the registry decoder refuses that `definition_id`, the registry is
blocked, and the world refuses to start. The resource-table fit check also counts the definition ID by its spelling,
so a near-bound asteroid or gas mission can exceed 4,096 bytes in a reachable state.

## What to do

1. **Reproduce first.** Turn W173's `KIND_DEFINITION` probe into a regression: a datapack definition with a
   128-character unqualified ID. It must fail on the shared head.
2. **Bound the canonical identifier** in the three parsers, exactly as `BoundedCelestialCodecs.validateResourceLocation`
   already does.
   - A definition, table or component over the bound is refused at datapack load, with the catalog's existing
     refusal and diagnostic for an invalid entry.
   - Saved data, the registry decoder and load acceptance do not change. Option A of W173 waits for the owner.
3. **Make `SatelliteRecordFit.resourceMissionBytes` count canonical identifiers,** so that the table check covers
   every reachable mission state. Add a test at the 4,096-byte boundary.
4. **Add W168-R5's guards** (W173-DESIGN, the W168-R5 part):
   - replace the `int` overload of `StorageBudget.reserve` with an accounts-only `reserveAccount(UUID owner)`, and
     move the four production calls and three test calls as the note lists;
   - add a test that the codec sizer overrides all four reservation methods itself;
   - make both test `CountingSizer`s delegate those methods, test code only.
5. **Change no bound, limit, saved format or protocol.**

## Verification

- Each new test alone, failing first where it reproduces.
- The `satellite` and `celestial` packages, then the whole unit suite.
- One complete GameTest with the unchanged checker; R131's host-lag rule applies.

## Not in this task

W173's option A (refusing an unwritable root at load), any decoder change, ADRs, the risk register, status files and
the ledger. A fresh read-only review follows before merge.
