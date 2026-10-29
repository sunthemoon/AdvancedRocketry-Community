# TASK W204: N3-C, the native reservation-refusal and named-pad cuts

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w204` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w204` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W204.md` (new; the design note and the cut mapping at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W204/` |
| Plan | `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md` (SHA-256 `0667fe44…8f555e`), section 3, "W203-P1"; its write scope, tests and exclusions are part of this TASK |
| Design | [records/W96-DESIGN.md](records/W96-DESIGN.md) (N3-C and its native matrix, R33's release-test-only lowerable limit) and `records/W91K.md` |

**Root decision R174.** W91K's `N2_REFUSED` has zero starts and named-pad retention and release are NOT_COVERED;
`scripts/run_v180_f3_n2_cuts.py` still lists `N2_REFUSED` as BLOCKED. N3-A and N3-B4 do not close these rows.

## What to do

1. **Design note and cut mapping first, committed before any native run.** Name the release-test seam that lowers the
   continuation limit (gated like the existing release tests; the production default of 128 unchanged; refused on a
   normal server; restored on failure), each cut, its oracle and its start count.
2. **Implement the seam and the oracle**, test-side, each with a test that fails first.
3. **Run the cuts** under the heavy-JVM lock on a JAR built from your committed source, in tranches of at most 60
   counted starts: refusal with the source, T, pad and live membership unchanged; a stop or abort variant; named-pad
   removal-moved and UNKNOWN with no early reuse and exactly one acknowledged release. Compare complete resources,
   UUID and owner, fuel and debit history, unrelated transfers and pad state on both restarts. Keep N2_MOVED, N2_UNKNOWN
   and the applicable governed return as controls. Stop and report if a row fails twice.
4. A production cause is not fixed here: describe it with its reproduction and stop for Root.

## Verification

The runner's Python tests, the new JUnit class alone and then the whole unit suite, the targeted scheduling, return
and pad GameTests, and the tranche ledgers. Root's hosted run on the pushed branch stands in for a complete local
GameTest. An independent review of the design, the oracle and the evidence follows.

## Not in this task

Cancellation redesign, durable cancellation intent, offline-crew rules, orphan saves, enabling any admission switch,
production changes, budgets, timeouts, ADRs, status files and the ledger.
