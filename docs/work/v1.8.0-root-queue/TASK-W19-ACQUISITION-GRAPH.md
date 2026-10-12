# TASK W19: close the survival acquisition gaps the strict recipe graph reports

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w19-acquisition` |
| Branch | `codex/v1.8.0-w19-acquisition` (local only; Root merges) |
| Ledger plan | C16d (components, recipe graph and rebalance, as far as acquisition goes) |
| Record | `docs/work/v1.8.0-root-queue/records/W19.md` |

## Why

Since W15 (merge `f5038f2f`), the test-side `PackagedRecipeGraph` is strict again. It
reports 18 unavailable nodes; `docs/work/v1.8.0-root-queue/records/W15.md` lists them.
They fall into two groups:

- **Graph gaps.** These items are obtainable in game through code, not packaged recipes,
  and the graph does not model that yet:
  - the five fluids `enriched_lava`, `hydrogen`, `nitrogen`, `oxygen` and `rocket_fuel`
    (electrolyzer, gas-giant harvesting, pumps, the Venus volcano);
  - `access:mars`;
  - possibly `lightwood_log` and `lightwood_planks`.
- **Content gaps.** These items have no producer at all. The dusts `silicon_dust`,
  `steel_dust`, `titanium_dust`, `titanium_aluminide_dust` and `titanium_iridium_dust`, and
  their `#forge:dusts/*` tags, are consumed by recipes but nothing produces them.

## Scope

1. **Graph gaps.**
   - For each code-backed acquisition, add an explicit, documented producer model to the
     graph that mirrors the real code path:
     - the electrolyzer recipes;
     - gas harvesting from `gas_harvest/*.json`;
     - the pump on fluid sources;
     - enriched lava from the volcano;
     - rocket fuel production;
     - Mars access by rocket travel, as the Moon is modelled;
     - lightwood from the Mars or exoplanet worldgen, if it exists.
   - Each model cites the code path it mirrors.
   - Add no model for something the game cannot actually do.
2. **Content gaps (dusts).**
   - Read ADR-064 §10 (components, recipe graph and rebalance) and the ledger rows for these
     dusts.
   - Then do exactly one of the following, and record which:
     - If the ADR or ledger gives the dusts a defined source, implement that source.
     - If the dusts are legacy inputs that the accepted design never meant to produce, keep
       the dusts as optional cross-mod inputs only. Tag them so that another mod's dusts can
       be used, and make sure no required item depends on them. The graph then treats them
       as optional, and the decision goes in your record with the ADR reference.
     - If neither applies, stop. Record the question for Root instead of inventing content.
3. After this task, the graph reports **no** unavailable node except the two pending
   world-first milestones, or the record explains each one that remains.

## Tests and commands

- The recipe-graph and reachability tests, run alone and in the whole unit suite.
- `runData` if you change data. `runGameTestServer` (under the lock), then the log checker.
- Never weaken an assertion. The graph's strictness stays.

## Not in this task

- Balance changes beyond what ADR-064 §10 states.
- New machines.
- Status files and the ledger.
