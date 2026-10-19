# TASK W109-review: independent read-only review of W109 (cargo C-A with C-D's resources) before merge

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. Codex
wrote W109 and both Claude slots are busy, so this is a fresh Codex session with no context from the author.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w109` at `37c3c81c` (base `e7649211`). Your review worktree `D:/GitHub/arce-v180-w109-review` is a detached checkout of `37c3c81c`; read the diff with `git diff e7649211..37c3c81c` there |
| Task it answers | [TASK-W109](TASK-W109-CARGO-C-A-FLUID-DEVICES.md), including Root's decision that C-A takes C-D's resource part |
| Contract | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) sections 5.1, 5.2, 5.4, 5.5 and 7 and the C-A and C-D rows of section 6; [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) sections 3 and 4 where 5.1 applies them |
| Risk and ADR | R-035's cargo paragraph and ADR-065's "W11c and W11d implementation note" |
| Record | `docs/work/v1.8.0-root-queue/records/W109.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W109/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W109-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The root** `arce_rocket_fluid_cargo`: schema 1 and every bound of section 5.1 (32 KiB, depth 20, 2,048 nodes;
   the tank envelope of 8 KiB; the escrow section of 256 bytes and one marker); the role equal to the block's; an
   unknown, future, invalid or mismatched root kept raw and protecting the device.
2. **Save protection** under section 7 and R-035: only an observed native incompatibility uses `GuardedChunkSaves`;
   a lossless root or one over the work budget is saved verbatim; Level saturation at 256 coordinates. Any new save
   refusal beyond what R-035 states is a finding.
3. **The capability, menu and drop carrier:** fill-only and drain-only roles; simulation without side effects; zero for
   stale, off-thread or reentrant calls; owner-only menu within 8 blocks and the held-container exchange; the carrier's
   bounds and owner-on-placement rule; `onlyOpCanSetNbt` and the refused `BlockEntityTag`.
4. **Protection:** an escrow-locked or unsupported root refuses player removal before loot, leaves the explosion list,
   drops nothing and refuses extraction.
5. **Admission is unwired:** nothing in production starts an escrow operation from these devices.
6. **Resources:** recipes exactly as section 5.5 (with the existing `EmptyTankIngredient`), loot, models, blockstates,
   en_us and zh_cn text, the preauthoring and generated provenance, and the art drawn in code (no copied asset).
7. **Tests:** do the A0 and A1 tests cover the C-A row (local capabilities; simulation, stale and reentrant calls;
   ownership, range and unload; menu replay; carrier ownership; protected removal; non-operator NBT; cut-G2; raw
   preservation, work-budget oversize, observed native veto, Level saturation)? Any weakened or edited existing test?
8. Class sizes over 500 lines and their responsibilities.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: the new
cargo device test classes and `RegisteredContentResourcesTest` and `RegisteredItemReachabilityTest`; and
`python -B scripts/validate_v180_provenance.py` if it runs without network. Run no GameTest, `runData` or server, and
take no heavy-JVM lock. Delete the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W109-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
