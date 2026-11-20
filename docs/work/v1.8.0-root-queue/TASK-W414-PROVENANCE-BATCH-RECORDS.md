# TASK W414: final provenance batch records for the uncovered asset cohorts, and the matrix corrections

Root: Claude. Date: 2026-11-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w414`, branch `codex/v1.8.0-w414` from the shared branch |
| Decision | Root decision R563 (QUEUE row W414), following R562 |
| Sources | [MATRIX-PROVENANCE-AUDIT](../../releases/v1.8.0/MATRIX-PROVENANCE-AUDIT.md) findings MP-02, MP-04 and MP-05 and its Appendix A (every C5 asset and the record that covers it, or that none does); [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md) sections 4.3 to 4.5; [asset and provenance rules](../../08-ASSET-LICENSE-AND-PROVENANCE.md); `docs/provenance/README.md`, `docs/provenance/schema-v1.json` and the existing v1.8 records as models; the generators under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/datagen/`; C5's JAR `D:/GitHub/ARCE-Task-Evidence/root-queue/C5/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar` (SHA-256 `e5cd2beb4a38b4ea741c5103b18ba224d3f102ef6231adbc3a0849ad4f1e35c8`) |
| Outputs | New batch records in `docs/provenance/` (one per cohort, named `v1.8.0-final-<cohort>.json` or as the README requires); in [PORTING_MATRIX](../../PORTING_MATRIX.md) only the implementation prose, evidence links and deciding references that MP-04 and MP-05 name; review sheets outside Git under `D:/GitHub/ARCE-Task-Evidence/final-campaign/9172113ad/provenance-review/`; the record `docs/work/v1.8.0-root-queue/records/W414.md` |
| Write scope | Those files only. No status value in the matrix changes (PLANNED stays PLANNED; nothing becomes DONE or PASSED). No change to existing provenance records, ADRs, the ledger, code or tests. |
| Not allowed | Setting any review status to APPROVED: approval is the maintainer's (ADR-061 section 4.5). Inventing an upstream identity for NEW or GENERATED work. Any JVM or Gradle run. |

## What to do

1. From Appendix A, group every asset that has generation-only or scope-only coverage into cohorts by its true
   origin (the generator class, or the NEW authoring record, or the data generator), and confirm each origin from the
   code. Assets that already have a complete final record are left alone.
2. For each cohort, write one record in the schema's form: origin (NEW or GENERATED, the generator class or authoring
   reference and the commit), licence MIT, the base identity where one exists, every target path in C5's JAR with its
   SHA-256 read from the JAR, and a review block with status `PENDING_HUMAN_REVIEW` and what the reviewer must look at.
   Hash the JAR first and stop if it differs.
3. **Review sheets** for the maintainer's visual review (ADR-061 section 4.5): for each NEW visual cohort, one image
   that shows every texture of the cohort at 4x scale with its path, next to the vanilla counterparts the ADR names
   where they apply (piston, furnace, dispenser, dropper, iron block, stone, cobblestone), with each vanilla texture
   taken from the 1.20.1 client JAR the runtime cells use (`D:/GitHub/ARCE-Task-Evidence/root-queue/P3/runtime/versions/1.20.1/1.20.1.jar`)
   only to render the sheet, never committed. Write the sheets and a `SHA256SUMS.txt` outside Git. If Python's
   imaging library is not available, write the sheets with a small pure-Python PNG writer, or list the files and say
   why no sheet was made.
4. **Matrix (MP-04, MP-05)**: update the eight rows' implementation prose and evidence links to what C5 contains, and
   add the deciding references to the `ASM/coremod` and old-world rows, exactly as the audit's closure text says.
   Keep every status value and every unexecuted acceptance requirement.
5. Run `python -B scripts/validate_repository.py`; it must end with `0 failed` (it validates provenance records; fix
   your records, never the validator). Link only to tracked files; use a code span for paths outside Git.
6. The record lists every new record with its cohort, asset count and the review it needs, the sheets and their
   hashes, the matrix rows changed, and the list of all records now PENDING_HUMAN_REVIEW for the owner's packet item
   P12 (the four MP-03 records included).

## Rules

- Commit on the branch in one reviewable commit per concern plus the record, in the style of AGENTS.md section 6. Do
  not push.
- The last line of your answer is the path of the record.
