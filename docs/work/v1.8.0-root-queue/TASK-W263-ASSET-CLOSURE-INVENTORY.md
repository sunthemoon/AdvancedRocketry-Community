# TASK W263: refresh the v1.8 asset inventory after the import batches (read-only)

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This task is
read-only: change nothing in any worktree, commit nothing and start no other agent.

| Item | Value |
|---|---|
| Checkout | `D:/GitHub/arce-v180-w263`, a detached checkout of the shared head named in your prompt; also read the unmerged branches `codex/v1.8.0-w260` (P4 fuel tanks) and `codex/v1.8.0-w261` (P0 landing pad) |
| Earlier inventory | `D:/GitHub/ARCE-Task-Evidence/root-queue/W245/INVENTORY.md`, SHA-256 `77134f1a8f5f653811c7cfd9b651cca8f5e214a3066372d4c9d19db43076af55` |
| Batches since | W246 (T1 to T3), W249 (P1), W255 (P2), W256 (S1), W258 (P3), and on branches W260 (P4) and W261 (P0); their records under `docs/work/v1.8.0-root-queue/records/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W263/INVENTORY.md`, and its SHA-256 on the last line of your answer |

**Root decision R290.** Before choosing the last asset batches, Root needs a current picture.

1. **What is left.** Every asset-plan row still IMPORT or REVIEW (with W260's and W261's drafts applied), with its
   matched upstream files, owners, consumers and W245's proposed batch, and every IMPORTED row's record. Say which
   rows still name owners that no remaining file matches (W249-R8, W258-R2).
2. **Retained NEW art.** Every NEW file that the batches left unreferenced (the motors' faces, the tanks' faces, the
   saw's block texture, the landing pad's, the flat textures W246 replaced), with whether anything still uses it, and a
   proposed disposition for each (keep as live art, keep as history pending human review, or remove).
3. **The remaining batches.** For X1 to X3 and J0 (exclusions), U1 (suit slot icons) and H1 (the laser gun's held
   model), the exact rows and files, the evidence an exclusion needs (no consumer, replacement), and a recommendation:
   which to do in v1.8 and which to leave to a later version, with reasons.
4. **Closure.** What ADR-061 and ADR-062 still require before the v1.8 asset plan can be called closed (per-asset
   closure, allowlist, human provenance review), separating what agents can do from what only the owner can.

Run the existing read-only audits (first-match, the asset coverage check, the content-ledger validator) where they help,
in your checkout only.

## Output format

The inventory as above, the commands you ran and their results, and its SHA-256 on the last line.
