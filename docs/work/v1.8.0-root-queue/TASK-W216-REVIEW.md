# TASK W216-review: independent read-only review of the ground-survey START preflight remedy

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w216` at `1346e73a9`, cut from `31099ce84`; the slice is `31099ce84..1346e73a9` (5 commits, 12 files). Production: `7fc12970b` (`satellite/mission/RecordEncodingIndex` and `satellite/persistence/ExactGroundPreflight` new; `GroundSurveys`, `RecordSizer`, `SatelliteMissionRegistry`, `CodecRecordSizer` changed). Review worktree `D:/GitHub/arce-v180-w216-review`, a detached checkout of `1346e73a9` |
| Task it answers | [TASK-W216](TASK-W216-GROUND-SURVEY-START-PREFLIGHT.md), Root decision R191 (after W213) |
| Record | `docs/work/v1.8.0-root-queue/records/W216.md` on the branch (the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W216/`; W213's in `D:/GitHub/ARCE-Task-Evidence/root-queue/W213/` |
| Hosted run | the run on `1346e73a9` (Root pushed the branch at 19:42); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W216-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W216 reports START near the 16 MiB bound down from a 286.2 ms idle wall median to 1.4 ms, with allocation down by more
than 99.9 percent, by keeping exact encoded sizes of the registry's immutable residents and measuring only the changed
records.

## What to check

1. **Exact equivalence with the old whole-root preflight.** For every accepted and refused mutation (START, advance,
   completion, removal, account creation), at, just under and just over the byte bound and the section budgets, after
   a load, a migration and a failed sizing: `ExactGroundPreflight.fits` gives the same answer as
   `groundMutationFits` did. In particular: the old path also wrote, compressed and read the root back; is the claim
   that a canonical payload of at most 16 MiB always fits the compressed bound and the reader's heap accounting
   ("leaves 64 KiB for its wrapper and gzip block overhead") true for the worst case, including incompressible data?
   Is the per-record framing (the four bytes per list element, the depth, the `Framing` root) exact?
2. **The index.** `RecordEncodingIndex` keys by object identity: every mutation path of satellites, missions,
   instances, ground surveys and accounts calls `changed`, including load, migration, failure clearing and removal; a
   path that bypasses it, or replaces a record with an equal but different object, cannot leave a stale size that
   admits an over-bound root; an UNKNOWN size forces the original fallback; the index is owner-thread only and its
   memory is bounded.
3. **Immutability.** The five record types are immutable values (no mutable field, collection or NBT reachable from
   them), or the identity cache is not sound.
4. **Cost moved, not hidden:** where the sizing runs now (every put, load), its cost per record and at the bound, and
   whether any of it lands on a player-facing tick beyond what docs/17 allows.
5. **Tests** (`GroundExactPreflightTest`, `GroundSectionPreflightTest`, `ResidentEncodingTest`): they failed first,
   compare against the whole-root preflight, cover the cases TASK-W216 lists and would catch a stale or bypassed
   index. `GroundSurveyScaleTest` changed only its comment; its limit, warm-ups and samples are unchanged.
6. **The measurement:** recompute the before and after figures from the retained evidence, W213's method, idle and
   under load; nothing subtracted.
7. **Nothing weakened:** formats, bounds, budgets, refusal codes, timeouts and assertions.
8. **Risk text:** which sentences of R-030 or other risks this makes wrong, for Root.
9. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W211 (`codex/v1.8.0-w211`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the three new test classes,
`GroundSurveyScaleTest` and the `satellite.persistence` and `satellite.mission` packages; the W216 Python tests if any.
Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build
or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W216-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
