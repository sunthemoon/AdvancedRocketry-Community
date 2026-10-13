# TASK W32: the chemical reactor's special recipes (space breathing, cartridge refresh)

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w32-chemical-special` |
| Branch | `codex/v1.8.0-w32-chemical-special` (local only; Root merges) |
| Ledger plan | C16c and C18a/C18b: `enchantment:spacebreathing` acquisition, carbon cartridge refresh |
| Record | `docs/work/v1.8.0-root-queue/records/W32.md` |

## Why

- **ADR-064 section 9 item 2.** The chemical reactor's carbon cartridge refresh "waits for
  C18a's cartridge", and the space protection recipe "for C18b's enchantment". "Those batches
  add them to this recipe type."
- **What exists now.** Both batches are merged: W14 added the cartridge, W03 the
  `space_breathing` enchantment, and W10 the chemical reactor on the kernel. Neither special
  recipe exists yet, so survival players cannot get the enchantment, and spent cartridges
  cannot be refreshed.
- **References.** W03's record (lines 159 to 161) and W10's record (lines 29 to 31) say so.

## Contract

1. **Space breathing.** ADR-066 section 5.1:
   - `space_breathing` level 1 is applied **only** by this chemical recipe: not by enchanting
     tables or books, and not by generic loot;
   - it applies to an eligible armour piece;
   - read section 5.1 for which stacks are eligible, and refuse the rest with a reason.

   Check how W03 restricted the enchantment, and keep those restrictions.
2. **Cartridge refresh.** ADR-066 section 3.1:
   - a special **lossless** chemical recipe;
   - not a plain item output that erases arbitrary NBT or creates a second cartridge;
   - it refreshes the cartridge's wear and keeps its identity and other data exactly.

   Read how W14 and W14F store cartridge wear and identity.
3. **Both are added to the existing chemical reactor recipe type** on the W01 kernel, through
   the kernel's special-recipe extension point. If no such point exists, add the narrowest one
   that W10's recipe processing allows, and document it. Inputs, outputs and FE follow the
   legacy numbers. Cite the 1.12.2 baseline recipe for each input and FE value, from the
   provenance-audited files.
4. **The recipe graph.** `PackagedRecipeGraph` and the all-item check (W15, W19) must model
   both, so `space_breathing` becomes reachable in survival.

## Tests

- **Unit tests:** the lossless NBT round trip for the refresh, including unknown extra NBT,
  and the eligibility rules.
- **GameTests:** a real reactor applies the enchantment to an eligible chestplate, and refuses
  an ineligible stack with nothing consumed.
- **GameTests:** a real reactor refreshes a spent cartridge, keeping its identity, without
  duplicating it.
- **The graph test:** shows the enchantment reachable.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Not in this task

- Other reactor recipes.
- The gas charge pad.
- Status files and the ledger.
