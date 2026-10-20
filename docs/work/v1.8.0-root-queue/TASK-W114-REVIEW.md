# TASK W114-review: independent read-only review of W114 (R40 receipt charges) before merge

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. Codex
wrote W114 and both Claude slots are busy, so this is a fresh Codex session with no context from the author.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w114` at `a447eaea` (base `43ee0aa0`; tested source `f54ad8f0`). Your review worktree `D:/GitHub/arce-v180-w114-review` is a detached checkout of `a447eaea`; read the diff with `git diff 43ee0aa0..a447eaea` there |
| Task it answers | [TASK-W114](TASK-W114-RECEIPT-CHARGE-R40.md), which implements Root decision R40 |
| Decision and contract | R40 in [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) (the R40 amendment paragraph) and W113's `D:/GitHub/ARCE-Task-Evidence/root-queue/W113/DECISION-NOTE.md`; R-035 in `docs/11-RISK-REGISTER.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W114.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W114/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W114-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The actor bound (R40 b).** `EscrowSettlementIntent.requireNewActorName` (1 to 16 characters, 33 to 126) is meant
   to match the 1.20.1 login rule. Check it against the Minecraft 1.20.1 and Forge 47 source in the Gradle cache (the
   login handler's name check and the profile length). Is it applied to every path that creates a new decision (the
   operator commands, `preview`, `floorPreview`, the command actor, any other caller), and to none that decodes a stored
   one? Do persisted decisions and receipts with the old 128-character, NUL or non-ASCII names still decode and
   re-encode to identical bytes? Is the refusal for a nonconforming profile (offline mode, a proxy) usable, and is the
   console fallback real?
2. **The cargo charge (R40 c).** `EscrowSettlementCapacity.markerLimit` charges one marker when any operation in the
   **component** is cargo. Can a connected component contain a cargo operation and a non-cargo operation on a different
   native rocket (a shared device endpoint, a chained operation, a retained record, a ledger written before cargo
   exclusivity)? If so, that rocket would be charged one marker instead of five. Say whether the limit should be per
   rocket instead, and whether `requireAdmission` and the shared preview agree for every kind. Check that the C1X
   control (charging on the admission path only) fails as the record says.
3. **The ordering.** The retained-marker `OTHER_TRANSFER_PENDING` refusal now precedes `fillRefusal`. Does any fill or
   cargo case change its result other than the intended one?
4. **The sizes.** Every kind fits on every registered Level, guarded and unguarded, measured with the real encoders;
   the worst guarded receipts at Tau Ceti g are about 1,557 bytes (cargo) and 1,833 (fuel). No ceiling, capacity,
   schema, format or decode bound changed. Is any Level or kind missing from the 90-case matrix?
5. **Edited pre-existing tests (AGENTS.md section 5).** List every pre-existing test file the diff edits and say for
   each whether an assertion is weaker or only re-pinned as R40 requires. In particular,
   `EscrowSettlementCapacityTest`'s renamed test now lets `preflight` pass at six to eight markers and pins the five-marker
   limit through `requireAdmission`: is that a legitimate consequence of the smaller actor charge, or a relaxation?
   Also the historical-name fixtures (`EscrowTestFixtures.storedActor`) and the GameTest actor rename.
6. **Merge with the shared branch.** W114 will be merged into `f1223c02`, which includes W112 (`16b368f6`) and W115
   (`f8ddfc25`; W115 also edits `EscrowTransferAdmission`). Read the shared versions with `git show f1223c02:<path>`,
   preview with `git merge-tree`, and say what the merge must keep from each side.
7. **The record** against the code: does it claim anything the diff does not do?

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`:
`EscrowReceiptChargeTest`, every edited pre-existing test class, and then the `resourceescrow` package. You may add a
probe test to check a finding; keep each probe in the output folder. Before each Gradle run, check that at least 6 GiB
of memory is free and wait if not; run one Gradle process at a time, and no GameTest, `runData` or server; take no
heavy-JVM lock. Delete the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W114-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
