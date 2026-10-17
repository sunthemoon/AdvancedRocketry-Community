# TASK W86: design note for part-1 findings F3 and N2 (per-record journal scheduling)

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. This is design work only: write no
production or test code. Do not start subagents.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w86-p1-f3-design` (from the shared branch) |
| Branch | `codex/v1.8.0-w86-p1-f3-design` (local only; Root merges) |
| Finding F3 | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-review/REVIEW.md` (SHA-256 `8c1da51138a655581c8a3256841615fd556c627eaed46edcf421b4fcc67de499`), F3: every journal write pauses all transfer processing server-wide |
| Finding N2 | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-P1F-review/ROOT-REVIEW.md`, N2: `failBackToSource` changes and removes the record before `afterAcknowledged`, so a full effect queue strands the pad and the live ID |
| Contract | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) revision 2: section 1's P1-F3 and P1-N2 rows and the P1-F3-D, P1-F3 and P1-N2 leaves in section 5. [records/W85-DESIGN.md](records/W85-DESIGN.md) revision 3 (P1-F8, frozen): its queued write-back and "the inherited whole-journal effect while F3 is open" |
| W11b2 code | Read W11b2 (design 2 in `records/W11B2-DESIGN-2.md` and its code) only through Git at commit `a8d81d24` (`git show`, `git grep a8d81d24`). Do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration`, where continuation 26 is working |
| Deliverable | `docs/work/v1.8.0-root-queue/records/W86-DESIGN.md` (two to four pages) and a short record `records/W86.md` |

## Why

Per-record scheduling changes when saved transfer data counts as acknowledged, so AGENTS.md section 13 requires a
design note and a read-only review before code. The implementation follows the W11b2 merge; freezing the design now
keeps it off the critical path.

## What the note must settle, citing code at `a8d81d24`

1. **The current gating:** every place that waits for a whole-root acknowledgement or a clean journal
   (`RocketTransferService` `tick`, `tickLive`, `tickSource`, `spawnDestination`, `failBackToSource`;
   `RocketTransferSavedData` `pollWriter`, `afterAcknowledged`, `canQueueEffect`; and W11b2's governed record-3
   executors, for example `EscrowGovernedSourceFlight` and `EscrowGovernedDestination`). Say what each one waits for
   and why.
2. **The per-record rule:** exact per-record versions and absence leases with one writer, bounded captures and final
   guards. The journal is one file, so define precisely when a record's version counts as acknowledged (the snapshot
   that contained it was acknowledged), and what UNKNOWN, a definite failure, a blocked journal and the first write of a
   session mean for each record. Compare at least two options and recommend one.
3. **What must not change:** the acknowledgement order of every record kind (journal root 3, W44's outbox, W11b2's
   governed record 3, P1-F8's write-back), the proofs, the holds and the replay exclusion. If the recommended option
   cannot keep one of them, say so and stop there with options for Root.
4. **N2:** reserving the continuation atomically before `failBackToSource` changes or removes the record, so that a
   full queue leaves the source, the record and the pad intact; and how this fits the per-record rule.
5. **P1-F8 after F3:** what W85's queued write-back holds once F3 is in, and which W85 residual text changes.
6. **Saved format:** confirm that no record, key, schema or root changes, or name each change with its migration.
7. **Tests:** the two-transfer delayed-write test, an UNKNOWN or restart case, N2's test (two occupied slots, delayed
   acknowledgement, one release, UNKNOWN or restart), and eight-transfer native timing showing that independently
   acknowledged work advances; with JVM-start estimates.
8. **Risk and ADR text** for Root (the R-035 rows that change), and **open questions**, each with a recommended answer.

## Commands

- No Gradle, GameTest or JVM run. `git show`, `git grep` and reading files are enough.
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files Git
  tracks (stage your record first); write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code; P1-F8's implementation; W11b3's other leaves.
- ADRs, the risk register, status files and the ledger (Root).
