# TASK W14F: fix the W14 review findings F1 and F2

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w14-life-support` (at the W14 delivery `60483096`) |
| Branch | `codex/v1.8.0-w14-life-support` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W14-review/REVIEW.md`, SHA-256 `121651590cfb1678801b4da6e805ec52f001a36762400df322887f6219f0e0b5`. Verdict BLOCK: F1, F2 (both Medium). |
| Record | `docs/work/v1.8.0-root-queue/records/W14F.md` |

Read the whole review first.

## Root's decisions

Fix each finding in its own commit, together with its tests.

1. **F1 (stale cartridge-bank identity).**
   - Removal protection for a bound scrubber facade includes exact bank admission (the
     same identity check `slot()` uses). A facade whose identity the vent's bank no longer
     admits is protected, not removable. Never load a chunk and never invent an
     identity.
   - Add a bounded online recovery: when the vent's bank holds a cartridge for an identity
     that no loaded facade can claim, it becomes retrievable by a defined player action at
     the vent (or by breaking the vent, as today), with its own server message. Explain the
     rule in your record.
   - Test a mixed cross-chunk snapshot (the review's scenario):
     - ordinary removal is refused;
     - the cartridge is kept and can still be used;
     - recovery works;
     - nothing is duplicated.
2. **F2 (a forwarding port mutates after revocation).**
   - After the foreign inspection call (`getSlots()` and similar), and immediately before
     every mutating call (`insertItem`, `extractItem`), revalidate the same seal lease, the
     same loaded target block entity and the operation state.
   - If anything changed, abort with nothing moved.
   - Test callbacks that revoke the seal or remove the target during inspection, for both
     insertion and extraction.
   - Keep the existing no-chain, simulation and expired-view assertions.
3. **The local rocket test failure** (`throwingcapturepreservessource`) from the W14 run.
   Check whether it reproduces on the current branch. If it does not, record it as an
   unrelated intermittent failure, with the run it happened in. Do not change rocket code.

## Commands

- The whole unit suite once.
- `runGameTestServer` (under the lock), then the log checker. Report the totals.

## Not in this task

- The gas charger (it waits for the D3 escrow design, W11).
- The chemical cartridge refresh.
- Status files and the ledger.
