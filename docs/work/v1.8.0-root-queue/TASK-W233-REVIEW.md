# TASK W233-review: independent read-only review of the K2 machine models

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w233` at `7b47f1295`, cut from `50f3190de`: W233's six commits (the design note `766a1b4db`, the NEW MTLs `faa023816`, the K2 import `8c16f3507`, the renderers and projections `183ca85f5`, the crystallizer light fix `d81e3d38e`, the record `a06edd097`) and Root's commit `7b47f1295` applying W233's audited drafts. Review worktree `D:/GitHub/arce-v180-w233-review`, a detached checkout of `7b47f1295` |
| Task it answers | [TASK-W233](TASK-W233-K2-IMPORT.md), Root decision R235, owner decision D8 |
| Record | `docs/work/v1.8.0-root-queue/records/W233.md` (the projection design at its top); drafts and audits in `D:/GitHub/ARCE-Task-Evidence/root-queue/W233/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W233-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The projection.** W233 changes what the server sends to clients for these machines. Check that it is the same
   kind of bounded block-entity update field as W51's centrifuge projection:
   - no new packet channel, persisted field, schema or identity;
   - the server decides it, and the client cannot influence it;
   - it is sent only on change;
   - it is excluded from saves;
   - it is correct through break, reform, rotation and chunk reload.
2. **Imports.** The four OBJ and texture pairs are byte copies of the pinned upstream files. The NEW MTLs only bind
   the audited textures. The provenance record is complete and stays PENDING_HUMAN_REVIEW. Import verify passes,
   and the etcher pair is absent.
3. **The client.** The catalog entries stay within the OBJ budgets and the allowed references. Rendering happens
   only while a machine is formed, with explicit fallbacks. The crystallizer light fix samples the right block. No
   `net.minecraft.client` reference appears in common code.
4. **The two GameTest failures in W233's local full run.** They are
   `LaserPhysicalGameTests.anUnloadedTargetStopsTheDrillWithoutLoadingIt` and
   `StationSkyContextGameTests.theSkyContextIsServerDerivedSentOnChangeAndFollowsEveryChange`, plus two
   `PlayerDataStorage` WARNs. Say whether anything in W233's diff could cause them, for example through
   block-entity updates, chunk tickets, batch order or shared state. Otherwise say what evidence shows they are
   unrelated. Read W233's retained logs if present, and the branch run on `7b47f1295` once it has finished.
5. **The applied drafts.**
   - The asset-plan rows and the content-ledger row are true to the evidence.
   - The `.gitattributes` lines keep the bytes.
   - The allowlist is unchanged.
6. **Nothing weakened.** Check for merge conflicts against the shared head, which now includes W225's sharded
   workflow, W226 and W232.

## Rerun

In your review worktree only:

- the client model, classic machine and projection unit tests;
- the importer and content-ledger Python tests;
- import verify;
- both validators.

Targeted unit runs need no heavy-JVM lock. Do not run a GameTest, server or client.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W233-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
