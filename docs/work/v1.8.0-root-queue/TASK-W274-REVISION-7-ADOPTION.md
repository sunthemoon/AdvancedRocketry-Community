# TASK W274: adopt ADR-062 revision 7, with all 95 REVIEW files excluded

Root: Claude. Date: 2026-11-04. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w274`, branch `codex/v1.8.0-w274`, created by Root from the shared head |
| Input | W268's proposal, branch `codex/v1.8.0-w268` at `b5639aee1`: the 17 files of `docs/work/v1.8.0-w268-review-closure/` (reviewed by a Claude session, rechecked, and corrected twice) |
| Authority | [OWNER-DECISIONS-20261104](records/OWNER-DECISIONS-20261104.md): the owner's words and Root decision R304 |
| Record | `docs/work/v1.8.0-root-queue/records/W274.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W274/` |
| Review | A separate session reviews the adoption before Root merges it (AGENTS.md section 13) |

## What the owner decided

On 2026-11-04 the owner accepted all eight groups of W268's proposal. G1 to G6 and G8 are accepted as the proposal
states them (85 files become EXCLUDE). For G7 the owner chose the NEW audio, after accepting all ten NEW sounds in an
in-game listen (D5): the ten legacy OGGs are not wanted, so they become EXCLUDE as well instead of staying REVIEW as
the proposal drafted. All 95 REVIEW files therefore become EXCLUDE, and no REVIEW file remains. Excluding a file finds
nothing about its authorship; G6's LibVulpes chain stays unresolved, as the proposal says.

## What to do

1. **Bring the proposal in.** Merge `b5639aee1` into your branch with `git merge --no-ff`; it only adds
   `docs/work/v1.8.0-w268-review-closure/`. Read `PROPOSAL.md` in full, especially "Adoption steps".
2. **Amend it for G7.** Change the builder and its drafts so that G7's ten OGGs, including the shared recording
   `sounds/lathe.ogg` and `sounds/rollingmachine.ogg`, are EXCLUDE. Rebuild with `--base` set to your branch head
   before adoption, and recompute every count, rule number and hash: REVIEW 95 to 0, EXCLUDE 270 to 365, and the
   allowlist drops 95 REVIEW entries. Update `PROPOSAL.md` so that it states what was adopted and that G7 was
   amended on the owner's decision, keeping the drafted G7 text visible as superseded.
3. **Adopt, following the proposal's steps 2 to 6**, with OWNER-DECISIONS-20261104 as the decision record:
   - the reviewed findings file: reviewer `sunthemoon` (the owner, as in the existing owner findings), `reviewed_at`
     `2026-11-04`, `basis` and `review_record` citing `docs/work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261104.md`,
     no `draft_status`, installed with `adopt_findings.py` into `docs/provenance/v1.8.0-origin-findings.json`;
   - the asset plan (`docs/work/v1.8.0-asset-plan.csv`) and the allowlist
     (`docs/work/v1.8.0-asset-import-allowlist.txt`);
   - ADR-062 revision 7, accepted: `status: ACCEPTED`, `revision: 7`, a `revision_7_date` of 2026-11-04 and a
     `revision_7_basis` naming the owner decision record, with the new allowlist pin;
   - the validator guard in `scripts/validate_v180_content_ledger.py` (step 4), with negative tests;
   - the fixture updates (step 5). No sound stays REVIEW now, so use the beacon restore route the proposal designs,
     not its "still-REVIEW sounds" alternative.
4. **The ledger, read-only.** Do not edit `docs/work/v1.8.0-content-ledger.csv`. In a disposable copy outside the
   worktree, find what the ledger validator needs to close the ten `sound_event` rows as IMPLEMENTED in v1.8.0 (the
   evidence file's path form and content) and report it in your record; Root closes them after the merge (R305).

## Verification

The proposal's step 6: the complete proposal, importer, ledger, provenance and resource Python suites; every import
verify; the ledger validator in its normal, `--require-accepted` and `--closure` modes (closure should fail only on
the 16 PLANNED rows; report its output); `python -B scripts/validate_repository.py --require-approved-identity`. No
Java or resource change is expected. If you make one, also run the unit tests and the GameTests it touches.

Commit on the branch; do not push. Root reviews, has the adoption reviewed, pushes for a sharded run and merges.

## Not in this task

The ledger, the status file and QUEUE; the 30 provenance records under D7 (W276); new art (W275); any change to a NEW
sound; any authorship finding beyond the EXCLUDED findings the proposal drafts.
