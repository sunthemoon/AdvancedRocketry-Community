# TASK W233: closure batch K2, four classic machine models (D8)

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w233` (new; branch `codex/v1.8.0-w233` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W233.md` |
| Owner decision | D8 in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md): the reviewed upstream-URL rule, "the centrifuge pair first"; W229 (merged at `bf446c779`) delivered the rule and K1 |
| Sources | [W51](records/W51.md) (the OBJ path, the formed projection, the centrifuge renderer and its fixture) and [W229](records/W229.md) (the K1 plan, record, drafts and verification); `tools/import/v180-k1-plan.json`; `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/W229-history-audit.json` (K2: 9 of 10 rows history-clear); ADR-061 section 4; `docs/work/v1.8.0-c18d-k1/VERIFICATION.md` |
| Drafts | `D:/GitHub/ARCE-Task-Evidence/root-queue/W233/W233-drafts.md` with an audit script that writes each run's results under a distinct name |

**Root decision R235.** Import K2's four complete OBJ and texture pairs, in the same way as K1:

- `chemicalreactor`;
- `crystalliser`;
- `cuttingmachine`;
- `lathe`.

Leave out the precision laser etcher pair. Its texture still fails the unchanged ambiguous-rename check, and an
origin finding for it is a separate decision.

1. **Plan and import.**
   - A `v180-k2-plan.json`. The OBJs and textures are upstream byte copies. Add a NEW MTL for each only if its OBJ
     names one that upstream lacks, as W51 did for the centrifuge.
   - The derivation re-run and the history check must both be CLEAR. If any entry is not CLEAR, stop that entry
     and say why.
   - Commit the provenance record and the runtime assets exactly as the importer writes them. The review stays
     PENDING_HUMAN_REVIEW.
2. **Client.**
   - Catalog each model on the physical-client OBJ path, within its existing budgets and references.
   - Render it only while its machine is formed, as the centrifuge does.
   - If a machine needs a new server-to-client projection, write a short design note first. The merge then waits
     for a read-only review (AGENTS.md section 13).
   - Each model has an explicit fallback; nothing renders `missingno`.
3. **Tests.** Loader, fixture and projection tests for each model. Show orientation, scale, collision and pattern
   invariance as W51 did. The tests fail first where that is meaningful.
4. **Drafts for Root.** The asset-plan rows, the content-ledger rows if any change, `.gitattributes` entries for text
   model files, and a K2 VERIFICATION summary. Do not edit the asset plan, the content ledger, the allowlist or
   ADR-061.
5. Run `tools/audit/screen_generated_art.py` if you add any NEW art. The workflow on worker branches does not run
   it, but governance does.

## Verification

- The importer and content-ledger Python tests.
- Import verify.
- The client model and classic machine unit tests.
- The whole unit suite.
- The repository and content-ledger validators, with the drafts applied in a temporary copy.

Root pushes. A branch run and an independent review follow.

## Not in this task

The etcher pair, K3 to K5, the real-GPU check (the final campaign), status files and the ledger itself.
