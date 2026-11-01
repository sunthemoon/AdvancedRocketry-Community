# TASK W241: closure batch K4, six machine models (D8)

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w241` (new; branch `codex/v1.8.0-w241` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W241.md`; keep every log, audit result and artefact under `D:/GitHub/ARCE-Task-Evidence/root-queue/W241/` |
| Owner decision | D8 in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md) |
| Precedent | [W233](records/W233.md) (K2) and [W236](records/W236.md) (K3), both merged, with their reviews in `D:/GitHub/ARCE-Task-Evidence/root-queue/W233-review/` and `W236-review/`, and W236-fix's `K3ObjModelTest`, which pins orientation against the legacy renderers |
| Rows | `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/W229-history-audit.json`, batch K4, all twelve history-clear: the OBJ and texture of `blackholegenerator`, `orbitallaserdrill`, `precisionassembler`, `railgun`, `rollingmachine` and `spaceelevator` |

**Root decision R253.** Import K4 in the same way as K2 and K3. Under the owner's delegation, Root decides design
points, so record any you meet as proposals for Root.

1. **First, for each machine.** Say whether it is a formed multiblock, which existing block entity owns its formed
   state, which legacy renderer and transform it used (upstream `c5cd5af6`), and whether it needs a projection. Some
   of these are endgame multiblocks with their own state: reuse their existing formed signal where one exists.
2. **Plan and import.** A `v180-k4-plan.json`, upstream byte copies, and NEW MTLs only where an OBJ names a missing
   one. The derivation and history checks must be CLEAR; the provenance record stays PENDING_HUMAN_REVIEW.
3. **Client.** Catalog entries within budgets and references, rendering in the legacy render state, with explicit
   fallbacks.
4. **Projection.** If a machine needs a new projection, write a design note first; a read-only review follows before
   the merge.
5. **Tests.** Loader, fixture and projection tests. Add an orientation test like `K3ObjModelTest` that compares every
   drawn position in all four facings with the transcribed legacy transform, so orientation is pinned from the
   start. Keep GameTests out of other tests' forced template chunks.
6. **Drafts for Root.** Asset-plan rows, the content-ledger row (keep "Forge parse/bake and V1 pending"),
   `.gitattributes` entries and a K4 VERIFICATION summary, in `D:/GitHub/ARCE-Task-Evidence/root-queue/W241/` with
   an audit that writes each run under a distinct name. Do not edit the asset plan, the content ledger, the
   allowlist or ADR-061.

## Verification

- The importer and content-ledger Python tests, and import verify.
- The client model and machine unit tests, and the whole unit suite.
- Both validators with the drafts applied in a temporary copy.
- One full GameTest run under the heavy-JVM lock, reporting any failure outside your diff with its log kept.

Root pushes. A sharded branch run and an independent review follow.

## Not in this task

K5, the etcher pair, the real-GPU check, status files and the ledger itself.
