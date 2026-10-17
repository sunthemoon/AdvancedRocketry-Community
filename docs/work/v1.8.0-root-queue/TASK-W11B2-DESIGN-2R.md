# TASK W11b2 design 2, revision: close the design-2 review findings (design only)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `9b889a14` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-D2-review/REVIEW.md`, SHA-256 `658fdf2a468c3eec165ee6a3a8efd0fc75b919e6ebb55ebc7971c088d56b9b81`. Verdict APPROVE-WITH-CHANGES: H1 and H2 High; M1 to M3 Medium; L1 to L9 Low |
| Output | Revise `docs/work/v1.8.0-root-queue/records/W11B2-DESIGN-2.md` in place. Add a "Revision 1" section at the top mapping each finding to the changed lines. No production or test code |

Read the whole review and the note. The reviewer said that Root re-reading the revised section 1
table and the F1 row of section 5.4 is enough; no full second review is needed. So keep the changes
precise.

## Root's decisions

1. **H1.** Every bound rocket gets a launch proof, not only rockets with a receipt. Use a bounded
   ARMED table, separate from the READY heads, as the reviewer proposes. Give its bound, its
   lifecycle and how it is pruned.
2. **H2.** Make the source durable at the frozen state E. Either:
   - checkpoint and verify the source at F1, before the flight starts; or
   - allow a safe cancel for a PREPARED transfer with no spawned destination.

   Choose one, give the reason, and state the forced-stop cuts that prove it, including board and
   refuel followed by a stop.
3. **M1.** Keep the transfer journal root at schema 2. Only governed records carry schema 3. A world
   with no ledger must stay readable by an older jar.
4. **M2, Root decision.** The ledger, intent, operation and receipt schemas are unreleased, and only
   test hooks can create such data. Change them **in place**, with no migration chain. A
   development world holding pre-change escrow data is refused, fail-closed, and keeps the raw file.
   Say this in the R-035 text.
5. **M3.** A missing credited source is REPAIR. Reconstruction is deferred to W27f. Remove the
   reconstruction design, or mark it as W27f's.
6. **L1 to L9.** Fold each into the note, or name it as an item for the implementation task.

## Not in this task

- Any production or test code.
- ADRs, the risk register, status files and the ledger (Root).
