# TASK W268-fix: correct the REVIEW closure proposal before it goes to the owner (W268-R1 to R10, R297)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w268` (existing; branch `codex/v1.8.0-w268` at `144d34b44`) |
| Task it answers | [TASK-W268](TASK-W268-REVIEW-CLOSURE-PROPOSAL.md) (R296) and its review `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-review/REVIEW.md` (SHA-256 `846b2235661b9e344c8b5fe9b4fe07e3fb42b67186da95c3a8e71cbb41af1bd6`, APPROVE-WITH-CHANGES); read all of it, including its evidence files |
| Record | Append a "W268-fix" section to `docs/work/v1.8.0-root-queue/records/W268.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-fix/` |

**Root decision R297**, under the owner's standing delegation for design points: the proposal goes to the owner only
after these corrections, and it is rebased on the current shared head so its counts are the ones the owner would adopt.

1. **Rebase.** Merge the shared head named in your prompt (it contains W265 and W267: no IMPORT file remains, EXCLUDE
   270, REVIEW 95) into your branch. Make `build_proposal.py` take the base commit as an argument (W268-R5), regenerate
   every draft and count on that head, and keep refusing a base whose REVIEW set is not the expected 95 files.
2. **W268-R1 (Medium).** G4's owner question states plainly that the 1.12 suit could be dyed (leather-armour layers,
   the overlay files being the undyed parts) and that the modern suit cannot; accepting G4 accepts that loss.
3. **W268-R2 (Medium).** For the 18 G1 files and the one G8 file the review lists, name the actual 1.12 consumer and
   the modern block that replaced it, or say "no consumer in 1.12" for the four that had none. Recheck the rest of G1
   and G8 with the same method.
4. **W268-R3 (Medium).** `machinegeneric.png`: state the three consumers that used AR's bundled file and that the
   other 73 used LibVulpes's own copy.
5. **W268-R4 (Low).** The appearance text says what is lost: no running-state faces on the modern machines, and the
   enriched lava and force field are not animated.
6. **W268-R6 (Low).** Add an adoption step, with a test in `test_proposal.py`, that refuses to apply the findings while
   any reviewer is still the placeholder. Do not change `scripts/validate_v180_content_ledger.py`; name the validator
   change that adoption needs.
7. **W268-R7 (Low).** In the adoption steps, list the four ledger tests: the exact fixture change for each of the
   three that need one, and why the fourth passes once revision 7 is accepted.
8. **W268-R8 (Low).** G7 notes that `lathe.ogg` and `rollingmachine.ogg` are the same recording.
9. **W268-R9 (Low).** Trim `REVIEW-EVIDENCE.json` to the evidence a reviewer needs (bindings, consumers and the
   per-file scan results, not every non-binding text match), aiming at about 1.5 MB; keep the full scan under
   `D:/GitHub/ARCE-Task-Evidence/root-queue/W268-fix/` with its SHA-256 named in PROPOSAL.md. Keep the nested
   `.gitattributes`.
10. **W268-R10 (Info).** Apply the wording notes.

Add a short owner-facing summary at the top of PROPOSAL.md: the eight questions in plain English, one line each, with
what accepting each one gives up.

## Verification

`build_proposal.py` on the new base reproduces every committed output byte for byte; the proposal's tests; with the
drafts applied in a scratch copy, the content-ledger validator, all import verifies, the importer, ledger, provenance and
resource Python tests and the repository validator, with any remaining failures explained as before; on the branch,
the repository validator.

Commit on the branch; do not push. Root reviews and pushes; the reviewer of W268 is asked to recheck.

## Not in this task

Applying any draft, accepting the ADR revision, filling in the owner's name, changing the validator, D5, D7, Java or
resources.
