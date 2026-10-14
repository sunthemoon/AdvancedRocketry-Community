# TASK W47: C18d per-asset closure audit (the 155 asset-coverage rows)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w47-asset-audit` (from the shared branch) |
| Branch | `codex/v1.8.0-w47-asset-audit` (local only; Root merges) |
| Input | `docs/work/v1.8.0-c18-contract/proposals/revision-03/asset-coverage.csv`: 155 rows (77 REVIEW, 77 IMPORT, 1 REGENERATE), none with terminal evidence |
| Output | `docs/work/v1.8.0-c18d-asset-closure/AUDIT-01.md` and `AUDIT-01.csv` (new) |
| Specification | ADR-066 section 7.3; ADR-061 (the import tools and allowlist); ADR-062 (dispositions); `docs/08-ASSET-LICENSE-AND-PROVENANCE.md`; `UPSTREAM.md`; `NOTICE.md` |

ADR-066 section 7.3 requires a terminal evidence record for every row before the C19 closure. A
terminal record is one of:
- an approved import with its source and target hashes, history, transformation and review;
- a recorded excluded origin with a NEW functional replacement and its original-art record;
- the already approved EXCLUDE disposition of a non-goal.

This task is the audit that lets Root plan the closure batches. It changes no runtime asset.

## What to do

1. For each row, find what the current shared branch actually ships for that asset:
   - an imported copy, with its target path and SHA-256 compared to `source_sha256`;
   - a NEW replacement, with its provenance record;
   - a placeholder;
   - nothing.

   Cite the provenance file and the commit that added it.
2. Classify each row into exactly one of:
   - **A, terminal now.** The evidence ADR-066 section 7.3 needs already exists. Name each piece.
   - **B, import ready.** An IMPORT row whose file the ADR-061 allowlist already approves, and which
     the runtime needs. Name the import tool command that would bring it in. Do not run an import.
   - **C, NEW replacement exists, record missing.** A NEW asset is shipped but its original-art or
     excluded-origin record is incomplete. Say what is missing.
   - **D, needs NEW work.** A REVIEW row with no replacement, which the runtime needs.
   - **E, not used by runtime.** A candidate for EXCLUDE as a non-goal. This needs an ADR-062
     revision; say so, and do not pre-clear it.
   - **F, needs the owner or an author.** An authorship confirmation, a translator credit, or
     anything else only the owner can supply.
3. Write `AUDIT-01.csv` with the input columns plus `class`, `runtime_target`, `runtime_sha256`,
   `evidence` and `missing`, one row per input row, in the same order.
4. Write `AUDIT-01.md`:
   - the counts per class;
   - the method;
   - the proposed closure batches, each small enough for one task and naming its tools;
   - the owner questions, gathered from class F.
5. **Check your work.** Write a small read-only script, kept in the output folder, that checks:
   - every input row appears exactly once;
   - every hash you cite matches the file on disk.

   Run it and record the result.

## Rules

- Change no runtime asset, ledger row, provenance record, ADR or status file. The output folder is the
  whole write scope.
- Do not weaken any screening threshold, and do not move a REVIEW row to CLEAR or EXCLUDE.
- Do not copy any upstream file into the repository.

## Commands

- Your check script, plus `git diff --check`.
- No Gradle run is needed. If you run one, say why.

## Not in this task

- The imports and the NEW art themselves (later batches).
- ADR-062 revisions, provenance records, the ledger and status files (Root).
