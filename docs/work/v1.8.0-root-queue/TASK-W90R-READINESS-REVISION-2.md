# TASK W90R: revision 2 of the readiness design, with the destination warm-up (Q1 option B)

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. This is design work only: write no
production or test code. Do not start subagents.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w90r-design` (from the shared branch) |
| Branch | `codex/v1.8.0-w90r-design` (local only; Root merges) |
| Subject | [records/W90-DESIGN.md](records/W90-DESIGN.md) revision 1 and [records/W90.md](records/W90.md) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W90-review/REVIEW.md` (Codex, APPROVE-WITH-CHANGES, SHA-256 `498009e8bfcaa586a0177373a059c9546e7e9583b2779a437a0a67b3fb00328d`), findings W90-R1 to W90-R5 and its Q1 answer |
| Root decision | `D:/GitHub/ARCE-Task-Evidence/root-queue/W90/ROOT-DECISION-R21.md` (SHA-256 `1322ab425a6aa87397f42abf49aac11f444beba6c89c38f5e61ddd49db1b91ee`), items 1 to 7 |
| W11b2 code | Read only through Git at `379525ea` (`git show 379525ea:<path>`, `git grep <pattern> 379525ea`). Do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` or `D:/GitHub/arce-v180-w11b2-c53`, where continuation 53 is working |
| Deliverable | `records/W90-DESIGN.md` revision 2 (edit it in place; keep a short "Changes from revision 1" list at the top) and an updated `records/W90.md` |

## What to do

1. **Apply R21 and answer every review finding.** For each of W90-R1 to W90-R5, say in the note what changed or
   why not. In particular:
   - section 2 and 7: remove option (A) as the recommendation and the automatic-return claim (R1); record R21 item 4;
   - the wording of R2 and R5;
   - one checked column set (R3, R21 item 2): its exact definition, the origin-column requirement for new governed
     admission, the executor hold for a recorded invalid end (reason name, log, recovery), and release of the origin
     and margin tickets;
   - the ticket budget as an explicit change (R4, R21 item 1), with the corrected bounds everywhere;
   - Q2 for governed launches only (R21 item 3): every pad path, F1's source, the refusal keys and how
     `RocketFlightRequestResult` carries them, and the English and Chinese text that a new source refusal needs.
2. **Design option (B), the destination warm-up, as a new section.** It must keep the full F1 and F2 census and
   every resource proof, and must not add a ticket after F2 other than item 1's. Settle at least:
   - when it starts (which request, after which existing validation: player, ownership, distance, rate, replay), and
     what is allocated (the server-selected destination's checked column set, one `TicketType` and key);
   - a fixed global cap, one warm-up per logical rocket, a non-renewable lifetime, and what a repeated request does
     (it must not add keys or extend an abandoned warm-up);
   - how the pad stays chosen during the warm-up, and how it interacts with continuation 53's pending governed pad
     reservations (read its TASK, `TASK-W11B2-CONTINUE-53.md` on branch `codex/v1.8.0-w11b2-c53`, through Git);
   - the request identity: either the player gets a "destination loading" refusal and launches again with a fresh
     request ID under the existing replay rules, or an internal continuation with a defined pending-request lifecycle;
     compare both and recommend one;
   - exact per-Level release on completion, F1 start, refusal, disconnect, rocket removal, expiry and restart;
   - fresh revalidation of the whole boundary before F1;
   - player-visible text (English and Chinese), logs (once per transition), and the inspect field;
   - its cost at the global cap, and tests (a real-entry cold-destination launch through F8, a repeated request, a
     disconnect, a cap refusal, a stale copy of L in the destination margin that F1 still refuses, a restart).
3. **Saved format:** confirm that neither change adds a saved record, key, schema or root, or name each with its
   migration.
4. **Risk and ADR text** for Root (R-035 and ADR-065 sentences), and remaining **open questions**, each with a
   recommended answer.

## Commands

- No Gradle, GameTest or server run: the machine is reserved for continuation 53's benchmark. `git show`, `git grep`
  and reading files are enough. Run `javap` only while `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` does not exist;
  otherwise read the sources jar with an unzip listing.
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files Git
  tracks; write other paths as code spans.
- Commit to the branch. Do not push.

## Not in this task

- Any production or test code; continuation 53's pad reservations; W83.
- ADRs, the risk register, status files and the ledger (Root).
