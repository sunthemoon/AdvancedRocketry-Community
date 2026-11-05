# TASK W299: duplicate drops in the adapter registration GameTests

Root: Claude. Date: 2026-11-05. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w299`, branch `codex/v1.8.0-w299`, created by Root from the shared head |
| Decision | Root decision R337 (QUEUE row W299) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W299/ci-41891733393-shard3-attempt1.log` (hosted run 41891733393, W290's branch, which does not touch adapters) |
| Earlier work | [W273](records/W273.md), merged at `0c0d093d7`: the GameTest framework's start button, placed on a command block outside the template, is destroyed by terrain water and its drop can land inside a test's region; W273 fixed `AdapterFaultGameTests` only. W272 added the failing test. |
| Record | `docs/work/v1.8.0-root-queue/records/W299.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W299/` |

## The failure

`src/adapterTest/.../AdapterRegistrationGameTests.externalAdapterRetriesRejectedPendingDisassembly` failed once on
hosted CI with `Disassembly dropped duplicate inventory items` (line 163). The message does not say which items.
`AdapterRegistrationGameTests` has the same kind of drop assertions as `AdapterFaultGameTests` (lines 128 and 163)
but not W273's removal of the fixture's own start control.

## What to do

1. Make every duplicate-drop assertion in the adapter source set report the items it found (type, count, position,
   age), as W273's reproduction did, so that a future failure names its cause. Keep the assertions' strength.
2. Determine whether this failure has W273's cause: reproduce with W273's method (terrain water reaching the start
   button) against `AdapterRegistrationGameTests`, and under load.
3. If it is the same cause, apply W273's remedy to every adapter fixture that can see the start button, ideally by
   sharing W273's helper rather than copying it; keep the regions and the assertions unchanged. If it is not, find the
   real source; if it is a product drop, stop and report it with a failing test.

## Verification

A reproduction failing before and passing after (if reproduced); the batches `w164_adapter_success`,
`w164_adapter_fault` and the registration batch 20 times in a row under load; the adapter GameTest classes; the whole
unit suite; the full GameTest run with `scripts/check_gametest_log.py` under the heavy-JVM lock; `python -B
scripts/validate_repository.py --require-approved-identity`.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Production assembly and disassembly, the adapter API, the expected-log manifest, the ledger, the status file and QUEUE.
