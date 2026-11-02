# TASK W249-recheck: independent read-only recheck of W249-fix

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w249` at `2529c9558`: W249-fix's commits `df3d78f7e` to `b4eab1017` on top of Root's `92c62ae37` (the ADR-061 paragraph moved, W249-R5), and Root's merge of the shared head `0c0e2a78f`. Review `git diff 92c62ae37 b4eab1017`. Review worktree `D:/GitHub/arce-v180-w249-review`, which Root moves to a detached checkout of `2529c9558` |
| Findings | W249-R1 to R4, R7 and R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W249-review/REVIEW.md` (SHA-256 `019d7ecac5096e31c51df7d04f76b3eaf7b3931032affc4ef1b23cbcd95e9c27`) |
| Task it answers | [TASK-W249-FIX](TASK-W249-FIX.md), Root decisions R276 and R278 |
| Record | The "W249-fix" section of `docs/work/v1.8.0-root-queue/records/W249.md` on the subject branch; logs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W249-fix/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W249-recheck/RECHECK.md`, and its SHA-256 on the last line of your answer |

**Root decision R278** (2026-11-02) accepts the vanilla consequences of `noOcclusion()` that W249-fix lists: light
reaches beneath a motor, grass survives under it, and beacon beams pass through, as with upstream's non-opaque motors.

## What to check

1. **W249-R1.** Import verify of every v1.8 import record passes in a fresh clone of `2529c9558` (not in an existing
   worktree); the record changed only in `plan_sha256` and `review.content_digest`; the importer's CR guard rejects a
   CRLF plan or record and its tests failed first.
2. **W249-R2.** `noOcclusion()` on the two mono motors changes only occlusion, solid rendering and light blocking:
   check rocket assembly, sealing and atmosphere, landing, placement and sky checks yourself against the code, and
   that the new `motor_occlusion` GameTest fails on the old registrations.
3. **W249-R3 and R4.** The design note's account of `flip_v: false` and the `_classic` names is accurate, and the
   unverified part is said plainly.
4. **W249-R7 and R9.** The new atlas fixture and the auditor's handling of `forge:obj` metadata keys, including the
   refused `mtl_override`.
5. **The sharded branch run** on `2529c9558` once it finishes, and **nothing weakened**.

## Rerun

In a fresh clone or your review worktree only: import verify for all eight records, the importer, content-ledger and
resource Python tests, the atlas, motor and client and data unit tests, the whole unit suite and the repository
validator. Do not run GameTests, servers or clients.

## Output format

A verdict for each finding (CLOSED or NOT CLOSED) and for the fix (APPROVE or BLOCK), new findings with an ID
(W249-R10, ...), the commands you ran and their results, and the SHA-256 of RECHECK.md on the last line.
