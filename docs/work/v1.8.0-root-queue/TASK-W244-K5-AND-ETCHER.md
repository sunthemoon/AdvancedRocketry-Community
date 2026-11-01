# TASK W244: closure batch K5 and the etcher pair (D8)

Root: Claude. Date: 2026-11-01. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w244` (new; branch `codex/v1.8.0-w244` from the shared head named in your prompt) |
| Record | `docs/work/v1.8.0-root-queue/records/W244.md`; keep every log, audit result and artefact under `D:/GitHub/ARCE-Task-Evidence/root-queue/W244/` |
| Owner decision | D8 in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md) |
| Precedent | [W229](records/W229.md) (the upstream-URL rule and K1), [W233](records/W233.md) (K2), [W236](records/W236.md) (K3) and [W241](records/W241.md) (K4, with W241-fix's rotation rule), all merged |
| Rows | `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/W229-history-audit.json`: batch K5 (`textures/models/spaceelevatorcapsule.png`, history-clear) and the etcher pair in batch K2 (`models/precisionlaseretcher.obj`, history-clear, and `textures/models/precisionlaseretcher.png`, whose history check stops on an ambiguous rename source) |

**Root decision R259**, under the owner's delegation:

1. **The etcher texture's origin, first.** Upstream `636e309d2661` adds `textures/models/precisionlaseretcher.png` with
   the bytes of two files it deletes, `models/precisionlaserengraver.png` and
   `textures/models/precisionlaserengraver.png`. Trace both candidates' complete histories with the importer's own
   checks and report each.
   - If both are clear and byte-identical, add an importer rule, failing first: an ambiguous rename whose candidates
     are byte-identical and all history-clear is clear, and the record lists every candidate. Add tests for a
     candidate with a different byte, a candidate with an unclear history, and three candidates. Write the ADR-061
     section 4 text as a draft for Root.
   - If any candidate is not clear, stop the etcher import, report the finding, and import K5 alone.
2. **K5.** Import the capsule texture. Say how the legacy renderer drew the capsule (upstream `c5cd5af6`: which OBJ
   group, transform and texture) and how the existing bounded capsule renderer can draw it. A new projection or
   packet field needs a design note first; prefer none.
3. **The etcher pair**, if step 1 clears it, the same way as K2: its formed signal and legacy transform stated first,
   orientation pinned in four facings like `K4ObjModelTest`, the R257 rotation rule from the start, and GameTests out
   of other tests' forced template chunks.
4. **Drafts for Root.** Asset-plan rows, the content-ledger row (keep "Forge parse/bake and V1 pending"),
   `.gitattributes` entries, the ADR-061 draft if step 1 adds a rule, and a VERIFICATION summary, in
   `D:/GitHub/ARCE-Task-Evidence/root-queue/W244/` with an audit that writes each run under a distinct name. Do not
   edit the asset plan, the content ledger, the allowlist or ADR-061.

## Verification

- The importer and content-ledger Python tests, and import verify.
- The client model and machine unit tests, and the whole unit suite.
- Both validators with the drafts applied in a temporary copy, and the generated-art screen if any NEW art is added.
- The model GameTest batches in a focused run under the heavy-JVM lock (the full run is Root's sharded branch run).

Root pushes. A sharded branch run and an independent review follow.

## Not in this task

Real-GPU checks, animation, status files and the ledger itself.
