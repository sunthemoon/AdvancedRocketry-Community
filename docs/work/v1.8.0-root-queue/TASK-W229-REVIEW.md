# TASK W229-review: independent read-only review of the upstream-URL rule and the K1 import

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w229` at `355e63b15`, cut from `57a6c84c8`: W229's three commits (the rule `81785064c`, the K1 import `f699da5a4`, the record `6a35d152f`) and Root's commit `355e63b15` applying W229's audited drafts (asset plan, content ledger, ADR-061 section 4 item 9, `.gitattributes`, `docs/work/v1.8.0-c18d-k1/VERIFICATION.md`). Review worktree `D:/GitHub/arce-v180-w229-review`, a detached checkout of `355e63b15` |
| Task it answers | [TASK-W229](TASK-W229-UPSTREAM-URL-RULE-AND-K1.md), Root decision R222, owner decision D8 ("批准一条经审核的窄规则（推荐）") |
| Record | `docs/work/v1.8.0-root-queue/records/W229.md`; the drafts and audits in `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/` (`W229-drafts.md`, `W229-drafts-audit.py`, `W229-history-audit.json`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W229-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The rule is exactly as narrow as D8 allows.**
   - Only the two upstream addresses qualify. Try to get past it: a lookalike or another owner, a fork, a longer
     host, credentials, a port, subpaths, queries, fragments, percent-escapes, Unicode lookalikes, trailing
     punctuation, a URL glued to other text, several URLs in one subject or body, and an own URL next to "taken
     from".
   - Every other history trigger, the AR 16x set, the derivation re-run, hashes, format budgets, reference and
     licence checks are unchanged.
   - The evidence that the zmaster587 address is the upstream's own earlier address is sound, and its limits are
     stated honestly.
2. **K1.**
   - The OBJ and PNG are byte-identical to the pinned upstream files, and the NEW MTL comes from its authoring
     commit.
   - The provenance record is complete and stays PENDING_HUMAN_REVIEW.
   - Import verify passes. The runtime assets are exactly what the importer writes.
   - The `.gitattributes` lines keep the bytes on a Windows checkout with `core.autocrlf=true`, and change nothing
     else.
3. **The applied drafts.**
   - The asset plan and ledger rows are true to the evidence: REDESIGNED does not claim visual acceptance, and
     Forge parse and bake and V1 are pending.
   - The ADR-061 text matches the code.
   - No other row is touched. The allowlist is unchanged and still matches ADR-062's pin.
4. **K2 to K5.** Check that the history results in `W229-history-audit.json` are what the record says (29 of 30
   clear, and the etcher texture's ambiguous rename still triggers). Nothing beyond K1 is imported.
5. **Nothing weakened.** No test, assertion or check is loosened. Check for merge conflicts against the shared head
   and against W225's and W226's branches.

## Rerun

In your review worktree only:

- the importer's tests (`tests/test_import_v180_assets.py`) and the content-ledger tests;
- `import_v180_assets.py` in verify mode on the K1 record;
- the repository validator, and the content-ledger validator with `--require-accepted`.

No Gradle, GameTest, server or client.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W229-R1, ...), severity, file and line,
problem and suggested change. The commands you ran and their results. The SHA-256 of REVIEW.md on the last line.
