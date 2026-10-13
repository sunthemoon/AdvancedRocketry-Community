# TASK W35: finish C16d (tracking circuit, graph without Tau Ceti, rebalance)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w35-c16d` |
| Branch | `codex/v1.8.0-w35-c16d` (local only; Root merges) |
| Ledger rows | `item_variant:ic/1` (tracking circuit), and the C16d rebalance items (W30 section 2.3) |
| Record | `docs/work/v1.8.0-root-queue/records/W35.md` |

## Goal

Finish ADR-064 section 10 (C16d). W30 found three items still open:

1. **The tracking circuit (section 10.1).**
   - Register it as an item, with the legacy recipe (machine and crafting) and the legacy role
     name. ADR-062 section 6 names it as a C16d input of C17a.
   - Use the same patterns as the other circuits W15 and the pre-handover acquisition slices
     delivered: the basic and advanced circuits and the boards.
   - Original art, with its provenance recorded before authoring.
   - English and Chinese text.
   - Model it in `PackagedRecipeGraph`, so the all-item check covers it.
   - Cite the 1.12.2 baseline recipe from the provenance-audited files.
2. **The graph run with the Tau Ceti Levels removed (section 10.2, ADR-063 revision 6).**
   - Add a test variant of the all-item and advancement reachability checks that removes the
     Tau Ceti Levels from the graph.
   - Report which items then become unreachable, and assert the expected set exactly.
3. **The rebalance (section 10.3).**
   - Existing v1.x recipes that the classic chain supersedes move to the classic components
     wherever the graph shows a route. An example is the v1.2 precision recipes that stand in
     for circuits.
   - List each change in the CHANGELOG.
   - Keep the graph strict, and keep every reachability assertion.

## Tests

- The data-generation test for the circuit.
- The graph tests: the strict set, and the Tau-less variant with its exact expected set.
- A GameTest that crafts the tracking circuit through its real machine recipe.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- The loaders that consume the tracking circuit (C17a, after W11b3).
- Status files and the ledger.
