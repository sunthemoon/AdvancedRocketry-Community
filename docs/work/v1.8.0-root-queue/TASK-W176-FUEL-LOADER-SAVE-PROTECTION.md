# TASK W176: the fluid fuel loader's save protection, then its S1 rows

Root: Claude. Date: 2026-10-28. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w176` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w176` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W176.md` (new; the one-page design at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W176/` |
| Scope source | section 3, B2, of `D:/GitHub/ARCE-Task-Evidence/root-queue/W172/INVENTORY.md` (SHA-256 `134fb14c…64ca`) |

The bay (W27b) and both cargo devices (W109, W120) use the native-save rule, and the fluid fuel loader
(`rocket/fuel/loader`) does not:
- a lossless root, or a root over the work budget, is saved unchanged;
- only an observed native incompatibility records a `GuardedChunkSaves` denial for that chunk;
- the 256-coordinate saturation boundary is pinned.

W82 and W95 left this, and the B3-D S1 rows of `records/W11B3-DESIGN.md` (around line 438), to Root. W95 section 7
calls it "a save refusal, which needs a risk-register entry and an ADR note (AGENTS.md section 3.3)".

## What to do

1. **The one-page design, committed before code.** Follow `RocketFluidCargoProtection` and `RocketFluidCargoBlockEntity`
   (W109). Write the risk-register text and the ADR-065 implementation note for Root:
   - scope (single chunk, or the whole Level after saturation);
   - duration;
   - log volume;
   - recovery.

   Root registers them before merge. **Stop and report** if the loader needs anything beyond what W109's rule does,
   such as a new bound or a different saturation.
2. **Implement it.** `FluidFuelLoaderBlockEntity` has 688 lines, so extract before adding; new classes stay under 500
   lines.
   - A GameTest pins the 256-coordinate saturation boundary.
   - Each fixture gets exact WARN expectations, in `scripts/gametest_expected_log.json`.
   - Update `tests/test_check_gametest_log.py` if the ERROR totals change.
3. **Then the B3-D S1 rows natively,** on a packaged dedicated server, with two restarts each:
   - sidedness;
   - an old world upgraded without escrow;
   - the old item loader unchanged;
   - a valid device and root restarted twice;
   - future and malformed roots preserved;
   - the downgrade behaviour;
   - cut-G2's S1 part.

   Model the harness on W136's `scripts/run_v180_cargo_s1.py`, with its Python tests.

## Verification

- Each new class alone, then the whole unit suite.
- One complete GameTest with the unchanged checker (R131 applies).
- The native rows under the heavy-JVM lock, with at most 60 counted starts.

## Not in this task

Fuel admission (the admission chain behind W164), ADRs, the risk register, status files and the ledger. A fresh
read-only review follows before merge.
