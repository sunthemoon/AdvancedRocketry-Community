# TASK W11b2, continuation 48: R19 (crewed pre-F2 cancellation ends in checked F8 cleanup) and the integrated native reruns

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c48` (new, at the integrated W11b2 head `2ed7f18e` plus this task) |
| Branch | `codex/v1.8.0-w11b2-c48` (local commits only; Root pushes and merges) |
| Input | Continuation 46's record `records/W11B2-C46.md` ("Native result correction") and its evidence `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/` (`native-PF_ARM_REPLACED/results.json`, `pre-f2-failed-disk.json`, `pre-f2-terminal-audit.json`) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`); R1 to R18; and **R19** in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C48/ROOT-DECISION-R19.md` (SHA-256 `bddaec0540e0e6a8607568f14e41a6bc3c37e1de040fa76e46e31975a43d9362`) |
| Record | `records/W11B2-C48.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C48/` |

The integrated head `2ed7f18e` is the W11b2 branch with continuations 44 (review part A's fixes), 45 (the benchmark
harness), 46 (R17 items 1, 2, 5 and 6 with R18) and 47 (R17 items 3 and 4, the per-rocket journal lease) merged, plus
Root's log expectation for the new `ARCE_GOVERNED_EXECUTOR_HOLD ... reason=PERSISTENT_PLACEMENT` WARN. 3,750 unit tests
passed on it; its hosted regression is running.

## What to do (one commit per item, each with its tests)

1. **R19:** change `EscrowPreF2Probe` and `scripts/run_v180_escrow_pre_f2.py` exactly as R19 items 1 to 4 say: the
   uncrewed cuts keep the retained CANCELLED arm; a crewed no-T cut (PF_ARM_REPLACED, PF_ARM_ACK) observes the
   acknowledged CANCELLED arm, then reports `outcome=CANCELLED_CLEANED` only after the checked F8 prune with every
   listed condition; the second recovery requires the cleaned state from the start after the 100-tick window. Add the
   Python oracle tests R19 item 4 lists. Keep every existing check of the source projection, serial, journal, ledger,
   ERROR/FATAL lines, pins, jar hash and lock.
2. **Native reruns on your committed source** (one forced stop and two restarts per cut, the owned heavy-JVM lock, the
   existing runners and Forge template server, `--java "C:/Program Files/Java/jdk-17.0.7/bin/java.exe"`):
   - every pre-F2 cut uncrewed (`run_v180_escrow_pre_f2.py` with no `--case`), to confirm R18 left uncrewed digests and
     outcomes unchanged and that continuation 47's lease did not change them;
   - PF_ARM_REPLACED and PF_ARM_ACK with `--crewed`;
   - SC_ASCENT with `--crewed` (`run_v180_escrow_source_clock.py`), once, on the integrated source.

   Report each result exactly. Do not rerun a failing cut to get a pass; if one fails, keep its evidence, diagnose it
   with a unit test or the retained disk files, commit what you have and report.
3. Run the whole unit suite after item 1 and the Python runner tests (`python -B -m unittest discover -s tests -p
   "test_v180_escrow_*.py"`). A full GameTest is not required: item 1 changes only a release-test probe and a runner,
   and Root's hosted regression covers the GameTests.

## Write scope and boundaries

`EscrowPreF2Probe`, `scripts/run_v180_escrow_pre_f2.py`, their tests and your record. If R19 needs a narrow additive
helper in `EscrowCrewedProbe` or another release-test probe, record it. No production gameplay, proof, acknowledgement
order, schema, lease, cleanup order, log checker or expectation change, and no relaxed budget, limit, timeout or
assertion. Stop only for a change outside R19: commit what you have with your record, and report.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C48/` and disposable
native sessions under `D:/GitHub/ARCE-Task-Evidence/Temp/W11b2-C48-<cut or run>`. An independent read-only review
(Claude) may run unit tests at the same time; other workers share the heavy-JVM lock: wait for it as WORKER-RULES says,
and never remove a lock you do not own. If the tool policy refuses to let you remove your own lock, world or server
copy, write the exact path in your record and final answer and continue; Root removes it.

## Not in this task

The station authority matrix failure in continuation 46's full GameTest (Root tracks it separately); the benchmark's
measured runs (a later continuation, on a quiet machine); production admission; ADRs, the risk register, the R-035
text, status files and the ledger (Root).
