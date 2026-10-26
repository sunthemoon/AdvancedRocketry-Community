# TASK W146: cargo changes do not republish the rocket's visual state to connected clients

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md), W144's record (the reference run and R85's
analysis) and [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) (the visual republication after each operation, and
the C-V checks) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w146` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w146` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W146.md` (new; a short plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W146/` |

## The finding

In W144's native reference run, connected test players received the rocket's initial visual packets, but no
updated-hash visual packet arrived after cargo operations changed the rocket. The design asks for visual republication
after each operation, and C-V's V1 check includes the cargo visual refresh.

## What to do

1. Find where a cargo change (load, unload, settlement, reconciliation roll-forward) should mark the rocket's visual
   snapshot dirty and republish it, and why it does not (for example the snapshot hash is not recomputed, the dirty flag
   is not set, or the tracker is not notified).
2. Fix it so that every cargo change that alters the rocket's snapshot republishes once to the players tracking the
   rocket, within the existing packet size limits, and an unchanged snapshot sends nothing.
3. Tests: a unit test of the trigger, and a GameTest with a connected test player that asserts exactly one updated-hash
   packet after a load and after an unload, and none after a refused operation. Then rerun W144's reference measurement
   of republication cost with the existing harness and report it.

The server stays authoritative; no client value decides anything. A network packet field change, size limit change or
saved format change needs Root first: stop and report. An independent read-only review by a fresh Codex session follows
before merge.

## Verification

Each changed class alone, the whole unit suite, one complete GameTest with the unchanged log checker, and the reference
measurement, under the heavy-JVM lock with the atomic pattern and the hand-off rule. Check C: and D: above 10 GiB and 6
GiB free memory first.

## Not in this task

W145's launch queue, enabling admission, V1 and V2 client evidence, status files and the ledger (Root).
