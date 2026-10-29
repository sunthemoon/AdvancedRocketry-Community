# TASK W205: exact drafts for W203's stale status text

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. You write drafts
only, outside the repository; change no tracked file and make no commit.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w205` (new; a detached checkout of the shared head named in your prompt) |
| Input | `D:/GitHub/ARCE-Task-Evidence/root-queue/W203/PLAN.md` (SHA-256 `0667fe44…8f555e`), section 2, T1 to T12, and the "Not stale" paragraph after T12 |
| Format | `D:/GitHub/ARCE-Task-Evidence/root-queue/W202-review/input/W202-drafts.md` and its audit script |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W205/W205-drafts.md`, plus an audit script and its JSON result in the same folder |

W203 lists twelve passages in `docs/status/CURRENT_VERSION.md`, `docs/status/COMPLETION-PLAN.md` and
`docs/work/v1.8.0-root-queue/QUEUE.md` that merged work has made wrong. Write exact `Target` / `Replace exactly` /
`With` replacements for each, against the files at the shared head:

1. State what is now true, citing commits, records or hosted runs as W203 does, in complete sentences in each file's
   language (CURRENT_VERSION and QUEUE in English, COMPLETION-PLAN in Chinese).
2. Keep the real gaps W203 names: N3-C (now W204), B3-S6, W90's measured costs, the S1 campaign and its three failed
   downgrade rows, R-040's near-bound latency, the reference, soak and client evidence, and no approved Required Gate.
   W198 is approved on re-review but not yet merged when you start; write it as "approved, merging", and Root will
   adjust it at merge.
3. For a dated checkpoint (T7, T8), keep the historical text and mark it as historical, or move it to the
   implementation log only if the file says that is where history goes; never rewrite an old result.
4. For T5, count the owner table's rows as they stand, and do not invent a count of individual choices.
5. Never put a newline inside a QUEUE table cell; keep every table row's column count.

The audit script checks that every anchor is unique in the current shared-head file, that nothing overlaps, that
table rows keep their column count, and that no replacement contains a newline inside a table cell; run it and keep
its JSON result.

## Not in this task

Any tracked file, ADRs, the risk register, the ledger and any change of a version or Gate status.
