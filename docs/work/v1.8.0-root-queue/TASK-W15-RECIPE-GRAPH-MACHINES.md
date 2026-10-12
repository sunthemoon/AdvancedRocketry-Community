# TASK W15: model the classic machine recipes in the recipe graph and restore strict mode

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w15-recipe-graph` |
| Branch | `codex/v1.8.0-w15-recipe-graph` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W15.md` |

## Why

The test-side `PackagedRecipeGraph` (under `src/test/**/recipegraph/`) checks that
packaged content can be reached in survival play. It used to fail on any recipe type it
did not model, so a new data shape could not be skipped silently. W12 (merge `667fa40c`)
changed it to **skip and report** unmodelled types, because the seven classic machine
recipe types merged after the graph was written. That was accepted only as an interim
measure. See the W12 merge commit message and `docs/work/v1.8.0-root-queue/records/W12.md`.

## Scope

1. **Producer models.** Add a model to `PackagedRecipeGraph` for every packaged classic
   machine recipe type:
   - `advancedrocketrycommunity:lathe`, `arc_furnace`, `cutting`, `crystallizing`,
     `chemical_reacting`, `laser_etching`, `centrifuging`;
   - any other packaged type that is still reported as unmodelled.

   Each recipe produces its item outputs from its item inputs, plus its fluid inputs where the
   graph models fluids. Follow the existing forms.
   - Each machine must itself be reachable: its controller, hatches and pattern parts.
   - The centrifuge's chance outputs count as producible.
   - The etcher's retained lens is a requirement, not a consumable.
2. **Restore strict mode.** Once every packaged type is modelled, an unknown recipe type
   fails again. Keep the report line. It must say "none".
3. **W12 follow-ups:**
   - Now that W10 is merged, wire the crystallizer advancement and the dilithium advancement
     to their real items and triggers, instead of `minecraft:impossible`.
   - The reachability test must show both are reachable.
   - The world-first One Small Step and Flight of the Phoenix stay `impossible`; they wait for
     W11.
4. Whatever the graph now finds unreachable is a real content gap: list it in your record
   for Root. Do not hide it.

## Tests and commands

- The recipe-graph and reachability tests, run alone and in the whole unit suite.
- `runData` if you change data. At the end, `runGameTestServer` (under the lock), then the
  log checker.
- Never weaken an assertion. The point of this task is to make the graph stricter.

## Not in this task

- New content.
- Balance changes.
- Status files and the ledger.
