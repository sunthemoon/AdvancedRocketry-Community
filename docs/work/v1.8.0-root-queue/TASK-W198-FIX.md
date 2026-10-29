# TASK W198-fix: close the three oracle gaps the W198 review found

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session; W198's own session
was long, so this file hands its work over.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w198` (W198's; continue on its branch) |
| Branch | `codex/v1.8.0-w198`, now at `1e86fa5a2` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W198.md`: append a section for this fix |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W198-review/REVIEW.md` (SHA-256 `6d813733…8fca6e`), findings W198-R1 to R3, and its `test_review_oracle.py` and `negative-oracle-tests.log` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W198/`; add a `fix-01/` folder |

**Root decision R173.** The review (BLOCK) found three positive invariants missing from W198's terrain oracle in
`scripts/w198_terrain.py`. It found no production fault and no failed native case. Fix the oracle, test-side only:

1. **W198-R1 (High).** Inspect every block entity and inventory-bearing entity inside the 16 census chunks already
   read, including nested inventories, and reject resources or sealed-member identities that the fixture does not
   account for. Do not widen the census.
2. **W198-R2 (High).** In this integrated matrix, a pre-RESTORED U must keep the exact current durable D1, with its
   fuel and debit history. Reject the snapshot-only `RETAINED_SNAPSHOT` fallback here, and report fuel disposal only
   after a valid block election. Leave the shared election helper's other uses unchanged.
3. **W198-R3 (Medium).** Require every expected member whenever the on-disk election is block-authoritative, including
   a retained RESTORED or COMMITTED U; keep legitimately partial pre-RESTORED cuts distinct.

Each finding gets its own commit with a negative Python test that fails first; the reviewer's three reproductions
are the model. Then **replay all 357 retained native archives** with the strengthened oracle (no new native runs) and
report each row's result. If a replay now fails, keep the archive, describe the row and its state, and stop for Root:
that would be a production question, not a fixture fix.

## Verification

The W198 Python tests, the reviewer's three reproductions now rejected, the full replay, and the `rocket.server` unit
package if a Java fixture changes. No GameTest, native server or heavy-JVM lock is needed for a replay-only fix.

## Not in this task

Production changes, new native runs, the census bounds, other tests, ADRs, status files and the ledger. A re-review
follows.
