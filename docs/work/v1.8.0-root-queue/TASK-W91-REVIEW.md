# TASK W91-review: independent read-only review of P1-F3 and P1-N2 (R23 item 7)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer:
change nothing in any repository worktree. Do not start subagents. Codex implemented most of W91 and both Codex slots
are busy, so this is a fresh Claude session with no context from the implementers.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w91-p1-f3` at `cbf235a9`, against the shared branch at `0a3964e7` (which the branch merged in `40a0f17d`): `git diff 0a3964e7..cbf235a9`, 62 files, 37 of them under `src/main` |
| Tasks it answers | [TASK-W91-P1-N2-F3](TASK-W91-P1-N2-F3.md) and its continuations W91B to W91I; read them in the checkout (`docs/work/v1.8.0-root-queue/`), since they are only on the W91 branch |
| Decisions | R23 (`D:/GitHub/ARCE-Task-Evidence/root-queue/W91/ROOT-DECISION-R23.md`, SHA-256 `f6efbff7c7fac6efea3b3ed217f7d56d678c3e864baa3047d4b93964341af064`); R24, R25 and R26 in TASK-W91C, TASK-W91D and TASK-W91E; R28 in TASK-W91I |
| Design (frozen) | `records/W86-DESIGN.md` in the checkout, as R23 amends it |
| Record | `records/W91.md` in the checkout (sections W91 to W91I) |
| Checkout | `D:/GitHub/arce-v180-w91-review`, detached at `cbf235a9`, made for you; do not commit in it |
| Evidence to check | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/` (in particular `implementation-w91f-20261018/`, `merge-w91h-20261018/` and `landed-w91i-20261018/`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **R23 exactly:** R17's logical consumers are kept; `actionable(L)` is defined over `RocketJournalLeases`; only the
   scheduling waits became per-record; every consumer that needs a settled journal uses `clean()`; F2's change is
   justified by F3's START_COUNTDOWN fallback. List every call site that moved from a whole-journal check to a
   per-record one, and say for each whether it may act on L while another record is dirty.
2. **The durability invariant:** no continuation or effect runs before the record's own change is in a committed
   generation captured at or after that change. Check the in-flight boundary (a generation captured before the change
   commits and must not release it), NOT_COMMITTED and UNKNOWN (the continuation stays until a later generation
   commits), stop and abort, and the prune rule (a removed ID stays in the fence and bay sets until its removal is
   acknowledged and its rocket is actionable).
3. **N2:** `failBackToSource` (now `RocketTransferContinuations.failBack`) reserves its continuation before it changes
   anything; a refusal leaves the source, T, the pad claim and `liveTransfers` unchanged; the continuation bound and
   what happens when it is full.
4. **The landed port (W91H) and R28:** W85 Design M's invariant (blocks are restored only after T's absence is
   durable) still holds; `begin`, the write-back and `releasePlain` reserve before their mutation; the write-back
   without a reservation during a stop or abort; whether any `RocketLanded*` path still needs whole-journal semantics.
5. **Changed existing tests (AGENTS section 5):** list every existing test whose expectation or fixture changed (R24's
   fixture adaptations, W91D's jackhammer fixture, R26's four fault injections, R28's four landed expectations, any
   other), and say whether each change stays within its decision or weakens the test.
6. **W91G's seam:** `RocketTransferService.governedLaunchDiscard` is behaviour-neutral.
7. **Log expectations:** W91F's four new expectations and the merged ERROR total of 106 match only lines that the new
   tests drive.
8. **Evidence:** W91I's runs (3,897 unit tests; 1,211 GameTests all passing; the log checker's `server-overloaded`
   count of 11 against 5 under machine load; the six native landed cuts, with K4's air instead of stone). Say whether
   the checker failure and the K4 difference need anything before the merge.
9. **Rerun** `gradlew.bat --offline test --tests '*rocket.server*' --tests '*rocket.flight*'` in the checkout (unit
   tests need no lock); take no lock and run no GameTest or server.

Not in this review: W91N and W91N2 (the native timing and its gated probes, on branch `codex/v1.8.0-w91n-native`),
which Root reviews separately; the F3 and N2 kill/restart cuts, which are a later leaf.

## Output format

- A verdict: MERGE, MERGE-WITH-FOLLOWUP, or FIX-FIRST.
- Findings, each with an ID (W91-R1, ...), a severity, the file and line, the problem, and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
