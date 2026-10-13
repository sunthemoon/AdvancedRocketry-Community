# TASK W33F: fix the W33 review findings F1, F2 and F3

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w33-torches` (W33 plus the shared branch, `084cdb74`) |
| Branch | `codex/v1.8.0-w33-torches` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W33-review/REVIEW.md`, SHA-256 `79e13c96f3f4fcfd5ff3eaac07c65453dc46711b4e00a996d8fc5451fd35e60b`. Verdict BLOCK: F1 High; F2 and F3 Medium |
| Failed CI | Hosted run 38423864085 on `084cdb74`: the two W25 frontier tests and the escrow `SUPERSEDING_SAVE` test (that one is W36's, not yours) |
| Record | `docs/work/v1.8.0-root-queue/records/W33F.md` |

Read the whole review. Fix each finding in its own commit, together with its tests.

## Root's decisions

1. **F1 (High): fluids must not delete a quarantined torch.**
   - Protect an unlit torch whose root is quarantined (an unresolved pair, or an undecodable
     retained root) against the native fluid replacement route:
     - `FlowingFluid.canHoldFluid` and `spreadTo`;
     - water's `beforeDestroyingBlock`;
     - lava.
   - For example, the quarantined block must not admit fluid replacement. Choose the narrowest
     native-compatible mechanism, and cite the vanilla methods from the mapped jar.
   - Add GameTests for standing and wall torches, with a retained root and with a removed pair,
     under water and under lava. Check that the block, the root and the identity survive, and
     that restoring the pair recovers the torch.
   - Update the R-034 text proposal in your record. Root edits the register.
2. **F2 (Medium): extinguishing and relighting are loaded-only.**
   - Extinguishing, the drop mode and relighting must never synchronously acquire an
     adjacent chunk through shape notifications or `canSurvive`.
   - Either check that every required neighbour cell is loaded before the mutation, and defer
     it otherwise, or use loaded-only handling.
   - Add boundary GameTests at a loaded/unloaded chunk edge for conversion, the drop mode and
     relighting, asserting that the loaded-chunk and ticket counts are unchanged.
3. **F3 (Medium): isolate W25's frontier fixture.**
   - `ScrubberRecoveryGameTests` around lines 335 to 343 borrows an unowned loaded/unloaded
     frontier near its origin.
   - Make it own an isolated frontier, for example a dedicated far chunk pair it loads and
     releases itself, so the native runner's accumulated structure tickets cannot remove the
     precondition.
   - Keep W25's unloaded-owner and facade assertions unchanged.
   - This is W25's fixture, and it is in your scope here because W33 cannot merge while it fails.
4. Release W33's own Moon force on constructor failure too (review F3, last paragraph).

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker. The two W25 frontier tests and all
  W33 tests must pass. Report any other failure; the escrow `SUPERSEDING_SAVE` one belongs to W36.

## Not in this task

- The escrow GameTests (W36).
- Other torch behaviour.
- ADR and risk-register edits (Root).
- Status files and the ledger.
