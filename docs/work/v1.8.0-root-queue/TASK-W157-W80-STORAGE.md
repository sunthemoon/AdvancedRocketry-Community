# TASK W157: W80-storage, the checked rejoin sidecar (first leaf of the offline-passenger release)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md), the frozen design
[records/W80-DESIGN.md](records/W80-DESIGN.md) (revision 3; sections 2, 6 and Appendices A and B), its review history in
the W80, W80R, W80-review-2 and W80R2 rows of [QUEUE](QUEUE.md), and the W80 implementation note in ADR-065 and the
W80 part of R-035 (Root registered both on 2026-10-24) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w157` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w157` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W157.md` (new; a one-page plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W157/` |

## What to do

The design's W80-storage leaf only: the sidecar model, its bounded checked writer, lazy startup integration and the
codecs.

1. **Step 0:** the design was frozen on 2026-10-16, before W11b2's last merges and W123's shared checked-write core.
   Check its storage assumptions against the merged code: the checked writer, readback and acknowledgement, startup
   ordering, and journal root 3. Record what changed. If an assumption no longer holds in a way that changes the
   design's storage contract, stop and report.
2. Implement the sidecar as section 2 states. It is created lazily, so a world that never uses the command has none.
   It has schema 1, the stated bounds, and a startup refusal for a future, malformed, oversized, unreadable or
   unknown-presence sidecar, with its bytes unchanged.
3. Command admission stays disabled: no `release-absent` command, no placement, no seat revocation. Those are the
   W80-release and W80-rejoin leaves.

No saved format beyond the sidecar, and no change to journal roots, bounds, budgets, timeouts or protocols. Each step in
its own commit, with a test that fails before the change.

## Verification

The design's A0 storage tests and the migration checks for this leaf: no sidecar without use; journal roots 1 and 2
still migrate to 3; the sidecar's schema 1 round-trips; a malformed or future sidecar refuses startup with unchanged
bytes across repeated attempts. A native start against a malformed sidecar, twice. Each changed class alone, the whole
unit suite, and one complete GameTest with the unchanged log checker, under the heavy-JVM lock with the atomic pattern
and the 90-second hand-off rule. Check C: and D: above 10 GiB and 6 GiB free memory first. This is a persistence slice:
a fresh Codex session reviews it before merge.

## Not in this task

W80-release, W80-rejoin, the command, enabling anything for players, ADRs, the risk register, status files and the
ledger (Root).
