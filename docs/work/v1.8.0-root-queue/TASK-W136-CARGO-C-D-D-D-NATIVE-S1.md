# TASK W136: the native S1 checks of cargo leaves C-D and D-D (fluid and item cargo resources)

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md), [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
section 6 (the C-D and D-D rows and the cut mapping's "Protected roots and native save scope" row) and the passage on
tank assembly in section 3, cut-G2 in [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md), and the records of the leaves
that delivered the resources: [W109](records/W109.md) (C-A with C-D's resources) and [W120](records/W120.md) (D-A with
D-D's resources). You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w136` (new; from the shared branch at the commit named in your prompt) |
| Branch | `codex/v1.8.0-w136` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W136.md` (new; a short plan at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W136/` |

W109 and W120 delivered C-D's and D-D's art, data, recipes, text and provenance, and left their native S1 checks open.
This task runs them, so the C-S and D-S cut families can follow. It produces S1 evidence and the harness for it; it does
not claim anything about an escrow cargo transaction (C-S and D-S) and does not enable admission.

## What to do

1. **runData twice** with a clean tracked and untracked tree after each run, and the reference, recipe, loot, registry,
   provenance and art checks the C-D row lists, for both leaves.
2. **Native S1 on a dedicated server built from your committed source**, for the fluid devices (C-D) and then the item
   devices (D-D):
   - packaged sidedness: the built JAR starts a dedicated server without client classes, and the production tags hold;
   - an old rocket with a chest (assembled before cargo existed) restores unchanged, and an old snapshot stays
     restorable, as section 3's tank-assembly passage requires;
   - two restarts of a tank snapshot fixture (C-D) and of an item snapshot fixture (D-D), each compared exactly;
   - protected device roots survive the restarts unchanged;
   - cut-G2 as W11B3-DESIGN defines it, with the cargo devices (the passenger, mount and teleport boundaries C-A's and
     D-A's GameTests exercise in process).
   Use the existing native harness patterns (`scripts/run_v180_*.py` and their tests) and add a runner with Python unit
   tests for its oracle. About six starts per leaf, as the design plans.
3. **D-D's wording.** W120 left some device statuses worded for fluids; D-E1 (W132) made item devices reach them. Check
   what an item device now shows for each status it can reach, and if any still reads as fluid, give items their own
   wording in both languages (append only; keep every key's ordinal and existing text).

Production code changes only where a check finds a defect; report each one and stop if it needs a saved format,
schema, protocol, bound or timeout change. No existing assertion is relaxed. A new WARN or ERROR that a GameTest drives
needs its own exact manifest entry.

## Verification

Each new or changed test class alone, then the whole unit suite and the Python tests you add; one full GameTest with the
unchanged log checker; the two runData runs and the S1 starts, all on committed source under the heavy-JVM lock with the
atomic pattern and the hand-off rule in WORKER-RULES, releasing it and deleting every server copy and
`build/gametest/world` in the `finally` of the same detached script that took it. Check C: and D: above 10 GiB and that
6 GiB of memory is free before each Gradle run or native start.

## Not in this task

C-S and D-S cuts, escrow transactions, enabling admission, V1 and V2 client evidence, ADRs, the risk register, status
files and the ledger (Root).
