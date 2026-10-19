# TASK W97B: complete B3-S1 and B3-S2 (K5, the baseline-failure cuts and the preflight refusal; Root decision R35)

Root: Claude. Date: 2026-10-19. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W97](TASK-W97-B3-S1-S2-LOADER-CUTS.md),
[records/W97.md](records/W97.md) and [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) section 5's S2 table first. You
are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w97b` (new; from the shared branch at `83a7408e`, which has W97 merged) |
| Branch | `codex/v1.8.0-w97b` (local commits only; Root merges) |
| Record | `records/W97.md` (extend it with a W97B section) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W97B/` |

## Root decision R35 (recorded here)

W97 passed 17 of B3-S1's and B3-S2's 21 required cuts. Root decides the rest:

1. **K5 is a coverage gap, not a product finding yet.** At the cut ("the first explicit native store and force selects
   DESTINATION"), raw storage already showed the source tank at 4,000 mB as well as the bank at 1,000 mB, both epochs
   1, and both restarts ended conserved at 4,000/1,000 with nothing pending. Find out, from the code and a native run,
   why the source store was already AFTER at that point (for example the barrier storing both endpoints before its
   first force, an autosave, or the fixture's chunk layout). Then either build a fixture that reaches the intended
   destination-only stored state, or show from the code that the barrier makes that state unreachable and record K5 as
   not applicable with that evidence. If the investigation shows resource loss, duplication or a wrong recovery, stop
   and report it as a finding.
2. **The two creation cuts with a failed baseline (definite and UNKNOWN)** may extend the existing gated ledger-writer
   fault hook so that it can select generation 1, the baseline write. This is a generation selection inside an existing
   gated injection, not a new kind of storage failure; it stays off unless the release-test gate and its property are
   set, and it changes no production decision. Show the three unresolved failures and the final `LEDGER_UNAVAILABLE`
   status natively.
3. **N3_PREFLIGHT** needs a real candidate-capacity preflight refusal. Build it inside the existing gated loader probe
   family (for example by reaching the marker or reservation bound with real admitted operations), not with the rider
   test W97 used for eligibility.

## What to do

Apply R35, run each new or corrected cut with two restarts on a jar built from your committed source, and compare raw
quantities, markers and epochs as W97 does. Then run the harness's tests, the whole unit suite if Java changes, one full
GameTest with the unchanged log checker (W90b's logger expectation is fixed on the shared branch since `3a99f4cd`), and
`python -B scripts/validate_repository.py --require-approved-identity`, all on committed source and serially.

## Write scope

The loader cut harness and its tests, the gated loader and escrow probes and the ledger writer's existing gated fault
hook (generation selection only), new unit tests, and the record. No production behaviour change; no relaxed
assertion, limit or timeout. Stop and report if one seems needed.

## Commands and not in this task

As WORKER-RULES says. Other workers (W98, W101) also run tests; take the heavy-JVM lock for every native run and
GameTest and release it between cuts when another worker waits. Check that C: and D: have more than 10 GiB free before
each heavy start (C: has about 12 GiB). Delete each cut's server copy afterwards. If the tool refuses your cleanup,
write the paths in the record and final answer at once.

Not in this task: B3-S3 to B3-S6, cargo, P1-N3, ADRs, the risk register, status files and the ledger (Root).
