# TASK W231: capacity and fairness data for a per-owner bay pending cap (D1c)

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w231` (new; branch `codex/v1.8.0-w231` from the shared head named in your prompt) |
| Output | `docs/work/v1.8.0-root-queue/records/W231.md` and any measurement tests, committed on your branch; raw results in `D:/GitHub/ARCE-Task-Evidence/root-queue/W231/` |
| Owner decision | D1c in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md): "增设每所有者限额，数值等 Root 给出数据后再定（简报推荐）": Root measures capacity and fairness and proposes a per-owner pending cap and its full-cap refusal; the owner then sets the number; ADR-065 section 10 is revised with mixed-owner fairness and saturation tests |
| Sources | [W27A-DESIGN](records/W27A-DESIGN.md) (shared pending slots: 64 per server, 8 per rocket; 128 retained receipts per owner, which is not this cap); [W127-PLAN](records/W127-PLAN.md) Q4 and Q9; risk R-039 and R-042; ADR-065 section 10; the shared pending accounting of the W11b3 loader and the bay in the code |

**Root decision R224.** The owner will set the number after seeing data.

## What to deliver

1. **The accounting as built.** Where the 64 and 8 limits are counted, which operations take a shared pending slot
   (loader operations and bay holds), when each slot is released, and what a full table refuses and tells the player.
   Cite file and line.
2. **Capacity.** Measured, not estimated:
   - the receipt store's and the journal's size;
   - the save and load time, and the tick cost of the coordinator's per-tick step, at 8, 16, 32 and 64 pending
     entries from one owner and from several owners.
   Use the existing benchmark or GameTest harnesses, and report P50, P95 and P99 against the docs/17 budgets.
3. **Fairness.** Scenarios where one owner fills the table (eight rockets with eight bays each) while other owners
   try loader and bay operations. State what each player sees, and for how long.
4. **The proposal.** Two or three candidate per-owner caps, each with:
   - the refusal reason and message;
   - whether operators are exempt;
   - how the cap interacts with the 8-per-rocket limit and with the retained receipts;
   - restart behaviour, and the migration of existing saves already over the cap;
   - the exact ADR-065 section 10 text it needs;
   - the fairness and saturation tests it needs.
   Recommend one, with reasons. The owner sets the number.

## Verification

The measurement tests you add pass three times. The repository validator passes. Production code is unchanged.

## Not in this task

Implementing the cap, the safe exit (W230), bay admission, status files and the ledger.
