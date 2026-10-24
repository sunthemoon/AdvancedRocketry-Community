# TASK W152: N3-B2, the retained disassembly record U and native completion (P1-N3 leaf N3-B, Root decision R87)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md), [records/W148-DESIGN.md](records/W148-DESIGN.md)
(sections 3.1, 3.2, 5, 5.1, 6 and the N3-B2 row of section 7), the W148 review's "Implementation prerequisites"
(`D:/GitHub/ARCE-Task-Evidence/root-queue/W148-review/REVIEW.md`), Root decision R87 (QUEUE), the N3-B notes in ADR-065
and R-035, and [records/W150.md](records/W150.md) (N3-B1, merged) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w152` (new; from the shared branch at the commit named in your prompt, which has N3-B1) |
| Branch | `codex/v1.8.0-w152` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W152.md` (new; a one-page plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152/` |

## What to do

N3-B2 as W148 section 7 states it: the ordinary disassembly executor, a bounded footprint and resource exclusion, the
narrow ordinary terrain capture facade, W123's presence predicate and restart recovery, implementing D0 to D5 and DR,
with U kept until a checked native completion or rollback.

**Prerequisite first (the review's condition 1):** demonstrate isolation before anything is enabled. For every object
in the existing supported set (including `VanillaContainerRocketAdapter`'s containers and what
`ServerLevelRocketTransactionWorld` places), prove the access, tick and re-entrancy guards, including startup ordering.
If an object cannot be guarded, stop and report it for Root: do not narrow the whitelist, bypass a guard or treat missing
coverage as a residual.

Constraints from W148 section 5 and R87: no saved format, schema, bound, lease, budget or timeout change; terrain and
entity work charged together against the existing ordinary sub-quota and caps, with the shared 256-inspection cursor;
one checkpoint requested per owner tick; do not change assembly ordering or governed code, and do not enable a
pre-M1-only player path (N3-B3 integrates I6). New log lines (`ARCE_DISASSEMBLY_DURABILITY_HELD` and its INFO) get exact
manifest entries when a GameTest drives them. Each step in its own commit with a test that fails before the change.

This is a persistence slice: an independent read-only review by a fresh Codex session follows before merge.

## Verification

A0 and A1 tests and the native cuts B-U, B-TERRAIN and B-ROLLBACK from W148 section 6, including recovery kills, two
restarts each; each changed class alone, the whole unit suite, one complete GameTest with the unchanged log checker,
under the heavy-JVM lock with the atomic pattern and the hand-off rule. Measure the worst-case tick cost of the
checkpoint. Check C: and D: above 10 GiB and 6 GiB free memory first. Keep the evidence under 100,000,000 bytes.

## Not in this task

N3-B3 (I6 integration), N3-B4, W98-R1 (W151), enabling admission, ADRs, the risk register, status files and the ledger
(Root).
