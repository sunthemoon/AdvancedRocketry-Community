# TASK W331: the satellite bay's native gaps for the next candidate (C2)

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w331`, branch `codex/v1.8.0-w331`, created by Root from the shared head (W325's runners merged) |
| Decision | Root decision R398 (QUEUE row W331), from W325's result `records/W325.md` and its matrix `records/W325-MATRIX.md` |
| Contract | `records/W27A-DESIGN.md` section 9 (B1 to B18), `records/W127-PLAN.md`, `records/W230-DESIGN.md`, W238 (16 pending operations per owner) |
| Record | `docs/work/v1.8.0-root-queue/records/W331.md` and the cut list `records/W331-DESIGN.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W331/` |

## Why

W325 ran the bay's matrix on candidate C1: 84 native cases pass with two restarts each, and no production defect was
found. But 27 native rows stayed NOT_COVERED because C1's packaged fixtures cannot reach them. A production fix (W328,
W329) already requires a new candidate C2, and every case reruns there (R386), so the missing fixtures are built now
and run on C2. Production admission stays off: every new fixture is reachable only on a GameTest server or a gated
release-test server, as the existing bay fixtures are (`arce satellite release-test ...`).

## The rows

W325's NOT_COVERED native rows, with the reason it gave (the matrix has each row's exact boundary):

| Rows | What is missing |
|---|---|
| B4.3, B4.10 | A decommission before the initial kill; the existing hook runs on the restart's first tick |
| B4.6 to B4.9 | An online cut that does not use a data satellite |
| B6.5 | Explicit deletion and reconstruction at the destination (today's cases prove only a consumed stationary source) |
| B6.8 | A native escrow-bound source refused with `BAY_SOURCE_PROTECTED` (only a GameTest exists) |
| B7.3 | Login and copies during a queued retirement, natively (only a GameTest exists) |
| B8.7, B8.8 | A group of several REGISTERED packages, and a partial group |
| B10.6 | A released flight with conflicting authority (the release fixture seeds coherent authority only) |
| B11.2 | Mixed authority in the pending pool |
| B11.7, B11.8, B11.9 | The admission union across consumers, capacity release and re-arm, and a fill that is not bay receipts only |
| B14.3, B14.4 | Request refusal natively, and a stale transaction record |
| B15.3, B15.4, B15.5 | An exception thrown after the replace at D1, after a MOVED UNKNOWN, and at the bay's D0 |
| B17.3, B17.4, B17.5 | End loading across several groups, partial admission across several bays, and a three-phase group |
| B18.6, B18.7 | A stop with a successor, and a stop while UNKNOWN is active |
| CAP.3 | Sixteen owned bays refused before loader capture, natively (only a GameTest exists) |

## What to do

1. **Cut list first** (`records/W331-DESIGN.md`, committed before the code). For each row: the composition of existing
   controls that reaches it, or the new gated fixture it needs, the boundary each cut stops at, and the oracle that must
   hold after each of the two restarts. Try composing existing C1 controls first. Where a row cannot be reached without
   new production behaviour, say so and leave it NOT_COVERED with the reason; do not invent a seam that production code
   would call.
2. **Build the fixtures and cases.** A new Java fixture is a gated test probe beside the existing ones, refused unless
   the release-test gate is on, with a unit test for the refusal. Runner and oracle changes go in the existing bay
   runners or in `scripts/bay_integrated_evidence.py`, with Python tests that fail without them. Keep every existing
   oracle, timeout and assertion as it is; W325's 67 runner tests and 21 evidence tests must still pass unchanged.
3. **The candidate as an argument.** The bay runners and `scripts/bay_integrated_evidence.py` must take the candidate
   JAR path, its SHA-256 and its source commit as arguments, recorded in each invocation, with the production-equality
   check against that commit, so that the whole bay matrix can run on C2 without code changes.
4. **A native smoke on a locally built JAR** (not a candidate): one cut per new fixture family, both restarts, to show
   that each fixture and oracle works. Show at least once per new family that the oracle fails when the protection it
   checks is disabled in a scratch copy, then discard the scratch copy. These smokes are not candidate evidence; the
   real runs happen on C2.

## Verification

- The Python tests of every runner and oracle you change, plus W325's existing bay tests unchanged.
- `./gradlew build` with all unit tests, under the heavy-JVM lock, if any Java changes.
- Any new GameTest alone, then the full GameTest run with `scripts/check_gametest_log.py`, under the lock. An addition
  to `scripts/gametest_expected_log.json` may name only lines that your new tests emit, each with a reason.
- The native smokes, under the lock. W330 and W328FR also take it (backslash lock path, as WORKER-RULES says), so
  take it per invocation and release it between invocations. Call Python by its full path in any detached script.
- `python -B scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews and merges.

## Not in this task

Production behaviour and admission; the cargo harness (W330); the shared-risk cases that the W327 audit proposed
(NOT_LOADED, pool exhaustion by released and REPAIR receipts, D6 timing, cross-family saturation, downgrade), which
are a later task; W325's 31 GameTest rows (F2 to F5, RT, N2), also a later task; the risk register, the status file
and QUEUE; running the matrix on a candidate.
