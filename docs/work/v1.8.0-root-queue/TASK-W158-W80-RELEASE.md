# TASK W158: W80-release, the release coordination of the offline-passenger release (second leaf)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md), the frozen design
[records/W80-DESIGN.md](records/W80-DESIGN.md) (revision 3; sections 1, 3, 5, 6, 7 and Appendices A and B),
[records/W157.md](records/W157.md) (W80-storage, merged `c7237169`, with its step 0), and the W80 note in ADR-065 and the
W80 part of R-035 first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w158` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w158` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W158.md` (new; a one-page plan at its top before any code) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W158/` |

## What to do

The design's W80-release leaf only. Bind the frozen H1 and H2 planning, the release fence, B0, B1, J1 and S2, B1's
tickets and the retry scheduling against the merged code. All of it goes on W157's sidecar.

1. **Step 0:** the design assumes W11b2's barrier binding: the native entity-store proof, the manifest on disk, and the
   journal's legacy compare-and-replace. Check those assumptions against the merged code (W11b2, W123, W153H's F1
   lease, W155's transfer history). Record what changed. If an assumption no longer holds in a way that changes the
   design's release contract, stop and report.
2. **Scope:** legacy (not governed) records only. Governed records stay excluded and are refused.
3. **No player-facing entry point:** no command, no placement, no login protection; those are W80-rejoin. Drive the
   release through an internal, gated entry point that the tests use, and keep production admission disabled.

No saved format beyond W157's sidecar, and no change to the journal root, bounds, budgets, timeouts or protocols. Each
step in its own commit, with a test that fails before the change. New log lines get exact manifest entries.

## Verification

The design's A0 tests for this leaf. The release fence, H1 and H2 absent sets, B0 and B1 with the ticket caps and the
200-tick retry, the "gone" outcome, J1's compare-and-replace and S2, and the interleavings with W44's election and
delivery and with governed journal writes. Native cuts for this leaf from Appendix A (C0 to C11 where they apply),
each with two restarts. Resource conservation at every cut. Each changed class alone, the whole unit suite, and one
complete GameTest with the unchanged log checker, under the heavy-JVM lock with the atomic pattern and the 90-second
hand-off rule. Check C: and D: above 10 GiB and 6 GiB free memory first. Keep the evidence under 100,000,000 bytes.
This is a persistence slice: a fresh Codex session reviews it before merge.

## Not in this task

W80-rejoin (the command, placement, protection and readback), governed records, enabling anything for players, ADRs,
the risk register, status files and the ledger (Root).
