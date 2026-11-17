# TASK W368: design note for X01 and X02 by application-level crash and fault simulation

Root: Claude. Date: 2026-11-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w368`, branch `codex/v1.8.0-w368`, at the shared head |
| Decision | Root decision R460, under R457 (QUEUE row W368) |
| Plan | [X01-X03-ISOLATION-PLAN](records/X01-X03-ISOLATION-PLAN.md): its decision row (option E4) and sections 3 and 4 for background |
| Deliverables | `docs/work/v1.8.0-root-queue/records/W368-DESIGN.md` and `records/W368.md` |
| Write scope | Those two files only. No code, test, script or Root-owned file. |

## Why

Rows X01 (OS crash and power loss) and X02 (storage failures that cannot be observed) are NOT_COVERED. Root chose to
test them on this PC, without a virtual machine or any destructive step, by simulation at the mod's own checked-writer
seam (R457, [OWNER-DECISIONS-20261117](records/OWNER-DECISIONS-20261117.md)). This note designs that harness.

## Read first

- `persistence/migration/CheckedSavedDataFile` and its `Committer` interface. Read the existing gated committers and
  cuts that use it: `BayReceiptCommitCut`, the escrow ledger's commit cuts and the transfer journal's.
- The writers that go through it:
  - the escrow ledger (`EscrowLedgerWriter`);
  - bay receipts;
  - the transfer journal;
  - the satellite registry, if it uses the same path.

  Note which managed stores do not use it. Vanilla region and player files do not.
- The existing E1 and E3 cases (observable storage failures) in the cargo storage families, and W11b2's record of the
  Windows directory-force residual.

## What the note must answer

1. **The durability model.**
   - State exactly what a crash at each point keeps: after a write, after a flush, after an atomic move, after a close.
   - Give one rule set that is conservative for a power loss: unflushed data may be lost or torn, and a move is durable
     only after the directory is flushed where the platform allows it.
   - Note what Windows does differently.
2. **The recorder (X01).** How a gated release-test committer records each operation of each write, in order, with the
   bytes needed to rebuild every crash prefix. Then how the replay builds a fresh world copy for each prefix and
   each allowed loss variant. Bound the number of prefixes per case.
3. **The cases.** Which existing native cuts to record, about ten, covering:
   - the bay's D1, D4 and D7 receipt writes;
   - fluid and item cargo writes;
   - the ledger's first creation;
   - the transfer journal;
   - the registry.

   Each replayed copy starts twice with the existing oracles. Allowed outcomes are: each store wholly old or wholly new;
   no duplicate and no loss; protected roots byte-identical; a hold or REPAIR the design allows.
4. **The fault injector (X02).** For each fault, say how it is injected and what it checks:
   - an I/O error on write;
   - a write that reports success but is lost;
   - a failed flush;
   - a failed move;
   - a failed close;
   - a full disk;
   - a corrupted read.

   Classify each outcome as seen and held, caught later, or not caught. A finding of the third kind stays a stated
   residual, never a relaxed oracle.
5. **What stays uncovered.** Real OS caches, file-system journals, disk firmware, a real power cut, and stores outside
   the seam. Say where each would be tested if ever needed.
6. **Gating.** Every hook does nothing without the release-test property. No production behaviour changes.
7. **Machine time, slices and estimate**, with the files each slice touches, the evidence layout and the size limits
   of AGENTS.md section 9.

Mark each open question for Root as a numbered question with your recommendation.

## Rules

- Design only. Commit the two files on your branch with messages in the style of AGENTS.md section 6. Do not push.
- English, full sentences, UTF-8 without BOM, LF line endings. Links only to tracked files.
- The last line of your answer is the path of `records/W368.md`.
