# TASK W236: closure batch K3, the planet analyser, beacon, observatory and solar array models (D8)

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w236` (new; branch `codex/v1.8.0-w236` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W236.md`; keep every log, audit result and generated artefact under `D:/GitHub/ARCE-Task-Evidence/root-queue/W236/`, never only in the worktree |
| Owner decision | D8 in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md) |
| Precedent | [W233](records/W233.md), merged at `229161522`: the K2 plan, NEW MTLs, formed-only renderers, bounded projections, fixtures and drafts, its review (`D:/GitHub/ARCE-Task-Evidence/root-queue/W233-review/REVIEW.md`) and its laser-fixture correction |
| Rows | `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/W229-history-audit.json`, batch K3, all seven history-clear: `models/astrobodydataprocessor.obj`, `models/beacon.obj`, `models/observatory.obj`, `models/solar_array.obj`, `textures/models/astrobodydataprocessor.png`, `textures/models/observatory.png`, `textures/models/solararray.png` |

**Root decision R240.** Import K3 in the same way as K2, and say at the start how each of the four machines fits:

- Each one is a formed multiblock, a single block, or something else. Each needs a projection, or none.
- The beacon has no K3 texture. Find what upstream's beacon model binds to, and either use an already imported or
  NEW texture through an MTL, or leave the beacon out with a reason.

1. **Plan and import.** A `v180-k3-plan.json`, upstream byte copies, and NEW MTLs only where an OBJ names one that
   upstream lacks. The derivation and history checks must be CLEAR; stop any entry that is not. The provenance
   record stays PENDING_HUMAN_REVIEW.
2. **Client.** Catalog entries within the OBJ budgets and references. Render only while the machine is in the state
   its legacy renderer used (formed, or always for a single block). Give each an explicit fallback.
3. **Projection.** If any machine needs a new projection, add a short design note first, and the merge then waits for
   a read-only review. Reuse W233's bounded extension where it fits.
4. **Tests.** Loader, fixture and projection tests for each model.
5. **GameTests.** Your GameTests must not sit inside another test's forced chunks. Vanilla's
   `StructureUtils.forceLoadChunks` forces offsets -1 to +3 around every placed template; that is W233's lesson.
6. **Drafts for Root.** Asset-plan rows, content-ledger rows, `.gitattributes` entries for text model files and a K3
   VERIFICATION summary. Do not edit the asset plan, the content ledger, the allowlist or ADR-061. Run
   `tools/audit/screen_generated_art.py` if you add NEW art.

## Verification

- The importer and content-ledger Python tests, and import verify.
- The client model and machine unit tests, and the whole unit suite.
- The repository and content-ledger validators with the drafts applied in a temporary copy.
- One full GameTest run under the heavy-JVM lock. Report any failure outside your diff with its log kept, and say
  whether your batches could perturb it.

Root pushes. A sharded branch run and an independent review follow.

## Not in this task

The etcher pair, K4 and K5, the real-GPU check, status files and the ledger.
