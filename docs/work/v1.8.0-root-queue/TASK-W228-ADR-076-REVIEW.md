# TASK W228: independent review of ADR-076 before its acceptance is recorded

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and an independent
reviewer: read only, change no tracked file, make no commit.

| Item | Value |
|---|---|
| Subject | [ADR-076](../../decisions/ADR-076-DATA-PACK-RELOAD-ENTRIES-OUTSIDE-THE-MOD.md), the docs/17 waiver for the data-pack reload entries that vanilla and Forge write, as on the shared branch |
| Owner decision | D17 in [OWNER-DECISIONS-20261031](records/OWNER-DECISIONS-20261031.md): accept for exactly the listed vanilla and Forge sites, expiring at the v1.9.0 Gate; the unbounded length of the two platform joined-list messages stays a recorded risk |
| Rule | [docs/17](../../17-V1PLUS-QUALITY-BUDGETS.md) section 10 item 6: a session other than the implementer reviews any threshold change. W193 wrote the ADR; no independent review of it is on record |
| Sources | [W193's record](records/W193.md); `scripts/gametest_expected_log.json`; `DataPackOverLimitReloadGameTests`; the evidence directory `D:/GitHub/ARCE-Task-Evidence/root-queue/` (`W192/vanilla-lines.txt`, `W192/w179-b4b2-1-entry-sizes.txt`, `W192/tools/entry_sizes.py`, `W179/heavy/b4b2-1.latest.log`); the Forge 1.20.1-47.4.10 userdev jar in the Gradle cache |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W228/REVIEW.md` with its SHA-256 |

**Root decision R221.** The owner accepted ADR-076 on 2026-10-31. Before Root records the acceptance, a session
other than W193 checks the ADR against docs/17 section 10.

## What to check

1. **Section 10 items 1 to 5.** The ADR states the original threshold, the failure and the real risk, the new
   threshold with data, whether it is temporary, and the version that restores the original threshold.
2. **Scope.** Each of the six sites exists at the stated class and line in the Forge 47.4.10 jar and writes the stated
   level and message. Recompute the hash of `vanilla-lines.txt`. The exclusions really keep every entry the mod
   writes (the recipe refusal summary, the pattern catalogs and the lookup-time logs) under docs/17. No other vanilla
   or Forge site that the mod's refusals can cause is left out unmentioned.
3. **Data.** Recompute the table from the W179 log with W192's tool, and the evidence hashes the ADR cites.
4. **Bound.** The "bound that still holds" claims are true. The two joined-list messages are named as unbounded in
   length and recorded as a risk, not waived as bounded.
5. **Verification.** The five expectations the ADR names are in `scripts/gametest_expected_log.json` with exact
   counts, so a change in their number fails the log check. The GameTest asserts Forge's INFO line once per refused
   recipe.
6. **Expiry and renewal.** These are concrete enough for the v1.9.0 Gate to act on.

## Verdict

APPROVE or BLOCK, with findings ranked Critical, High, Medium or Low. Each finding gives the evidence (file and line,
command and output) and the smallest correction. A Medium or higher finding blocks the recording of the acceptance
until Root corrects it.

## Not in this task

Any tracked file, the ADR's acceptance itself, the mod's own log bounds, other waivers.
