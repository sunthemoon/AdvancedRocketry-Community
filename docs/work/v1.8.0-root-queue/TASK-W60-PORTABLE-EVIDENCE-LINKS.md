# TASK W60: portable evidence references (C19-D)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w60-evidence-links` (from the shared branch) |
| Branch | `codex/v1.8.0-w60-evidence-links` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W60.md` (new) |
| Input | `records/W58.md` sections 3 and 5 (slice C19-D); the error list in `D:/GitHub/ARCE-Task-Evidence/root-queue/W58/audit-logs/` |

`python -B scripts/validate_repository.py --require-approved-identity` fails its Markdown phase:
5,597 links checked, 703 broken in 229 files (W58).
- 531 are absolute paths (`D:/GitHub/ARCE-Task-Evidence/...` and similar), which the validator
  rejects as unsafe.
- 165 point to `.zip` files inside the repository that were never committed. Most of them still
  exist as **untracked** files in the owner's main checkout `D:/GitHub/AdvancedRocketry-Community`
  (about 150 files, 2.6 GB, the largest 182 MB). They cannot be committed: AGENTS.md section 9 limits
  a file to 50 MB and an evidence folder to 100 MB.
- 7 point to other missing files.

## Root's rule (AGENTS.md section 9: external artifacts are recorded with location and SHA-256)

1. **An absolute link** becomes plain text: keep the link text and put the path in a code span,
   for example ``[review](D:/x/REVIEW.md)`` becomes ``review (`D:/x/REVIEW.md`)``. Do not add or
   remove any other word.
2. **A link to an uncommitted file inside the repository** (the ZIPs):
   - write a committed manifest `docs/work/v1.8.0-external-evidence/MANIFEST.md`. One row per
     untracked evidence file under `docs/` in the main checkout: repository-relative path, size in
     bytes, SHA-256, and the location "untracked in the owner's main checkout". Read the main checkout
     only; never add, move or delete a file there;
   - rewrite each such link like rule 1, and add "(see the external evidence manifest)" once per
     paragraph. Do not link to the manifest from inside a code span;
   - a target that is neither committed nor present in the main checkout gets "(not retained)"
     after the code span.
3. **The 7 other missing targets:** look for the file under another name, in another commit, or in
   `ARCE-Task-Evidence`. If found, link the committed file or apply rule 1. If not, apply rule 2's
   "(not retained)". List each case in the record.
4. **Sealed files.** A file listed in an in-repository `SHA256SUMS.txt` or `checksums.txt` is not
   edited (AGENTS.md section 10). For each one, add a correction file next to it, named after it with
   `-CORRECTION-W60.md`, holding the corrected links and the reason. Add the correction file to no
   checksum list. W58 found one: `docs/work/v1.8.0-c18a-sleep/reviews/memory-window-69/REPORT.md`.
   Check for others.
5. **Files you do not edit**, because another task or Root owns them:
   - `docs/status/*`: write the exact replacement lines into your record for Root;
   - the records of active tasks: `records/W11B2.md`, `records/W44I.md`, `records/W44L.md`,
     `records/W52F.md`. List their broken links in your record for Root.

   Everything else under `docs/` and the repository root is in scope.

## What to do

1. Write a script (kept in `D:/GitHub/ARCE-Task-Evidence/root-queue/W60/`, not committed) that
   applies rules 1 to 3 mechanically, and review its output by hand. It must change only link syntax,
   plus the two fixed phrases. Show this with a check that removes the changed links from the old and
   new text and compares the rest.
2. Commit in reviewable parts: the manifest; then the absolute-link rewrite; then the ZIP and
   missing-target rewrite; then the correction files.
3. Run the validator's Markdown phase until it reports no broken link outside the excluded files.
   W58's counter raised `MAX_MARKDOWN_ERRORS` in memory, without a repository change, to see the
   whole list. Do that again; do not change the validator.

## Commands

- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Expect the
  v0.1.0 asset failures until W52F merges; report every FAIL line.
- `python -B scripts/validate_v1plus_planning.py`, `python -B scripts/validate_v180_content_ledger.py
  --require-accepted`, and `git diff --check`.
- The Python tests that read the edited documents: run `python -B -m unittest discover -s tests` only
  for the modules whose fixtures name an edited file. Find them with `git grep`, and list them.
- No Gradle run is needed.

## Not in this task

- The validator itself, status files, the ledger, ADR decisions and sealed files (rule 4 covers them).
- Moving or committing any ZIP.
