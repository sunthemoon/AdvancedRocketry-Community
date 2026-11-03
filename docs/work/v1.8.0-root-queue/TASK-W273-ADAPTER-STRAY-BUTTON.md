# TASK W273: the stray stone button in the adapter capture-throw test's region

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w273`, branch `codex/v1.8.0-w273`, created by Root from the shared head |
| Subject | `AdapterFaultGameTests.throwingCapturePreservesSource` (batch `w164_adapter_success`, source set `src/adapterTest`) |
| Evidence | W272's loaded focused runs of `w164_adapter_success,w164_adapter_fault`, in `D:/GitHub/ARCE-Task-Evidence/root-queue/W272/`: `repeat-01-latest.log` line 356, `load-03-latest.log` line 362 and `stress-08-latest.log` line 368 (SHA-256 `c57d67e07ff4f2048aa9f96a3f9f4dc0474cb2f1ae396b93b521d7be8b166c40`) |
| Record | `docs/work/v1.8.0-root-queue/records/W273.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W273/` |
| Decision | Root decision R303 (2026-11-03): a test-reliability fix, run while no content task is open |

## What the logs show

In three of about twenty-five loaded focused runs, the test failed with `Duplicate inventory drops found: [1
stone_button at (142.05885489478297, -60.0, z) age=1264]`, with z between 0.46 and 0.54. The x coordinate and the
age are identical in all three, so the same item is produced at the same tick of every run; only its landing point
varies. The test's region is `AABB(absolutePos(ORIGIN)).inflate(3)` (assembler at `143, -59, 3`, so the region starts
at z = 0), and `noDrops` (`AdapterFaultGameTests.java` line 381) counts every `ItemEntity` in it. Root's unverified
reading: the button is not part of any adapter fixture (no fixture places one), so it probably comes from the GameTest
framework or a neighbouring structure (for example a button attached to a structure or marker block that is cleared
or replaced at batch start), and the test fails only when the item lands just inside z = 0. The full local runs and
hosted runs have not failed this way so far.

## What to do

1. **Find the source.** Identify which block the stone button belonged to, what removed its support and when (the
   item's age of 1264 at the check dates its creation). Run the same selection on the shared base without load and
   under load, and say whether the item appears in passing runs too (outside the region).
2. **Rule out a product defect.** Show that the button is not a member or cargo of any rocket in the batch and that
   no assembly, capture, restore or disassembly path produced it. If it is a product drop, stop and report it with a
   failing test instead of changing the fixture.
3. **Fix the cause, in this order of preference:** stop the drop at its source (the fixture or framework placement
   that leaves an unsupported button), or make the test's region exclude what no rocket operation in this test can
   produce, with the reason in a comment. Do not remove the duplicate-drop assertion or narrow it to fewer item types
   than the rocket's members and cargo. Do not raise a timeout, skip the test or move it to another batch
   (AGENTS.md section 5).

## Verification

A deterministic reproduction where you can make one (for example by forcing the item's landing point), failing
before the change and passing after it; the selection `w164_adapter_success,w164_adapter_fault` 20 times in a row
under load (state how you made the load), each with a search for undeclared WARN or ERROR lines and for any stray
item in the adapter regions; the adapter GameTest classes you changed; the whole unit suite; the full GameTest run
with `scripts/check_gametest_log.py` under the heavy-JVM lock; the repository validator.

Commit on the branch; do not push. Root reviews, pushes for a sharded run and merges.

## Not in this task

Production assembly, capture, restore and disassembly behaviour, the adapter API, W272's pending-rejection fix, the
expected-log manifest and checker, other intermittent tests in the status file, the status file, QUEUE and the ledger.
