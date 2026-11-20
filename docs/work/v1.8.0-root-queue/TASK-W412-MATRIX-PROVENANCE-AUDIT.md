# TASK W412: the porting-matrix, disposition and provenance audit on candidate C5

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w412`, branch `codex/v1.8.0-w412` from the shared branch |
| Decision | Root decision R561 (QUEUE row W412) |
| Sources | [version document](../../versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md) sections 3, 5, 9 (item "asset provenance completeness"), 14 (items 1 to 3) and 15; [PORTING_MATRIX](../../PORTING_MATRIX.md); the ADRs under `docs/decisions/` (ADR-062 and its revisions especially); [asset and provenance rules](../../08-ASSET-LICENSE-AND-PROVENANCE.md); `docs/provenance/` (README, schema and the v1.8 manifests); `UPSTREAM.md` and `NOTICE.md`; C5's JAR `D:/GitHub/ARCE-Task-Evidence/root-queue/C5/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar` (SHA-256 `e5cd2beb4a38b4ea741c5103b18ba224d3f102ef6231adbc3a0849ad4f1e35c8`) and its inventory `D:/GitHub/ARCE-Task-Evidence/final-campaign/9172113ad/fixtures/K13/assets.csv`; the release drafts [REQUIREMENT-MAP](../../releases/v1.8.0/REQUIREMENT-MAP.md) and [GATE-STATUS](../../releases/v1.8.0/GATE-STATUS.md) |
| Output | `docs/releases/v1.8.0/MATRIX-PROVENANCE-AUDIT.md` and a short record `docs/work/v1.8.0-root-queue/records/W412.md` |
| Write scope | Those two files. Read only otherwise: no change to the matrix, an ADR, a manifest, code, a test or another record. |
| Not allowed | Marking a requirement or Gate as passed. Any JVM, server or client. |

## What to do

1. **Matrix (version 14.1 and 14.2).** Count the matrix's rows by status. List every UNKNOWN (there should be none),
   every MISSING with whether it is inside v1.8's agreed scope, and every DEFERRED or REJECTED with the ADR (and
   revision) that decides it; check that the ADR really names that item. List rows whose status disagrees with the
   code or data in C5 (for example a row marked DONE whose registry ID is absent from the JAR, or an ID present in the
   JAR with no matrix row).
2. **Provenance (version 14.3, 9 and 15).** For every asset in C5's JAR (from `assets.csv`; check its hash against the
   JAR first), find the provenance record that covers it (source, licence, upstream commit or NEW origin, hash where
   the manifest records one) and say whether the JAR's bytes match the recorded hash. Report assets with no record,
   records whose hash differs, and records for files the JAR no longer contains. Note which checks the repository
   validator already makes automatically, so the audit does not repeat them as new findings.
3. **Licence and notices.** Check that `NOTICE.md` and `UPSTREAM.md` cover what C5 ships, that the JAR carries the
   licence and notice files the provenance rules require, and that nothing from a source other than the MIT upstream
   or recorded NEW work is present.
4. **Findings.** Number each finding, with its evidence (path, ID, hash) and a severity under docs/17 section 3, and
   say what would close it (a matrix correction, a manifest entry, an ADR line, a code change). Do not fix anything.
5. Run `python -B scripts/validate_repository.py`; it must end with `0 failed`. Link only to tracked files; use a code
   span for paths outside Git.

## Rules

- Commit on the branch in one commit in the style of AGENTS.md section 6. Do not push.
- The last line of your answer is the path of the record.
