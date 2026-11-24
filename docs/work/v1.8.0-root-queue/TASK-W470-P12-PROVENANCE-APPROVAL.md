# TASK W470: check the 62 provenance records against C6 and write Root's approval blocks (P12)

Root: Claude. Date: 2026-11-24. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w470`, branch `codex/v1.8.0-w470` from the shared branch |
| Decision | Root decision R683 (QUEUE row W470), under the owner's delegation of 2026-11-24 ([OWNER-DECISIONS-20261124](records/OWNER-DECISIONS-20261124.md)) |
| Sources | [ADR-061](../../decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md) sections 4.3 to 4.5 and 4.8; [W414](records/W414.md) (the 58 final batch records and the 26 comparison sheets); [W412](records/W412.md) and packet item P12 of [OWNER-FINAL-PACKET-DRAFT](records/OWNER-FINAL-PACKET-DRAFT.md) (the four c17a records without a review block); the repository's validators that read `docs/provenance/` (find them); `tools/audit/vanilla_derivation.py`; [LICENSE](../../../LICENSE) and [NOTICE](../../../NOTICE.md) |
| Inputs (read only) | C6's JAR `D:/GitHub/ARCE-Task-Evidence/root-queue/C6/candidate/unpacked/advancedrocketry-community-1.20.1-1.8.0-dev.jar` (SHA-256 `9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`; check it first); the vanilla 1.20.1 client JAR whose SHA-256 `docs/work/v1.8.0-vanilla-derivation.json` records (find a local copy on this PC and check its hash); W414's sheets in `D:/GitHub/ARCE-Task-Evidence/final-campaign/9172113ad/provenance-review/` |
| Outputs | Tracked: the review blocks of the records that pass, and the record `docs/work/v1.8.0-root-queue/records/W470.md`. Outside Git: `D:/GitHub/ARCE-Task-Evidence/final-campaign/c8fa16e16/provenance-approval/` with the hash-check and pixel-check results, the scratch scripts and a `SHA256SUMS.txt` |
| Not allowed | Any change under `src/` (frozen at C6, R600). Changing any field of a record other than its review block, except adding the review block the four c17a records lack. Changing a tool, a validator, a threshold or a test to make a record pass. Copying a vanilla file, or any image, into Git. Approving a record whose check fails. |

## What to do

1. **List.** List the 62 records: every JSON record in `docs/provenance/` whose review status is
   `PENDING_HUMAN_REVIEW` (W414 counts 58), and the four c17a records that W412 found without a review block. If the
   count is not 62, report the list and the difference and stop there.
2. **Hash check against C6.** For every entry of every record, find the asset in C6's JAR at its recorded path and
   compare its SHA-256 with the recorded one (W414 checked against C5's JAR). Report per record: entries, matched,
   mismatched and missing, with the path and both hashes of each mismatch.
3. **License check.** For every record, check that its license and copyright fields name this project's license and
   notice, and that no entry claims an upstream, vanilla or third-party origin that the record does not support.
   Report any exception.
4. **Pixel check of the NEW textures.** Section 4.8's derivation check was run on legacy assets; run the same measures
   and thresholds on every PNG entry these records classify as `NEW`, taken from C6's JAR, against the vanilla 1.20.1
   client JAR. If the tool's command line cannot take these files, call its functions from a scratch script outside
   Git; do not change the tool. Report each texture's verdict (`HIT`, `SUSPECT`, `CLEAR` or `UNSUPPORTED`) with its
   best measures. Root asks you to show these in the record even when they are `CLEAR`: the bucket icons
   (`enriched_lava_bucket`, `rocket_fuel_bucket`), `unlit_torch`, `lightwood_planks`, `lightwood_log`,
   `charcoal_log`, `blast_brick`, `coil_side` and the two ore overlays.
5. **Review blocks.** Root's visual review is done (its text is below). For each record that passed steps 2 and 3 and
   whose `NEW` textures are all `CLEAR` in step 4 (or that has none), set the review status to `APPROVED`, the
   reviewer to the reviewer string below, the review date to `2026-11-24`, and the findings to Root's text plus that
   record's own counts from steps 2 to 4. Keep each record's content digest valid; if the validator requires a
   recomputation, do it by the existing canonical rule and say so. Leave a record pending, and report why, if any of
   its entries mismatched or is missing, a license field fails, or a texture is `HIT`, `SUSPECT` or `UNSUPPORTED`.
   For the four c17a records, add the review block in the form that ADR-061 section 4.5 and the validator require for
   their schema; if they cannot take one without changing other fields, leave them unchanged and report instead.
6. **Checks.** Run `python -B scripts/validate_repository.py` (its `Summary:` line must show `0 failed`), every
   validator that reads `docs/provenance/`, including any mode that requires every record to be approved, and the
   Python tests that cover those validators. Report which records still block such a mode, if any.
7. **Record** the list, the per-record table, the pixel-check table, which records you approved and which you left
   pending, the commands, and the hashes of both JARs.

## Reviewer string and Root's findings

Reviewer: `Root (Claude), under the owner's delegation of 2026-11-24 (R683, docs/work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261124.md)`

Root's findings, to open each record's findings list (add the record's own counts after them):

- `On 2026-11-24 Root compared every texture on W414's 26 comparison sheets by eye with the vanilla piston, furnace, dispenser, dropper, iron block, stone and cobblestone faces named by ADR-061 section 4.5, and found no copy or derivation; the textures are flat original designs.`
- `W470 rechecked every entry's SHA-256 against candidate C6's JAR, checked the license fields, and ran the section 4.8 pixel measures on the NEW textures against the vanilla 1.20.1 client JAR.`
- `This review is by Root, an AI agent, under the owner's delegation; it is not a human review, a Gate result or a release approval.`

For a record without textures, use the second and third findings only, with its counts.

## Rules

- Do not delete tests, loosen assertions or raise timeouts (AGENTS.md section 5). Linux-safe tests only.
- No JVM, Gradle or GameTest is needed; do not take the heavy-JVM lock.
- Commit on the branch, in the style of AGENTS.md section 6: the review blocks, then the record. Do not push.
- The last line of your answer is the path of the record.
