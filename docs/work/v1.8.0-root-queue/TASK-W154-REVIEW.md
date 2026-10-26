# TASK W154-review: independent read-only review of the sealed-disassembly design (W154 revision 2, R93, R94)

Root: Claude. Date: 2026-10-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W154-DESIGN.md` on branch `codex/v1.8.0-w154` at `c4d3deb6` (revision 2; revision 1 is `967e56aa`). Review worktree `D:/GitHub/arce-v180-w154-review`, a detached checkout of `c4d3deb6` |
| Tasks it answers | [TASK-W154](TASK-W154-CONTAINER-ISOLATION-DESIGN.md) and [TASK-W154B](TASK-W154B-SEALED-STAGING-REVISION.md) (R93), plus R94 below |
| Background | [records/W148-DESIGN.md](records/W148-DESIGN.md) and its review (`D:/GitHub/ARCE-Task-Evidence/root-queue/W148-review/REVIEW.md`), R87 and R91 (QUEUE), W152's record (`records/W152.md` on branch `codex/v1.8.0-w152`) and its probes |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W154/` (revision 1 and `revision2/`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W154-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## Root decision R94 (2026-10-24), on the design's section 6

1. **Built-in adapters** (every shipped entry: chest, barrel, pressurized tank, satellite bay and the plain members):
   prepare, publish and abort as section 6 proposes. Create and restore the target before anything can reach it,
   verify it, then install it and send the neighbour and comparator updates once. A failed prepare leaves C exactly as
   it was, as a held member with one diagnostic.
2. **External adapters through the API:** no API change and no exclusion. Their restore keeps today's installed-host
   contract with two hardenings: the block is installed with neighbour and shape updates suppressed and restore runs
   in the same call, before any tick; the neighbour and comparator updates follow restore. The API documentation
   states the expectation explicitly: a provider's block must not move resources from its construction, `onLoad` or
   neighbour callbacks before restore returns. A provider that breaks that rule has a provider defect that this mod
   cannot correct from outside without a Mixin.
3. **A failed external restore** (false, a throw, over the 5 ms check or a mismatched recapture): put C back with the
   original payload without dropping the partial target's contents, hold the member and log once. Under the
   documented rule nothing left the target, so C keeps every item exactly once. No retry loop and no deletion.
4. The same place-then-restore order exists in today's assembly rollback and disassembly restore. N3-B2 hardens the
   unseal path only; report a reachable built-in case on the other paths as a separate finding.

## What to check

1. **Conservation at every cut.** C0 to C5 and unsealing, with two restarts and a kill during the first recovery:
   each original item exists exactly once, a foreign item is never erased, and no state is left without an owner.
2. **R93's argument.** After D and U are durably gone, is a later transfer out of N really an ordinary container
   transfer with the torn-save behaviour of every vanilla chest (ADR-054), and not part of the disassembly's
   two-store transfer? Is there any order in which a carrier on disk and a published container both count?
3. **Access closure.** The section 4 table against the shipped set and the API adapters: capabilities on every face,
   hoppers, droppers, comparators, menus, breaking (survival and creative), explosions, pistons, mobs, ticks, chunk
   unload and reload, startup before the journal is ready, an unreadable journal.
4. **R94.** Whether the built-in prepare and publish is implementable with today's classes, whether the external
   hardening and the documented rule are sound, and whether putting C back after a failed external restore keeps
   conservation.
5. **Saved format and limits.** The carrier root and `SEALED_DISASSEMBLY` in schema 2: mixed legacy and new roots,
   old-reader behaviour, preservation of malformed and future data, the bounds, per-tick work, and no server-thread
   block. Nothing beyond R93's authorized change.
6. **Tests.** Whether section 8 would catch a wrong order: a real hopper, a dropper, an extracting and inserting
   capability consumer, native cuts with two restarts, and a test for each R94 path.

## Rerun

Read-only inspection; optionally rerun the design's abstract models in the evidence folder. No Gradle, GameTest or
server, and no heavy-JVM lock. Delete nothing outside your own folder.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W154-R1, ...), severity, section, problem and
suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
