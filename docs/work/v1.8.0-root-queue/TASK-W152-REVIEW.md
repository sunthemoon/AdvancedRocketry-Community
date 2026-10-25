# TASK W152-review: independent read-only review of W152 (N3-B2, retained U with sealed staging)

Root: Claude. Date: 2026-10-25. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w152` at `ca8cfa98`: W152's work to `d208bca8` plus Root's tag-overlay integration `ca8cfa98` (`build.gradle` and three regenerated tags). Read `git diff 6f88b36f..ca8cfa98` (130 files); production source is `72918c3d`. Review worktree `D:/GitHub/arce-v180-w152-review`, a detached checkout of `ca8cfa98` |
| Tasks it answers | [TASK-W152](TASK-W152-N3-B2-RETAINED-U.md) and [TASK-W152B](TASK-W152B-SEALED-STAGING-IMPLEMENTATION.md) with Root decisions R96 to R101 |
| Design | [records/W154-DESIGN.md](records/W154-DESIGN.md) (revision 2), [TASK-W154B](TASK-W154B-SEALED-STAGING-REVISION.md) (R93), [TASK-W154-REVIEW](TASK-W154-REVIEW.md) (R94), [TASK-W154-REREVIEW](TASK-W154-REREVIEW.md) (R95), the N3-B2 isolation bullet in ADR-065 and the N3-B2 part of R-035 |
| Record | `docs/work/v1.8.0-root-queue/records/W152.md` on the branch (long; its top section is the current summary) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152/` (R101 and Root's `root-integration/`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Conservation.** At every cut from C0 to C5, at unsealing and lazy unsealing, and in recovery: the original
   contents and any foreign items are each counted exactly once, a member is never both a live container and a sealed
   carrier, and no path restores U's data over a container that was already live. Check the record's claim that a
   consumer-only save reproduces only vanilla's existing residual and adds none (ADR-065's "Why no added residual").
2. **The decisions.**
   - R97: every adapter prepares on a target with no Level, and publishes in one step.
   - R98: built-in chest and barrel capture read owned data directly, without `saveWithoutMetadata`, with
     byte-identical payloads. The Access Transformer entries are limited to what that needs and name R98. No Mixin.
   - R96: an aborted target is marked removed and, for the built-ins, emptied.
   - R100 and R101: a carrier whose loaded root fails validation stays sealed and is written back verbatim. There is
     no save refusal, and only the native writer's two normalizations apply.
3. **The carrier.** It is inert: explosion-immune, `PushReaction.BLOCK`, in the three tags, not holdable by endermen,
   `onlyOpCanSetNbt`, no interaction that moves resources. Its schema-1 root and journal schema 2's
   `SEALED_DISASSEMBLY` are bounded and validated. An older reader refuses the new type.
4. **Open items in the record.**
   - **Overload warnings.** The full GameTest logged 15 "Can't keep up" warnings against a maximum of 8, in non-W152
     batches. Check whether W152 adds any server-thread work that runs outside its own tests: a per-tick scan, the
     completion hint in `72918c3d`, or lazy-unseal discovery. Root is rerunning the full GameTest with CPU sampling,
     and will add its result to the evidence.
   - **The checkpoint `STOP_RULE`.** Say whether U's checkpoint adds a new path to the existing W128 maximum-size
     synchronous-writer cost, or only reuses it.
5. **Scope.** No admission or I6 enabled. No bound, budget, lease, timeout or protocol change. Nothing weakened. New
   log lines have exact entries in `scripts/gametest_expected_log.json`. New tests fail on the old code. Root's
   `build.gradle` change excludes only the three superseded copies, in both `processResources` and `sourcesJar`.
6. **Merge** with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`, which now has W158):
   list the conflicts. Root resolves them and must keep both sides' guards in `RocketEntity.canAddPassenger` and
   `RocketTransferService`.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- W152's new and changed unit classes alone;
- then the `resourceescrow` and `rocket.flight.persistence` packages;
- W152's Python tests for the native oracle.

**Take the heavy-JVM lock for every Gradle run in this review**, with the atomic pattern and the 90-second hand-off.
Root is measuring server overload in a concurrent GameTest. No GameTest or server of your own. Check 6 GiB free memory
first. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W152-R1, ...), severity, file and line, problem
and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
