# TASK W117-review: independent read-only review of W117 (cargo leaf C-E1) before merge

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. Codex
wrote W117 and both Claude slots are busy, so this is a fresh Codex session with no context from the author (as for
W109-review and W114-review).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w117` at `18b5f795` (base `2e44edcc`; tested source `9a46b712`). Your review worktree `D:/GitHub/arce-v180-w117-review` is a detached checkout of `18b5f795`; read the diff with `git diff 2e44edcc..18b5f795` there |
| Task it answers | [TASK-W117](TASK-W117-CARGO-C-E1-ADMISSION-QUEUE.md) (Root's split of C-E into C-E1 to C-E3) |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 3.1, 3.2 (items 1 to 8) and 3.3 (with R40 and R41); [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) sections 2 and 4 (the fill admission and queue C-E1 generalizes) |
| Record | `docs/work/v1.8.0-root-queue/records/W117.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W117/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W117-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The extraction** (`af45c245`): moving `prepareTransfer`, `prepareCargo` and `fillRefusal` out of
   `EscrowCoordinator` changes no behaviour; W11b3's `prepareFill` results and tests are unchanged.
2. **The refusal mapping** (`3780cef3`, `eb40e9ba`, `42aeb0e1`): for cargo, an `append` failure and a before-wrapper
   mismatch are STALE, the marker limit `ROCKET_LIMIT`, a full root `ESCROW_FULL`, and the candidate's own component
   over a ceiling `CARGO_TOO_LARGE`; the closed `Fill` contract is unchanged. Are the cases told apart by type, not by
   message text? Does any input that was refused before now pass? `eb40e9ba` continues selection after a size refusal:
   is that what section 2.3 and 5.2 allow, and can it select a candidate the design would not?
3. **The switches** (`5b47e955`): one per cargo kind, off in production, separate from the fuel switch, checked before
   any capture, ticket or preparation, and turned on only through a gated test hook.
4. **The device wiring** (`f237f718`): the fluid devices go through the preparation ticket, the native capture and
   `prepareCargo` on the owner thread; a refusal changes nothing; QUEUED shows the pending status.
5. **First-ledger creation** (`61289d17`): the same order and checked baseline as B3-E's for fills. Can a crash or a
   definite or UNKNOWN write outcome leave a ledger, a queue entry or a locked endpoint that nothing resolves?
6. **The shared queue** (`50c65b09`, `EscrowResourceQueue` replacing `EscrowFillQueue`): fill pacing unchanged (8
   visits, one fuel capture per tick, 256 slots); at most one whole-snapshot reconstruction per tick on the unchanged
   session budget; fuel entries charge no reconstruction; a maximum-size rocket is not starved. Compare against section
   3.2 item 7's 64 commits per tick and say whether anything differs.
7. **The turn tokens** (`bc289907`, `6505e05e`, `05b4c483`): RAM only, bounded by registered loaded work, discarded on
   unload or lifecycle end, never overriding a hold or becoming durable; the fuel loader's lifecycle change does not
   alter its own admission.
8. **Tests and limits** (AGENTS.md section 5): list every edited pre-existing test file (the admission and capacity
   classes among them) and say for each whether an assertion is weaker or only re-pinned; no limit, budget or timeout
   relaxed. Flag any class over 500 lines.
9. **The full GameTest's two failures** (`ClassicScaleGameTests.sixteenOfEveryKindStayIdleThenHalfResumeAfterRealUnload`
   at scale stage 7, and `PlanetaryAdmissionGameTests.closedOrbitStationCanDepartButStillRequiresLiveMembership`) and
   the 18 host-overload warnings. Both tests have failed before in other workers' local runs under load (W105, W98,
   W118). Could anything in W117's diff (the fuel loader's lifecycle, the queue, the runtime) reach either test's path?
10. **What C-E1 does not show.** The record says A1 cannot show a competing device served after a durable PREPARED
    operation, because only C-E2 resolves it. Is the A0 coverage of the token handoff adequate until then?
11. **Merge with the shared branch:** preview with `git merge-tree` against the shared head
    (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`), which since W117's base added W116, W107B and the
    queue records; W118 (D-P) will also touch `EscrowCargoEligibility` and `EscrowCargoPreparedCapture`. Say what the
    merge must keep from each side.
12. **The record** against the code.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W117's
eleven isolated classes, then the `resourceescrow`, `rocket.cargo` and `rocket.fuel.loader` packages. You may add a
probe test to check a finding; keep each probe in the output folder. Before each Gradle run, check that at least 6 GiB of
memory is free and wait if not; run one Gradle process at a time, and no GameTest, `runData` or server; take no
heavy-JVM lock. Delete the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W117-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
