# TASK W99-review: independent read-only review of cargo leaf C-I (recognition identity)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: change
nothing in any repository worktree. Do not start subagents. Claude implemented W99, so this is a fresh Codex session.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w99` at `3ef5771d` against the shared branch at `10bc96e6`: `git diff 10bc96e6..3ef5771d` |
| Task it answers | [TASK-W99](TASK-W99-CARGO-C-I-IDENTITY.md) |
| Design (frozen) | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) section 2.5 and section 6's C-I row |
| Record | `records/W99.md` in the checkout |
| Checkout | `D:/GitHub/arce-v180-w99-review`, detached at `3ef5771d`, made for you; do not commit in it |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W99/evidence/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W99-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The digest:** exactly section 2.5's definition (prefix, fields, payload adapter ID but not payload data), cached,
   never persisted; the content hash byte-identical (W99 pins two prior values; check that the shared field writer
   change in `RocketSnapshotHasher` keeps every existing hash).
2. **The predicate:** `sameSnapshotId && (sameContentHash || (epoch > 0 && sameStructureDigest &&
   currentLedgerBinding))` at both sites, inside the existing owner, identity, placement and state checks; a neutral
   rocket never reads the ledger; every fence, floor, veto and governed routing of section 2.5 still applies; whether
   any other recognition site (the record's note that some forward-flight paths act on the first match without the
   fence) can now accept a changed copy, and whether that matters before C-E.
3. **Tests:** whether the A0 and A1 cases cover what the C-I row lists, and whether they fail without the new branch
   (W99 says 3 of 6 do).
4. **Scope:** no saved-format change, no change to an acknowledgement order, a proof, a lease, a fence, a floor or the
   replay exclusion; the files outside the listed scope.
5. **Rerun** the two new unit classes and `gradlew.bat --offline test --tests '*rocket.model*' --tests
   '*RocketTransfer*'` in the checkout (unit tests need no lock); take no lock and run no GameTest or server.

## Output format

- A verdict: MERGE, MERGE-WITH-FOLLOWUP, or FIX-FIRST.
- Findings, each with an ID (W99-R1, ...), a severity, the file and line, the problem, and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
