# TASK W308: what happens when a station warps to the Space body

Root: Claude. Date: 2026-11-06. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w308`, branch `codex/v1.8.0-w308`, created by Root from the shared head |
| Decision | Root decision R355 (QUEUE row W308) |
| Background | W294 (record `docs/work/v1.8.0-root-queue/records/W294.md` on its branch until it merges), item 2: the warp target list offers the Space body because ADR-044 section 4 offers any existing, orbitable, known body other than the current orbit, and ADR-031 makes the Space body orbitable; the satellite bay already refuses it; nobody has tested what a warp to `space` does |
| Record | `docs/work/v1.8.0-root-queue/records/W308.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W308/` |

## What to do

1. Write a GameTest that warps a station to the Space body through the real warp path (controller, cost, countdown,
   commit) and records what happens: does the warp complete, and are the station's orbit, sky context, docking pads,
   rockets landing there and the satellite and orbital laser links in a sensible state afterwards?
2. If everything works, keep the test as a guard and say so in the record; Space stays a warp target.
3. If something misbehaves, decide from ADR-044, ADR-031 and the warp code whether the Space body should not be a
   warp target. If so, exclude it in both the target list (`warpTargets`) and the quote (`quote`), keep the test as a
   guard that it is refused with a clear reason, and draft the ADR-044 implementation note text in the record for
   Root to add (do not edit the ADR yourself). If the fault is in how a warp to Space is carried out and Space should
   stay a target, fix that instead, with the test failing before.

No saved field, packet or protocol change; if one seems needed, stop and say so.

## Verification

The new test; the existing warp, station and satellite tests; `./gradlew build` with all unit tests; the full GameTest
run with `scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B scripts/validate_repository.py
--require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Other warp targets, Cygnus X-1 (public by ADR-057), the ledger, the status file and QUEUE.
