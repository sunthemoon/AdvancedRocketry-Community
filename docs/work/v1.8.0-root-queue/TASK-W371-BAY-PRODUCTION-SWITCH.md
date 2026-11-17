# TASK W371: a production admission switch for the satellite bay, off

Root: Claude. Date: 2026-11-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w371`, branch `codex/v1.8.0-w371`, at the shared head |
| Decision | Root decision R464, under R459 ([OWNER-DECISIONS-20261117](records/OWNER-DECISIONS-20261117.md); QUEUE row W371) |
| Record | `docs/work/v1.8.0-root-queue/records/W371.md` |
| Write scope | `BayDeploymentSession` and one small new switch class beside it, their unit tests, and the record. No other production change, no register, ADR, status file or QUEUE change. |

## Why

The four cargo kinds each have a production admission constant (`EscrowCargoSwitches.production`, all false). The bay
has none: `BayDeploymentSession.admissionEnabled()` answers only for the release-test gate, so enabling the bay would
need a code change ([C3-SWITCH-REPORT](records/C3-SWITCH-REPORT.md), sections 2.2 and 3.1). Root decided that every
switch is made ready but not enabled (R459). Enabling the bay then becomes a one-line change once the owner says yes.

## What to do

1. Add a production constant for bay deployment admission, `false`, in the style of `EscrowCargoSwitches`. Make
   `admissionEnabled()` answer `true` when the constant is true or the existing release-test gate is open, and
   nothing else.
2. With the constant false, behaviour must be byte-identical to today: same answers, same logs and same menu
   statuses, in production and under the release-test gate.
3. Add a unit test that pins the constant `false`. Add one that shows the gate still opens admission exactly as before.
   Add one that would fail if the constant were true without the owner's decision; name the decision in its message.
4. Run the bay unit and GameTest classes that call `admissionEnabled()`, then the full unit suite. No GameTest
   expectation changes.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5).
- Use `JAVA_HOME=C:\Program Files\Java\jdk-17.0.7`. If you run the full GameTest server, take the heavy-JVM lock.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives what was done, the commits, the commands and results. Its last line, and the last line of your
  answer, is the path of `records/W371.md`.
