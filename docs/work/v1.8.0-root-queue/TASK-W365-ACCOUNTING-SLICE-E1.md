# TASK W365: per-fluid signed accounting, Slice E part 1 (Python decoders and oracles for the new format)

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w365`, branch `codex/v1.8.0-w365`, at `d957d7dce` (Slices A and B with the shared head) |
| Decision | Root decision R448 (QUEUE row W365) |
| Design | [W357-DESIGN](records/W357-DESIGN.md), section 7.3 and section 8 row E. Slice B's saved forms are in `records/W362.md`. |
| Record | `docs/work/v1.8.0-root-queue/records/W365.md` |
| Write scope | Python under `scripts/` that decodes or checks the escrow ledger or receipts (`run_v180_escrow_forced_stop.py`'s ledger parser, `inspect_escrow_receipt.py`, and the loader, cargo and item oracles named in design section 7.3), the three gated Java probes that compare `accounting_status` with `UNKNOWN` (W362R finding 9), their tests under `tests/` and `src/test`, and the record. No production code, no native run, and no change to any family's case selection. |

## What to do

1. **Explicit readers.**
   - Give the shared ledger parser and the receipt inspector explicit readers for root 2 and root 3 and for receipt
     schemas 3 and 4.
   - Validate account references, decode account rows with every bound of R443 (2,048 bytes, depth 5, 65 nodes, 14
     members), and recompute the signed balance of a single-member KNOWN row.
   - Never simply widen an accepted version, and never swap `UNKNOWN` for `KNOWN` in a string check.
   - Legacy fixtures may stay root 2 on purpose. A fresh root-3 fluid case must not silently take the legacy branch.
2. **The oracles of design section 7.3.** Add version-aware account checks to:
   - the fluid S1, storage and recovery oracles;
   - the S4 and S5 settlement runner;
   - the loader integrity, abandon, normalize and chain oracles;
   - the item and cargo contention oracles where they inspect the root or a receipt.
   New fluid receipts may be KNOWN with a zero residual; missing-endpoint cases stay UNKNOWN; `payload_amount`
   keeps its meaning. List every caller of the shared parser and say how each is affected.
3. **The three gated probes.** Make their `accounting_status` comparisons accept KNOWN where the new format allows
   it, and test both values.
4. **Tests.** Unit tests on synthetic root-2 and root-3 fixtures, built with Slice B's real codecs where a Java fixture
   is needed:
   - each reader;
   - each refusal: a bad version, a bad reference, an over-bound row and a wrong balance;
   - a negative control per oracle change.
   Every existing runner test keeps passing unchanged.

## Rules

- Do not delete tests, loosen assertions or change an existing oracle's expectation for root 2 (AGENTS.md section 5).
- Run the Python tests you touched and the full Python test suite of the runners you changed, and the targeted Java
  tests. Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7` for Gradle.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives what was done, the caller inventory, the commits, the commands and results, and what native runs
  remain for after activation. Its last line, and the last line of your answer, is the path of `records/W365.md`.
