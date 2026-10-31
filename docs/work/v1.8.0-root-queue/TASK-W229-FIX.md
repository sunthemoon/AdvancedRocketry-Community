# TASK W229-fix: the review's two URL-rule findings and the evidence correction

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w229` (W229's; continue on its branch) |
| Branch | `codex/v1.8.0-w229`, now at `355e63b15` (Root's commit applying W229's drafts; pushed). Local commits only; Root pushes and merges |
| Record | `docs/work/v1.8.0-root-queue/records/W229.md`: append "R229 review corrections" |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W229-review/REVIEW.md` (SHA-256 `e96edbff…25984111`): APPROVE-WITH-CHANGES, findings W229-R1 and W229-R2 (Medium) and W229-R3 (Low) |

**Root decision R229.** Fix all three on W229's branch before Root merges it.

1. **W229-R1: a left boundary.**
   - Keep the original URL-start detector unchanged.
   - Grant the exemption only when the whole URL token has an explicit permitted boundary on both sides.
   - A glued prefix such as `prefixhttps://…` or `x-https://…` must still trigger, in the subject and in the body.
   - Keep the real `0c0017bc` subject clear.
2. **W229-R2: ASCII only inside the exemption.**
   - The detector keeps its baseline Unicode `re.I` behaviour.
   - Only the comparison with the two allowed addresses and their delimiters is ASCII.
   - Lookalikes such as `httpſ://example.org/other` and `HTTPſ://github.com/another/AdvancedRocketry` must trigger
     exactly as before the rule existed. Compare them with the baseline in the subject and the body.
3. **Tests.** Add the review's probe cases as regression tests that fail first on `355e63b15`.
4. **ADR-061 text.** If the rule's wording in ADR-061 section 4 item 9 (as applied in `355e63b15`) needs a change
   for the left boundary, put the exact replacement in
   `D:/GitHub/ARCE-Task-Evidence/root-queue/W229/W229-fix-drafts.md` for Root to apply. Do not edit ADR-061.
5. **W229-R3: the evidence correction.**
   - Root's own rerun of `W229-drafts-audit.py` on 2026-10-31 at about 22:50, against `6a35d152f`, replaced the
     audit outputs that your record had pinned.
   - Add a dated correction to the record. It states this, lists the replaced files with their current hashes,
     and says the original bytes are not recoverable unless you still hold them.
   - From now on, keep each audit run's outputs under a distinct name.
6. **Re-verify K1.** Run import verify and the K2 to K5 history rerun with the changed tool, and state any change in
   their results.

## Verification

- The importer's and the content ledger's Python tests.
- Import verify.
- The repository validator, and the content-ledger validator with `--require-accepted`.

## Not in this task

K2 to K5 imports, the etcher rename, status files and the ledger.
