# TASK W56: verify three PLANNED ledger rows that look implemented (audit only)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w56-ledger-candidates` (from the shared branch) |
| Branch | `codex/v1.8.0-w56-ledger-candidates` (local only; Root merges) |
| Output | `docs/work/v1.8.0-root-queue/records/W56.md` (new). No production, ledger or status change |
| Ledger | `docs/work/v1.8.0-content-ledger.csv`; validator `scripts/validate_v180_content_ledger.py --require-accepted` |

Three rows are still PLANNED, but merged slices appear to implement them:

| Row | Likely delivery |
|---|---|
| `config:CATEGORY_GENERAL.makeMaterialsForOtherMods` (C16d) | common item tags in recipes; see W35 and the C16d records |
| `enchantment:spacebreathing` (C18b) | the `space_breathing` enchantment and its chemical reactor application; see W32 |
| `libvulpes:java/blockMotor` (C16a) | the four motor tiers and the motors tag (ADR-064 section 4.1); see W41 and C16a-07a |

## What to do

For each row:
1. Find what the shared branch actually ships for it: registry IDs, config keys, tags, recipes,
   tests, and the commit and record that delivered it. Cite file and line.
2. Read the ledger's other delivered rows, their dispositions (IMPLEMENTED, REDESIGNED and so on),
   the targets, and the evidence column. Propose a row in exactly that style:
   - the disposition;
   - the target, as plain text if it is not a registry ID (the validator treats any target containing
     `advancedrocketrycommunity:` as a registry ID);
   - the decision ADRs;
   - the notes;
   - the evidence file (an existing `docs/work/v1.8.0-*/VERIFICATION.md`).
3. Write a short evidence paragraph, like the existing VERIFICATION addenda, naming the commits,
   hosted runs and tests.
4. If a row is **not** fully delivered, say exactly what is missing and do not propose a delivery.

Put the proposals in your record as a JSON block with this shape:
`{"verification": ..., "section": ..., "rows": [{"unit", "disposition", "target", "decision", "notes"}]}`.
Root applies it with its own script.

Then check the proposal without committing the change:
- apply it to a scratch copy of the ledger outside the repository;
- run the validator against that copy, if the validator can take a path;
- otherwise describe how Root should check it.

## Commands

- `python -B scripts/validate_v180_content_ledger.py --require-accepted` on the unchanged tree.
- `git diff --check`. No Gradle run is needed.

## Not in this task

- Any change to the ledger, status files, production code or tests.
