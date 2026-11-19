# TASK W398: the final campaign's phase 1 inputs, from existing evidence

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w398`, branch `codex/v1.8.0-w398` from the shared branch |
| Decision | Root decision R532 (QUEUE row W398) |
| Output | `docs/work/v1.8.0-root-queue/records/CAMPAIGN-INPUTS-MANIFEST.md`, a DRAFT record |
| Write scope | That one record. No code, script, test, ADR, register, status file, QUEUE or other record. No native run, Gradle, GameTest or client session: the heavy-JVM lock belongs to candidate C4's native runs until about 2026-11-21. |

## Why

The step lists [CAMPAIGN-CHECKLISTS-A](records/CAMPAIGN-CHECKLISTS-A.md) to [-J](records/CAMPAIGN-CHECKLISTS-J.md)
(W396 and W397) end with open questions. Most ask for phase 1's inputs in
[FINAL-CAMPAIGN-PLAN](records/FINAL-CAMPAIGN-PLAN.md) section 5: P1 (the candidate's identity), P2 (the fixtures,
with world paths, coordinates and hashes) and P3 (the harness: server templates and client profiles per Forge and
JEI combination, the attach mode and frame-time capture). Much of this already exists in earlier evidence and
records. This task gathers it in one place, so that Root builds only what is missing.

## What to do

1. **Collect every open question** from the ten checklist files, with its row IDs.
2. **P1.** Candidate C4 is `659bbcb6a745885565c07d98f5d695d43beef3f2`: hosted regression run 44630684725 (attempt 1),
   governance run 44630684714, JAR SHA-256 `c048743e61da4f939ce0492ca3ec73b7d69ef1634577bebc3279c91cf506cea1`, JAR file
   `D:/GitHub/ARCE-Task-Evidence/root-queue/C4/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar`. The
   build evidence zip beside it (`D:/GitHub/ARCE-Task-Evidence/root-queue/C4/candidate/`) holds the identity files;
   read them (copy the zip to a Temp folder and extract it there) and give the JAR size and the hashes of the sources
   and API JARs if the evidence has them, or say they are not in it.
3. **P2, one row per fixture** the plan or the checklists name (the v0.9 Beta archive, the v1.0 world, the v1.3 to
   v1.7 worlds, the v1.7 placeholder world, the many-machine layout, the API fixture mod, the three rockets of 64,
   512 and 2,048 blocks, the reference workload world, the campaign world of W254-V2-CHECKLIST, the planetary
   variants, the station and route packs, and any other the checklists ask for): whether it exists, where (a path
   under `D:/GitHub/ARCE-Task-Evidence/` or a tracked file), its hash if recorded, which record or script created it,
   and the coordinates or settings the checklists need that its record gives. If it does not exist, say what must be
   built, from which existing script or GameTest fixture if one is close, and roughly how long. Search the evidence
   tree and the records; do not guess paths.
4. **P3.** The real-client harness on D: (its records name it; start from the plan's section 3). For each of the four
   Forge and JEI combinations of the version document's section 10, say whether a server template and a client
   profile exist and where; whether an attach-to-owner-window mode exists; and what frame-time tool is available.
   Say what must be built.
5. **Answer each open question** you can answer from what you found, with a citation, and mark the rest as
   "needs Root" with what is missing. Do not change any checklist.
6. Run `python -B scripts/validate_repository.py`; it must end with `0 failed`. Link only to tracked files that exist
   on the shared branch; use a code span for anything else.

## Rules

- Read only, apart from your record and a Temp folder you remove at the end.
- Commit on the branch in one commit in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
