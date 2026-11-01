# TASK W241-review: independent read-only review of the K4 machine models

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w241` at `fb3cbe720`. It holds W241's commits up to `4656c7a28` (projection design and NEW materials `db3ce4c62`, material identifiers `b32cbf430`, import `5d43b202d`, renderers and projections `3b24a4773`, republish `ae9288ad8`, capsule composition `1984a0795`, cleared flag on load `1ba2f97b5`, fixture and reload corrections `6e310c287`, record `4656c7a28`), Root's commit `4b87f2f00` applying W241's audited drafts, and Root's merge of the shared head `9ace3837a`. Review worktree `D:/GitHub/arce-v180-w241-review`, a detached checkout of `fb3cbe720` |
| Task it answers | [TASK-W241](TASK-W241-K4-IMPORT.md), Root decision R253, owner decision D8 |
| Precedent | W236 (K3) and its review (`D:/GitHub/ARCE-Task-Evidence/root-queue/W236-review/REVIEW.md`), merged at `2e549f3d2`, and W236-fix's `K3ObjModelTest` |
| Record | `docs/work/v1.8.0-root-queue/records/W241.md` on the subject branch; drafts, audits and logs in `D:/GitHub/ARCE-Task-Evidence/root-queue/W241/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W241-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

**Root decision R255.** Under the owner's delegation, Root adopts W241's five design proposals (record section
"Pre-implementation projection design and proposals for Root") for development, subject to this review: the K3
reflection convention with render-only boxes, the elevator's canonical NORTH ground anchor without the tether, door,
capsule or orbital hull, the mirrored precision assembler on its JSON fallback, the exact legacy rolling-machine
instances, and hulls composed with the existing effects. V1 still has to judge overlap, placement and platform
clearance. Say if any proposal is unsound.

## What to check

1. **Projections.** Each new formed flag is the same bounded, transient, server-decided block-entity update field
   that W233 and W236 use:
   - no packet channel, persisted field, schema or identity is added;
   - it is sent only on change;
   - it is absent from saves;
   - it is correct through break, reform, rotation, revalidation and chunk reload.
2. **The capsule codec.** `ElevatorCapsuleCodec` now accepts one more named boolean, `formed` (`1984a0795`). Confirm
   it travels only from server to client, is bounded, rejects every other new key and malformed byte, and leaves the
   ride count, entry fields, countdown and lease limits unchanged; and that nothing a client sends can set formation.
3. **Changed behaviour after the first full run.** The unfiltered GameTest run at `ae9288ad8` failed twelve tests.
   - Judge each correction in `1984a0795`, `1ba2f97b5` and `6e310c287`: a fixture or a wrong expectation corrected,
     or an assertion weakened?
   - `1ba2f97b5` clears the flag when an endgame device loads, and the rolling test no longer expects the stored
     formation to show immediately after a load. Say how long a formed hull stays hidden after a chunk load or
     restart, and whether that is acceptable or a finding.
4. **Imports.** The six OBJs and six PNGs are byte copies, the six NEW MTLs bind only audited textures, the provenance
   stays PENDING_HUMAN_REVIEW, import verify passes against the authoring commit `b32cbf430`, and K5 and the etcher
   are untouched.
5. **The client.**
   - Budgets and references are respected (the catalog's 34,300 budget vertices).
   - Each machine renders in its legacy idle state, with explicit fallbacks.
   - `K4ObjModelTest` compares every drawn position in all four facings against the transcribed legacy transforms
     (upstream `c5cd5af6`). Check two of the six transcriptions yourself, including the rolling machine.
   - No client reference appears in common code.
6. **The applied drafts** (`4b87f2f00`). The asset-plan, ledger and `.gitattributes` rows and the K4 VERIFICATION
   summary are true to the evidence, and the allowlist and ADR-061 are unchanged.
7. **GameTests.** The seven `K4ModelGameTests` stay out of other tests' forced template chunks. Read the sharded
   branch run 41279515705 on `fb3cbe720` once it finishes; it is the first full GameTest run after the corrections.
8. **Nothing weakened.**

## Rerun

In your review worktree only:

- the client model, machine, endgame and projection unit tests;
- the importer and content-ledger Python tests;
- import verify;
- both validators.

Do not run GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W241-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
