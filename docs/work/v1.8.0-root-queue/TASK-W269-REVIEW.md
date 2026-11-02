# TASK W269-review: independent read-only review of the campaign-only release-test gate (R298)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w269` at `3f639257a`: the design note `6884a6857`, the implementation `dfc377778` and the record `3f639257a` on the shared commit `2f24c7542`. Review worktree `D:/GitHub/arce-v180-w269-review`, a detached checkout of `3f639257a` that Root creates |
| Task | [TASK-W269](TASK-W269-V2-CAMPAIGN-GATE.md) (R298) |
| Records | `records/W269-DESIGN.md`, `records/W269.md` and the updated `records/W254-V2-CHECKLIST.md` section 0.1 on the subject branch; evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W269/`, including `ADR065-NOTE-DRAFT.md` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W269-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

This change widens a release-test gate in production source, so review it as a security boundary.

## What to check

1. **The gate can only open where intended.** Trace every path into `EscrowTestEndpoints.enabled` and
   `ReleaseTestPlayers.ownsAllConnectedPlayersOr`. With the property unset the behaviour is exactly as before. With it
   set, the gate still needs the owner thread, the hooks property, a dedicated server, RCON off and the exact marker;
   any third real player closes it; GameTest and integrated servers are unaffected. Look for a path where a named UUID
   gains facility ownership, a fixture command or a test-player operation it did not have before.
2. **Startup validation.** A malformed property stops a dedicated server before normal startup in every case the
   design lists, and the exception actually propagates through Forge's event bus (check the native evidence).
3. **The marker check.** Fixed size and bytes, no symlink, sampled once at startup, never created by the mod; say
   whether any race or path trick lets an unmarked world count as marked.
4. **Lifecycle and logs.** The server-keyed map is cleared on stop and holds no Levels or players; exactly the stated
   INFO lines appear, and none when unset.
5. **Tests and evidence.** The new tests would fail without the change; the four native cases show what the record
   says; nothing was weakened, including the first-use dependency list the change edits.
6. **The checklist and the ADR-065 note draft.** Section 0.1 now states the decision, property, marker and log lines
   accurately; the draft note is accurate and claims nothing about admission or V2 results.

## Rerun

In fresh clones or your review worktree only: the new gate tests, the bay, escrow and release-test unit tests, the
first-use dependency test, the whole unit suite and the repository validator. Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W269-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
