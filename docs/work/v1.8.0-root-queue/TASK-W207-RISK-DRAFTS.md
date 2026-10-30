# TASK W207-risk-drafts: exact R-035 and W90-DESIGN text for W207's terrain stage

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. You write drafts
only, outside the repository; change no tracked file and make no commit.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w207-review` (W207's review checkout at `e2dbbb74c`, for W207's code and record) |
| Targets | the shared tree's `D:/GitHub/AdvancedRocketry-Community/docs/11-RISK-REGISTER.md` (R-035) and `D:/GitHub/AdvancedRocketry-Community/docs/work/v1.8.0-root-queue/records/W90-DESIGN.md`; W207 changes neither, so these are the merged state. Read them; do not edit them |
| Inputs | finding W207-R1 of `D:/GitHub/ARCE-Task-Evidence/root-queue/W207-review/REVIEW.md` (SHA-256 `7408cee6…d5abe2a2`); `docs/work/v1.8.0-root-queue/records/W207.md` and `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/RocketPadTerrainPreparation.java` in the worktree |
| Format | `D:/GitHub/ARCE-Task-Evidence/root-queue/W204-review/input/W204-drafts.md` and its audit script |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W207-review/input/W207-drafts.md`, plus an audit script and its JSON result in the same folder |

R-035 is one Markdown table row, in Chinese. Write exact `Target` / `Replace exactly` / `With` replacements for what
W207-R1 asks, checking each claim against W207's code:

1. **R-035:** a scoped W207 amendment covering the six points W207-R1 lists (TERRAIN counting toward the same
   ceilings; candidate-footprint tickets at level 33 with no reservation, named-pad claim or F1 acceptance, and one
   pending selection per tick; promotion to the level-31 selected-margin warm-up keeping the start, lifetime and
   request; the 200 to 600 tick deadline spanning both stages, with vanilla expiry only a per-ticket backstop; repeated
   requests during TERRAIN; the transition logs and the inspect line). Qualify the sentences the review names
   ("目的地预热", the three-state quota wording, "就为选定目的地", "只在开始时添加一次", and the recheck of the kept
   candidate) as applying to the selected stage. Leave the 400-tick flight-ticket rule unchanged.
2. **W90-DESIGN:** a short note at lines 275 to 318 and 338 to 349 (or the nearest unique anchors) marking what W207
   supersedes: section 7.1's synchronous load is historical, and section 7.4's reservation rules apply once selection
   succeeds. Do not rewrite the historical proposal.

Keep the R-035 row on one line with the same column count, in complete Chinese sentences, without closing the risk or
any Gate. W207-R2 (the record's range) is Root's and not part of this task. The audit script checks that every anchor
is unique in the current file, that nothing overlaps, and that the R-035 row keeps its column count; run it and keep
its JSON result.

## Not in this task

Any tracked file, W207-R2, ADRs, status files and the ledger.
