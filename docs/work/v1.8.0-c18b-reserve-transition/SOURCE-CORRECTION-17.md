# Reserve source17: separate Root precision correction

2026-10-08. Original Claude source/handoff remain committed at
707c389bf8863f1b4e5a67881cf4a7aa974b3fed. The author explicitly releases all
interests and grants Root the returned three-path commit; that commit is backed
up by normal push. This separate Root delta changes only one source comment:
active plus transfer is bounded by max(active,target), not target alone when
the existing active balance already exceeds a lowered target. No executable
statement or test assertion changes. Actual source review/JUnit remains pending.

The handoff's claim that single phase advance/decision equality implies one
engine invocation is too strong: identical pure calls have identical outputs.
Those tests evidence decision semantics, not call count. The helper has one
literal direct call; independent source inspection must establish that control
flow separately. Original HANDOFF-16 and external author report are preserved,
not rewritten into a completed review or test result.

Actual public receipt: CLI/runner exit 0, no cap, all ten I/O observations true,
17 full governance reads before first write, 44 turns, client USD 2.1540128.
Tool counts are Read29/Glob2/Grep6/Write4/Edit2, not the report's approximate
35-call/cost narration. No JVM/Git command ran in that worker. This precision
record is not a waiver, whole-equipment adoption or Gate conclusion.
