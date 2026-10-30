# TASK W211-review: final independent read-only review of B3-S6

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w211` at `27f820f0e` (pushed), cut from `e00ef1ff2`; 73 commits, 94 files. The interim review covered `e00ef1ff2..fedd1874b`; this review covers the whole slice, with most attention on `fedd1874b..27f820f0e`. Review worktree `D:/GitHub/arce-v180-w211-final-review`, a detached checkout of `27f820f0e` |
| Task it answers | [TASK-W211](TASK-W211-B3S6-LOADER-REUSED-FAMILIES.md) with Root decisions R182 and R185 to R205 (R205 is in that TASK's last section) |
| Interim review | [TASK-W211-INTERIM-REVIEW](TASK-W211-INTERIM-REVIEW.md); `D:/GitHub/ARCE-Task-Evidence/root-queue/W211-interim-review/REVIEW.md` (SHA-256 `3fad14a4…5f67c745c`) and its mutation witnesses |
| Plan | `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md`, section 3 "W203-P2" and section 1.2 |
| Record | `docs/work/v1.8.0-root-queue/records/W211.md` at `27f820f0e` (newest sections first) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W211/` (read only; 115 MB, sealed packages with checksums) |
| Hosted run | the run on `27f820f0e` (Root pushed the branch at 07:02); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W211-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W211 reports C at 39 of 50 passed, the other eleven (F9, P1-F8 and real-loader N2 in their ordinary form) out of reach
because a settled loader-funded rocket keeps its ledger binding and the product routes it to the governed launch,
pinned by three routing tests; with A, B and the earlier rows, 221-plus native starts, the retained replay passing
264 of 264 images, and every failed attempt kept as failed.

## What to check

1. **The interim findings are fixed:** I1, I2, I3 and I5 in the oracle with failing-first tests on the reviewer's
   mutations (rerun the interim review's `mutation_checks.py` against the corrected oracle), and I4's N2 mapping.
2. **The rows since `fedd1874b`** (G1 veto and saturation, O1 to O4 and O3J, the B chains, veto, abandon and
   normalization, C's F1 to F8, sticky reservation and successor refill): recompute each from the retained images and
   results; both restarts completed; resources, epochs, identities, snapshots, typed fuel, records and markers exactly
   as claimed; the replay is what the record says.
3. **The production-source changes:** `EscrowNamedFillTestHook` (R198: exactly three O3J names), the journal-policy
   link in `RocketTransferSavedData.requestWrite` (R200), and the new `EscrowLoaderS6*` probes in `src/main`: each is
   inert unless the hooks property, the exact case and the release gate all hold; the identical policy comes back
   otherwise; nothing changes what the product writes or decides; no new failure injection beyond the existing kinds.
4. **The decisions' conditions were kept:** R190 and R201 renewals (images unchanged and explained, test-side causes,
   failing-first fixes, replays, failed attempts kept); R202's three fixture-owned stores and no others; R203 and R204's
   rule against bypassing product guards.
5. **The eleven exclusions (R205):** the routing rule is real and the three tests pin it; the ordinary forms of F9,
   P1-F8 and N2 truly cannot be reached with a loader-funded rocket. Then judge whether the **governed** analogues are
   covered by passed rows: a failed or vetoed destination publication on a governed flight (F9's purpose) and named-pad
   crash windows on a governed flight (N2's purpose), if governed flights can use named pads. Name any governed
   analogue that is neither covered nor out of reach.
6. **The matrix as a whole** against S7's list: every representative is passed, mapped to real-loader evidence, a
   labelled residual (E4, the BI O-veto first attempt), or a pinned exclusion; nothing is credited by analogy.
7. **Counts** match the campaign reports; **cleanup**: no world, export, archive or lock left behind.
8. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the S6, named-fill hook and journal-policy
Java test classes and the `resourceescrow` package; the loader S6 oracle, flight and inventory Python tests; the replay
script on the retained evidence. Targeted unit runs need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest,
native case, runData, full build or server. Delete the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W211-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
