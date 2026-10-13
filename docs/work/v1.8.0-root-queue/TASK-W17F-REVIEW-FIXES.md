# TASK W17F: fix the W17 review findings

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w17-solar-array` (at the W17 delivery `d9cb2467`) |
| Branch | `codex/v1.8.0-w17-solar-array` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W17-review/REVIEW.md`, SHA-256 `4bd848ae286597d29e05bbb38ca45cddcd1ca9845ae0d07805bf40cc147873b2`. Verdict BLOCK on F1 (High); F2 Medium; F3 to F5 Low |
| Record | `docs/work/v1.8.0-root-queue/records/W17F.md` |

Read the whole review first. Fix each finding in its own commit, together with its tests.

## Root's decisions

1. **F1: a panel counts for one array only.** ADR-065 section 9 counts each panel as one
   collector, and section 1 says about 2 FE per panel. Shared panels are not legacy parity
   that we keep.
   - A panel cell that another FORMED controller counts is a conflict, exactly like a
     shared plug. Apply the review's suggested fix:
     - check the counted cells with `footprints.controllersAt` after `evaluate`;
     - wake CONFLICT controllers when those cells are released.
   - Add GameTests for:
     - the two-end layout (NORTH at the origin and SOUTH at `(0,y,22)`, sharing 63 panels);
     - the perpendicular crossing (sharing 9 panels);
     - only one array forms and counts each cell;
     - the other array recovers after the first is removed.
2. **F2: operator-only NBT.** Override `onlyOpCanSetNbt()` to return `true` on the array
   controller **and** on the existing single `SolarGeneratorBlockEntity`. Add assertions like
   `ForceFieldGameTests` line 308.
3. **F3: a live claim stays live.** Queue the holder before invalidating it, so that the
   established claim survives the holder's own dirty event. Add a test that does not call
   `revalidate` directly.
4. **F4: the missing tests.**
   - `BreakEvent` and `ExplosionEvent.Detonate` refusal for a repair-required array;
   - the chunk load and unload wiring (`SolarArrayRuntime.loaded` and `unloaded`), through
     the real events.
5. **F5:** no code. Root adds the sentence to ADR-065's W17 note at merge. Put the
   proposed sentence in your record.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- Other solar behaviour.
- The beacon and the satellite bay.
- Status files and the ledger.
